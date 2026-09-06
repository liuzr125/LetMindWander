package com.zhixing;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F02 个人资料：验证本人资料保存、字段校验与手机号只读掩码。
 * 头像上传（/media/upload）依赖真实 OSS，不在 H2 测试中覆盖。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f02;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.sql.init.mode=always",
        "app.wechat.mock-enabled=true",
        "app.sms.mock-enabled=true",
        "app.sms.fixed-code=123456",
        "app.registration-store=memory"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F02ProfileIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    /** 完整走一遍 F01 注册流程，返回 accessToken。 */
    private String registerAndLogin(String wechatCode) throws Exception {
        String created = mockMvc.perform(post("/api/admin/invites")
                        .header("X-Admin-Token", "dev-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"count\":1,\"expiresInDays\":7}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String inviteCode = objectMapper.readTree(created).path("codes").get(0).path("code").asText();

        String loginBody = "{\"code\":\"" + wechatCode + "\",\"inviteCode\":\"" + inviteCode + "\",\"privacyVersion\":\"PRIVACY_V1\"}";
        String login = mockMvc.perform(post("/api/auth/wechat")
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String ticket = objectMapper.readTree(login).path("registrationTicket").asText();

        mockMvc.perform(post("/api/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registrationTicket\":\"" + ticket + "\",\"mobile\":\"13800008000\"}"))
                .andExpect(status().isOk());

        String registerBody = "{\"registrationTicket\":\"" + ticket + "\",\"mobile\":\"13800008000\",\"smsCode\":\"123456\"," +
                "\"nickname\":\"学习者\",\"privacyVersion\":\"PRIVACY_V1\",\"aiConsent\":false}";
        String registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(registered).path("accessToken").asText();
    }

    @Test
    void profileSaveRoundTripsAndValidates() throws Exception {
        String token = registerAndLogin("mock-f02-user");

        // 完整资料保存：昵称/真实姓名/英语名字/生日/性别/爱好去重/自我介绍/可见范围
        String version = mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int rowVersion = objectMapper.readTree(version).path("rowVersion").asInt();
        String userId = objectMapper.readTree(version).path("id").asText();
        String update = "{\"rowVersion\":" + rowVersion + ",\"nickname\":\"小灶同学\",\"realName\":\"张三\",\"englishName\":\"Alice\",\"birthday\":\"1995-06-15\"," +
                "\"gender\":1,\"hobbies\":[\"摄影\",\"AI\",\"英语\",\"摄影\"],\"introduction\":\"你好，世界\"," +
                "\"visibility\":\"friends\",\"profileVisibility\":{\"real_name\":\"private\",\"introduction\":\"public\"}}";
        mockMvc.perform(put("/api/user").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("小灶同学"))
                .andExpect(jsonPath("$.realName").value("张三"))
                .andExpect(jsonPath("$.englishName").value("Alice"))
                .andExpect(jsonPath("$.birthday").value("1995-06-15"))
                .andExpect(jsonPath("$.gender").value(1))
                .andExpect(jsonPath("$.introduction").value("你好，世界"))
                .andExpect(jsonPath("$.visibility").value("public"))
                .andExpect(jsonPath("$.profileVisibility.real_name").value("private"))
                .andExpect(jsonPath("$.profileVisibility.introduction").value("public"))
                .andExpect(jsonPath("$.hobbies.length()").value(3))
                .andExpect(jsonPath("$.hobbies[0]").value("摄影"))
                .andExpect(jsonPath("$.mobileMasked").value("138 **** 8000"))
                .andExpect(jsonPath("$.shortId").value("R_0001"));

        // 保存后 GET /me 回读一致
        mockMvc.perform(get("/api/user").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("小灶同学"))
                .andExpect(jsonPath("$.realName").value("张三"));

        // Stale writes are rejected and a limited profile never returns phone numbers or private fields.
        mockMvc.perform(put("/api/me").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":" + rowVersion + ",\"nickname\":\"旧页面提交\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_VERSION_CONFLICT"));
        mockMvc.perform(get("/api/users/" + userId + "/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.fields.introduction").value("你好，世界"))
                .andExpect(jsonPath("$.fields.real_name").doesNotExist())
                .andExpect(jsonPath("$.mobileMasked").doesNotExist());

        // 未来生日被拒
        mockMvc.perform(put("/api/user").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":2,\"nickname\":\"小灶同学\",\"birthday\":\"2999-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_BIRTHDAY"));

        // 空昵称被拒
        mockMvc.perform(put("/api/user").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":2,\"nickname\":\"\"}"))
                .andExpect(status().isBadRequest());

        // 单个爱好超过 20 字符被拒
        mockMvc.perform(put("/api/user").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":2,\"nickname\":\"小灶同学\",\"hobbies\":[\"123456789012345678901\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_HOBBY"));

        // 性别非法值被拒
        mockMvc.perform(put("/api/user").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rowVersion\":2,\"nickname\":\"小灶同学\",\"gender\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GENDER"));
    }
}
