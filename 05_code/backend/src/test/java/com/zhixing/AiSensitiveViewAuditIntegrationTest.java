package com.zhixing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zhixing.common.*;
import com.zhixing.service.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:sensitive-view;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
      "spring.sql.init.mode=always"
    })
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AiSensitiveViewAuditIntegrationTest {
  private static final String ACTOR = "11111111111111111111111111111111";
  private static final String QUESTION = "private original question";
  private static final String ANSWER = "private original answer";
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired AiService ai;
  @MockBean AdminAuthService auth;
  private String job;

  @BeforeEach
  void seed() {
    when(auth.require(anyString(), eq("ai_audit")))
        .thenAnswer(
            call -> {
              if (!"viewer-session".equals(call.getArgument(0)))
                throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_MENU_FORBIDDEN", "没有权限");
              return new AdminPrincipal(
                  ACTOR, "viewer", "ADMIN", "审计员", Collections.singleton("ai_audit"));
            });
    job = CryptoUtils.randomId();
    jdbc.update(
        "INSERT INTO ai_job(id,owner_id,scope_key,action_code,source_type,source_id,source_version,source_fingerprint,prompt_version,input_text,output_json,state,queue_expires_at,payload_expires_at,request_key_hash) VALUES(?,?,?,'ask_question','question',?,1,?,'TEST',?,?,'succeeded',?,?,?)",
        job,
        ACTOR,
        ACTOR,
        job,
        CryptoUtils.sha256(job),
        QUESTION,
        "{\"answer\":\"" + ANSWER + "\"}",
        Timestamp.from(Instant.now().plusSeconds(3600)),
        Timestamp.from(Instant.now().plusSeconds(86400)),
        CryptoUtils.sha256(job));
  }

  private int auditCount() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM admin_audit WHERE target_id=? AND action_code='ai_conversation_view'",
        Integer.class,
        job);
  }

  @Test
  void eachDetailReadPersistsRealViewerAndNoSensitiveTextInAudit() throws Exception {
    for (int i = 0; i < 2; i++)
      mvc.perform(
              get("/api/admin/ai-audit/questions/{id}/detail", job)
                  .header("X-Admin-Token", "viewer-session"))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "no-store"))
          .andExpect(header().string("Pragma", "no-cache"))
          .andExpect(jsonPath("$.question").value(QUESTION))
          .andExpect(jsonPath("$.answer").value(ANSWER));
    assertThat(auditCount()).isEqualTo(2);
    assertThat(
            jdbc.queryForList(
                "SELECT metadata_json FROM admin_audit WHERE target_id=?", String.class, job))
        .containsOnly("{}");
    for (Map<String, Object> row :
        jdbc.queryForList(
            "SELECT admin_id,target_type,metadata_json,expires_at,created_at FROM admin_audit WHERE target_id=?",
            job)) {
      assertThat(row.get("admin_id")).isEqualTo(ACTOR);
      assertThat(row.get("target_type")).isEqualTo("ai_job");
      assertThat(row.get("metadata_json").toString())
          .doesNotContain(QUESTION, ANSWER, "viewer-session");
      assertThat(((Timestamp) row.get("expires_at")).toInstant())
          .isAfter(Instant.now().plus(Duration.ofDays(179)));
      assertThat(row.get("created_at")).isNotNull();
    }
  }

  @Test
  void summaryListingDoesNotDiscloseBodyOrCreateViewAudit() throws Exception {
    String body =
        mvc.perform(get("/api/admin/ai-audit/questions").header("X-Admin-Token", "viewer-session"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains(job).doesNotContain(QUESTION, ANSWER);
    assertThat(auditCount()).isZero();
  }

  @Test
  void forbiddenReadDoesNotDiscloseBodyOrWriteAudit() throws Exception {
    String body =
        mvc.perform(
                get("/api/admin/ai-audit/questions/{id}/detail", job)
                    .header("X-Admin-Token", "denied-session"))
            .andExpect(status().isForbidden())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain(QUESTION, ANSWER);
    assertThat(auditCount()).isZero();
  }

  @Test
  void missingRecordDoesNotCreateSuccessfulViewAudit() throws Exception {
    mvc.perform(
            get("/api/admin/ai-audit/questions/{id}/detail", CryptoUtils.randomId())
                .header("X-Admin-Token", "viewer-session"))
        .andExpect(status().isNotFound());
    assertThat(auditCount()).isZero();
  }

  @Test
  void auditDatabaseFailurePreventsResponseBodyDisclosure() throws Exception {
    jdbc.execute(
        "ALTER TABLE admin_audit ADD CONSTRAINT reject_sensitive_view CHECK(action_code<>'ai_conversation_view')");
    try {
      String body =
          mvc.perform(
                  get("/api/admin/ai-audit/questions/{id}/detail", job)
                      .header("X-Admin-Token", "viewer-session"))
              .andExpect(status().isServiceUnavailable())
              .andExpect(header().string("Cache-Control", "no-store"))
              .andExpect(jsonPath("$.code").value("ADMIN_AUDIT_UNAVAILABLE"))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).doesNotContain(QUESTION, ANSWER, "Jdbc", "constraint", "INSERT");
      assertThat(auditCount()).isZero();
    } finally {
      jdbc.execute("ALTER TABLE admin_audit DROP CONSTRAINT reject_sensitive_view");
    }
  }

  @Test
  void serviceRejectsMissingActorBeforeDisclosingOrAuditing() {
    assertThatThrownBy(() -> ai.adminQuestionAuditDetail(job, null))
        .isInstanceOf(ApiException.class);
    assertThat(auditCount()).isZero();
  }
}
