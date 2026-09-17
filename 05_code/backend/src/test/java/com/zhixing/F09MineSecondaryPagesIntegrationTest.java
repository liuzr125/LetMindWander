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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f09;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F09MineSecondaryPagesIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;

 @Test void mineFriendsPrivacyAndSchedulesFormClosedLoops() throws Exception {
  Session a=register("mine-a","13800009001","小林"); Session b=register("mine-b","13800009002","阿岚");
  mvc.perform(get("/api/mine/overview").header("Authorization",bearer(a.token))).andExpect(status().isOk()).andExpect(jsonPath("$.profile.shortId").value(a.shortId)).andExpect(jsonPath("$.aiLimit").value(10));
  mvc.perform(get("/api/friends/search").param("type","id").param("query",b.shortId).header("Authorization",bearer(a.token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(b.id));
  mvc.perform(post("/api/friends/requests").header("Authorization",bearer(a.token)).contentType(MediaType.APPLICATION_JSON).content("{\"targetUserId\":\""+b.id+"\",\"remark\":\"一起交流学习笔记\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("pending"));
  String received=mvc.perform(get("/api/friends/requests").param("direction","received").header("Authorization",bearer(b.token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].remark").value("一起交流学习笔记")).andReturn().getResponse().getContentAsString();
  JsonNode request=json.readTree(received).get(0);
  mvc.perform(post("/api/friends/requests/"+request.path("id").asText()+"/decision").header("Authorization",bearer(b.token)).contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"accept\",\"expectedVersion\":"+request.path("versionNo").asInt()+"}")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("accepted"));
  mvc.perform(get("/api/friends").header("Authorization",bearer(a.token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(b.id));
  mvc.perform(post("/api/consents").header("Authorization",bearer(a.token)).contentType(MediaType.APPLICATION_JSON).content("{\"purpose\":\"ai_send\",\"documentVersion\":\"AI_SEND_V1\",\"decision\":\"grant\"}")).andExpect(status().isOk());
  mvc.perform(get("/api/mine/privacy").header("Authorization",bearer(a.token))).andExpect(status().isOk()).andExpect(jsonPath("$.aiConsent").value(true));
  mvc.perform(post("/api/mine/feedback").header("Authorization",bearer(a.token)).contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"suggestion\",\"body\":\"希望增加夜间模式\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("open"));
  mvc.perform(post("/api/mine/exports").header("Authorization",bearer(a.token))).andExpect(status().isOk()).andExpect(jsonPath("$.exportState").value("queued"));

  jdbc.update("INSERT INTO content_source(id,name,source_type,license_note,enabled) VALUES('source00000000000000000000000001','官方文档','manual','仅保存摘要和原文链接',1)");
  mvc.perform(get("/api/resource-schedules/sources/available").header("Authorization",bearer(a.token))).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("官方文档"));
  String schedule=mvc.perform(post("/api/resource-schedules").header("Authorization",bearer(a.token)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"每日技术资源\",\"resourceKind\":\"resource\",\"keywords\":\"AI,RAG\",\"topics\":[\"AI\"],\"sourceIds\":[\"source00000000000000000000000001\"],\"weekdaysMask\":127,\"minuteOfDay\":420,\"perRunLimit\":5,\"state\":\"active\",\"dedupEnabled\":true,\"summaryEnabled\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("每日技术资源")).andExpect(jsonPath("$.sourceNames[0]").value("官方文档")).andReturn().getResponse().getContentAsString();
  JsonNode s=json.readTree(schedule);
  mvc.perform(post("/api/resource-schedules/"+s.path("id").asText()+"/state").header("Authorization",bearer(a.token)).contentType(MediaType.APPLICATION_JSON).content("{\"state\":\"paused\",\"expectedVersion\":"+s.path("versionNo").asInt()+"}")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("paused"));
 }

 private Session register(String code,String mobile,String nickname)throws Exception{
  String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();
  String invite=json.readTree(invites).path("codes").get(0).path("code").asText();
  String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();
  String ticket=json.readTree(login).path("registrationTicket").asText();
  mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\"}"));
  String registered=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\""+mobile+"\",\"smsCode\":\"123456\",\"nickname\":\""+nickname+"\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString();
  JsonNode root=json.readTree(registered),user=root.path("user");return new Session(root.path("accessToken").asText(),user.path("id").asText(),user.path("shortId").asText());
 }
 private String bearer(String token){return "Bearer "+token;}
 private static class Session{final String token,id,shortId;Session(String t,String i,String s){token=t;id=i;shortId=s;}}
}
