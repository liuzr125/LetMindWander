package com.zhixing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
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

        String sourceId=CryptoUtils.randomId(), articleId=CryptoUtils.randomId(), versionId=CryptoUtils.randomId();
        jdbc.update("INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'Test article','original','Test')",sourceId);
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'english_article',?,?,'published',?,?)",articleId,sourceId,CryptoUtils.sha256(articleId),versionId,versionId);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,body,license_snapshot,body_hash,review_status,created_by,article_blocks) VALUES(?,?,1,'Test','Hello.','Test',?,'approved',?,?)",
                versionId,articleId,CryptoUtils.sha256(versionId),sourceId,"[{\"paragraph_id\":\"p1\",\"text\":\"Hello.\"}]");
        mvc.perform(post("/api/learning/contents/{id}/paragraphs/p1/explanation",articleId).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(false));
        mvc.perform(post("/api/learning/contents/{id}/paragraphs/p1/explanation",articleId).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cached").value(true));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE action_code='explain_article_paragraph' AND source_id=?",Integer.class,versionId));
        mvc.perform(get("/api/ai/ask/history").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));

        mvc.perform(put("/api/admin/users/{id}/ai-authorization",session.userId).header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/ai/models").header("Authorization",bearer(session.token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.consentGranted").value(true)).andExpect(jsonPath("$.models.length()").value(1)).andExpect(jsonPath("$.models[0].available").value(true));
        JsonNode answer=json.readTree(mvc.perform(post("/api/ai/ask").header("Authorization",bearer(session.token)).header("Idempotency-Key","f12-ask-1").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"请解释 RAG\",\"modelId\":\"00000000000000000000000000000032\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.specification").value("flash")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertTrue(answer.path("answer").asText().contains("请解释 RAG"));

        String legacyId=CryptoUtils.randomId();
        jdbc.update("INSERT INTO ai_job(id,owner_id,scope_key,action_code,source_type,source_id,source_version,source_fingerprint,consent_version,prompt_version,input_text,output_json,state,attempt_count,queue_expires_at,payload_expires_at,request_key_hash) " +
                "SELECT ?,owner_id,scope_key,action_code,source_type,?,source_version,source_fingerprint,consent_version,prompt_version,?,output_json,state,attempt_count,queue_expires_at,payload_expires_at,? FROM ai_job WHERE owner_id=? AND action_code='ask_question' LIMIT 1",
                legacyId,legacyId,"请用简洁中文解释下面这段英语，说明关键词汇、语法结构和自然译文：\\n\\nHello.",CryptoUtils.sha256(legacyId),session.userId);

        mvc.perform(get("/api/ai/ask/history").header("Authorization",bearer(session.token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].question").value("请解释 RAG")).andExpect(jsonPath("$.items[0].state").value("succeeded"));
        mvc.perform(get("/api/admin/ai-audit/questions").header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        String usage=mvc.perform(get("/api/admin/ai/usage").header("X-Admin-Token","dev-admin-token")).andExpect(status().isOk()).andExpect(jsonPath("$.logs[0].state").value("succeeded")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(usage.contains("请解释 RAG"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ai_job WHERE owner_id=? AND action_code='ask_question'",Integer.class,session.userId));
        String encrypted=jdbc.queryForObject("SELECT param_value FROM app_parameter WHERE param_key='AI_DEEPSEEK_API_KEY'",String.class);assertTrue(encrypted.startsWith("enc:v1:"));assertFalse(encrypted.contains("test-provider-key"));

        // 每日额度改由「系统参数」维护：改参数当天即生效，且我的页面直接显示参数值
        jdbc.update("INSERT INTO app_parameter(id,param_key,param_value,is_secret,description,state,version_no) VALUES('f1200000000000000000000000000010','ai.personal_daily_limit','1',0,'AI 每日提问次数上限（单个用户）','active',1)");
        mvc.perform(get("/api/mine/overview").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aiLimit").value(1)).andExpect(jsonPath("$.aiUsed").value(1));
        mvc.perform(post("/api/ai/ask").header("Authorization",bearer(session.token)).header("Idempotency-Key","f12-ask-2").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"请再解释一次 RAG\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("AI_DAILY_QUOTA_EXCEEDED"));
        // 参数值非法时回退到兜底默认值，避免把额度锁死
        jdbc.update("UPDATE app_parameter SET param_value='abc',version_no=version_no+1 WHERE param_key='ai.personal_daily_limit'");
        mvc.perform(get("/api/mine/overview").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aiLimit").value(10));
    }

    private Session register() throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f12-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000012\"}"));JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000012\",\"smsCode\":\"123456\",\"nickname\":\"AI用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());}
    private String bearer(String token){return "Bearer "+token;}private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
