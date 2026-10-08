package com.zhixing.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.zhixing.common.ApiException;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminPrincipal;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminInviteMenuTest {
  @Test
  void inviteEndpointsRequireTheirOwnMenuAndDoNotReachControllerWithoutIt() throws Exception {
    AdminAuthService auth = mock(AdminAuthService.class);
    when(auth.require("session", "invites"))
        .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MENU_FORBIDDEN", "没有邀请码权限"));
    AdminSessionFilter filter = new AdminSessionFilter(auth, new AppProperties());
    for (String path :
        new String[] {
          "/api/admin/invites",
          "/api/admin/invites/example/revoke",
          "/api/admin/invite-stats",
          "/api/admin/admission"
        }) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
      request.addHeader("X-Admin-Token", "session");
      MockHttpServletResponse response = new MockHttpServletResponse();
      AtomicBoolean reached = new AtomicBoolean();
      filter.doFilter(request, response, (req, res) -> reached.set(true));
      assertThat(response.getStatus()).isEqualTo(403);
      assertThat(reached.get()).isFalse();
    }
    verify(auth, times(4)).require("session", "invites");
  }

  @Test
  void accountEndpointsContinueToUseUsersPermission() throws Exception {
    AdminAuthService auth = mock(AdminAuthService.class);
    when(auth.require("session", "users"))
        .thenReturn(
            new AdminPrincipal("id", "editor", "ADMIN", "管理员", Collections.singleton("users")));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
    request.addHeader("X-Admin-Token", "session");
    AtomicBoolean reached = new AtomicBoolean();
    new AdminSessionFilter(auth, new AppProperties())
        .doFilter(request, new MockHttpServletResponse(), (req, res) -> reached.set(true));
    assertThat(reached.get()).isTrue();
    verify(auth).require("session", "users");
  }
}
