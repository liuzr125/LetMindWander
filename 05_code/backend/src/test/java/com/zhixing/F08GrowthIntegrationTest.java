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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f08;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F08GrowthIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

    @Test void growthSummaryRevisionAndActionFormAClosedLoop() throws Exception {
        Session session=register();
        JsonNode plan=json.readTree(mvc.perform(post("/api/plans/default").header("Authorization",bearer(session.token))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String freeSchedule=seedFreeReview(session.userId);
        mvc.perform(get("/api/reviews/queue").header("Authorization",bearer(session.token)).param("date",LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].scheduleId").value(freeSchedule)).andExpect(jsonPath("$[0].taskId").doesNotExist());
        LocalDate sourceWeek=LocalDate.now(ZoneId.of("Asia/Shanghai")).with(DayOfWeek.MONDAY).minusWeeks(1);
        seedWeek(session.userId,plan.path("id").asText(),sourceWeek);
        seedOrphanSummary(session.userId,sourceWeek.minusWeeks(1));

        mvc.perform(get("/api/growth").header("Authorization",bearer(session.token)).param("weekStart",sourceWeek.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.weekCompleted").value(1)).andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.previousSummary.hasData").value(false));

        JsonNode draft=json.readTree(mvc.perform(get("/api/weekly-summaries/{weekStart}",sourceWeek).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("draft")).andExpect(jsonPath("$.metrics.learningCount").value(1))
                .andReturn().getResponse().getContentAsString());
        JsonNode edited=json.readTree(mvc.perform(put("/api/weekly-summaries/{weekStart}",sourceWeek).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"本周理解了检索增强生成的基本流程。\",\"expectedVersion\":"+draft.path("versionNo").asInt()+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revisionNo").value(2)).andReturn().getResponse().getContentAsString());
        JsonNode confirmed=json.readTree(mvc.perform(post("/api/weekly-summaries/{weekStart}/confirm",sourceWeek).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"revisionId\":\""+edited.path("currentRevisionId").asText()+"\",\"expectedVersion\":"+edited.path("versionNo").asInt()+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("confirmed")).andReturn().getResponse().getContentAsString());

        LocalDate actionDate=sourceWeek.plusWeeks(1).with(DayOfWeek.FRIDAY);
        JsonNode actions=json.readTree(mvc.perform(post("/api/weekly-summaries/{weekStart}/actions",sourceWeek).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"整理一条 RAG 笔记\",\"note\":\"写出三条要点\",\"scheduledDate\":\""+actionDate+"\",\"estimatedMinutes\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actions[0].state").value("draft")).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/weekly-summaries/{weekStart}/actions/confirm",sourceWeek).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmTemporaryDays\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.actions[0].state").value("confirmed"));

        String actionId=actions.path("actions").get(0).path("id").asText();
        Integer stateCount=jdbc.queryForObject("SELECT COUNT(*) FROM weekly_action WHERE id=? AND state='confirmed'",Integer.class,actionId);
        org.junit.jupiter.api.Assertions.assertEquals(1,stateCount);

        seedExtraTask(session.userId,sourceWeek,plan.path("id").asText());
        mvc.perform(get("/api/weekly-summaries/{weekStart}",sourceWeek).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stale").value(true)).andExpect(jsonPath("$.confirmedRevisionId").value(confirmed.path("confirmedRevisionId").asText()));
        mvc.perform(post("/api/weekly-summaries/{weekStart}/recalculate",sourceWeek).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("draft")).andExpect(jsonPath("$.hasConfirmedVersion").value(true));
    }

    private void seedWeek(String owner,String planId,LocalDate week){
        String packageId="81000000000000000000000000000001",taskId="81000000000000000000000000000002",journalId="81000000000000000000000000000003",revisionId="81000000000000000000000000000004";
        jdbc.update("INSERT INTO daily_package (id,owner_id,business_date,plan_id,state,is_temporary,version_no,budget_seconds,current_count,final_done_count) VALUES (?,?,?,?,?,?,?,?,?,?)",packageId,owner,week,planId,"active",0,1,600,1,1);
        jdbc.update("INSERT INTO daily_task (id,owner_id,package_id,task_type,title_snapshot,target_key,status,estimated_seconds,sort_no,version_no) VALUES (?,?,?,?,?,?,?,?,?,?)",taskId,owner,packageId,"tech","RAG 基础","tech:test","DONE",180,1,1);
        jdbc.update("INSERT INTO daily_journal (id,owner_id,business_date,current_revision_id,submitted_revision_id,version_no,state) VALUES (?,?,?,?,?,?,?)",journalId,owner,week,revisionId,revisionId,1,"submitted");
        jdbc.update("INSERT INTO journal_revision (id,owner_id,journal_id,revision_no,done_text,blocker_text,learned_text,next_step_text,content_hash,save_kind) VALUES (?,?,?,?,?,?,?,?,?,?)",revisionId,owner,journalId,1,"完成学习","检索结果需要验证","理解 RAG 流程","整理笔记",CryptoUtils.sha256("journal"),"submit");
    }

    private void seedExtraTask(String owner,LocalDate week,String planId){String packageId=jdbc.queryForObject("SELECT id FROM daily_package WHERE owner_id=? AND business_date=?",String.class,owner,week);jdbc.update("INSERT INTO daily_task (id,owner_id,package_id,task_type,title_snapshot,target_key,status,estimated_seconds,sort_no,version_no) VALUES (?,?,?,?,?,?,?,?,?,?)","81000000000000000000000000000005",owner,packageId,"word","context","word:test","DONE",30,2,1);}
    private void seedOrphanSummary(String owner,LocalDate week){jdbc.update("INSERT INTO weekly_summary (id,owner_id,week_start,current_revision_id,source_fingerprint,is_stale,version_no) VALUES (?,?,?,?,?,?,?)","81000000000000000000000000000009",owner,week,"81000000000000000000000000000010",CryptoUtils.sha256("orphan"),0,1);}
    private String seedFreeReview(String owner){String knowledge=CryptoUtils.randomId(),schedule=CryptoUtils.randomId();jdbc.update("INSERT INTO knowledge_item (id,owner_id,item_type,title,body,search_text,learning_status,verification_status,version_no,visibility,state) VALUES (?,?,?,?,?,?,?,?,?,?,?)",knowledge,owner,"note","自由复习项","无需进入今日任务包","自由复习项 无需进入今日任务包","learning","unverified",1,"private","active");jdbc.update("INSERT INTO review_schedule (id,owner_id,knowledge_id,state,stage,due_date,version_no) VALUES (?,?,?,?,?,?,?)",schedule,owner,knowledge,"active",0,LocalDate.now(ZoneId.of("Asia/Shanghai")),1);return schedule;}
    private Session register() throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f08-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000008\"}"));JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000008\",\"smsCode\":\"123456\",\"nickname\":\"成长用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());}
    private String bearer(String token){return "Bearer "+token;}
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
