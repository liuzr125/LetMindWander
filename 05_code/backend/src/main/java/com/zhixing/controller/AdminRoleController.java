package com.zhixing.controller;

import com.zhixing.config.AdminSessionFilter;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminPrincipal;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminRoleController {
  private final AdminAuthService auth;

  public AdminRoleController(AdminAuthService auth) {
    this.auth = auth;
  }

  /** 查看角色、管理员账号及可分配菜单，入口受角色管理菜单权限保护。 */
  @GetMapping("/roles")
  public Map<String, Object> administration() {
    return auth.administration();
  }

  /** 创建自定义角色并分配有效菜单，不改变已有角色权限。 */
  @PostMapping("/roles")
  public Map<String, Object> role(
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return auth.createRole(
        value(body, "name"),
        value(body, "description"),
        codes(body == null ? null : body.get("menuCodes")),
        principal(request));
  }

  /** 替换角色菜单权限；禁止移除超级管理员的默认权限。 */
  @PutMapping("/roles/{roleId}/menus")
  public Map<String, Object> menus(
      @PathVariable String roleId,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return auth.setRoleMenus(
        roleId, codes(body == null ? null : body.get("menuCodes")), principal(request));
  }

  /** 创建指定角色的管理员账号，密码仅以安全摘要持久化。 */
  @PostMapping("/accounts")
  public Map<String, Object> account(
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return auth.createAccount(
        value(body, "username"),
        value(body, "password"),
        value(body, "roleId"),
        principal(request));
  }

  /** 重置管理员密码并撤销该账号所有会话，超级管理员账号受额外保护。 */
  @PutMapping("/accounts/{accountId}/password")
  public Map<String, Object> password(
      @PathVariable String accountId,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return auth.resetAccountPassword(accountId, value(body, "password"), principal(request));
  }

  private AdminPrincipal principal(HttpServletRequest request) {
    return (AdminPrincipal) request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE);
  }

  private String value(Map<String, Object> body, String key) {
    Object value = body == null ? null : body.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private List<String> codes(Object raw) {
    if (!(raw instanceof List)) return Collections.emptyList();
    List<String> values = new ArrayList<String>();
    for (Object value : (List<?>) raw) if (value != null) values.add(String.valueOf(value));
    return values;
  }
}
