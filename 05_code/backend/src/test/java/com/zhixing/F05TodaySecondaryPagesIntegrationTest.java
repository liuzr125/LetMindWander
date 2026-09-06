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
import java.time.LocalDate;
import java.time.ZoneId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f05;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F05TodaySecondaryPagesIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

    @Test void todayContentJournalAndReviewFormAClosedLoop() throws Exception {
        Session session=register(); seedContents();
        mvc.perform(post("/api/plans/default").header("Authorization",bearer(session.token))).andExpect(status().isOk());
        String today=LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        JsonNode day=json.readTree(mvc.perform(post("/api/days/{date}/activate",today).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andReturn().getResponse().getContentAsString());
        JsonNode tech=find(day,"tech"),word=find(day,"word"),journal=find(day,"journal");
        mvc.perform(get("/api/learning/contents/{id}",tech.path("contentId").asText()).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("RAG 基础"));
        mvc.perform(post("/api/learning/contents/{id}/understood",tech.path("contentId").asText()).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"taskId\":\""+tech.path("id").asText()+"\",\"expectedVersion\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(true));
        mvc.perform(post("/api/learning/contents/{id}/word-book",word.path("contentId").asText()).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inWordBook").value(true));
        mvc.perform(post("/api/journals/{date}/submit",today).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0,\"doneText\":\"完成今日学习\",\"blockerText\":\"\",\"learnedText\":\"理解 RAG\",\"nextStepText\":\"继续复习\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("submitted")).andExpect(jsonPath("$.versionNo").value(1));
        mvc.perform(post("/api/learning/contents/{id}/review",tech.path("contentId").asText()).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inReview").value(true));
        String packageId=jdbc.queryForObject("SELECT id FROM daily_package WHERE owner_id=? AND business_date=?",String.class,session.userId,LocalDate.parse(today));
        String knowledgeId=jdbc.queryForObject("SELECT id FROM knowledge_item WHERE owner_id=? AND bookmark_content_id=?",String.class,session.userId,tech.path("contentId").asText());
        String scheduleId=jdbc.queryForObject("SELECT id FROM review_schedule WHERE owner_id=? AND knowledge_id=?",String.class,session.userId,knowledgeId);
        jdbc.update("UPDATE review_schedule SET due_date=? WHERE id=?",LocalDate.parse(today),scheduleId);
        String reviewTask="55555555555555555555555555555555";
        jdbc.update("INSERT INTO daily_task (id,owner_id,package_id,task_type,title_snapshot,target_key,knowledge_id,status,estimated_seconds,sort_no,version_no) VALUES (?,?,?,?,?,?,?,?,?,?,?)",reviewTask,session.userId,packageId,"review","复习 RAG","review:"+knowledgeId,knowledgeId,"TODO",30,9,1);
        mvc.perform(get("/api/reviews/queue").header("Authorization",bearer(session.token)).param("date",today))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].scheduleId").value(scheduleId));
        mvc.perform(post("/api/reviews/{id}/feedback",scheduleId).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":\"remember\",\"taskId\":\""+reviewTask+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/days/{date}",today).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tasks[?(@.id == '"+journal.path("id").asText()+"')].status").value("DONE"));
    }

    private JsonNode find(JsonNode day,String type){for(JsonNode task:day.path("tasks"))if(type.equals(task.path("taskType").asText()))return task;throw new AssertionError("missing "+type);}
    private void seedContents(){String source="11111111111111111111111111111111";jdbc.update("INSERT INTO content_source (id,name,license_note) VALUES (?,?,?)",source,"测试来源","测试许可");insertContent("22222222222222222222222222222222","33333333333333333333333333333333","tech",null,"RAG 基础","检索增强生成","先检索证据，再组织回答。",source);insertContent("44444444444444444444444444444444","66666666666666666666666666666666","word","junior","context","上下文","The model uses context.",source);jdbc.update("UPDATE content_version SET word_term='context',phonetic='/ˈkɒntekst/',meaning='上下文；语境',example_text='The model uses context.',example_translation='模型使用上下文。' WHERE id='66666666666666666666666666666666'");}
    private void insertContent(String id,String version,String type,String stage,String title,String summary,String body,String source){byte[] hash=com.zhixing.common.CryptoUtils.sha256(id);jdbc.update("INSERT INTO learning_content (id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,type,source,hash,"word".equals(type)?com.zhixing.common.CryptoUtils.sha256(title):null,stage,"published",version,version);jdbc.update("INSERT INTO content_version (id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,license_snapshot,body_hash,review_status,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",version,id,1,title,summary,body,"intro","word".equals(type)?30:180,"测试许可",com.zhixing.common.CryptoUtils.sha256(body),"approved","00000000000000000000000000000002");}
    private Session register() throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f05-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000003\"}"));JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000003\",\"smsCode\":\"123456\",\"nickname\":\"今日用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());}
    private String bearer(String token){return "Bearer "+token;}
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
