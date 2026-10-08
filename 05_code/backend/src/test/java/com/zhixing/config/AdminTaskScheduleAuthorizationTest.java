package com.zhixing.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.zhixing.common.ApiException;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminPrincipal;
import javax.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.*;

class AdminTaskScheduleAuthorizationTest {
  @Test
  void newScheduleEndpointRequiresJobsMenuAndWrapsAuthorizedSession() throws Exception {
    AdminAuthService auth = mock(AdminAuthService.class);
    AdminPrincipal principal =
        new AdminPrincipal(
            "test-admin", "admin", "ADMIN", "管理员", java.util.Collections.singleton("jobs"));
    when(auth.require("session-token", "jobs")).thenReturn(principal);
    AppProperties properties = new AppProperties();
    properties.setAdminLegacyTokenEnabled(false);
    AdminSessionFilter filter = new AdminSessionFilter(auth, properties);
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/admin/tasks/article/schedule");
    request.addHeader("X-Admin-Token", "session-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    verify(auth).require("session-token", "jobs");
    verify(chain).doFilter(any(), eq(response));
    assertThat(request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE)).isSameAs(principal);
  }

  @Test
  void userWithoutJobsPermissionCannotEditSchedules() throws Exception {
    AdminAuthService auth = mock(AdminAuthService.class);
    when(auth.require("session-token", "jobs"))
        .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MENU_FORBIDDEN", "没有任务权限"));
    AppProperties properties = new AppProperties();
    properties.setAdminLegacyTokenEnabled(false);
    AdminSessionFilter filter = new AdminSessionFilter(auth, properties);
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/admin/tasks/pricing/schedule");
    request.addHeader("X-Admin-Token", "session-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = mock(FilterChain.class);
    filter.doFilter(request, response, chain);
    assertThat(response.getStatus()).isEqualTo(403);
    verifyNoInteractions(chain);
  }
}
