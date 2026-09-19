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
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f11;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F11VocabularyBookIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

    @Test void requiresABookAndRefreshesUnstartedTodayWordsWhenSelectionChanges() throws Exception {
        Session session=register(); seedCatalog();
        mvc.perform(get("/api/learning/contents").header("Authorization",bearer(session.token))
                .param("type","word").param("keyword","alfa"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items",org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.items[0].contentId").value(WORD_ONE))
                .andExpect(jsonPath("$.items[0].wordTerm").value("alpha"))
                .andExpect(jsonPath("$.items[0].aliasMatch").value(true));
        mvc.perform(post("/api/plans/default").header("Authorization",bearer(session.token))).andExpect(status().isOk());
        String today=LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        mvc.perform(post("/api/days/{date}/activate",today).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[?(@.taskType == 'word')]").isEmpty())
                .andExpect(jsonPath("$.gaps[?(@.taskType == 'word')].reason").value("未选择词书"));

        mvc.perform(get("/api/vocabulary-books").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].selected").value(false));

        select(session.token,BOOK_ONE);
        mvc.perform(get("/api/vocabulary-books/current").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(BOOK_ONE));
        assertThat(activeWord(session.userId,today)).isEqualTo(WORD_ONE);
        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.learnedCount").value(0)).andExpect(jsonPath("$.remainingCount").value(1))
                .andExpect(jsonPath("$.dailyNewCount").value(3)).andExpect(jsonPath("$.estimatedRemainingDays").value(1))
                .andExpect(jsonPath("$.completionRate").value(0.0)).andExpect(jsonPath("$.items[0].contentId").value(WORD_ONE))
                .andExpect(jsonPath("$.items[0].learned").value(false));
        mvc.perform(post("/api/learning/contents/{id}/understood",WORD_ONE).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE owner_id=? AND feedback='understood'",Integer.class,session.userId)).isEqualTo(1);
        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)).param("status","learned"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.learnedCount").value(1))
                .andExpect(jsonPath("$.remainingCount").value(0)).andExpect(jsonPath("$.estimatedRemainingDays").value(0)).andExpect(jsonPath("$.completionRate").value(100.0))
                .andExpect(jsonPath("$.items[0].learned").value(true));
        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)).param("status","remaining"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

        select(session.token,BOOK_TWO);
        mvc.perform(get("/api/vocabulary-books/current").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(BOOK_TWO));
        assertThat(activeWord(session.userId,today)).isEqualTo(WORD_TWO);
        mvc.perform(get("/api/vocabulary-books/current/progress").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookId").value(BOOK_TWO))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.learnedCount").value(1)).andExpect(jsonPath("$.remainingCount").value(1))
                .andExpect(jsonPath("$.items[?(@.contentId == '"+WORD_ONE+"')].learned").value(true));
        mvc.perform(post("/api/learning/contents/{id}/review",WORD_ONE).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inReview").value(true))
                .andExpect(jsonPath("$.understood").value(true));
        assertThat(jdbc.queryForObject("SELECT learning_status FROM learning_record WHERE owner_id=? AND content_id=?",String.class,session.userId,WORD_ONE)).isEqualTo("understood");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE owner_id=?",Integer.class,session.userId)).isEqualTo(1);
        mvc.perform(post("/api/learning/contents/{id}/feedback",WORD_ONE).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":\"unclear\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(false))
                .andExpect(jsonPath("$.familiarityPercent").value(0)).andExpect(jsonPath("$.inReview").value(true));
        mvc.perform(post("/api/learning/contents/{id}/feedback",WORD_ONE).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":\"fuzzy\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(false))
                .andExpect(jsonPath("$.familiarityPercent").value(50)).andExpect(jsonPath("$.inReview").value(true));
        assertThat(jdbc.queryForObject("SELECT learning_status FROM learning_record WHERE owner_id=? AND content_id=?",String.class,session.userId,WORD_ONE)).isEqualTo("learning");
        assertThat(jdbc.queryForObject("SELECT stage FROM review_schedule rs JOIN knowledge_item ki ON ki.id=rs.knowledge_id WHERE rs.owner_id=? AND ki.bookmark_content_id=?",Integer.class,session.userId,WORD_ONE)).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT due_date FROM review_schedule rs JOIN knowledge_item ki ON ki.id=rs.knowledge_id WHERE rs.owner_id=? AND ki.bookmark_content_id=?",LocalDate.class,session.userId,WORD_ONE)).isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1));
        mvc.perform(post("/api/learning/contents/{id}/feedback",WORD_ONE).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"feedback\":\"remember\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(true))
                .andExpect(jsonPath("$.familiarityPercent").value(85));
        assertThat(jdbc.queryForObject("SELECT stage FROM review_schedule rs JOIN knowledge_item ki ON ki.id=rs.knowledge_id WHERE rs.owner_id=? AND ki.bookmark_content_id=?",Integer.class,session.userId,WORD_ONE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT due_date FROM review_schedule rs JOIN knowledge_item ki ON ki.id=rs.knowledge_id WHERE rs.owner_id=? AND ki.bookmark_content_id=?",LocalDate.class,session.userId,WORD_ONE)).isEqualTo(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_event WHERE owner_id=?",Integer.class,session.userId)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_vocabulary_book WHERE owner_id=? AND state='active'",Integer.class,session.userId)).isEqualTo(1);
    }

    private void select(String token,String bookId) throws Exception {
        mvc.perform(put("/api/vocabulary-books/current").header("Authorization",bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\""+bookId+"\",\"dailyNewLimit\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selected").value(true));
    }
    private String activeWord(String owner,String date){return jdbc.queryForObject("SELECT content_id FROM daily_task t JOIN daily_package p ON p.id=t.package_id WHERE t.owner_id=? AND p.business_date=? AND t.task_type='word' AND t.status<>'CANCELLED'",String.class,owner,LocalDate.parse(date));}

    private void seedCatalog(){
        String source="f1100000000000000000000000000000";
        jdbc.update("INSERT INTO content_source(id,name,license_note) VALUES(?,?,?)",source,"词书测试来源","测试许可");
        insertWord(WORD_ONE,"f1110000000000000000000000000001","alpha",source);
        jdbc.update("INSERT INTO word_alias(id,content_id,alias_term,normalized_alias,alias_hash,alias_type,state,source_note) VALUES(?,?,?,?,?,?,?,?)",
                "f1190000000000000000000000000009",WORD_ONE,"alfa","alfa",CryptoUtils.sha256("alfa"),"misspelling","active","集成测试别名");
        insertWord(WORD_TWO,"f1120000000000000000000000000002","beta",source);
        insertBook(BOOK_ONE,"BOOK_ONE","第一词书",1,WORD_ONE,"f1150000000000000000000000000005");
        insertBook(BOOK_TWO,"BOOK_TWO","第二词书",2,WORD_TWO,"f1160000000000000000000000000006");
        jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,?,?)",
                "f1100000000000000000000000000010",BOOK_TWO,WORD_ONE,2,4,1);
        jdbc.update("UPDATE vocabulary_book SET word_count=2 WHERE id=?",BOOK_TWO);
    }
    private void insertWord(String id,String version,String term,String source){jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",id,"word",source,CryptoUtils.sha256(id),CryptoUtils.sha256(term),"junior","published",version,version);jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",version,id,1,term,term,term,"intro",30,term,term,"测试许可",CryptoUtils.sha256(term),"approved","00000000000000000000000000000002");}
    private void insertBook(String id,String code,String name,int sort,String word,String relation){jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,description,word_count,sort_no,is_recommended,state) VALUES(?,?,?,?,?,?,?,?,?)",id,code,name,"k12",name,1,sort,1,"active");jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,?,?)",relation,id,word,1,5,1);}

    private Session register() throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f11-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000011\"}"));JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000011\",\"smsCode\":\"123456\",\"nickname\":\"词书用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());}
    private String bearer(String token){return "Bearer "+token;}
    private static final String BOOK_ONE="f1130000000000000000000000000003",BOOK_TWO="f1140000000000000000000000000004",WORD_ONE="f1170000000000000000000000000007",WORD_TWO="f1180000000000000000000000000008";
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
