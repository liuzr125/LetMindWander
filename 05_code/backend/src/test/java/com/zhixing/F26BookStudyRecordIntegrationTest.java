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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F26 词书学习记录：用户学习每本英语词书的记录落库，并能到管理端查看记录列表、详情（每日明细 + 已学词条）。
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f26;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F26BookStudyRecordIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;

    @Test void studyingWordsOfEachBookIsRecordedAndVisibleInAdmin() throws Exception {
        Session session=register();
        seedSource();

        // 词条 1 同时在两本词书里：学习它要给两本书都留下记录
        word(W1,V1,"apple");word(W2,V2,"banana");word(W3,V3,"cherry");
        book(BOOK_A,"测试词书甲",new String[]{W1,W2,W3});
        book(BOOK_B,"测试词书乙",new String[]{W1});

        mvc.perform(put("/api/vocabulary-books/current").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\""+BOOK_A+"\",\"dailyNewLimit\":2}")).andExpect(status().isOk());
        assertThat(recordCount(session.userId)).isEqualTo(0);

        // 学习动作 1：词条页「已认识」
        mvc.perform(post("/api/learning/contents/{id}/understood",W1).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(recordCount(session.userId)).isEqualTo(2);
        assertThat(sum(session.userId,BOOK_A,"study_count")).isEqualTo(1);
        assertThat(sum(session.userId,BOOK_B,"study_count")).isEqualTo(1);
        assertThat(daily(session.userId,BOOK_A,"study_count")).isEqualTo(1);

        // 学习动作 2：今日单词卡「认识」（remember）
        mvc.perform(post("/api/learning/contents/{id}/feedback",W2).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":\"remember\"}"))
                .andExpect(status().isOk());
        // 学习动作 3：调整熟悉度
        mvc.perform(put("/api/learning/contents/{id}/familiarity",W1).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"familiarityPercent\":60,\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        assertThat(sum(session.userId,BOOK_A,"study_count")).isEqualTo(3);
        assertThat(daily(session.userId,BOOK_A,"study_count")).isEqualTo(3);
        assertThat(sum(session.userId,BOOK_A,"study_day_count")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_book_study_record WHERE owner_id=? AND first_studied_at IS NOT NULL AND last_studied_at IS NOT NULL",Integer.class,session.userId)).isEqualTo(2);

        // 学习动作 4：到期复习（复习项反查词条，再记到词条所属词书）
        scheduleReview(session.userId,W3,"复习词条");
        mvc.perform(post("/api/reviews/{id}/feedback",SCHEDULE).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":\"remember\"}"))
                .andExpect(status().isOk());
        assertThat(sum(session.userId,BOOK_A,"reviewed_count")).isEqualTo(1);
        assertThat(daily(session.userId,BOOK_A,"reviewed_count")).isEqualTo(1);

        // 管理端列表：一本书一行，用户/词书/进度/学习天数/次数齐全
        JsonNode page=utf8(mvc.perform(get("/api/admin/study-records").header("X-Admin-Token","dev-admin-token")
                .param("keyword",session.nickname)).andReturn().getResponse().getContentAsByteArray());
        assertThat(page.path("total").asInt()).isEqualTo(2);
        assertThat(page.path("summary").path("records").asInt()).isEqualTo(2);
        assertThat(page.path("summary").path("users").asInt()).isEqualTo(1);
        assertThat(page.path("summary").path("books").asInt()).isEqualTo(2);
        assertThat(page.path("summary").path("todayStudied").asInt()).isEqualTo(2);
        JsonNode row=null;
        for(JsonNode item:page.path("items")) if(BOOK_A.equals(item.path("bookId").asText())) row=item;
        assertThat(row).isNotNull();
        assertThat(row.path("nickname").asText()).isEqualTo(session.nickname);
        assertThat(row.path("shortId").asText()).startsWith("R_");
        assertThat(row.path("mobile").asText()).isEqualTo("138****0041");
        assertThat(row.path("bookName").asText()).isEqualTo("测试词书甲");
        assertThat(row.path("levelLabel").asText()).isEqualTo("小学");
        assertThat(row.path("totalWords").asInt()).isEqualTo(3);
        assertThat(row.path("learnedWords").asInt()).isEqualTo(2);
        assertThat(row.path("learningWords").asInt()).isEqualTo(0);
        assertThat(row.path("completionPercent").asInt()).isEqualTo(67);
        assertThat(row.path("studyCount").asInt()).isEqualTo(3);
        assertThat(row.path("reviewedCount").asInt()).isEqualTo(1);
        assertThat(row.path("studyDayCount").asInt()).isEqualTo(1);
        assertThat(row.path("currentBook").asBoolean()).isTrue();

        // 管理端详情：每日明细（新学词数按 learning_record 实时统计）
        String today=LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        mvc.perform(get("/api/admin/study-records/{ownerId}/{bookId}",session.userId,BOOK_A).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record.bookName").value("测试词书甲"))
                .andExpect(jsonPath("$.activeDays").value(1))
                .andExpect(jsonPath("$.days[0].businessDate").value(today))
                .andExpect(jsonPath("$.days[0].newWordCount").value(2))
                .andExpect(jsonPath("$.days[0].studyCount").value(3))
                .andExpect(jsonPath("$.days[0].reviewedCount").value(1));

        // 管理端详情里的已学词条
        mvc.perform(get("/api/admin/study-records/{ownerId}/{bookId}/words",session.userId,BOOK_A).header("X-Admin-Token","dev-admin-token").param("status","learned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].word").exists())
                .andExpect(jsonPath("$.items[0].statusLabel").value("已学会"))
                .andExpect(jsonPath("$.items[0].difficultyLabel").value("入门"));
        mvc.perform(get("/api/admin/study-records/{ownerId}/{bookId}/words",session.userId,BOOK_B).header("X-Admin-Token","dev-admin-token").param("status","all"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].word").value("apple"));

        // 词书下拉（不依赖「英语单词」菜单权限）
        JsonNode books=utf8(mvc.perform(get("/api/admin/study-records/books").header("X-Admin-Token","dev-admin-token")).andReturn().getResponse().getContentAsByteArray());
        String matchedBook=null;
        for(JsonNode item:books) if(BOOK_A.equals(item.path("id").asText())) matchedBook=item.path("name").asText();
        assertThat(matchedBook).isEqualTo("测试词书甲");

        // 守卫：未授权 / 状态非法 / 记录不存在
        mvc.perform(get("/api/admin/study-records")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/study-records").header("X-Admin-Token","wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/study-records/{ownerId}/{bookId}/words",session.userId,BOOK_A).header("X-Admin-Token","dev-admin-token").param("status","unknown"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STUDY_WORD_STATUS"));
        mvc.perform(get("/api/admin/study-records/{ownerId}/{bookId}",session.userId,"ffffffffffffffffffffffffffffffff").header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_STUDY_RECORD_NOT_FOUND"));
        mvc.perform(get("/api/admin/study-records").header("X-Admin-Token","dev-admin-token").param("dateFrom","2026-09-22").param("dateTo","2026-09-01"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    /** 换个用户学习同一本书，记录互不影响（每用户 × 每词书一行）。 */
    @Test void recordsAreScopedByUserAndBook() throws Exception {
        Session alice=register("f26-alice","13800000043","学习甲");
        Session bob=register("f26-bob","13800000042","学习乙");
        seedSource();
        word(W1,V1,"apple");
        book(BOOK_A,"测试词书甲",new String[]{W1});
        mvc.perform(post("/api/learning/contents/{id}/understood",W1).header("Authorization",bearer(alice.token)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post("/api/learning/contents/{id}/understood",W1).header("Authorization",bearer(bob.token)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT owner_id,study_count FROM vocabulary_book_study_record WHERE book_id=? AND owner_id IN (?,?)",BOOK_A,alice.userId,bob.userId);
        assertThat(rows).hasSize(2);
        for(Map<String,Object> row:rows) assertThat(((Number)row.get("study_count")).intValue()).isEqualTo(1);
    }

    private int recordCount(String ownerId){return jdbc.queryForObject("SELECT COUNT(*) FROM vocabulary_book_study_record WHERE owner_id=?",Integer.class,ownerId);}
    private int sum(String ownerId,String bookId,String column){return jdbc.queryForObject("SELECT "+column+" FROM vocabulary_book_study_record WHERE owner_id=? AND book_id=?",Integer.class,ownerId,bookId);}
    private int daily(String ownerId,String bookId,String column){return jdbc.queryForObject("SELECT "+column+" FROM vocabulary_book_study_daily WHERE owner_id=? AND book_id=? AND business_date=?",Integer.class,ownerId,bookId,LocalDate.now(ZoneId.of("Asia/Shanghai")));}

    private void seedSource(){jdbc.update("DELETE FROM content_source WHERE id=?",SOURCE);
        jdbc.update("INSERT INTO content_source(id,name,license_note) VALUES(?,?,?)",SOURCE,"学习记录来源","测试许可");}
    private void word(String id,String version,String term){
        jdbc.update("DELETE FROM vocabulary_book_word WHERE content_id=?",id);
        jdbc.update("DELETE FROM content_version WHERE content_id=?",id);
        jdbc.update("DELETE FROM learning_content WHERE id=?",id);
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,?,?,?,?,NULL,'published',?,?,CURRENT_TIMESTAMP)",
                id,"word",SOURCE,CryptoUtils.sha256(id),CryptoUtils.sha256(term),version,version);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,word_term,meaning,phonetic,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'intro',30,?,?,?,'测试许可',?,'approved',?)",
                version,id,term,term,term,term,term,term+"/ˈtest/",CryptoUtils.sha256(term),SOURCE);
    }
    private void book(String bookId,String name,String[] contents){
        jdbc.update("DELETE FROM vocabulary_book_word WHERE book_id=?",bookId);
        jdbc.update("DELETE FROM vocabulary_book WHERE id=?",bookId);
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,description,word_count,sort_no,is_recommended,state) VALUES(?,?,?,?,?,?,?,1,1,'active')",
                bookId,"F26-"+bookId.substring(28),name,"k12","primary","学习记录测试",contents.length);
        int i=1;
        for(String content:contents)jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,5,1)",String.format("f26m%s%08d",bookId.substring(28),i),bookId,content,i++);
    }
    private void scheduleReview(String ownerId,String contentId,String title){
        String knowledge="f26k0000000000000000000000000001";
        jdbc.update("DELETE FROM review_schedule WHERE id=?",SCHEDULE);
        jdbc.update("DELETE FROM knowledge_item WHERE id=?",knowledge);
        jdbc.update("INSERT INTO knowledge_item(id,owner_id,item_type,title,body,search_text,source_content_id) VALUES(?,?,'word',?,?,?,?)",
                knowledge,ownerId,title,title,title,contentId);
        jdbc.update("INSERT INTO review_schedule(id,owner_id,knowledge_id,state,stage,due_date) VALUES(?,?,?,'active',0,?)",SCHEDULE,ownerId,knowledge,LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    private Session register() throws Exception {return register("f26-user","13800000041","学习记录用户");}
    private Session register(String code,String mobile,String nickname) throws Exception {
        String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();
        String invite=json.readTree(invites).path("codes").get(0).path("code").asText();
        String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();
        String ticket=json.readTree(login).path("registrationTicket").asText();
        mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\"}"));
        String registerBody=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\",\"smsCode\":\"123456\",\"nickname\":\""+nickname+"\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString();
        JsonNode registered=json.readTree(registerBody);
        assertThat(registered.path("accessToken").asText()).as("注册响应：%s",registerBody).isNotEmpty();
        return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText(),nickname);
    }
    private String bearer(String token){return "Bearer "+token;}
    /** MockMvc 默认按 ISO-8859-1 读响应体，中文会乱码，统一按 UTF-8 解码。 */
    private JsonNode utf8(byte[] body) throws Exception {return json.readTree(new String(body,java.nio.charset.StandardCharsets.UTF_8));}
    private static final String SOURCE="f2600000000000000000000000000000",W1="f26a0000000000000000000000000001",W2="f26a0000000000000000000000000002",W3="f26a0000000000000000000000000003";
    private static final String V1="f26v0000000000000000000000000001",V2="f26v0000000000000000000000000002",V3="f26v0000000000000000000000000003";
    private static final String BOOK_A="f26b000000000000000000000000000a",BOOK_B="f26b000000000000000000000000000b",SCHEDULE="f26s0000000000000000000000000001";
    private static class Session{final String token,userId,nickname;Session(String token,String userId,String nickname){this.token=token;this.userId=userId;this.nickname=nickname;}}
}
