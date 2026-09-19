package com.zhixing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @BeforeEach void seed(){
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
}
