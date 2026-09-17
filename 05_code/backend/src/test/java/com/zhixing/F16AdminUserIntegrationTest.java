package com.zhixing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f16;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F16AdminUserIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed(){
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
                .andExpect(jsonPath("$.items[0].wxOpenId").doesNotExist());
        mvc.perform(get("/api/admin/users").header("X-Admin-Token","dev-admin-token").param("status","unknown"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_USER_STATUS"));
    }
}
