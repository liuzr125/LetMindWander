package com.zhixing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f13;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.sql.init.mode=always",
        "app.ai.credential-encryption-key=test-credential-master-key"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F13AliyunTtsConfigIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void onlyAdminCanConfigureTtsAndCredentialsAreEncrypted() throws Exception {
        mvc.perform(get("/api/admin/ai/tts"))
                .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/admin/ai/tts")
                        .header("X-Admin-Token", "dev-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts\",\"voice\":\"aixia\",\"sampleRate\":16000,\"appKey\":\"test-app-key\",\"accessKeyId\":\"test-ak-id\",\"accessKeySecret\":\"test-ak-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.appKeyConfigured").value(true))
                .andExpect(jsonPath("$.accessKeyIdConfigured").value(true))
                .andExpect(jsonPath("$.accessKeySecretConfigured").value(true));

        String appKey = jdbc.queryForObject("SELECT param_value FROM app_parameter WHERE param_key='ALIYUN_NLS_APP_KEY'", String.class);
        String accessKeyId = jdbc.queryForObject("SELECT param_value FROM app_parameter WHERE param_key='ALIYUN_AK_ID'", String.class);
        String accessKeySecret = jdbc.queryForObject("SELECT param_value FROM app_parameter WHERE param_key='ALIYUN_AK_SECRET'", String.class);
        for (String encrypted : new String[]{appKey, accessKeyId, accessKeySecret}) {
            assertTrue(encrypted.startsWith("enc:v1:"));
        }
        assertFalse(appKey.contains("test-app-key"));
        assertFalse(accessKeyId.contains("test-ak-id"));
        assertFalse(accessKeySecret.contains("test-ak-secret"));

        String response = mvc.perform(get("/api/admin/ai/tts")
                        .header("X-Admin-Token", "dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("test-app-key"));
        assertFalse(response.contains("test-ak-id"));
        assertFalse(response.contains("test-ak-secret"));
    }
}
