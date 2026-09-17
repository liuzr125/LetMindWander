package com.zhixing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f12;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory","app.ai.mock-enabled=true","app.ai.credential-encryption-key=test-credential-master-key"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F12AiAskIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;

    @Test void consentModelBudgetAskAndCostLogFormAClosedLoop() throws Exception {
        Session session=register();
        mvc.perform(post("/api/ai/ask").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"请解释 RAG\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AI_CONSENT_REQUIRED"));

        mvc.perform(put("/api/admin/ai/models/{id}","00000000000000000000000000000031").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"providerCode\":\"deepseek\",\"modelCode\":\"deepseek-flash\",\"displayName\":\"DeepSeek Flash\",\"specification\":\"flash\",\"baseUrl\":\"https://api.deepseek.com/chat/completions\",\"apiKeyParamKey\":\"AI_DEEPSEEK_API_KEY\",\"apiKey\":\"test-provider-key\",\"enabled\":true,\"defaultModel\":true,\"maxOutputTokens\":1200,\"timeoutSeconds\":60,\"currency\":\"CNY\",\"inputPerMillion\":1.0,\"outputPerMillion\":2.0,\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.credentialConfigured").value(true));
        mvc.perform(put("/api/admin/ai/budget").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"limitAmount\":100,\"currency\":\"CNY\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/consents").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"ai_send\",\"documentVersion\":\"AI_SEND_V1\",\"decision\":\"grant\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/ai/models").header("Authorization",bearer(session.token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.consentGranted").value(true)).andExpect(jsonPath("$.models.length()").value(1)).andExpect(jsonPath("$.models[0].available").value(true));
        JsonNode answer=json.readTree(mvc.perform(post("/api/ai/ask").header("Authorization",bearer(session.token)).header("Idempotency-Key","f12-ask-1").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"请解释 RAG\",\"modelId\":\"00000000000000000000000000000032\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.specification").value("flash")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(answer.path("answer").asText().contains("请解释 RAG"));

        mvc.perform(get("/api/ai/ask/history").header("Authorization",bearer(session.token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].question").value("请解释 RAG")).andExpect(jsonPath("$.items[0].state").value("succeeded"));
        String usage=mvc.perform(get("/api/admin/ai/usage").header("X-Admin-Token","dev-admin-token")).andExpect(status().isOk()).andExpect(jsonPath("$.logs[0].state").value("succeeded")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(usage.contains("请解释 RAG"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE owner_id=? AND action_code='ask_question'",Integer.class,session.userId));
        String encrypted=jdbc.queryForObject("SELECT param_value FROM app_parameter WHERE param_key='AI_DEEPSEEK_API_KEY'",String.class);assertTrue(encrypted.startsWith("enc:v1:"));assertFalse(encrypted.contains("test-provider-key"));
    }

    private Session register() throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f12-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000012\"}"));JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000012\",\"smsCode\":\"123456\",\"nickname\":\"AI用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());}
    private String bearer(String token){return "Bearer "+token;}private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
