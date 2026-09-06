package com.zhixing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f01;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.sql.init.mode=always",
        "app.wechat.mock-enabled=true",
        "app.sms.mock-enabled=true",
        "app.sms.fixed-code=123456",
        "app.registration-store=memory"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F01FlowIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void invitedUserCanRegisterAndRestoreSession() throws Exception {
        String created = mockMvc.perform(post("/api/admin/invites")
                        .header("X-Admin-Token", "dev-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1,\"expiresInDays\":7}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String inviteCode = objectMapper.readTree(created).path("codes").get(0).path("code").asText();

        String login = mockMvc.perform(post("/api/auth/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<String, String>() {{
                            put("code", "mock-new-user");
                            put("inviteCode", inviteCode);
                            put("privacyVersion", "PRIVACY_V1");
                        }}))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("registration_required"))
                .andReturn().getResponse().getContentAsString();
        String ticket = objectMapper.readTree(login).path("registrationTicket").asText();

        mockMvc.perform(post("/api/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationTicket\":\"" + ticket + "\",\"mobile\":\"13800008000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(true))
                .andExpect(jsonPath("$.expiresIn").value(300));

        String registerBody = "{\"registrationTicket\":\"" + ticket + "\",\"mobile\":\"13800008000\",\"smsCode\":\"123456\"," +
                "\"nickname\":\"学习者\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}";
        String registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("authenticated"))
                .andExpect(jsonPath("$.user.mobileMasked").value("138 **** 8000"))
                .andExpect(jsonPath("$.user.shortId").value("R_0001"))
                .andReturn().getResponse().getContentAsString();
        JsonNode result = objectMapper.readTree(registered);

        mockMvc.perform(get("/api/user").header("Authorization", "Bearer " + result.path("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("学习者"))
                .andExpect(jsonPath("$.hasCurrentPlan").value(true));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(result.path("user").path("id").asText()));

        mockMvc.perform(post("/api/auth/wechat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"mock-new-user\",\"privacyVersion\":\"PRIVACY_V1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("authenticated"))
                .andExpect(jsonPath("$.user.id").value(result.path("user").path("id").asText()));

        mockMvc.perform(get("/api/admin/invite-stats").header("X-Admin-Token", "dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitedUsed").value(1))
                .andExpect(jsonPath("$.redeemedCodes").value(1));
    }
}
