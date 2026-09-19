package com.zhixing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:f16;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.sql.init.mode=always",
        "app.ai.pricing-sync-enabled=false"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F16AdminOverviewIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void adminCanReadOperationalOverview() throws Exception {
        mvc.perform(get("/api/admin/overview").header("X-Admin-Token", "dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessDate").isString())
                .andExpect(jsonPath("$.activeUsers").isNumber())
                .andExpect(jsonPath("$.publishedContent").isNumber())
                .andExpect(jsonPath("$.todayTasks").isNumber())
                .andExpect(jsonPath("$.pricing.enabled").value(false));
    }
}
