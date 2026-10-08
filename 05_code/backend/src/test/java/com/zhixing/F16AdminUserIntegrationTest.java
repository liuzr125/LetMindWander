package com.zhixing;

import com.zhixing.common.CryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.sql.Timestamp;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f16;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F16AdminUserIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed(){
        jdbc.update("DELETE FROM admin_audit WHERE target_id='f1600000000000000000000000000001' AND action_code='user_account_detail_view'");
        jdbc.update("DELETE FROM app_user WHERE id='f1600000000000000000000000000001'");
        jdbc.update("INSERT INTO app_user(id,seq_no,short_id,wx_app_id,wx_open_id,nickname,mobile,status,ai_consent_version,ai_consented_at,last_login_at) VALUES('f1600000000000000000000000000001',1601,'F16USER','wx-f16','open-f16','f16-user','13812345678','active','AI_SEND_V1',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
    }

    @Test
    void accountTabApiIsProtectedSearchableAndPrivacySafe() throws Exception {
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("X-Admin-Token","dev-admin-token").param("keyword","f16-user"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items[0].shortId").value("F16USER"))
                .andExpect(jsonPath("$.items[0].mobileMasked").value("138****5678"))
                .andExpect(jsonPath("$.items[0].aiConsented").value(true))
                .andExpect(jsonPath("$.items[0].mobile").doesNotExist())
                .andExpect(jsonPath("$.items[0].wxOpenId").doesNotExist());
        mvc.perform(get("/api/admin/users").header("X-Admin-Token","dev-admin-token").param("status","unknown"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_USER_STATUS"));
    }

    @Test
    void fullMobileIsAvailableOnlyThroughAuditedAccountDetail() throws Exception {
        String userId="f1600000000000000000000000000001";
        mvc.perform(get("/api/admin/users/{id}/account",userId)).andExpect(status().isUnauthorized());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit WHERE action_code='user_account_detail_view' AND target_id=?",Integer.class,userId));
        mvc.perform(get("/api/admin/users/{id}/account",userId).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.mobile").value("13812345678"))
                .andExpect(jsonPath("$.nickname").value("f16-user"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty()).andExpect(jsonPath("$.lastLoginAt").isNotEmpty())
                .andExpect(jsonPath("$.wxOpenId").doesNotExist()).andExpect(jsonPath("$.wxAppId").doesNotExist());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit WHERE action_code='user_account_detail_view' AND target_id=?",Integer.class,userId));
        assertEquals("{}",jdbc.queryForObject("SELECT metadata_json FROM admin_audit WHERE action_code='user_account_detail_view' AND target_id=?",String.class,userId));
        mvc.perform(get("/api/admin/users/{id}/account","missing-user").header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_USER_NOT_FOUND"));
        jdbc.update("UPDATE app_user SET mobile=NULL WHERE id=?",userId);
        mvc.perform(get("/api/admin/users/{id}/account",userId).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mobile").doesNotExist());
    }

    @Test
    void accountDetailRequiresUserMenuAndAuditsActualAdministrator() throws Exception {
        String userId="f1600000000000000000000000000001",roleId="f1600000000000000000000000000002",adminId="f1600000000000000000000000000003",sessionId="f1600000000000000000000000000004",token="f16-account-detail-admin-session";
        jdbc.update("DELETE FROM admin_session WHERE id=?",sessionId);
        jdbc.update("DELETE FROM admin_account WHERE id=?",adminId);
        jdbc.update("DELETE FROM admin_role_menu WHERE role_id=?",roleId);
        jdbc.update("DELETE FROM admin_role WHERE id=?",roleId);
        jdbc.update("INSERT INTO admin_role(id,code,name) VALUES(?,'F16_ACCOUNT_DETAIL','F16 account detail')",roleId);
        jdbc.update("INSERT INTO admin_account(id,username,password_hash,role_id) VALUES(?,'f16-detail-admin','test-unused-password-hash',?)",adminId,roleId);
        StringBuilder hash=new StringBuilder();for(byte value:CryptoUtils.sha256(token))hash.append(String.format("%02x",value));
        jdbc.update("INSERT INTO admin_session(id,account_id,token_hash,expires_at,last_seen_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP)",sessionId,adminId,hash.toString(),Timestamp.from(Instant.now().plusSeconds(600)));
        mvc.perform(get("/api/admin/users/{id}/account",userId).header("X-Admin-Token",token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_MENU_FORBIDDEN"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit WHERE action_code='user_account_detail_view' AND target_id=?",Integer.class,userId));
        jdbc.update("INSERT INTO admin_role_menu(role_id,menu_id) SELECT ?,id FROM admin_menu WHERE code='users'",roleId);
        mvc.perform(get("/api/admin/users/{id}/account",userId).header("X-Admin-Token",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mobile").value("13812345678"));
        assertEquals(adminId,jdbc.queryForObject("SELECT admin_id FROM admin_audit WHERE action_code='user_account_detail_view' AND target_id=?",String.class,userId));
    }
}
