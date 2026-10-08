package com.zhixing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhixing.common.CryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:f15;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
      "spring.sql.init.mode=always"
    })
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F15AdminTechnicalContentIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired com.zhixing.mapper.ContentCollectionMapper collection;
  @Autowired com.zhixing.mapper.AdminContentMapper adminContent;
  @Autowired org.apache.ibatis.session.SqlSession sqlSession;

  private static final String SOURCE = "f1500000000000000000000000000001";
  private static final String TOPIC = "f1500000000000000000000000000002";
  private static final String CONTENT = "f1500000000000000000000000000003";
  private static final String VERSION = "f1500000000000000000000000000004";

  @BeforeEach
  void seed() {
    jdbc.update("DELETE FROM content_topic WHERE id='f1500000000000000000000000000005'");
    jdbc.update("DELETE FROM content_version WHERE id=?", VERSION);
    jdbc.update("DELETE FROM learning_content WHERE id=?", CONTENT);
    jdbc.update("DELETE FROM learning_topic WHERE id=?", TOPIC);
    jdbc.update("DELETE FROM content_source WHERE id=?", SOURCE);
    jdbc.update(
        "INSERT INTO content_source(id,name,source_type,url,license_note,enabled) VALUES(?,?,?, ?,?,1)",
        SOURCE,
        "官方技术文档",
        "official",
        "https://example.com/docs",
        "允许测试引用");
    jdbc.update(
        "INSERT INTO learning_topic(id,scope_key,name,normalized_name,state) VALUES(?, 'global',?,?,'active')",
        TOPIC,
        "AI基础",
        "ai基础");
    jdbc.update(
        "INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at) VALUES(?,'tech',?,?, 'published',?,?,?)",
        CONTENT,
        SOURCE,
        CryptoUtils.sha256("f15-content"),
        VERSION,
        VERSION,
        java.sql.Timestamp.valueOf("2026-09-18 08:00:00"));
    jdbc.update(
        "INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,origin_url,origin_author,origin_published_at,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'advanced',420,?,?,?,?,?,'approved','00000000000000000000000000000002')",
        VERSION,
        CONTENT,
        "理解向量检索",
        "从召回到排序的完整链路",
        "向量检索先召回候选内容，再通过排序模型提高相关性。",
        "https://example.com/vector",
        "测试作者",
        java.sql.Timestamp.valueOf("2026-09-14 17:10:33"),
        "允许测试引用",
        CryptoUtils.sha256("f15-body"));
    jdbc.update(
        "INSERT INTO content_topic(id,content_version_id,topic_id) VALUES('f1500000000000000000000000000005',?,?)",
        VERSION,
        TOPIC);
  }

  @Test
  void adminCanFilterAndReadPublishedTechnicalContent() throws Exception {
    mvc.perform(get("/api/admin/content/technical")).andExpect(status().isUnauthorized());

    mvc.perform(get("/api/admin/content/technical").header("X-Admin-Token", "dev-admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.pageSize").value(20))
        .andExpect(jsonPath("$.items[0].title").value("理解向量检索"))
        .andExpect(jsonPath("$.items[0].originPublishedAt").exists())
        .andExpect(jsonPath("$.items[0].topics[0]").value("AI基础"));

    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("topic", "AI基础")
                .param("keyword", "排序模型"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("topic", "不存在主题"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));

    mvc.perform(
            get("/api/admin/content/technical/{id}", CONTENT)
                .header("X-Admin-Token", "dev-admin-token"))
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
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("pageSize", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PAGE"));
    mvc.perform(
            get("/api/admin/content/technical/{id}", "00000000000000000000000000000000")
                .header("X-Admin-Token", "dev-admin-token"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TECH_CONTENT_NOT_FOUND"));
  }

  @Test
  void sourceDateUsesShanghaiDayBoundariesAndSupportsCombinedFilters() throws Exception {
    jdbc.update(
        "UPDATE content_version SET origin_published_at=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.parse("2026-09-14T17:10:33Z")),
        VERSION);
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("startDate", "2026-09-15")
                .param("endDate", "2026-09-15")
                .param("topic", "AI基础")
                .param("keyword", "向量"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("endDate", "2026-09-14"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    jdbc.update(
        "UPDATE content_version SET origin_published_at=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.parse("2026-09-15T16:00:00Z")),
        VERSION);
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("startDate", "2026-09-15")
                .param("endDate", "2026-09-15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    jdbc.update(
        "UPDATE content_version SET origin_published_at=? WHERE id=?",
        java.sql.Timestamp.from(java.time.Instant.parse("2026-09-14T16:00:00Z")),
        VERSION);
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("startDate", "2026-09-15")
                .param("endDate", "2026-09-15"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
  }

  @Test
  void dateFallbackAndInvalidRangesAreHandled() throws Exception {
    jdbc.update("UPDATE content_version SET origin_published_at=NULL WHERE id=?", VERSION);
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("startDate", "2026-09-18")
                .param("endDate", "2026-09-18"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1));
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("startDate", "2026-09-20")
                .param("endDate", "2026-09-18"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("startDate", "2026-02-30"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SOURCE_DATE"));
  }

  @Test
  @Transactional
  void sourceDateSortIsGlobalStableAndKeepsFilters() throws Exception {
    String early = sortFixture(101, "early", "2026-09-01 08:00:00", "2026-09-30 08:00:00");
    String tieA = sortFixture(102, "tie", "2026-09-10 08:00:00", "2026-09-29 08:00:00");
    String tieB = sortFixture(103, "tie", "2026-09-10 08:00:00", "2026-09-28 08:00:00");
    String fallback = sortFixture(104, "fallback", null, "2026-09-20 08:00:00");
    String undated = sortFixture(105, "undated", null, null);
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("keyword", "F15 source sort"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sortOrder").value("desc"))
        .andExpect(jsonPath("$.total").value(5))
        .andExpect(jsonPath("$.items[0].contentId").value(fallback))
        .andExpect(jsonPath("$.items[1].contentId").value(tieA))
        .andExpect(jsonPath("$.items[2].contentId").value(tieB))
        .andExpect(jsonPath("$.items[3].contentId").value(early))
        .andExpect(jsonPath("$.items[4].contentId").value(undated));
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("keyword", "F15 source sort")
                .param("sortOrder", "ASC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sortOrder").value("asc"))
        .andExpect(jsonPath("$.items[0].contentId").value(early))
        .andExpect(jsonPath("$.items[1].contentId").value(tieA))
        .andExpect(jsonPath("$.items[2].contentId").value(tieB))
        .andExpect(jsonPath("$.items[3].contentId").value(fallback))
        .andExpect(jsonPath("$.items[4].contentId").value(undated));
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("keyword", "F15 source sort")
                .param("pageSize", "2")
                .param("page", "2")
                .param("sortOrder", "desc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.items[0].contentId").value(tieB))
        .andExpect(jsonPath("$.items[1].contentId").value(early));
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("keyword", "F15 source sort")
                .param("topic", "AI基础")
                .param("startDate", "2026-09-01")
                .param("endDate", "2026-09-20")
                .param("pageSize", "2")
                .param("page", "2")
                .param("sortOrder", "asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(4))
        .andExpect(jsonPath("$.items[0].contentId").value(tieB))
        .andExpect(jsonPath("$.items[1].contentId").value(fallback));
  }

  private String sortFixture(int number, String title, String origin, String published) {
    String content = String.format("f15%029d", number),
        version = String.format("f15%029d", number + 1000);
    jdbc.update(
        "INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at) VALUES(?,'tech',?,?,'published',?,?,?)",
        content,
        SOURCE,
        CryptoUtils.sha256(content),
        version,
        version,
        published == null ? null : java.sql.Timestamp.valueOf(published));
    jdbc.update(
        "INSERT INTO content_version(id,content_id,version_no,title,body,difficulty,estimated_seconds,origin_published_at,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,'advanced',420,?,'允许测试引用',?,'approved','00000000000000000000000000000002')",
        version,
        content,
        "F15 source sort " + title,
        "排序测试",
        origin == null ? null : java.sql.Timestamp.valueOf(origin),
        CryptoUtils.sha256(version));
    jdbc.update(
        "INSERT INTO content_topic(id,content_version_id,topic_id) VALUES(?,?,?)",
        CryptoUtils.randomId(),
        version,
        TOPIC);
    return content;
  }

  @Test
  void invalidSourceDateSortIsRejected() throws Exception {
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("sortOrder", "desc; DROP TABLE learning_content"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SORT_ORDER"));
  }

  @Test
  @Transactional
  void vocabularyLabelsAreNotTechnicalCoverageTargets() {
    jdbc.update(
        "UPDATE learning_topic SET scope_key='system',topic_type='vocabulary' WHERE id=?", TOPIC);
    org.assertj.core.api.Assertions.assertThat(adminContent.selectTechnicalTopics())
        .noneMatch(topic -> "AI基础".equals(topic.getName()));
    jdbc.update("UPDATE learning_topic SET topic_type='technical' WHERE id=?", TOPIC);
    sqlSession.clearCache();
    org.assertj.core.api.Assertions.assertThat(adminContent.selectTechnicalTopics())
        .anyMatch(topic -> "AI基础".equals(topic.getName()));
  }

  @Test
  void coverageTargetCanBeSavedAndInvalidTargetsAreRejected() throws Exception {
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/admin/content/coverage/target")
                .header("X-Admin-Token", "dev-admin-token")
                .contentType("application/json")
                .content("{\"target\":30}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.technicalTarget").value(30));
    mvc.perform(get("/api/admin/content/coverage").header("X-Admin-Token", "dev-admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.technicalTarget").value(30));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/admin/content/coverage/target")
                .header("X-Admin-Token", "dev-admin-token")
                .contentType("application/json")
                .content("{\"target\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TOPIC_TARGET"));
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                    "/api/admin/content/coverage/target")
                .header("X-Admin-Token", "dev-admin-token")
                .contentType("application/json")
                .content("{\"target\":25.5}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deletedTopicsAreHiddenAndCollectionDoesNotResurrectThem() throws Exception {
    jdbc.update("UPDATE learning_topic SET scope_key='system',del_is=1 WHERE id=?", TOPIC);
    mvc.perform(
            get("/api/admin/content/technical")
                .header("X-Admin-Token", "dev-admin-token")
                .param("topic", "AI基础"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(0));
    mvc.perform(
            get("/api/admin/content/technical/{id}", CONTENT)
                .header("X-Admin-Token", "dev-admin-token"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.topics.length()").value(0));
    collection.ensureTopic(CryptoUtils.randomId(), "AI基础", "ai基础");
    org.junit.jupiter.api.Assertions.assertEquals(
        1,
        jdbc.queryForObject("SELECT del_is FROM learning_topic WHERE id=?", Integer.class, TOPIC));
    org.junit.jupiter.api.Assertions.assertEquals(
        0, collection.insertContentTopic(CryptoUtils.randomId(), "new-version", "ai基础"));
  }
}
