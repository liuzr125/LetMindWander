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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F23 管理端「AI 用量日志」：按 天 × 用户 展示每日额度使用与调用明细。 */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f23;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.wechat.mock-enabled=true","app.sms.mock-enabled=true","app.sms.fixed-code=123456","app.registration-store=memory"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F23AdminAiUsageLogIntegrationTest {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String TODAY=LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DATE);
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;

    @Test void usageLogsAggregatePerUserAndDayWithFiltersAndGuards() throws Exception {
        String day=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).format(DATE);
        String earlier=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(2).format(DATE);
        Session alice=register("f23-alice","13800000021","英语用户");
        Session bob=register("f23-bob","13800000022","数学用户");

        quota(day,alice.userId,10,4);quota(day,bob.userId,10,2);quota(earlier,alice.userId,10,1);
        quota(day,"00000000000000000000000000000000",200,7);quota(day,"ffffffffffffffffffffffffffffffff",200,3);
        attempt(alice.userId,day,"succeeded",100,50,"0.000420");attempt(alice.userId,day,"succeeded",80,40,"0.000310");
        attempt(alice.userId,day,"failed",null,null,null);attempt(bob.userId,day,"reserved",null,null,null);
        attempt("ffffffffffffffffffffffffffffffff",day,"succeeded",10,5,"0.000010");

        JsonNode page=logs("dateFrom",day,"dateTo",day);
        assertEquals(2,page.path("total").asInt(),"只应统计有 app_user 的用户行，global/system 作用域被排除");
        JsonNode first=page.path("items").get(0);
        assertEquals(alice.userId,first.path("userId").asText());
        assertEquals("英语用户",first.path("nickname").asText());
        assertEquals("138****0021",first.path("mobile").asText(),"手机号必须脱敏");
        assertEquals(4,first.path("usedCount").asInt());assertEquals(10,first.path("limitCount").asInt());assertEquals(6,first.path("remaining").asInt());
        assertEquals(2,first.path("succeededCount").asInt());assertEquals(1,first.path("failedCount").asInt());
        assertEquals(180,first.path("inputTokens").asInt());assertEquals(90,first.path("outputTokens").asInt());
        assertEquals(0.00073,first.path("costCny").asDouble(),1e-9);
        JsonNode second=page.path("items").get(1);
        assertEquals(bob.userId,second.path("userId").asText());assertEquals(2,second.path("usedCount").asInt());
        assertEquals(0,second.path("succeededCount").asInt());assertEquals(1,second.path("runningCount").asInt());

        JsonNode summary=page.path("summary");
        assertEquals(2,summary.path("userDays").asInt());
        assertEquals(6,summary.path("usedTotal").asInt());
        assertEquals(20,summary.path("limitTotal").asInt());
        assertEquals(2,summary.path("succeededTotal").asInt());
        assertEquals(1,summary.path("failedTotal").asInt());
        assertEquals(180,summary.path("inputTokens").asInt());
        assertEquals(90,summary.path("outputTokens").asInt());
        assertEquals(0.00073,summary.path("costTotal").asDouble(),1e-9);

        // 关键词可按昵称 / 手机号 / 用户 ID 过滤
        JsonNode byNickname=logs("dateFrom",day,"dateTo",day,"keyword","数学");
        assertEquals(1,byNickname.path("total").asInt());assertEquals("数学用户",byNickname.path("items").get(0).path("nickname").asText());
        JsonNode byMobile=logs("dateFrom",day,"dateTo",day,"keyword","13800000021");
        assertEquals(1,byMobile.path("total").asInt());assertEquals(alice.userId,byMobile.path("items").get(0).path("userId").asText());
        assertEquals(1,logs("dateFrom",day,"dateTo",day,"keyword",alice.userId.substring(0,10)).path("total").asInt());

        // 日期区间：跨天查询拿到 3 条（2 个用户昨天 + alice 前天）
        assertEquals(3,logs("dateFrom",earlier,"dateTo",day).path("total").asInt());
        JsonNode onlyEarlier=logs("dateFrom",earlier,"dateTo",earlier);
        assertEquals(1,onlyEarlier.path("total").asInt());assertEquals(earlier,onlyEarlier.path("items").get(0).path("quotaDate").asText());

        // 分页
        JsonNode secondPage=logs("dateFrom",day,"dateTo",day,"page","2","pageSize","1");
        assertEquals(2,secondPage.path("total").asInt());assertEquals(2,secondPage.path("totalPages").asInt());assertEquals(1,secondPage.path("items").size());
        assertEquals(bob.userId,secondPage.path("items").get(0).path("userId").asText(),"按用量倒序后第二页应为用量较少的用户");

        // 默认区间=今天（测试数据都在过去，因此默认为空）
        JsonNode defaultRange=logs();
        assertEquals(TODAY,defaultRange.path("dateFrom").asText());assertEquals(TODAY,defaultRange.path("dateTo").asText());assertEquals(0,defaultRange.path("total").asInt());

        // 守卫：缺 token / 非法日期 / 区间过长 / 非法分页
        mvc.perform(get("/api/admin/ai/usage-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/ai/usage-logs").header("X-Admin-Token","dev-admin-token").param("dateFrom","2026-13-99")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE"));
        mvc.perform(get("/api/admin/ai/usage-logs").header("X-Admin-Token","dev-admin-token").param("dateFrom","2026-01-01").param("dateTo","2026-12-31")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
        mvc.perform(get("/api/admin/ai/usage-logs").header("X-Admin-Token","dev-admin-token").param("pageSize","500")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));

        String raw=rawLogs("dateFrom",day,"dateTo",day,"pageSize","100");
        assertFalse(raw.contains("ffffffff"),"系统作用域的用量不应出现在日志里");
        assertTrue(raw.contains(alice.userId)&&raw.contains(bob.userId));

        // 汇总里同时给出「参数当前上限」，方便核对参数改动是否生效（当日行里的上限是快照）
        assertEquals(10,page.path("summary").path("personalLimit").asInt());
        assertEquals(200,page.path("summary").path("globalLimit").asInt());
        jdbc.update("INSERT INTO app_parameter(id,param_key,param_value,is_secret,description,state,del_is,version_no) VALUES(?,?,?,0,?,'active',0,1)",
                CryptoUtils.randomId(),"ai.personal_daily_limit","120","AI 每日提问次数上限（单个用户）");
        JsonNode afterParameter=logs("dateFrom",day,"dateTo",day);
        assertEquals(120,afterParameter.path("summary").path("personalLimit").asInt());
        assertEquals(10,afterParameter.path("items").get(0).path("limitCount").asInt(),"历史当天行仍按当时的快照显示");
    }

    private String rawLogs(String... keyValues) throws Exception {
        MockHttpServletRequestBuilder builder=get("/api/admin/ai/usage-logs").header("X-Admin-Token","dev-admin-token");
        for(int i=0;i+1<keyValues.length;i+=2) builder=builder.param(keyValues[i],keyValues[i+1]);
        return mvc.perform(builder).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
    private JsonNode logs(String... keyValues) throws Exception { return json.readTree(rawLogs(keyValues)); }

    private void quota(String day,String scope,int limit,int used){jdbc.update("INSERT INTO ai_daily_quota(id,quota_date,scope_key,limit_count,used_count,reserved_count) VALUES(?,?,?,?,?,0)",CryptoUtils.randomId(),day,scope,limit,used);}
    private void attempt(String owner,String day,String state,Integer input,Integer output,String settled){jdbc.update("INSERT INTO ai_attempt(id,job_id,attempt_no,trigger_type,owner_id,price_id,budget_id,quota_date,state,reserved_amount,settled_amount,input_tokens,output_tokens) VALUES(?,?,1,'manual',?,?,?,?,?,0.001000,?,?,?)",
            CryptoUtils.randomId(),CryptoUtils.randomId(),owner,CryptoUtils.randomId(),CryptoUtils.randomId(),day,state,settled,input,output);}

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
