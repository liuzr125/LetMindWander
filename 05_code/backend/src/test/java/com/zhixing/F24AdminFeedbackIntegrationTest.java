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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F24 管理端「用户反馈」：查看待处理事项的正文与详情，并推进处理状态。 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f24;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F24AdminFeedbackIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;

    @Test void feedbackListDetailAndStateChangeCloseTheLoop() throws Exception {
        Session alice=register("f24-alice","13800000031","反馈用户");
        Session bob=register("f24-bob","13800000032","建议用户");
        String aliceBody="学习页点击发音后没有声音，重装后仍然如此。";
        String bobBody="希望单词卡片可以自定义复习顺序。";
        String aliceId=submit(alice,"bug",aliceBody);
        String bobId=submit(bob,"suggestion",bobBody);

        // 概览「待处理事项」当前为 2 条未处理反馈
        mvc.perform(get("/api/admin/overview").header("X-Admin-Token","dev-admin-token")).andExpect(status().isOk()).andExpect(jsonPath("$.openFeedback").value(2));

        JsonNode page=logs("state","all");
        assertEquals(2,page.path("total").asInt());
        assertEquals(2,page.path("summary").path("open").asInt());
        assertEquals(0,page.path("summary").path("handled").asInt());
        assertEquals(2,page.path("summary").path("total").asInt());
        JsonNode aliceRow=null;
        for(JsonNode row:page.path("items")) if(aliceId.equals(row.path("id").asText())) aliceRow=row;
        assertNotNull(aliceRow,"列表应包含刚提交的反馈（同一秒提交，顺序不做假设）");
        assertEquals("反馈用户",aliceRow.path("nickname").asText());
        assertEquals("138****0031",aliceRow.path("mobile").asText(),"手机号必须脱敏");
        assertEquals("功能异常",aliceRow.path("categoryLabel").asText());
        assertEquals("待处理",aliceRow.path("stateLabel").asText());
        assertEquals(aliceBody.length()>80?aliceBody.substring(0,80)+"…":aliceBody,aliceRow.path("excerpt").asText());
        assertTrue(aliceRow.path("body").isMissingNode()||aliceRow.path("body").isNull(),"列表不下发完整正文，正文只在详情里返回");

        // 详情返回完整正文与用户信息
        JsonNode detail=json.readTree(mvc.perform(get("/api/admin/feedback/"+aliceId).header("X-Admin-Token","dev-admin-token")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(aliceBody,detail.path("body").asText());
        assertEquals("bug",detail.path("category").asText());
        assertEquals(alice.userId,detail.path("ownerId").asText());
        assertEquals("待处理",detail.path("stateLabel").asText());
        assertTrue(detail.path("createdAt").asText().length()>0);

        // 关键词命中正文 / 昵称 / 手机号
        assertEquals(1,logs("state","all","keyword","发音").path("total").asInt());
        assertEquals(1,logs("state","all","keyword","建议用户").path("total").asInt());
        assertEquals(1,logs("state","all","keyword","13800000032").path("total").asInt());
        assertEquals(0,logs("state","all","keyword","不存在的关键词").path("total").asInt());

        // 标记已处理：状态推进、概览待处理事项减少、列表按待处理优先排序
        JsonNode handled=json.readTree(mvc.perform(put("/api/admin/feedback/"+bobId+"/state").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"handled\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("handled",handled.path("state").asText());assertEquals("已处理",handled.path("stateLabel").asText());
        mvc.perform(get("/api/admin/overview").header("X-Admin-Token","dev-admin-token")).andExpect(status().isOk()).andExpect(jsonPath("$.openFeedback").value(1));
        JsonNode mixed=logs("state","all");
        assertEquals(aliceId,mixed.path("items").get(0).path("id").asText(),"待处理的反馈排在前面");
        assertEquals(1,logs("state","open").path("total").asInt());
        assertEquals(1,logs("state","handled").path("total").asInt());
        assertEquals(bobId,logs("state","handled").path("items").get(0).path("id").asText());

        // 重新打开后概览回到 2
        mvc.perform(put("/api/admin/feedback/"+bobId+"/state").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"open\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.stateLabel").value("待处理"));
        mvc.perform(get("/api/admin/overview").header("X-Admin-Token","dev-admin-token")).andExpect(status().isOk()).andExpect(jsonPath("$.openFeedback").value(2));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit WHERE action_code='user_feedback_state_change' AND target_id=?",Integer.class,bobId),"两次状态变更各留一条审计");

        // 分页
        JsonNode secondPage=logs("state","all","page","2","pageSize","1");
        assertEquals(2,secondPage.path("total").asInt());assertEquals(2,secondPage.path("totalPages").asInt());assertEquals(1,secondPage.path("items").size());

        // 守卫
        mvc.perform(get("/api/admin/feedback")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/feedback").header("X-Admin-Token","dev-admin-token").param("state","archived")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_STATE"));
        mvc.perform(get("/api/admin/feedback").header("X-Admin-Token","dev-admin-token").param("pageSize","500")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        mvc.perform(get("/api/admin/feedback/"+CryptoUtils.randomId()).header("X-Admin-Token","dev-admin-token")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ADMIN_FEEDBACK_NOT_FOUND"));
        mvc.perform(put("/api/admin/feedback/"+aliceId+"/state").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"closed\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_STATE"));
        mvc.perform(put("/api/admin/feedback/"+CryptoUtils.randomId()+"/state").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"handled\"}")).andExpect(status().isNotFound());

        // 小程序侧的类型校验仍然生效
        mvc.perform(post("/api/mine/feedback").header("Authorization","Bearer "+alice.token).contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"unknown\",\"body\":\"类型不对\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FEEDBACK_CATEGORY"));
        mvc.perform(post("/api/mine/feedback").header("Authorization","Bearer "+alice.token).contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"copyright\",\"body\":\"   \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertFalse(logs("state","all").toString().contains("类型不对"),"未通过校验的反馈不应入库");
    }

    private String submit(Session session,String category,String body) throws Exception {
        JsonNode created=json.readTree(mvc.perform(post("/api/mine/feedback").header("Authorization","Bearer "+session.token).contentType(MediaType.APPLICATION_JSON).content("{\"category\":\""+category+"\",\"body\":\""+body+"\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return created.path("id").asText();
    }
    private JsonNode logs(String... keyValues) throws Exception {
        MockHttpServletRequestBuilder builder=get("/api/admin/feedback").header("X-Admin-Token","dev-admin-token");
        for(int i=0;i+1<keyValues.length;i+=2) builder=builder.param(keyValues[i],keyValues[i+1]);
        return json.readTree(mvc.perform(builder).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private Session register(String code,String mobile,String nickname) throws Exception {
        String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String invite=json.readTree(invites).path("codes").get(0).path("code").asText();
        String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String ticket=json.readTree(login).path("registrationTicket").asText();
        mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\"}"));
        JsonNode registered=json.readTree(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\",\"smsCode\":\"123456\",\"nickname\":\""+nickname+"\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new Session(registered.path("accessToken").asText(),registered.path("user").path("id").asText());
    }
    private static class Session{final String token,userId;Session(String token,String userId){this.token=token;this.userId=userId;}}
}
