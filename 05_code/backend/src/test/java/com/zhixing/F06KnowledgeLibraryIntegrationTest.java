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
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f06;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F06KnowledgeLibraryIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

    @Test void knowledgeCreateSearchEditVerifyShareReviewAndDeleteClosedLoop() throws Exception {
        Session owner=register("knowledge-owner","13800000011","知识作者");
        Session friend=register("knowledge-friend","13800000012","知识好友");
        seedFriend(owner.userId,friend.userId);

        Map<String,Object> note=new LinkedHashMap<String,Object>();
        note.put("itemType","note");note.put("title","RAG 的三个关键步骤");note.put("body","检索、参考、生成");
        note.put("tags",Arrays.asList("AI","RAG","AI"));note.put("state","active");note.put("visibility","private");
        JsonNode created=json.readTree(mvc.perform(post("/api/knowledge").header("Authorization",bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(note)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("RAG 的三个关键步骤"))
                .andExpect(jsonPath("$.tags.length()").value(2)).andReturn().getResponse().getContentAsString());
        String noteId=created.path("id").asText();

        mvc.perform(get("/api/knowledge").header("Authorization",bearer(owner.token)).param("type","note").param("query","RAG"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(noteId));

        note.put("title","RAG 的三个关键步骤与边界");note.put("expectedVersion",1);
        mvc.perform(put("/api/knowledge/{id}",noteId).header("Authorization",bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(note)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(2));
        mvc.perform(put("/api/knowledge/{id}",noteId).header("Authorization",bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(note))).andExpect(status().isConflict());

        mvc.perform(post("/api/knowledge/{id}/review",noteId).header("Authorization",bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inReview").value(true));

        Map<String,Object> share=new LinkedHashMap<String,Object>();share.put("expectedVersion",2);share.put("visibility","selected");share.put("selectedFriendIds",Collections.singletonList(friend.userId));
        mvc.perform(put("/api/knowledge/{id}/visibility",noteId).header("Authorization",bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(share)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("selected"))
                .andExpect(jsonPath("$.selectedFriendIds[0]").value(friend.userId)).andExpect(jsonPath("$.versionNo").value(3));

        Map<String,Object> problem=new LinkedHashMap<String,Object>();
        problem.put("itemType","problem");problem.put("title","容器中的 Java OOM 排查");problem.put("state","active");problem.put("visibility","private");
        Map<String,String> fields=new LinkedHashMap<String,String>();fields.put("phenomenon","运行时出现 OOM");fields.put("environment","Java 17 容器");fields.put("cause","内存限制可能过小");fields.put("solution","核对容器限制与 JVM 堆设置");fields.put("verification","");problem.put("problem",fields);
        JsonNode problemCreated=json.readTree(mvc.perform(post("/api/knowledge").header("Authorization",bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(problem))).andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("unverified")).andReturn().getResponse().getContentAsString());
        String problemId=problemCreated.path("id").asText();
        mvc.perform(put("/api/knowledge/{id}/verification",problemId).header("Authorization",bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"status\":\"verified\",\"note\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/knowledge/{id}/verification",problemId).header("Authorization",bearer(owner.token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":1,\"status\":\"verified\",\"note\":\"已在限制环境复现并验证\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verificationStatus").value("verified"))
                .andExpect(jsonPath("$.problem.verification").value("已在限制环境复现并验证"));

        mvc.perform(delete("/api/knowledge/{id}",noteId).header("Authorization",bearer(owner.token)).param("expectedVersion","3"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/knowledge/{id}",noteId).header("Authorization",bearer(owner.token))).andExpect(status().isNotFound());
        Integer paused=jdbc.queryForObject("SELECT COUNT(*) FROM review_schedule WHERE knowledge_id=? AND state='paused'",Integer.class,noteId);
        org.junit.jupiter.api.Assertions.assertEquals(1,paused);
    }

    private void seedFriend(String a,String b){String low=a.compareTo(b)<0?a:b,high=a.compareTo(b)<0?b:a;jdbc.update("INSERT INTO friend_relation (id,user_low_id,user_high_id,state,generation,version_no) VALUES (?,?,?,?,?,?)","abababababababababababababababab",low,high,"active",1,1);}
    private Session register(String code,String mobile,String nickname) throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\"}"));JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\",\"smsCode\":\"123456\",\"nickname\":\""+nickname+"\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString());return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());}
    private String bearer(String token){return "Bearer "+token;}
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
