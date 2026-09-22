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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F28 词书学习记录按「轮次」：切走冻结旧记录，切回同一本书新开一轮并带入上一轮的已学/未学，
 * 之后的学习只改当前轮，管理端能看到每一轮的选择时间与各自的进度。
 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f28;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F28BookStudyRoundIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;

    @Test void switchingBackOpensNewRoundAndKeepsOldOneFrozen() throws Exception {
        Session session=register();
        seedSource();
        word(W1,V1,"apple");word(W2,V2,"banana");word(W3,V3,"cherry");word(W4,V4,"donut");
        book(BOOK_A,"测试词书甲",new String[]{W1,W2,W3});
        book(BOOK_B,"测试词书乙",new String[]{W4});

        // 第 1 轮：选词书甲
        select(session,BOOK_A,3);
        Map<String,Object> roundA1=round(session.userId,BOOK_A,1);
        assertThat(roundA1.get("selected_at")).isNotNull();
        assertThat(roundA1.get("ended_at")).isNull();
        assertThat(((Number)roundA1.get("carried_learned_count")).intValue()).isZero();
        study(session,W1);study(session,W2);
        assertThat(number(session.userId,BOOK_A,1,"study_count")).isEqualTo(2);

        // 切到词书乙：词书甲第 1 轮冻结（结束时间 + 结束时已学快照）
        select(session,BOOK_B,1);
        Map<String,Object> frozen=round(session.userId,BOOK_A,1);
        assertThat(frozen.get("ended_at")).isNotNull();
        assertThat(((Number)frozen.get("final_learned_count")).intValue()).isEqualTo(2);
        assertThat(((Number)frozen.get("study_count")).intValue()).isEqualTo(2);
        study(session,W4);
        assertThat(number(session.userId,BOOK_B,1,"study_count")).isEqualTo(1);

        // 再切回词书甲：新开第 2 轮，带入上一轮的已学 2 个
        select(session,BOOK_A,3);
        List<Map<String,Object>> rounds=rounds(session.userId,BOOK_A);
        assertThat(rounds).hasSize(2);
        Map<String,Object> roundA2=round(session.userId,BOOK_A,2);
        assertThat(((Number)roundA2.get("carried_learned_count")).intValue()).isEqualTo(2);
        assertThat(roundA2.get("ended_at")).isNull();
        assertThat(number(session.userId,BOOK_B,1,"study_count")).isEqualTo(1);
        assertThat(round(session.userId,BOOK_B,1).get("ended_at")).isNotNull();

        // 第 2 轮继续学：只改第 2 轮，第 1 轮保持冻结
        study(session,W3);
        assertThat(number(session.userId,BOOK_A,2,"study_count")).isEqualTo(1);
        assertThat(number(session.userId,BOOK_A,1,"study_count")).isEqualTo(2);
        assertThat(((Number)round(session.userId,BOOK_A,1).get("final_learned_count")).intValue()).isEqualTo(2);

        // 管理端列表：三轮记录，轮次与选择时间都在，已结束的轮次显示结束快照、进行中的显示实时
        JsonNode page=utf8(mvc.perform(get("/api/admin/study-records").header("X-Admin-Token","dev-admin-token").param("keyword",session.nickname))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertThat(page.path("total").asInt()).isEqualTo(3);
        JsonNode a2=null,a1=null;
        for(JsonNode item:page.path("items")){
            if(BOOK_A.equals(item.path("bookId").asText())&&item.path("roundNo").asInt()==2)a2=item;
            if(BOOK_A.equals(item.path("bookId").asText())&&item.path("roundNo").asInt()==1)a1=item;
        }
        assertThat(a2).isNotNull();assertThat(a1).isNotNull();
        assertThat(a2.path("selectedAt").asText()).isNotEmpty();
        assertThat(a2.path("endedAt").isMissingNode()||a2.path("endedAt").isNull()).as("进行中的轮次没有结束时间").isTrue();
        assertThat(a2.path("carriedLearnedCount").asInt()).isEqualTo(2);
        assertThat(a2.path("learnedWords").asInt()).isEqualTo(3);          // 进行中 → 实时（2 带入 + 1 新学）
        assertThat(a1.path("endedAt").asText("")).isNotEmpty();             // 已结束 → 冻结
        assertThat(a1.path("learnedWords").asInt()).isEqualTo(2);           // 结束快照，不被后来的学习改写
        assertThat(a1.path("studyCount").asInt()).isEqualTo(2);
        assertThat(a2.path("studyCount").asInt()).isEqualTo(1);
        assertThat(a2.path("currentRound").asBoolean()).isTrue();
        assertThat(a1.path("currentRound").asBoolean()).isFalse();

        // 详情按记录 ID：本轮带入 2、本轮新学 1，每日明细与词条（本轮/整本）都对得上
        String recordA2=String.valueOf(roundA2.get("id"));
        mvc.perform(get("/api/admin/study-records/{recordId}",recordA2).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.record.roundNo").value(2))
                .andExpect(jsonPath("$.record.carriedLearnedCount").value(2))
                .andExpect(jsonPath("$.roundNewWords").value(1))
                .andExpect(jsonPath("$.days[0].newWordCount").value(1))
                .andExpect(jsonPath("$.days[0].studyCount").value(1));
        mvc.perform(get("/api/admin/study-records/{recordId}/words",recordA2).header("X-Admin-Token","dev-admin-token").param("scope","round"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("round"))
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].word").value("cherry"));
        mvc.perform(get("/api/admin/study-records/{recordId}/words",recordA2).header("X-Admin-Token","dev-admin-token").param("scope","book").param("status","learned"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(3));
        // 第 1 轮的详情只覆盖它自己的窗口（那时还没学 cherry）
        String recordA1=String.valueOf(round(session.userId,BOOK_A,1).get("id"));
        mvc.perform(get("/api/admin/study-records/{recordId}/words",recordA1).header("X-Admin-Token","dev-admin-token").param("scope","round"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));

        // 重新选定同一本（未切走）不会再多开一轮
        select(session,BOOK_A,3);
        assertThat(rounds(session.userId,BOOK_A)).hasSize(2);
    }

    private void study(Session session,String contentId) throws Exception {
        mvc.perform(post("/api/learning/contents/{id}/understood",contentId).header("Authorization","Bearer "+session.token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }
    private void select(Session session,String bookId,int dailyNewLimit) throws Exception {
        mvc.perform(put("/api/vocabulary-books/current").header("Authorization","Bearer "+session.token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookId\":\""+bookId+"\",\"dailyNewLimit\":"+dailyNewLimit+"}")).andExpect(status().isOk());
    }
    private List<Map<String,Object>> rounds(String ownerId,String bookId){
        return jdbc.queryForList("SELECT id,round_no,selected_at,ended_at,carried_learned_count,final_learned_count,study_count FROM vocabulary_book_study_record WHERE owner_id=? AND book_id=? ORDER BY round_no",ownerId,bookId);
    }
    private Map<String,Object> round(String ownerId,String bookId,int roundNo){
        return jdbc.queryForMap("SELECT id,round_no,selected_at,ended_at,carried_learned_count,final_learned_count,study_count FROM vocabulary_book_study_record WHERE owner_id=? AND book_id=? AND round_no=?",ownerId,bookId,roundNo);
    }
    private int number(String ownerId,String bookId,int roundNo,String column){
        return jdbc.queryForObject("SELECT "+column+" FROM vocabulary_book_study_record WHERE owner_id=? AND book_id=? AND round_no=?",Integer.class,ownerId,bookId,roundNo);
    }
    private JsonNode utf8(byte[] body) throws Exception {return json.readTree(new String(body,java.nio.charset.StandardCharsets.UTF_8));}

    private void seedSource(){jdbc.update("DELETE FROM content_source WHERE id=?",SOURCE);
        jdbc.update("INSERT INTO content_source(id,name,license_note) VALUES(?,?,?)",SOURCE,"轮次来源","测试许可");}
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
                bookId,"F28-"+bookId.substring(28),name,"k12","primary","轮次测试",contents.length);
        int i=1;
        for(String content:contents)jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,5,1)",String.format("f28m%s%08d",bookId.substring(28),i),bookId,content,i++);
    }

    private Session register(){
        try{
            String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();
            String invite=json.readTree(invites).path("codes").get(0).path("code").asText();
            String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f28-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();
            String ticket=json.readTree(login).path("registrationTicket").asText();
            mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000061\"}"));
            String body=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000061\",\"smsCode\":\"123456\",\"nickname\":\"轮次用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString();
            JsonNode registered=json.readTree(body);
            assertThat(registered.path("accessToken").asText()).as("注册响应：%s",body).isNotEmpty();
            return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText(),"轮次用户");
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static final String SOURCE="f2800000000000000000000000000000";
    private static final String W1="f28a0000000000000000000000000001",W2="f28a0000000000000000000000000002",W3="f28a0000000000000000000000000003",W4="f28a0000000000000000000000000004";
    private static final String V1="f28v0000000000000000000000000001",V2="f28v0000000000000000000000000002",V3="f28v0000000000000000000000000003",V4="f28v0000000000000000000000000004";
    private static final String BOOK_A="f28b000000000000000000000000000a",BOOK_B="f28b000000000000000000000000000b";
    private static class Session{final String token,userId,nickname;Session(String token,String userId,String nickname){this.token=token;this.userId=userId;this.nickname=nickname;}}
}
