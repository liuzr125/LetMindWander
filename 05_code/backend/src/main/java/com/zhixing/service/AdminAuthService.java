package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AdminAuthService {
    private static final String SUPER_ROLE_ID="00000000000000000000000000a1";
    private final JdbcTemplate jdbc; private final AppProperties properties;
    public AdminAuthService(JdbcTemplate jdbc,AppProperties properties){this.jdbc=jdbc;this.properties=properties;}

    @Transactional
    public Map<String,Object> login(String rawUsername,String rawPassword){
        String username=clean(rawUsername), password=rawPassword==null?"":rawPassword;
        if(username==null||password.length()==0)throw bad("ADMIN_LOGIN_REQUIRED","请输入账号和密码");
        bootstrap();
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT a.id,a.username,a.password_hash,a.enabled,r.code role_code,r.name role_name FROM admin_account a JOIN admin_role r ON r.id=a.role_id WHERE a.username=?",username);
        if(rows.isEmpty()||!truth(rows.get(0),"enabled")||!AdminPasswordHasher.matches(password,text(rows.get(0),"password_hash")))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_LOGIN_INVALID","账号或密码错误");
        Map<String,Object> row=rows.get(0); String token=CryptoUtils.randomToken(36), hash=sha256Hex(token); Instant now=Instant.now(), expires=now.plus(properties.getAdminSessionTtl());
        jdbc.update("INSERT INTO admin_session(id,account_id,token_hash,expires_at,created_at,last_seen_at) VALUES(?,?,?,?,?,?)",CryptoUtils.randomId(),text(row,"id"),hash,Timestamp.from(expires),Timestamp.from(now),Timestamp.from(now));
        jdbc.update("UPDATE admin_account SET last_login_at=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",Timestamp.from(now),text(row,"id"));
        AdminPrincipal principal=principal(text(row,"id"),text(row,"username"),text(row,"role_code"),text(row,"role_name"));
        return sessionView(token,expires,principal);
    }
    public AdminPrincipal require(String token,String menu){
        AdminPrincipal principal=authenticate(token);
        if(principal==null)throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_SESSION_INVALID","登录已失效，请重新登录");
        if(menu!=null&&!principal.hasMenu(menu))throw new ApiException(HttpStatus.FORBIDDEN,"ADMIN_MENU_FORBIDDEN","当前角色没有该菜单权限");
        return principal;
    }
    public AdminPrincipal authenticate(String token){
        if(clean(token)==null)return null;
        String hash=sha256Hex(token); List<Map<String,Object>> rows=jdbc.queryForList("SELECT a.id,a.username,r.code role_code,r.name role_name FROM admin_session s JOIN admin_account a ON a.id=s.account_id JOIN admin_role r ON r.id=a.role_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>CURRENT_TIMESTAMP AND a.enabled=1 AND r.enabled=1",hash);
        if(rows.isEmpty())return null; Map<String,Object> row=rows.get(0);
        jdbc.update("UPDATE admin_session SET last_seen_at=CURRENT_TIMESTAMP WHERE token_hash=?",hash);
        return principal(text(row,"id"),text(row,"username"),text(row,"role_code"),text(row,"role_name"));
    }
    public void logout(String token){if(clean(token)!=null)jdbc.update("UPDATE admin_session SET revoked_at=CURRENT_TIMESTAMP WHERE token_hash=? AND revoked_at IS NULL",sha256Hex(token));}
    public Map<String,Object> me(AdminPrincipal principal){return sessionView(null,null,principal);}
    public Map<String,Object> administration(){Map<String,Object> out=new LinkedHashMap<String,Object>();out.put("menus",menus());out.put("roles",roles());out.put("accounts",accounts());return out;}
    @Transactional public Map<String,Object> createRole(String rawName,String rawDescription,List<String> menuCodes,AdminPrincipal actor){
        String name=clean(rawName),description=clean(rawDescription);if(name==null||name.length()>80)throw bad("ROLE_NAME_INVALID","角色名称不能为空且不能超过 80 个字符");if(description!=null&&description.length()>255)throw bad("ROLE_DESCRIPTION_INVALID","角色说明不能超过 255 个字符");
        if(exists("SELECT COUNT(*) FROM admin_role WHERE name=?",name))throw new ApiException(HttpStatus.CONFLICT,"ROLE_NAME_EXISTS","该角色名称已存在");
        Set<String> requested=validMenuCodes(menuCodes);String id=CryptoUtils.randomId(),code="CUSTOM_"+id.substring(0,12).toUpperCase(Locale.ROOT);
        jdbc.update("INSERT INTO admin_role(id,code,name,description,is_system,enabled,sort_order,created_at,updated_at) VALUES(?,?,?,?,0,1,(SELECT COALESCE(MAX(sort_order),0)+10 FROM admin_role),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,code,name,description);
        for(String item:requested)jdbc.update("INSERT INTO admin_role_menu(role_id,menu_id,created_at) SELECT ?,id,CURRENT_TIMESTAMP FROM admin_menu WHERE code=?",id,item);
        audit(actor,"admin_role_create","admin_role",id,"{\"menuCount\":"+requested.size()+"}");Map<String,Object> out=administration();out.put("createdRoleId",id);return out;
    }
    @Transactional public Map<String,Object> setRoleMenus(String roleId,List<String> menuCodes,AdminPrincipal actor){
        if(SUPER_ROLE_ID.equals(roleId))throw bad("SUPER_ROLE_LOCKED","超级管理员默认拥有全部菜单权限，不能移除");
        if(!exists("SELECT COUNT(*) FROM admin_role WHERE id=? AND enabled=1",roleId))throw bad("ROLE_NOT_FOUND","角色不存在或已停用");
        Set<String> requested=validMenuCodes(menuCodes);
        jdbc.update("DELETE FROM admin_role_menu WHERE role_id=?",roleId); for(String code:requested)jdbc.update("INSERT INTO admin_role_menu(role_id,menu_id,created_at) SELECT ?,id,CURRENT_TIMESTAMP FROM admin_menu WHERE code=?",roleId,code);
        audit(actor,"role_menu_update","admin_role",roleId,"{\"menuCount\":"+requested.size()+"}"); return administration();
    }
    @Transactional public Map<String,Object> createAccount(String rawUsername,String password,String roleId,AdminPrincipal actor){
        String username=clean(rawUsername);if(username==null||!username.matches("[A-Za-z][A-Za-z0-9_.-]{2,39}"))throw bad("ADMIN_USERNAME_INVALID","账号需为 3–40 位字母开头的字母、数字、点、下划线或连字符");
        validatePassword(password);
        if(!exists("SELECT COUNT(*) FROM admin_role WHERE id=? AND enabled=1",roleId))throw bad("ROLE_NOT_FOUND","请选择可用角色");
        try{jdbc.update("INSERT INTO admin_account(id,username,password_hash,role_id,enabled,password_updated_at,created_at,updated_at) VALUES(?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",CryptoUtils.randomId(),username,AdminPasswordHasher.hash(password),roleId);}catch(Exception duplicate){throw new ApiException(HttpStatus.CONFLICT,"ADMIN_USERNAME_EXISTS","该管理员账号已存在");}
        audit(actor,"admin_account_create","admin_account",username,"{\"roleId\":\""+roleId+"\"}");return administration();
    }
    @Transactional public Map<String,Object> resetAccountPassword(String accountId,String password,AdminPrincipal actor){
        validatePassword(password);List<Map<String,Object>> rows=jdbc.queryForList("SELECT a.id,a.username,r.code role_code FROM admin_account a JOIN admin_role r ON r.id=a.role_id WHERE a.id=?",accountId);
        if(rows.isEmpty())throw bad("ADMIN_ACCOUNT_NOT_FOUND","管理账号不存在");Map<String,Object> target=rows.get(0);
        if("SUPER_ADMIN".equals(text(target,"role_code"))&&!"SUPER_ADMIN".equals(actor.getRoleCode()))throw new ApiException(HttpStatus.FORBIDDEN,"SUPER_ADMIN_PROTECTED","不能修改超级管理员的密码");
        jdbc.update("UPDATE admin_account SET password_hash=?,password_updated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=?",AdminPasswordHasher.hash(password),accountId);
        jdbc.update("UPDATE admin_session SET revoked_at=CURRENT_TIMESTAMP WHERE account_id=? AND revoked_at IS NULL",accountId);
        audit(actor,"admin_password_reset","admin_account",accountId,"{}");Map<String,Object> out=administration();out.put("currentSessionRevoked",accountId.equals(actor.getAccountId()));return out;
    }
    private void bootstrap(){
        String username=clean(properties.getAdminBootstrapUsername()),password=properties.getAdminBootstrapPassword();
        if(username==null||password==null||password.length()<10)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"ADMIN_BOOTSTRAP_REQUIRED","请配置 ADMIN_BOOTSTRAP_USERNAME 与安全的 ADMIN_BOOTSTRAP_PASSWORD 后登录");
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM admin_account WHERE username=?",Integer.class,username);if(count!=null&&count>0)return;
        jdbc.update("INSERT INTO admin_account(id,username,password_hash,role_id,enabled,password_updated_at,created_at,updated_at) VALUES(?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",CryptoUtils.randomId(),username,AdminPasswordHasher.hash(password),SUPER_ROLE_ID);
    }
    private AdminPrincipal principal(String accountId,String username,String roleCode,String roleName){Set<String> menus=new LinkedHashSet<String>();for(Map<String,Object> row:jdbc.queryForList("SELECT m.code FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id WHERE rm.role_id=(SELECT role_id FROM admin_account WHERE id=?) AND m.enabled=1 ORDER BY m.sort_order",accountId))menus.add(text(row,"code"));return new AdminPrincipal(accountId,username,roleCode,roleName,menus);}
    private Map<String,Object> sessionView(String token,Instant expires,AdminPrincipal principal){Map<String,Object> out=new LinkedHashMap<String,Object>();if(token!=null)out.put("token",token);if(expires!=null)out.put("expiresAt",expires);out.put("username",principal.getUsername());out.put("roleCode",principal.getRoleCode());out.put("roleName",principal.getRoleName());out.put("menus",new ArrayList<String>(principal.getMenuCodes()));return out;}
    private List<Map<String,Object>> menus(){return jdbc.queryForList("SELECT id,code,name,path,icon,sort_order sortOrder FROM admin_menu WHERE enabled=1 ORDER BY sort_order,id");}
    private List<Map<String,Object>> roles(){List<Map<String,Object>> roles=jdbc.queryForList("SELECT id,code,name,description,is_system systemRole FROM admin_role WHERE enabled=1 ORDER BY sort_order,id");for(Map<String,Object> role:roles){List<Map<String,Object>> assigned=jdbc.queryForList("SELECT m.code FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id WHERE rm.role_id=? ORDER BY m.sort_order",text(role,"id"));List<String> codes=new ArrayList<String>();for(Map<String,Object> menu:assigned)codes.add(text(menu,"code"));role.put("menuCodes",codes);}return roles;}
    private List<Map<String,Object>> accounts(){return jdbc.queryForList("SELECT a.id,a.username,a.enabled,a.last_login_at lastLoginAt,r.id role_id,r.name roleName,r.code roleCode FROM admin_account a JOIN admin_role r ON r.id=a.role_id ORDER BY a.created_at,a.id");}
    private void audit(AdminPrincipal actor,String action,String type,String id,String metadata){jdbc.update("INSERT INTO admin_audit(id,admin_id,action_code,target_type,target_id,result_code,metadata_json,expires_at) VALUES(?,?,?,?,?,'succeeded',?,?)",CryptoUtils.randomId(),actor.getAccountId(),action,type,id,metadata,Timestamp.from(Instant.now().plus(Duration.ofDays(180))));}
    private void validatePassword(String password){if(password==null||password.length()<10||password.length()>128)throw bad("ADMIN_PASSWORD_INVALID","密码长度需为 10–128 位");}
    private Set<String> validMenuCodes(List<String> menuCodes){Set<String> requested=new LinkedHashSet<String>();if(menuCodes!=null)for(String code:menuCodes){String value=clean(code);if(value!=null)requested.add(value);}Set<String> valid=new LinkedHashSet<String>();for(Map<String,Object> menu:menus())valid.add(text(menu,"code"));if(!valid.containsAll(requested))throw bad("MENU_NOT_FOUND","包含不存在或已停用的菜单");return requested;}
    private boolean exists(String sql,Object value){Integer count=jdbc.queryForObject(sql,Integer.class,value);return count!=null&&count>0;} private String clean(String value){if(value==null)return null;String clean=value.trim();return clean.isEmpty()?null:clean;} private String text(Map<String,Object> row,String key){for(Map.Entry<String,Object> entry:row.entrySet())if(key.equalsIgnoreCase(entry.getKey()))return entry.getValue()==null?null:String.valueOf(entry.getValue());return null;} private boolean truth(Map<String,Object> row,String key){String value=text(row,key);return "1".equals(value)||"true".equalsIgnoreCase(value);} private String sha256Hex(String value){StringBuilder output=new StringBuilder(64);for(byte item:CryptoUtils.sha256(value))output.append(String.format("%02x",item));return output.toString();} private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
}
