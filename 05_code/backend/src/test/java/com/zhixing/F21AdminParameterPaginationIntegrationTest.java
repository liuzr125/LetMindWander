package com.zhixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:f21;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.sql.init.mode=always",
        "app.ai.pricing-sync-enabled=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F21AdminParameterPaginationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @BeforeEach void seed(){
        jdbc.update("DELETE FROM app_parameter WHERE param_key LIKE 'f21.page.%'");
        for(int i=1;i<=12;i++)jdbc.update("INSERT INTO app_parameter(id,param_key,param_value,is_secret,description,state,del_is,version_no) VALUES(?,?,?,?,?,'active',0,1)",String.format("f21%029d",i),String.format("f21.page.%02d",i),"value-"+i,i==1?1:0,"分页参数 "+i);
    }

    @Test void adminParametersUseTenItemsPerPageByDefault() throws Exception {
        mvc.perform(get("/api/admin/parameters").param("keyword","f21.page")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/parameters").header("X-Admin-Token","dev-admin-token").param("keyword","f21.page"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.total").value(12)).andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].paramKey").value("f21.page.01")).andExpect(jsonPath("$.items[0].paramValue").doesNotExist())
                .andExpect(jsonPath("$.items[0].valueDisplay").value("已加密保存"));
        mvc.perform(get("/api/admin/parameters").header("X-Admin-Token","dev-admin-token").param("keyword","f21.page").param("page","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].paramKey").value("f21.page.11"));
        mvc.perform(get("/api/admin/parameters").header("X-Admin-Token","dev-admin-token").param("pageSize","101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
    }

    /** 改 AI 每日上限参数后，当天的额度快照必须立即同步，否则用量日志会一直显示旧上限。 */
    @Test void aiDailyLimitParameterChangeSyncsTodayQuotaSnapshot() throws Exception {
        String today=LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String userScope="f21user000000000000000000000001", globalScope="00000000000000000000000000000000", systemScope="ffffffffffffffffffffffffffffffff";
        quota(today,userScope,10);quota(today,globalScope,200);quota(today,systemScope,200);

        // 个人上限 10 → 120：只同步用户行，全站/系统行不动
        String created=json.readTree(mvc.perform(post("/api/admin/parameters").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paramKey\":\"ai.personal_daily_limit\",\"paramValue\":\"120\",\"description\":\"AI 每日提问次数上限（单个用户）\",\"state\":\"active\",\"isSecret\":false}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("id").asText();
        assertEquals(120,limit(today,userScope));assertEquals(200,limit(today,globalScope));assertEquals(200,limit(today,systemScope));

        // 改成 30：仍然立即生效
        mvc.perform(put("/api/admin/parameters/"+created).header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paramKey\":\"ai.personal_daily_limit\",\"paramValue\":\"30\",\"description\":\"AI 每日提问次数上限（单个用户）\",\"state\":\"active\",\"isSecret\":false}"))
                .andExpect(status().isOk());
        assertEquals(30,limit(today,userScope));

        // 全站上限 500：同步全站与系统行
        mvc.perform(post("/api/admin/parameters").header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paramKey\":\"ai.global_daily_limit\",\"paramValue\":\"500\",\"description\":\"AI 每日调用次数上限（全站）\",\"state\":\"active\",\"isSecret\":false}"))
                .andExpect(status().isOk());
        assertEquals(500,limit(today,globalScope));assertEquals(500,limit(today,systemScope));assertEquals(30,limit(today,userScope));

        // 非法值回退到兜底默认（个人 10 / 全站 200），不会把额度写坏
        mvc.perform(put("/api/admin/parameters/"+created).header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paramKey\":\"ai.personal_daily_limit\",\"paramValue\":\"abc\",\"description\":\"AI 每日提问次数上限（单个用户）\",\"state\":\"active\",\"isSecret\":false}"))
                .andExpect(status().isOk());
        assertEquals(10,limit(today,userScope));

        // 其它参数不触碰额度快照
        mvc.perform(put("/api/admin/parameters/"+String.format("f21%029d",2)).header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paramKey\":\"f21.page.02\",\"paramValue\":\"changed\",\"description\":\"分页参数 2\",\"state\":\"active\",\"isSecret\":false}"))
                .andExpect(status().isOk());
        assertEquals(10,limit(today,userScope));assertEquals(500,limit(today,globalScope));

        // 昨天的历史行不被回溯修改
        String yesterday=LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        quota(yesterday,userScope,10);
        mvc.perform(put("/api/admin/parameters/"+created).header("X-Admin-Token","dev-admin-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"paramKey\":\"ai.personal_daily_limit\",\"paramValue\":\"88\",\"description\":\"AI 每日提问次数上限（单个用户）\",\"state\":\"active\",\"isSecret\":false}"))
                .andExpect(status().isOk());
        assertEquals(88,limit(today,userScope));assertEquals(10,limit(yesterday,userScope),"历史日期保留当天快照");
    }

    private void quota(String day,String scope,int limit){jdbc.update("INSERT INTO ai_daily_quota(id,quota_date,scope_key,limit_count,used_count,reserved_count) VALUES(?,?,?,?,0,0)",CryptoUtils.randomId(),day,scope,limit);}
    private int limit(String day,String scope){return jdbc.queryForObject("SELECT limit_count FROM ai_daily_quota WHERE quota_date=? AND scope_key=?",Integer.class,day,scope);}
}
