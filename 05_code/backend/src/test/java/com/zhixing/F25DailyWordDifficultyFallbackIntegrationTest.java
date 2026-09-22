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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 换词书后今日新词补排：词条难度与计划难度不一致时也要排满（计划难度优先、同书其它难度补齐），
 * 避免出现「英语新词 已完成」+「素材不足，少 N 项」的断档。
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f25;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F25DailyWordDifficultyFallbackIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

    @Test void switchingToBookWithDifferentDifficultyStillFillsTodayNewWords() throws Exception {
        Session session=register();
        seedSource();
        mvc.perform(post("/api/plans/default").header("Authorization",bearer(session.token))).andExpect(status().isOk());
        String planDifficulty=jdbc.queryForObject("SELECT difficulty FROM learning_plan WHERE owner_id=? ORDER BY created_at DESC LIMIT 1",String.class,session.userId);
        int wanted=jdbc.queryForObject("SELECT new_word_count FROM learning_plan WHERE owner_id=? ORDER BY created_at DESC LIMIT 1",Integer.class,session.userId);
        assertThat(wanted).isGreaterThan(1);

        // 词书里的词条难度与计划难度刻意相反，且数量足够计划所需
        String bookDifficulty="intro".equalsIgnoreCase(planDifficulty)?"advanced":"intro";
        List<String> expected=new ArrayList<String>();
        for(int i=1;i<=wanted;i++){
            String content=String.format("f25%029d",i),version=String.format("f25v%028d",i);
            word(content,version,"word"+i,bookDifficulty);expected.add(content);
        }
        book(BOOK,bookDifficulty,expected);

        String today=LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        mvc.perform(post("/api/days/{date}/activate",today).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tasks[?(@.taskType == 'word')]").isEmpty())
                .andExpect(jsonPath("$.gaps[?(@.taskType == 'word')].reason").value("未选择词书"));

        mvc.perform(put("/api/vocabulary-books/current").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\""+BOOK+"\",\"dailyNewLimit\":"+wanted+"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selected").value(true));

        // 换书后：今日新词按计划数量排满，且不再报「素材不足」
        List<String> tasks=jdbc.queryForList("SELECT t.content_id FROM daily_task t JOIN daily_package p ON p.id=t.package_id " +
                "WHERE t.owner_id=? AND p.business_date=? AND t.task_type='word' AND t.status<>'CANCELLED' ORDER BY t.sort_no",
                String.class,session.userId,LocalDate.parse(today));
        assertThat(tasks).containsExactlyElementsOf(expected);
        mvc.perform(get("/api/days/{date}",today).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gaps[?(@.taskType == 'word')]").isEmpty())
                .andExpect(jsonPath("$.tasks[?(@.taskType == 'word' && @.status == 'TODO')]").isNotEmpty());

        // 已完成的词任务仍保留（事实记录），换书不会把它抹掉
        java.util.Map<String,Object> firstTask=jdbc.queryForMap("SELECT t.id,t.version_no FROM daily_task t JOIN daily_package p ON p.id=t.package_id " +
                "WHERE t.owner_id=? AND p.business_date=? AND t.task_type='word' AND t.status<>'CANCELLED' ORDER BY t.sort_no LIMIT 1",session.userId,LocalDate.parse(today));
        String taskId=String.valueOf(firstTask.get("id"));
        mvc.perform(post("/api/tasks/{id}/events",taskId).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"start\",\"expectedVersion\":1}")).andExpect(status().isOk());
        mvc.perform(post("/api/tasks/{id}/events",taskId).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"complete\",\"expectedVersion\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));

        mvc.perform(put("/api/vocabulary-books/current").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\""+BOOK+"\",\"dailyNewLimit\":"+wanted+"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM daily_task WHERE id=?",String.class,taskId)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM daily_task t JOIN daily_package p ON p.id=t.package_id " +
                "WHERE t.owner_id=? AND p.business_date=? AND t.task_type='word' AND t.status='DONE'",Integer.class,session.userId,LocalDate.parse(today)))
                .isEqualTo(1);
    }

    private void seedSource(){jdbc.update("DELETE FROM content_source WHERE id=?",SOURCE);
        jdbc.update("INSERT INTO content_source(id,name,license_note) VALUES(?,?,?)",SOURCE,"换书测试来源","测试许可");}
    private void word(String id,String version,String term,String difficulty){
        jdbc.update("DELETE FROM vocabulary_book_word WHERE content_id=?",id);
        jdbc.update("DELETE FROM content_version WHERE content_id=?",id);
        jdbc.update("DELETE FROM learning_content WHERE id=?",id);
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,?,?,?,?,NULL,'published',?,?,CURRENT_TIMESTAMP)",
                id,"word",SOURCE,CryptoUtils.sha256(id),CryptoUtils.sha256(term),version,version);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,?,30,?,?,'测试许可',?,'approved',?)",
                version,id,term,term,term,difficulty,term,term,CryptoUtils.sha256(term),SOURCE);
    }
    private void book(String bookId,String difficulty,List<String> contents){
        jdbc.update("DELETE FROM vocabulary_book_word WHERE book_id=?",bookId);
        jdbc.update("DELETE FROM vocabulary_book WHERE id=?",bookId);
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,description,word_count,sort_no,is_recommended,state) VALUES(?,?,?,?,?,?,?,1,1,'active')",
                bookId,"F25-BOOK","换书测试词书","k12",difficulty,"难度与计划相反",contents.size());
        int i=1;
        for(String content:contents)jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,5,1)",String.format("f25m%028d",i),bookId,content,i++);
    }

    private Session register() throws Exception {
        String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();
        String invite=json.readTree(invites).path("codes").get(0).path("code").asText();
        String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f25-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();
        String ticket=json.readTree(login).path("registrationTicket").asText();
        mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000025\"}"));
        JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000025\",\"smsCode\":\"123456\",\"nickname\":\"换书用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());
        return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());
    }
    private String bearer(String token){return "Bearer "+token;}
    private static final String SOURCE="f2500000000000000000000000000000",BOOK="f25b0000000000000000000000000001";
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
