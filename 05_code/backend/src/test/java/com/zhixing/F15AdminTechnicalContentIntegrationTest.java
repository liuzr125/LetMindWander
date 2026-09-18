package com.zhixing;

import com.zhixing.common.CryptoUtils;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f15;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F15AdminTechnicalContentIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static final String SOURCE="f1500000000000000000000000000001";
    private static final String TOPIC="f1500000000000000000000000000002";
    private static final String CONTENT="f1500000000000000000000000000003";
    private static final String VERSION="f1500000000000000000000000000004";

    @BeforeEach
    void seed(){
        jdbc.update("DELETE FROM content_topic WHERE id='f1500000000000000000000000000005'");
        jdbc.update("DELETE FROM content_version WHERE id=?",VERSION);
        jdbc.update("DELETE FROM learning_content WHERE id=?",CONTENT);
        jdbc.update("DELETE FROM learning_topic WHERE id=?",TOPIC);
        jdbc.update("DELETE FROM content_source WHERE id=?",SOURCE);
        jdbc.update("INSERT INTO content_source(id,name,source_type,url,license_note,enabled) VALUES(?,?,?, ?,?,1)",SOURCE,"官方技术文档","official","https://example.com/docs","允许测试引用");
        jdbc.update("INSERT INTO learning_topic(id,scope_key,name,normalized_name,state) VALUES(?, 'global',?,?,'active')",TOPIC,"AI基础","ai基础");
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at) VALUES(?,'tech',?,?, 'published',?,?,?)",CONTENT,SOURCE,CryptoUtils.sha256("f15-content"),VERSION,VERSION,java.sql.Timestamp.valueOf("2026-09-18 08:00:00"));
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,origin_url,origin_author,origin_published_at,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'advanced',420,?,?,?,?,?,'approved','00000000000000000000000000000002')",VERSION,CONTENT,"理解向量检索","从召回到排序的完整链路","向量检索先召回候选内容，再通过排序模型提高相关性。","https://example.com/vector","测试作者",java.sql.Timestamp.valueOf("2026-09-14 17:10:33"),"允许测试引用",CryptoUtils.sha256("f15-body"));
        jdbc.update("INSERT INTO content_topic(id,content_version_id,topic_id) VALUES('f1500000000000000000000000000005',?,?)",VERSION,TOPIC);
    }

    @Test
    void adminCanFilterAndReadPublishedTechnicalContent() throws Exception {
        mvc.perform(get("/api/admin/content/technical"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/admin/content/technical").header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items[0].title").value("理解向量检索"))
                .andExpect(jsonPath("$.items[0].originPublishedAt").exists())
                .andExpect(jsonPath("$.items[0].topics[0]").value("AI基础"));

        mvc.perform(get("/api/admin/content/technical").header("X-Admin-Token","dev-admin-token").param("topic","AI基础").param("keyword","排序模型"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/admin/content/technical").header("X-Admin-Token","dev-admin-token").param("topic","不存在主题"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));

        mvc.perform(get("/api/admin/content/technical/{id}",CONTENT).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("向量检索先召回候选内容，再通过排序模型提高相关性。"))
                .andExpect(jsonPath("$.sourceName").value("官方技术文档"))
                .andExpect(jsonPath("$.originUrl").value("https://example.com/vector"))
                .andExpect(jsonPath("$.originAuthor").value("测试作者"))
                .andExpect(jsonPath("$.originPublishedAt").exists())
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.licenseSnapshot").value("允许测试引用"))
                .andExpect(jsonPath("$.reviewStatus").value("approved"));
    }

    @Test
    void invalidPagingAndUnknownContentAreReported() throws Exception {
        mvc.perform(get("/api/admin/content/technical").header("X-Admin-Token","dev-admin-token").param("pageSize","101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        mvc.perform(get("/api/admin/content/technical/{id}","00000000000000000000000000000000").header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TECH_CONTENT_NOT_FOUND"));
    }
}
