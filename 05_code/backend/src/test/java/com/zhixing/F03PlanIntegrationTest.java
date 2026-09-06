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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:f03;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE", "spring.sql.init.mode=always", "app.wechat.mock-enabled=true", "app.sms.mock-enabled=true", "app.sms.fixed-code=123456", "app.registration-store=memory"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F03PlanIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void savesVersionedPlanAndRejectsStaleWrite() throws Exception {
        String token = register("f03-user");
        String topic = mockMvc.perform(post("/api/plans/topics").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  AI  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("AI")).andReturn().getResponse().getContentAsString();
        String topicId = objectMapper.readTree(topic).path("id").asText();
        String plan = mockMvc.perform(get("/api/plans").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(1)).andReturn().getResponse().getContentAsString();
        int version = objectMapper.readTree(plan).path("versionNo").asInt();
        String payload = "{\"versionNo\":" + version + ",\"dailyBudgetMin\":15,\"weekdaysMask\":31,\"topicIds\":[\"" + topicId + "\"],\"difficulty\":\"advanced\",\"techCount\":2,\"newWordCount\":4,\"journalEnabled\":true,\"reviewEnabled\":true,\"reviewLimit\":5,\"paused\":false}";
        mockMvc.perform(put("/api/plans").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(2)).andExpect(jsonPath("$.dailyBudgetMin").value(15))
                .andExpect(jsonPath("$.effectiveDate").value(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString()))
                .andExpect(jsonPath("$.topics[0].name").value("AI"));
        String todayPayload = payload.replace("\"versionNo\":" + version, "\"versionNo\":2")
                .replace("\"paused\":false", "\"paused\":false,\"adjustToday\":true");
        mockMvc.perform(put("/api/plans").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(todayPayload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(3))
                .andExpect(jsonPath("$.effectiveDate").value(LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()));
        mockMvc.perform(get("/api/plans").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(3));
        String nextSavePayload = payload.replace("\"versionNo\":" + version, "\"versionNo\":3");
        mockMvc.perform(put("/api/plans").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(nextSavePayload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.versionNo").value(4))
                .andExpect(jsonPath("$.effectiveDate").value(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString()));
        mockMvc.perform(put("/api/plans").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLAN_VERSION_CONFLICT"));
    }

    private String register(String code) throws Exception {
        String invites = mockMvc.perform(post("/api/admin/invites").header("X-Admin-Token", "dev-admin-token").contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"expiresInDays\":7}"))
                .andReturn().getResponse().getContentAsString();
        String invite = objectMapper.readTree(invites).path("codes").get(0).path("code").asText();
        String login = mockMvc.perform(post("/api/auth/wechat").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\",\"inviteCode\":\"" + invite + "\",\"privacyVersion\":\"PRIVACY_V1\"}"))
                .andReturn().getResponse().getContentAsString();
        String ticket = objectMapper.readTree(login).path("registrationTicket").asText();
        mockMvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\"" + ticket + "\",\"mobile\":\"13800000001\"}"));
        String registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"registrationTicket\":\"" + ticket + "\",\"mobile\":\"13800000001\",\"smsCode\":\"123456\",\"nickname\":\"计划用户\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(registered).path("accessToken").asText();
    }
}
