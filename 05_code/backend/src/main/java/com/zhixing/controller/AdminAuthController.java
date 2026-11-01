package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.config.AdminSessionFilter;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminLoginGuard;
import com.zhixing.service.AdminPrincipal;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
  private final AdminAuthService auth;
  private final AdminLoginGuard loginGuard;

  public AdminAuthController(AdminAuthService auth, AdminLoginGuard loginGuard) {
    this.auth = auth;
    this.loginGuard = loginGuard;
  }

  /** 校验管理员账号密码并签发有期限的会话；不返回密码或密码摘要。 */
  @PostMapping("/login")
  public Map<String, Object> login(
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    try (AdminLoginGuard.Attempt attempt =
        loginGuard.begin(value(body, "username"), request.getRemoteAddr())) {
      try {
        Map<String, Object> result = auth.login(value(body, "username"), value(body, "password"));
        attempt.succeeded();
        return result;
      } catch (ApiException exception) {
        if (exception.getStatus() == HttpStatus.UNAUTHORIZED) attempt.failed();
        throw exception;
      }
    }
  }

  /** 返回当前管理员角色与菜单权限，认证信息来自请求过滤器。 */
  @GetMapping("/me")
  public Map<String, Object> me(HttpServletRequest request) {
    return auth.me(principal(request));
  }

  /** 撤销当前管理员会话，后续请求必须重新登录。 */
  @PostMapping("/logout")
  public Map<String, Object> logout(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    auth.logout(token);
    return java.util.Collections.<String, Object>singletonMap("loggedOut", true);
  }

  private AdminPrincipal principal(HttpServletRequest request) {
    return (AdminPrincipal) request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE);
  }

  private String value(Map<String, Object> body, String key) {
    Object value = body == null ? null : body.get(key);
    return value == null ? null : String.valueOf(value);
  }
}
