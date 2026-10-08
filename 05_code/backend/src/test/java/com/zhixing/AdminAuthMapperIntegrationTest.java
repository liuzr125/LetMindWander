package com.zhixing;

import static org.junit.jupiter.api.Assertions.*;

import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import com.zhixing.config.StandaloneMapperFactory;
import com.zhixing.mapper.AdminAuditMapper;
import com.zhixing.mapper.AdminAuthMapper;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminPrincipal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** Executes real semantic mapper XML for administrator login, roles, sessions, and audit. */
class AdminAuthMapperIntegrationTest {
  @Test
  void roleChangesAndPasswordResetPreserveSessionPermissionBoundaries() throws Exception {
    DriverManagerDataSource ds =
        new DriverManagerDataSource(
            "jdbc:h2:mem:admin-mapper-"
                + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "sa",
            "");
    ResourceDatabasePopulator schema =
        new ResourceDatabasePopulator(
            new ClassPathResource("schema.sql"), new ClassPathResource("data.sql"));
    schema.setSqlScriptEncoding("UTF-8");
    schema.execute(ds);
    SqlSessionTemplate mappings = StandaloneMapperFactory.create(ds);
    AppProperties properties = new AppProperties();
    properties.setAdminBootstrapUsername("test-superadmin");
    properties.setAdminBootstrapPassword("test-super-password-12");
    AdminAuthService service =
        new AdminAuthService(
            mappings.getMapper(AdminAuthMapper.class),
            mappings.getMapper(AdminAuditMapper.class),
            properties);
    Map<String, Object> logged = service.login("test-superadmin", "test-super-password-12");
    AdminPrincipal superadmin = service.require(String.valueOf(logged.get("token")), "roles");
    Map<String, Object> roles =
        service.createRole(
            "公告测试角色", "只看公告", Collections.singletonList("announcements"), superadmin);
    String roleId = String.valueOf(roles.get("createdRoleId"));
    service.createAccount("reader-admin", "initial-password-12", roleId, superadmin);
    Map<String, Object> reader = service.login("reader-admin", "initial-password-12");
    String readerToken = String.valueOf(reader.get("token"));
    AdminPrincipal principal = service.require(readerToken, "announcements");
    assertEquals(Collections.singleton("announcements"), principal.getMenuCodes());
    assertEquals(
        "ADMIN_MENU_FORBIDDEN",
        assertThrows(ApiException.class, () -> service.require(readerToken, "users")).getCode());
    service.setRoleMenus(roleId, Collections.emptyList(), superadmin);
    assertEquals(
        "ADMIN_MENU_FORBIDDEN",
        assertThrows(ApiException.class, () -> service.require(readerToken, "announcements"))
            .getCode());
    service.resetAccountPassword(principal.getAccountId(), "replacement-password-12", superadmin);
    assertNull(service.authenticate(readerToken));
    assertNotNull(service.login("reader-admin", "replacement-password-12").get("token"));
    JdbcTemplate jdbc = new JdbcTemplate(ds);
    assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit", Integer.class));
    // 真会话经认证过滤器退出后，原 token 再访问认证接口必须失效。
    org.springframework.test.web.servlet.MockMvc mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.zhixing.controller.AdminAuthController(service))
            .addFilters(new com.zhixing.config.AdminSessionFilter(service, properties))
            .build();
    String adminToken = String.valueOf(logged.get("token"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/admin/auth/logout")
                .header("X-Admin-Token", adminToken))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/admin/auth/me")
                .header("X-Admin-Token", adminToken))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnauthorized());
    assertNull(service.authenticate(adminToken));
  }
}
