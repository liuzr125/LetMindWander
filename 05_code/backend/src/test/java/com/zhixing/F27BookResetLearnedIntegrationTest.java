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
 * F27「重新学习」：把当前词书已学的单词重新划回未学（学习记录保留），并同步取消这些词的到期复习排期。
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f27;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F27BookResetLearnedIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;

    @Test void resetLearnedMovesLearnedWordsBackToUnlearned() throws Exception {
        Session session=register();
        seedSource();
        // w1 同时属于两本词书；w2 只在词书甲；w4 只在词书乙
        word(W1,V1,"apple");word(W2,V2,"banana");word(W4,V4,"cherry");
        book(BOOK_A,"测试词书甲",new String[]{W1,W2});
        book(BOOK_B,"测试词书乙",new String[]{W1,W4});

        mvc.perform(put("/api/vocabulary-books/current").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\""+BOOK_A+"\",\"dailyNewLimit\":2}")).andExpect(status().isOk());

        // 学 w1、w2（词书甲），再学 w4（只在词书乙）
        mvc.perform(post("/api/learning/contents/{id}/understood",W1).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post("/api/learning/contents/{id}/feedback",W2).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":\"remember\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/learning/contents/{id}/understood",W4).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.learnedCount").value(2))
                .andExpect(jsonPath("$.totalCount").value(2)).andExpect(jsonPath("$.remainingCount").value(0));

        // w1、w4 各有一条到期复习排期
        scheduleReview(session.userId,W1,"复习A",SCHEDULE_A);
        scheduleReview(session.userId,W4,"复习B",SCHEDULE_B);
        assertThat(reviewState(SCHEDULE_A)).isEqualTo("active");

        // 重新学习：词书甲已学 2 个 → 划回未学
        mvc.perform(post("/api/vocabulary-books/current/reset-learned").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId").value(BOOK_A))
                .andExpect(jsonPath("$.bookName").value("测试词书甲"))
                .andExpect(jsonPath("$.resetCount").value(2))
                // w1 的排期 + w2 通过记忆反馈自动生成的排期，都属于词书甲，一起取消
                .andExpect(jsonPath("$.pausedReviewCount").value(2))
                .andExpect(jsonPath("$.learnedCount").value(0))
                .andExpect(jsonPath("$.remainingCount").value(2));

        // 词书甲进度归零
        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.learnedCount").value(0))
                .andExpect(jsonPath("$.remainingCount").value(2)).andExpect(jsonPath("$.completionRate").value(0.0));
        // 列表接口里也变成未学
        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)).param("status","learned"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)).param("status","remaining"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].learned").value(false));

        // 学习状态回到未学、熟悉度清空，但学习记录本身（首次学会时间）保留
        assertThat(learningStatus(W1)).isEqualTo("unlearned");
        assertThat(learningStatus(W2)).isEqualTo("unlearned");
        assertThat(value(W1,"familiarity_percent")).isNull();
        assertThat(value(W1,"first_completed_at")).isNotNull();
        // 不属于词书甲的词（w4）不受影响
        assertThat(learningStatus(W4)).isEqualTo("understood");
        // 到期复习：词书甲的 w1 排期被取消，词书乙的 w4 保持
        assertThat(reviewState(SCHEDULE_A)).isEqualTo("paused");
        assertThat(reviewState(SCHEDULE_B)).isEqualTo("active");
        // 共享词条属于「词」维度：词书乙里 w1 也变成未学，但 w4 仍已学
        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookId").value(BOOK_A));

        // 再点一次：没有已学词，重置数为 0 且不报错（幂等）
        mvc.perform(post("/api/vocabulary-books/current/reset-learned").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resetCount").value(0)).andExpect(jsonPath("$.pausedReviewCount").value(0));
    }

    @Test void resetRequiresSelectedBookAndSession() throws Exception {
        Session session=register("f27-nobook","13800000052","没选词书");
        mvc.perform(post("/api/vocabulary-books/current/reset-learned").header("Authorization",bearer(session.token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VOCABULARY_BOOK_REQUIRED"));
        mvc.perform(post("/api/vocabulary-books/current/reset-learned")).andExpect(status().isUnauthorized());
    }

    /** 选词书后重新学习：已学词回到未学，之后排新词时会重新候选（今日已排任务不变）。 */
    @Test void resetWordsBecomeCandidatesAgain() throws Exception {
        Session session=register("f27-candidate","13800000053","重新候选");
        seedSource();
        word(W1,V1,"apple");word(W2,V2,"banana");
        book(BOOK_A,"测试词书甲",new String[]{W1,W2});
        mvc.perform(put("/api/vocabulary-books/current").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\""+BOOK_A+"\",\"dailyNewLimit\":2}")).andExpect(status().isOk());
        mvc.perform(post("/api/learning/contents/{id}/understood",W1).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post("/api/vocabulary-books/current/reset-learned").header("Authorization",bearer(session.token))).andExpect(status().isOk());
        long stillLearned=jdbc.queryForObject("SELECT COUNT(*) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
                "WHERE lr.owner_id=? AND w.book_id=? AND lr.learning_status IN ('understood','mastered')",Long.class,session.userId,BOOK_A);
        assertThat(stillLearned).isZero();
        List<Map<String,Object>> candidates=jdbc.queryForList("SELECT lr.content_id FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
                "WHERE lr.owner_id=? AND w.book_id=? AND lr.learning_status NOT IN ('understood','mastered')",session.userId,BOOK_A);
        assertThat(candidates).hasSize(1);
    }

    private String learningStatus(String contentId){return jdbc.queryForObject("SELECT learning_status FROM learning_record WHERE owner_id=(SELECT owner_id FROM learning_record WHERE content_id=? LIMIT 1) AND content_id=?",String.class,contentId,contentId);}
    private Object value(String contentId,String column){return jdbc.queryForMap("SELECT "+column+" FROM learning_record WHERE content_id=? LIMIT 1",contentId).values().iterator().next();}
    private String reviewState(String scheduleId){return jdbc.queryForObject("SELECT state FROM review_schedule WHERE id=?",String.class,scheduleId);}

    private void seedSource(){jdbc.update("DELETE FROM content_source WHERE id=?",SOURCE);
        jdbc.update("INSERT INTO content_source(id,name,license_note) VALUES(?,?,?)",SOURCE,"重新学习来源","测试许可");}
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
                bookId,"F27-"+bookId.substring(28),name,"k12","primary","重新学习测试",contents.length);
        int i=1;
        for(String content:contents)jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,5,1)",String.format("f27m%s%08d",bookId.substring(28),i),bookId,content,i++);
    }
    private void scheduleReview(String ownerId,String contentId,String title,String scheduleId){
        String knowledge="f27k"+scheduleId.substring(28);
        jdbc.update("DELETE FROM review_schedule WHERE id=?",scheduleId);
        jdbc.update("DELETE FROM knowledge_item WHERE id=?",knowledge);
        jdbc.update("INSERT INTO knowledge_item(id,owner_id,item_type,title,body,search_text,source_content_id) VALUES(?,?,'word',?,?,?,?)",knowledge,ownerId,title,title,title,contentId);
        jdbc.update("INSERT INTO review_schedule(id,owner_id,knowledge_id,state,stage,due_date) VALUES(?,?,?,'active',0,?)",scheduleId,ownerId,knowledge,LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    private Session register(){return register("f27-user","13800000051","重新学习用户");}
    private Session register(String code,String mobile,String nickname){
        try{
            String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();
            String invite=json.readTree(invites).path("codes").get(0).path("code").asText();
            String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();
            String ticket=json.readTree(login).path("registrationTicket").asText();
            mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\"}"));
            String registerBody=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\",\"smsCode\":\"123456\",\"nickname\":\""+nickname+"\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString();
            JsonNode registered=json.readTree(registerBody);
            assertThat(registered.path("accessToken").asText()).as("注册响应：%s",registerBody).isNotEmpty();
            return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText(),nickname);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private String bearer(String token){return "Bearer "+token;}
    private static final String SOURCE="f2700000000000000000000000000000";
    private static final String W1="f27a0000000000000000000000000001",W2="f27a0000000000000000000000000002",W4="f27a0000000000000000000000000004";
    private static final String V1="f27v0000000000000000000000000001",V2="f27v0000000000000000000000000002",V4="f27v0000000000000000000000000004";
    private static final String BOOK_A="f27b000000000000000000000000000a",BOOK_B="f27b000000000000000000000000000b";
    private static final String SCHEDULE_A="f27s000000000000000000000000000a",SCHEDULE_B="f27s000000000000000000000000000b";
    private static class Session{final String token,userId,nickname;Session(String token,String userId,String nickname){this.token=token;this.userId=userId;this.nickname=nickname;}}
}
