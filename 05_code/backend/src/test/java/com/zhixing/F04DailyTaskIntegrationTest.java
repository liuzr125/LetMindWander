package com.zhixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f04;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc class F04DailyTaskIntegrationTest {
 @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 @Test void recordsStateEventsAndComputesServerProgress() throws Exception {String token=register();mvc.perform(post("/api/plans/default").header("Authorization","Bearer "+token)).andExpect(status().isOk());String date=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString();String day=mvc.perform(get("/api/days/{date}",date).header("Authorization","Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.currentCount").value(1)).andReturn().getResponse().getContentAsString();String id=json.readTree(day).path("tasks").get(0).path("id").asText();mvc.perform(post("/api/tasks/{id}/events",id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"start\",\"expectedVersion\":1}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DOING"));mvc.perform(post("/api/tasks/{id}/events",id).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content("{\"eventType\":\"complete\",\"expectedVersion\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));mvc.perform(get("/api/days/{date}",date).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.doneCount").value(1)).andExpect(jsonPath("$.completionRate").value(100.0));}
 private String register() throws Exception {String invites=mvc.perform(post("/api/admin/invites").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}")).andReturn().getResponse().getContentAsString();String invite=json.readTree(invites).path("codes").get(0).path("code").asText();String login=mvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"f04-user\",\"inviteCode\":\""+invite+"\",\"privacyVersion\":\"PRIVACY_V1\"}")).andReturn().getResponse().getContentAsString();String ticket=json.readTree(login).path("registrationTicket").asText();mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000002\"}"));String registered=mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\""+ticket+"\",\"mobile\":\"13800000002\",\"smsCode\":\"123456\",\"nickname\":\"任务用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}")).andReturn().getResponse().getContentAsString();return json.readTree(registered).path("accessToken").asText();}
}
