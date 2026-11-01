package com.zhixing.config;

import com.zhixing.common.ApiException;
import com.zhixing.service.AdminPrincipal;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

/** 人工操作只使用过滤器已认证的主体；禁止静默回退到固定管理员。 */
public final class AdminActors {
  private AdminActors() {}

  public static String requireId(HttpServletRequest request) {
    Object principal = request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE);
    if (!(principal instanceof AdminPrincipal))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_SESSION_INVALID", "需要管理员登录");
    return ((AdminPrincipal) principal).getAccountId();
  }
}
