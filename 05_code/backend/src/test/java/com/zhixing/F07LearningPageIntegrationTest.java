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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f07;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F07LearningPageIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;

    @Test void learningListsNotebookArticleFamiliarityAndSourceNoteFormAClosedLoop() throws Exception {
        Session session=register();seedLearningDictionary();seed(session.userId);
        mvc.perform(get("/api/learning/filters").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.difficulties[1].label").value("基础"))
                .andExpect(jsonPath("$.stages[2].label").value("初中阶段"))
                .andExpect(jsonPath("$.notebookStatuses[1].value").value("review"));
        mvc.perform(get("/api/learning/contents").header("Authorization",bearer(session.token)).param("type","tech").param("topicId",TOPIC))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("理解 RAG")).andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/learning/contents/{id}",TECH).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceName").value("测试来源"))
                .andExpect(jsonPath("$.sourceType").value("official"))
                .andExpect(jsonPath("$.sourceUrl").value("https://example.com/source"))
                .andExpect(jsonPath("$.originUrl").value("https://example.com/original"))
                .andExpect(jsonPath("$.originAuthor").value("测试作者"))
                .andExpect(jsonPath("$.originPublishedAt").exists())
                .andExpect(jsonPath("$.publishedAt").exists());
        mvc.perform(get("/api/learning/contents").header("Authorization",bearer(session.token)).param("type","word").param("stage","junior"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].wordTerm").value("context"));
        mvc.perform(post("/api/learning/contents/{id}/word-book",WORD).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inWordBook").value(true));
        mvc.perform(put("/api/learning/contents/{id}/familiarity",WORD).header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON).content("{\"familiarityPercent\":85,\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.familiarityPercent").value(85)).andExpect(jsonPath("$.recordVersion").value(1));
        mvc.perform(get("/api/learning/contents").header("Authorization",bearer(session.token)).param("type","word").param("notebook","true").param("status","familiar"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].contentId").value(WORD));
        mvc.perform(get("/api/learning/notebook/summary").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.dueCount").value(0));
        mvc.perform(get("/api/learning/contents").header("Authorization",bearer(session.token)).param("type","english_article").param("page","1").param("pageSize","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("A Careful Decision 2: Museum"))
                .andExpect(jsonPath("$.items[1].title").value("A Careful Decision 10: Park")).andExpect(jsonPath("$.hasMore").value(true));
        mvc.perform(get("/api/learning/contents").header("Authorization",bearer(session.token)).param("type","english_article").param("page","2").param("pageSize","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("A Careful Decision 101: Home"))
                .andExpect(jsonPath("$.items[1].title").value("How RAG Works")).andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/learning/contents/{id}",ARTICLE).header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.articleBlocks[0].text").value("RAG retrieves useful context before answering."))
                .andExpect(jsonPath("$.articleBlocks[0].words[0].contentId").value(WORD))
                .andExpect(jsonPath("$.articleBlocks[0].tokens[2].text").value("retrieves"))
                .andExpect(jsonPath("$.articleBlocks[0].tokens[2].known").value(true))
                .andExpect(jsonPath("$.articleBlocks[0].tokens[2].speechKey").value("glossary:retrieves"))
                .andExpect(jsonPath("$.articleBlocks[0].tokens[2].meaning").value("检索；取回"))
                .andExpect(jsonPath("$.articleBlocks[0].tokens[6].text").value("context"))
                .andExpect(jsonPath("$.articleBlocks[0].tokens[6].contentId").value(WORD))
                .andExpect(jsonPath("$.articleBlocks[0].tokens[6].meaning").value("上下文"));
        mvc.perform(post("/api/learning/contents/{id}/understood",ARTICLE).header("Authorization",bearer(session.token))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.understood").value(true));
        assertEquals("article:"+ARTICLE,jdbc.queryForObject("SELECT learning_key FROM learning_record WHERE owner_id=? AND content_id=?",String.class,session.userId,ARTICLE));
        mvc.perform(post("/api/knowledge").header("Authorization",bearer(session.token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemType\":\"note\",\"title\":\"RAG 笔记\",\"body\":\"先检索再回答\",\"state\":\"active\",\"visibility\":\"private\",\"sourceContentId\":\""+ARTICLE+"\",\"sourceContentVersionId\":\""+ARTICLE_VERSION+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceContentId").value(ARTICLE)).andExpect(jsonPath("$.sourceTitle").value("How RAG Works"));
        mvc.perform(get("/api/knowledge").header("Authorization",bearer(session.token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("RAG 笔记"));
    }

    private void seedLearningDictionary(){
        parameter("f070000000000000000000000000001","learning.difficulties","[{\"value\":\"intro\",\"label\":\"基础\"},{\"value\":\"advanced\",\"label\":\"进阶\"}]");
        parameter("f070000000000000000000000000002","learning.stages","[{\"value\":\"primary\",\"label\":\"小学阶段\"},{\"value\":\"junior\",\"label\":\"初中阶段\"},{\"value\":\"senior\",\"label\":\"高中阶段\"}]");
        parameter("f070000000000000000000000000003","learning.notebook_statuses","[{\"value\":\"review\",\"label\":\"待复习\"},{\"value\":\"familiar\",\"label\":\"已熟悉\"}]");
    }
    private void parameter(String id,String key,String value){jdbc.update("INSERT INTO app_parameter(id,param_key,param_value,is_secret,description,state,version_no) VALUES(?,?,?,?,?,?,?)",id,key,value,0,"测试学习字典","active",1);}

    private void seed(String ownerId){String source="11111111111111111111111111111111";jdbc.update("INSERT INTO content_source (id,name,source_type,url,license_note) VALUES (?,?,?,?,?)",source,"测试来源","official","https://example.com/source","测试许可");
        jdbc.update("INSERT INTO article_word_glossary(id,term,phonetic,meaning) VALUES (?,?,?,?)","f070000000000000000000000000010","retrieves","/rɪˈtriːvz/","检索；取回");
        insertContent(TECH,TECH_VERSION,"tech",null,"理解 RAG","先检索证据，再组织回答。","先检索证据，再组织回答。",source,null);jdbc.update("INSERT INTO content_topic (id,content_version_id,topic_id) VALUES (?,?,?)","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",TECH_VERSION,TOPIC);
        insertContent(WORD,WORD_VERSION,"word","junior","context","上下文","context",source,null);jdbc.update("UPDATE content_version SET word_term='context',phonetic='/context/',meaning='上下文',example_text='Use context.' WHERE id=?",WORD_VERSION);
        String blocks="[{\"paragraph_id\":\"p1\",\"text\":\"RAG retrieves useful context before answering.\",\"translation\":\"RAG 在回答前检索有用的上下文。\",\"words\":[{\"content_id\":\""+WORD+"\",\"term\":\"context\",\"meaning\":\"上下文\"}]}]";
        insertContent(ARTICLE,ARTICLE_VERSION,"english_article",null,"How RAG Works","理解检索增强生成","RAG retrieves useful context before answering.",source,blocks);
        insertContent(ARTICLE_2,ARTICLE_VERSION_2,"english_article",null,"A Careful Decision 2: Museum","编号排序测试","Article two.",source,null);
        insertContent(ARTICLE_10,ARTICLE_VERSION_10,"english_article",null,"A Careful Decision 10: Park","编号排序测试","Article ten.",source,null);
        insertContent(ARTICLE_101,ARTICLE_VERSION_101,"english_article",null,"A Careful Decision 101: Home","编号排序测试","Article one hundred and one.",source,null);
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,state) VALUES(?,?,'测试初中词书','official','junior','active')",BOOK,"F07_JUNIOR");
        jdbc.update("INSERT INTO user_vocabulary_book(id,owner_id,book_id,state) VALUES(?,?,?,'active')","f070000000000000000000000000301",ownerId,BOOK);
        mapArticle(ARTICLE,1);mapArticle(ARTICLE_2,2);mapArticle(ARTICLE_10,3);mapArticle(ARTICLE_101,4);
    }
    private void mapArticle(String contentId,int slot){jdbc.update("INSERT INTO english_article_book(id,content_id,book_id,generated_date,slot_no,target_words_json,topic_snapshot_json) VALUES(?,?,?,?,?,'[]','{}')",CryptoUtils.randomId(),contentId,BOOK,LocalDate.of(2026,9,18),slot);}
    private void insertContent(String id,String version,String type,String stage,String title,String summary,String body,String source,String blocks){byte[] hash=CryptoUtils.sha256(id);jdbc.update("INSERT INTO learning_content (id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES (?,?,?,?,?,?,?,?,?,?)",id,type,source,hash,"word".equals(type)?CryptoUtils.sha256(title):null,stage,"published",version,version,java.sql.Timestamp.valueOf("2026-09-18 08:00:00"));jdbc.update("INSERT INTO content_version (id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,origin_url,origin_author,origin_published_at,license_snapshot,body_hash,review_status,article_blocks,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",version,id,1,title,summary,body,"intro",180,"https://example.com/original","测试作者",java.sql.Timestamp.valueOf("2026-09-14 17:10:33"),"测试许可",CryptoUtils.sha256(body),"approved",blocks,"00000000000000000000000000000002");}
    private Session register() throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f07-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000007\"}"));JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000007\",\"smsCode\":\"123456\",\"nickname\":\"学习用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());}
    private String bearer(String token){return "Bearer "+token;}
    private static final String TOPIC="00000000000000000000000000000011",BOOK="f070000000000000000000000000030",TECH="22222222222222222222222222222222",TECH_VERSION="33333333333333333333333333333333",WORD="44444444444444444444444444444444",WORD_VERSION="55555555555555555555555555555555",ARTICLE="66666666666666666666666666666666",ARTICLE_VERSION="77777777777777777777777777777777";
    private static final String ARTICLE_2="f070000000000000000000000000102",ARTICLE_VERSION_2="f070000000000000000000000000202",ARTICLE_10="f070000000000000000000000000110",ARTICLE_VERSION_10="f070000000000000000000000000210",ARTICLE_101="f070000000000000000000000000101",ARTICLE_VERSION_101="f070000000000000000000000000201";
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
