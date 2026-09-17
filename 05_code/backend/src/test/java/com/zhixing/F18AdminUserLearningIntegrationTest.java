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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f18;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F18AdminUserLearningIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void adminCanReadPlanVocabularyAndNotebookWithoutPrivateIdentifiers() throws Exception {
        Session session=register();seedCatalog();
        mvc.perform(post("/api/plans/default").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").isNotEmpty());
        mvc.perform(put("/api/vocabulary-books/current").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\""+BOOK+"\",\"dailyNewLimit\":4}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selected").value(true));
        mvc.perform(post("/api/learning/contents/{id}/word-book",WORD).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inWordBook").value(true));
        mvc.perform(post("/api/learning/contents/{id}/understood",WORD).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(true));

        mvc.perform(get("/api/admin/users/{userId}/learning",session.userId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users/{userId}/learning",session.userId).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.nickname").value("进度用户"))
                .andExpect(jsonPath("$.account.mobileMasked").value("138****0018"))
                .andExpect(jsonPath("$.account.wxOpenId").doesNotExist())
                .andExpect(jsonPath("$.plan.id").isNotEmpty())
                .andExpect(jsonPath("$.vocabulary.bookName").value("管理端测试词书"))
                .andExpect(jsonPath("$.vocabulary.totalCount").value(1))
                .andExpect(jsonPath("$.vocabulary.learnedCount").value(1))
                .andExpect(jsonPath("$.vocabulary.remainingCount").value(0))
                .andExpect(jsonPath("$.vocabulary.items").isEmpty())
                .andExpect(jsonPath("$.notebook.totalCount").value(1))
                .andExpect(jsonPath("$.notebookItems.items[0].wordTerm").value("progress"))
                .andExpect(jsonPath("$.today.totalCount").value(0));
        mvc.perform(get("/api/admin/users/{userId}/learning",session.userId).header("X-Admin-Token","dev-admin-token").param("notebookPageSize","51"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        mvc.perform(get("/api/admin/users/{userId}/learning","missing-user").header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_USER_NOT_FOUND"));
    }

    private void seedCatalog(){
        jdbc.update("INSERT INTO content_source(id,name,license_note) VALUES(?,?,?)",SOURCE,"管理端测试来源","测试许可");
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",WORD,"word",SOURCE,CryptoUtils.sha256(WORD),CryptoUtils.sha256("progress"),"junior","published",VERSION,VERSION);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,word_term,phonetic,meaning,example_text,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",VERSION,WORD,1,"progress","进步","progress","intro",30,"progress","/ˈprəʊɡres/","进步；进展","Make progress every day.","测试许可",CryptoUtils.sha256("progress"),"approved","00000000000000000000000000000002");
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,description,word_count,sort_no,is_recommended,state) VALUES(?,?,?,?,?,?,?,?,?)",BOOK,"F18_BOOK","管理端测试词书","k12","用于管理端学习详情测试",1,18,1,"active");
        jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,?,?)",BOOK_WORD,BOOK,WORD,1,5,1);
    }

    private Session register() throws Exception {
        String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}"))
                .andReturn().getResponse().getContentAsString();
        String invite=json.readTree(invites).path("codes").get(0).path("code").asText();
        String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f18-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}"))
                .andReturn().getResponse().getContentAsString();
        String ticket=json.readTree(login).path("registrationTicket").asText();
        mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000018\"}"));
        JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000018\",\"smsCode\":\"123456\",\"nickname\":\"进度用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}"))
                .andReturn().getResponse().getContentAsString());
        return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());
    }
    private String bearer(String token){return "Bearer "+token;}
    private static final String SOURCE="f1800000000000000000000000000001",WORD="f1800000000000000000000000000002",VERSION="f1800000000000000000000000000003",BOOK="f1800000000000000000000000000004",BOOK_WORD="f1800000000000000000000000000005";
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
