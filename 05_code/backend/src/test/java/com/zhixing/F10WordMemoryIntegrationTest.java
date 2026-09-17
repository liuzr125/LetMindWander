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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f10;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F10WordMemoryIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

    @Test void reviewedQuestionsHintsFirstAnswerRetriesIdempotencyAndResultsFormAClosedLoop() throws Exception {
        Session user=register();seed();
        mvc.perform(get("/api/word-memory/hints").header("Authorization",bearer(user.token)).param("contentId",WORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].methodType").value("association"));
        String created=mvc.perform(post("/api/word-memory/sessions").header("Authorization",bearer(user.token)).header("Idempotency-Key","session-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"contentIds\":[\""+WORD+"\"],\"dimensions\":[\"meaning\",\"spelling\"],\"source\":\"word_detail\",\"returnTo\":\"/pages/word/detail/index?id="+WORD+"\",\"addToReview\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.currentEpisode.dimension").value("meaning"))
                .andExpect(jsonPath("$.currentEpisode.expectedAnswer").doesNotExist()).andReturn().getResponse().getContentAsString();
        JsonNode session=json.readTree(created);String sessionId=session.path("sessionId").asText(),meaningEpisode=session.path("currentEpisode").path("id").asText();
        mvc.perform(post("/api/word-memory/sessions/{sid}/episodes/{eid}/hint",sessionId,meaningEpisode).header("Authorization",bearer(user.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"hintType\":\"clue\",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("表示信息所处的上下文环境")).andExpect(jsonPath("$.sessionVersion").value(2));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM word_memory_hint_event WHERE episode_id=? AND hint_type='clue'",Integer.class,meaningEpisode));
        String firstAttempt="{\"answer\":\"上下文\",\"durationMs\":1200,\"expectedVersion\":2}";
        mvc.perform(post("/api/word-memory/sessions/{sid}/episodes/{eid}/attempts",sessionId,meaningEpisode).header("Authorization",bearer(user.token))
                .header("Idempotency-Key","attempt-meaning-1").contentType(MediaType.APPLICATION_JSON).content(firstAttempt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("hinted_correct")).andExpect(jsonPath("$.firstAttempt").value(true)).andExpect(jsonPath("$.sessionVersion").value(3));
        mvc.perform(post("/api/word-memory/sessions/{sid}/episodes/{eid}/attempts",sessionId,meaningEpisode).header("Authorization",bearer(user.token))
                .header("Idempotency-Key","attempt-meaning-1").contentType(MediaType.APPLICATION_JSON).content(firstAttempt))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptNo").value(1)).andExpect(jsonPath("$.sessionVersion").value(3));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM word_memory_attempt WHERE episode_id=?",Integer.class,meaningEpisode));

        String current=mvc.perform(get("/api/word-memory/sessions/{id}",sessionId).header("Authorization",bearer(user.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentEpisode.dimension").value("spelling")).andReturn().getResponse().getContentAsString();
        String spellingEpisode=json.readTree(current).path("currentEpisode").path("id").asText();
        mvc.perform(post("/api/word-memory/sessions/{sid}/episodes/{eid}/attempts",sessionId,spellingEpisode).header("Authorization",bearer(user.token))
                .header("Idempotency-Key","attempt-spelling-1").contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"contest\",\"durationMs\":900,\"expectedVersion\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("incorrect")).andExpect(jsonPath("$.canRetry").value(true)).andExpect(jsonPath("$.sessionVersion").value(4));
        mvc.perform(post("/api/word-memory/sessions/{sid}/episodes/{eid}/attempts",sessionId,spellingEpisode).header("Authorization",bearer(user.token))
                .header("Idempotency-Key","attempt-spelling-2").contentType(MediaType.APPLICATION_JSON).content("{\"answer\":\"Context\",\"durationMs\":700,\"expectedVersion\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").value("retry_correct")).andExpect(jsonPath("$.firstAttempt").value(false)).andExpect(jsonPath("$.sessionVersion").value(5));
        assertEquals("incorrect",jdbc.queryForObject("SELECT first_result FROM word_memory_episode WHERE id=?",String.class,spellingEpisode));

        mvc.perform(post("/api/word-memory/sessions/{id}/finish",sessionId).header("Authorization",bearer(user.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":5,\"partial\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("completed")).andExpect(jsonPath("$.validObjectiveCount").value(2))
                .andExpect(jsonPath("$.hintedCorrectCount").value(1)).andExpect(jsonPath("$.retryCorrectCount").value(1))
                .andExpect(jsonPath("$.firstIncorrectCount").value(1)).andExpect(jsonPath("$.weakWordCount").value(1)).andExpect(jsonPath("$.sessionVersion").value(6))
                .andExpect(jsonPath("$.reviewPlans[0].state").value("not_enrolled"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM word_memory_evidence WHERE owner_id=?",Integer.class,user.userId));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_record WHERE owner_id=?",Integer.class,user.userId));
    }

    private void seed(){String source="11111111111111111111111111111111";jdbc.update("INSERT INTO content_source (id,name,license_note) VALUES (?,?,?)",source,"测试来源","测试许可");
        jdbc.update("INSERT INTO learning_content (id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",WORD,"word",source,CryptoUtils.sha256(WORD),CryptoUtils.sha256("context"),"junior","published",VERSION,VERSION);
        jdbc.update("INSERT INTO content_version (id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",VERSION,WORD,1,"context","上下文","context","intro",30,"context","上下文","测试许可",CryptoUtils.sha256("context"),"approved","00000000000000000000000000000002");
        jdbc.update("INSERT INTO word_memory_hint (id,content_version_id,method_type,hint_body,source_type,state,hint_version) VALUES (?,?,?,?,?,?,?)",HINT,VERSION,"association","把 context 理解为信息所处的上下文环境","editorial","published",1);
        jdbc.update("INSERT INTO word_memory_question (id,content_version_id,dimension,prompt_text,expected_answer,accepted_answers_json,answer_policy,hint_text,state,question_version) VALUES (?,?,?,?,?,?,?,?,?,?)",MEANING,VERSION,"meaning","context 的中文含义是？","上下文","[\"语境\"]","exact","表示信息所处的上下文环境","published",1);
        jdbc.update("INSERT INTO word_memory_question (id,content_version_id,dimension,prompt_text,expected_answer,accepted_answers_json,answer_policy,hint_text,state,question_version) VALUES (?,?,?,?,?,?,?,?,?,?)",SPELLING,VERSION,"spelling","请根据“上下文”拼写英文单词","context",null,"case_insensitive","首字母是 c","published",1);}
    private Session register() throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f10-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000010\"}"));JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000010\",\"smsCode\":\"123456\",\"nickname\":\"记忆训练用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());}
    private String bearer(String token){return "Bearer "+token;}
    private static final String WORD="44444444444444444444444444444444",VERSION="55555555555555555555555555555555";
    private static final String HINT="66666666666666666666666666666666",MEANING="77777777777777777777777777777777",SPELLING="88888888888888888888888888888888";
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
