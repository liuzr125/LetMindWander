package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.mapper.AdminAuditMapper;
import com.zhixing.mapper.AdminAuthMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AdminAuthService.class);
  private static final String SUPER_ROLE_ID = "00000000000000000000000000a1";
  private final AdminAuthMapper mapper;
  private final AdminAuditMapper auditMapper;
  private final AppProperties properties;

  public AdminAuthService(
      AdminAuthMapper mapper, AdminAuditMapper auditMapper, AppProperties properties) {
    this.mapper = mapper;
    this.auditMapper = auditMapper;
    this.properties = properties;
  }

  @Transactional
  public Map<String, Object> login(String rawUsername, String rawPassword) {
    String username = clean(rawUsername), password = rawPassword == null ? "" : rawPassword;
    if (username == null || password.length() == 0) throw bad("ADMIN_LOGIN_REQUIRED", "请输入账号和密码");
    bootstrap();
    List<Map<String, Object>> rows = mapper.selectAccountForLogin(username);
    if (rows.isEmpty()
        || !truth(rows.get(0), "enabled")
        || !AdminPasswordHasher.matches(password, text(rows.get(0), "password_hash")))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_INVALID", "账号或密码错误");
    Map<String, Object> row = rows.get(0);
    String token = CryptoUtils.randomToken(36), hash = sha256Hex(token);
    Instant now = Instant.now(), expires = now.plus(properties.getAdminSessionTtl());
    mapper.insertSession(CryptoUtils.randomId(), text(row, "id"), hash, expires, now, now);
    mapper.updateLastLogin(now, text(row, "id"));
    AdminPrincipal principal =
        principal(
            text(row, "id"), text(row, "username"), text(row, "role_code"), text(row, "role_name"));
    LOG.info("Administrator session issued");
    return sessionView(token, expires, principal);
  }

  public AdminPrincipal require(String token, String menu) {
    AdminPrincipal principal = authenticate(token);
    if (principal == null)
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_SESSION_INVALID", "登录已失效，请重新登录");
    if (menu != null && !principal.hasMenu(menu))
      throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MENU_FORBIDDEN", "当前角色没有该菜单权限");
    return principal;
  }

  // 每次认证重新读取账号、角色和菜单状态，使改权、停用和密码重置即时生效。
  public AdminPrincipal authenticate(String token) {
    if (clean(token) == null) return null;
    String hash = sha256Hex(token);
    List<Map<String, Object>> rows = mapper.selectAuthenticatedAccount(hash);
    if (rows.isEmpty()) return null;
    Map<String, Object> row = rows.get(0);
    mapper.touchSession(hash);
    return principal(
        text(row, "id"), text(row, "username"), text(row, "role_code"), text(row, "role_name"));
  }

  /** 撤销摘要匹配的会话；不记录原始令牌。 */
  public void logout(String token) {
    if (clean(token) != null) mapper.revokeToken(sha256Hex(token));
  }

  public Map<String, Object> me(AdminPrincipal principal) {
    return sessionView(null, null, principal);
  }

  public Map<String, Object> administration() {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    out.put("menus", menus());
    out.put("roles", roles());
    out.put("accounts", accounts());
    return out;
  }

  @Transactional
  public Map<String, Object> createRole(
      String rawName, String rawDescription, List<String> menuCodes, AdminPrincipal actor) {
    String name = clean(rawName), description = clean(rawDescription);
    if (name == null || name.length() > 80) throw bad("ROLE_NAME_INVALID", "角色名称不能为空且不能超过 80 个字符");
    if (description != null && description.length() > 255)
      throw bad("ROLE_DESCRIPTION_INVALID", "角色说明不能超过 255 个字符");
    if (mapper.countRolesByName(name) > 0)
      throw new ApiException(HttpStatus.CONFLICT, "ROLE_NAME_EXISTS", "该角色名称已存在");
    Set<String> requested = validMenuCodes(menuCodes);
    String id = CryptoUtils.randomId(),
        code = "CUSTOM_" + id.substring(0, 12).toUpperCase(Locale.ROOT);
    mapper.insertRole(id, code, name, description);
    for (String item : requested) mapper.assignRoleMenu(id, item);
    audit(actor, "admin_role_create", "admin_role", id, "{\"menuCount\":" + requested.size() + "}");
    Map<String, Object> out = administration();
    out.put("createdRoleId", id);
    return out;
  }

  @Transactional
  public Map<String, Object> setRoleMenus(
      String roleId, List<String> menuCodes, AdminPrincipal actor) {
    if (SUPER_ROLE_ID.equals(roleId)) throw bad("SUPER_ROLE_LOCKED", "超级管理员默认拥有全部菜单权限，不能移除");
    if (mapper.countEnabledRole(roleId) == 0) throw bad("ROLE_NOT_FOUND", "角色不存在或已停用");
    Set<String> requested = validMenuCodes(menuCodes);
    // 菜单替换和审计在同一事务内提交，失败时不会留下空权限角色。
    mapper.clearRoleMenus(roleId);
    for (String code : requested) mapper.assignRoleMenu(roleId, code);
    audit(
        actor,
        "role_menu_update",
        "admin_role",
        roleId,
        "{\"menuCount\":" + requested.size() + "}");
    return administration();
  }

  @Transactional
  public Map<String, Object> createAccount(
      String rawUsername, String password, String roleId, AdminPrincipal actor) {
    String username = clean(rawUsername);
    if (username == null || !username.matches("[A-Za-z][A-Za-z0-9_.-]{2,39}"))
      throw bad("ADMIN_USERNAME_INVALID", "账号需为 3–40 位字母开头的字母、数字、点、下划线或连字符");
    validatePassword(password);
    if (mapper.countEnabledRole(roleId) == 0) throw bad("ROLE_NOT_FOUND", "请选择可用角色");
    try {
      mapper.insertAccount(
          CryptoUtils.randomId(), username, AdminPasswordHasher.hash(password), roleId);
    } catch (Exception duplicate) {
      throw new ApiException(HttpStatus.CONFLICT, "ADMIN_USERNAME_EXISTS", "该管理员账号已存在");
    }
    audit(
        actor,
        "admin_account_create",
        "admin_account",
        username,
        "{\"roleId\":\"" + roleId + "\"}");
    return administration();
  }

  @Transactional
  public Map<String, Object> resetAccountPassword(
      String accountId, String password, AdminPrincipal actor) {
    validatePassword(password);
    List<Map<String, Object>> rows = mapper.selectAccountWithRole(accountId);
    if (rows.isEmpty()) throw bad("ADMIN_ACCOUNT_NOT_FOUND", "管理账号不存在");
    Map<String, Object> target = rows.get(0);
    if ("SUPER_ADMIN".equals(text(target, "role_code"))
        && !"SUPER_ADMIN".equals(actor.getRoleCode()))
      throw new ApiException(HttpStatus.FORBIDDEN, "SUPER_ADMIN_PROTECTED", "不能修改超级管理员的密码");
    mapper.updatePassword(AdminPasswordHasher.hash(password), accountId);
    // 密码变更必须同步撤销全部旧会话，不能只更新密码摘要。
    mapper.revokeAccountSessions(accountId);
    audit(actor, "admin_password_reset", "admin_account", accountId, "{}");
    Map<String, Object> out = administration();
    out.put("currentSessionRevoked", accountId.equals(actor.getAccountId()));
    return out;
  }

  // 仅在显式登录时懒初始化配置的引导管理员，不在应用启动时创建账号。
  private void bootstrap() {
    String username = clean(properties.getAdminBootstrapUsername()),
        password = properties.getAdminBootstrapPassword();
    if (username == null || password == null || password.length() < 10)
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "ADMIN_BOOTSTRAP_REQUIRED",
          "请配置 ADMIN_BOOTSTRAP_USERNAME 与安全的 ADMIN_BOOTSTRAP_PASSWORD 后登录");
    Integer count = mapper.countAccountsByUsername(username);
    if (count != null && count > 0) return;
    mapper.insertAccount(
        CryptoUtils.randomId(), username, AdminPasswordHasher.hash(password), SUPER_ROLE_ID);
  }

  private AdminPrincipal principal(
      String accountId, String username, String roleCode, String roleName) {
    Set<String> menus = new LinkedHashSet<String>();
    for (Map<String, Object> row : mapper.selectAccountMenus(accountId))
      menus.add(text(row, "code"));
    return new AdminPrincipal(accountId, username, roleCode, roleName, menus);
  }

  private Map<String, Object> sessionView(String token, Instant expires, AdminPrincipal principal) {
    Map<String, Object> out = new LinkedHashMap<String, Object>();
    if (token != null) out.put("token", token);
    if (expires != null) out.put("expiresAt", expires);
    out.put("username", principal.getUsername());
    out.put("roleCode", principal.getRoleCode());
    out.put("roleName", principal.getRoleName());
    out.put("menus", new ArrayList<String>(principal.getMenuCodes()));
    out.put("menuItems", menus());
    return out;
  }

  private List<Map<String, Object>> menus() {
    return mapper.selectMenus();
  }

  // 一次查角色、一次查全部角色菜单，避免角色数增加时产生 N+1 查询。
  private List<Map<String, Object>> roles() {
    List<Map<String, Object>> roles = mapper.selectRoles();
    Map<String, List<String>> assignments = new LinkedHashMap<>();
    for (Map<String, Object> row : mapper.selectAllRoleMenus())
      assignments
          .computeIfAbsent(text(row, "role_id"), key -> new ArrayList<>())
          .add(text(row, "code"));
    for (Map<String, Object> role : roles)
      role.put("menuCodes", assignments.getOrDefault(text(role, "id"), Collections.emptyList()));
    return roles;
  }

  private List<Map<String, Object>> accounts() {
    return mapper.selectAccounts();
  }

  private void audit(AdminPrincipal actor, String action, String type, String id, String metadata) {
    auditMapper.insertAudit(
        CryptoUtils.randomId(),
        actor.getAccountId(),
        action,
        type,
        id,
        "succeeded",
        metadata,
        Instant.now().plus(Duration.ofDays(180)));
    LOG.info("Administrator action audited: action={}, targetType={}", action, type);
  }

  private void validatePassword(String password) {
    if (password == null || password.length() < 10 || password.length() > 128)
      throw bad("ADMIN_PASSWORD_INVALID", "密码长度需为 10–128 位");
  }

  private Set<String> validMenuCodes(List<String> menuCodes) {
    Set<String> requested = new LinkedHashSet<String>();
    if (menuCodes != null)
      for (String code : menuCodes) {
        String value = clean(code);
        if (value != null) requested.add(value);
      }
    Set<String> valid = new LinkedHashSet<String>();
    for (Map<String, Object> menu : menus()) valid.add(text(menu, "code"));
    if (!valid.containsAll(requested)) throw bad("MENU_NOT_FOUND", "包含不存在或已停用的菜单");
    return requested;
  }

  private String clean(String value) {
    if (value == null) return null;
    String clean = value.trim();
    return clean.isEmpty() ? null : clean;
  }

  private String text(Map<String, Object> row, String key) {
    for (Map.Entry<String, Object> entry : row.entrySet())
      if (key.equalsIgnoreCase(entry.getKey()))
        return entry.getValue() == null ? null : String.valueOf(entry.getValue());
    return null;
  }

  private boolean truth(Map<String, Object> row, String key) {
    String value = text(row, key);
    return "1".equals(value) || "true".equalsIgnoreCase(value);
  }

  private String sha256Hex(String value) {
    StringBuilder output = new StringBuilder(64);
    for (byte item : CryptoUtils.sha256(value)) output.append(String.format("%02x", item));
    return output.toString();
  }

  private ApiException bad(String code, String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, message);
  }
}
