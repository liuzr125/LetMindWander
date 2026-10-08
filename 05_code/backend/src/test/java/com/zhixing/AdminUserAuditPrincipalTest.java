package com.zhixing;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhixing.config.AdminSessionFilter;
import com.zhixing.config.AppProperties;
import com.zhixing.controller.AdminUserController;
import com.zhixing.model.AdminUserView;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminPrincipal;
import com.zhixing.service.AdminUserService;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminUserAuditPrincipalTest {
  @Test
  void authorizationAuditUsesAuthenticatedAdministratorInsteadOfConfiguredFallback()
      throws Exception {
    AppProperties properties = new AppProperties();
    AdminAuthService auth = mock(AdminAuthService.class);
    AdminUserService users = mock(AdminUserService.class);
    when(auth.require("real-session", "users"))
        .thenReturn(
            new AdminPrincipal(
                "actual-administrator",
                "test-admin",
                "ADMIN",
                "管理员",
                Collections.singleton("users")));
    when(users.setAiAuthorization("target-user", true, "actual-administrator"))
        .thenReturn(new AdminUserView());
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new AdminUserController(users, properties))
            .addFilters(new AdminSessionFilter(auth, properties))
            .build();
    mvc.perform(
            put("/api/admin/users/target-user/ai-authorization")
                .header("X-Admin-Token", "real-session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isOk());
    verify(users).setAiAuthorization("target-user", true, "actual-administrator");
    verifyNoMoreInteractions(users);
  }
}
