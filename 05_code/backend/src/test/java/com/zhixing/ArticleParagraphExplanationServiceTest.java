package com.zhixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import com.zhixing.service.AiService;
import com.zhixing.service.ArticleParagraphExplanationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ArticleParagraphExplanationServiceTest {
    @Test void explanationIsPersistedAndReusedWithoutAnotherAiCall() {
        Fixture fixture = new Fixture();
        try {
            when(fixture.ai.generateSystemText(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("自然译文：你好。关键词：hello。");
            Map<String, Object> first = fixture.service.explain("user-one", fixture.article, "p1");
            Map<String, Object> second = fixture.service.explain("user-two", fixture.article, "p1");
            assertThat(first).containsEntry("cached", false);
            assertThat(second).containsEntry("cached", true).containsEntry("explanation", first.get("explanation"));
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM article_paragraph_explanation WHERE state='ready'", Integer.class)).isEqualTo(1);
            verify(fixture.ai, times(1)).generateSystemText(anyString(), anyString(), anyString(), anyString(), eq("explain_article_paragraph"), eq("english_article"));
        } finally { fixture.close(); }
    }

    @Test void simultaneousRequestsUseOneGeneration() throws Exception {
        Fixture fixture = new Fixture();
        try {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            when(fixture.ai.generateSystemText(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenAnswer(invocation -> {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("timed out");
                return "自然译文：你好。关键词：hello。";
            });
            CompletableFuture<Map<String, Object>> first = CompletableFuture.supplyAsync(() -> fixture.service.explain("user-one", fixture.article, "p1"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<Map<String, Object>> second = CompletableFuture.supplyAsync(() -> fixture.service.explain("user-two", fixture.article, "p1"));
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).containsEntry("cached", false);
            assertThat(second.get(5, TimeUnit.SECONDS)).containsEntry("cached", true);
            verify(fixture.ai, times(1)).generateSystemText(anyString(), anyString(), anyString(), anyString(), eq("explain_article_paragraph"), eq("english_article"));
        } finally { fixture.close(); }
    }

    @Test void failedGenerationLeavesNoReusableCache() {
        Fixture fixture = new Fixture();
        try {
            when(fixture.ai.generateSystemText(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalStateException("model unavailable"))
                    .thenReturn("自然译文：你好。");
            assertThatThrownBy(() -> fixture.service.explain("user-one", fixture.article, "p1"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM article_paragraph_explanation", Integer.class)).isZero();
            assertThat(fixture.service.explain("user-one", fixture.article, "p1")).containsEntry("cached", false);
            verify(fixture.ai, times(2)).generateSystemText(anyString(), anyString(), anyString(), anyString(), eq("explain_article_paragraph"), eq("english_article"));
        } finally { fixture.close(); }
    }

    @Test void changedParagraphTextGetsItsOwnExplanation() {
        Fixture fixture = new Fixture();
        try {
            when(fixture.ai.generateSystemText(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("第一版解释")
                    .thenReturn("第二版解释");
            assertThat(fixture.service.explain("user-one", fixture.article, "p1")).containsEntry("explanation", "第一版解释");
            fixture.jdbc.update("UPDATE content_version SET article_blocks=? WHERE id=(SELECT published_version_id FROM learning_content WHERE id=?)",
                    "[{\"paragraph_id\":\"p1\",\"text\":\"Hello again.\"}]", fixture.article);
            assertThat(fixture.service.explain("user-one", fixture.article, "p1")).containsEntry("explanation", "第二版解释");
            assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM article_paragraph_explanation WHERE state='ready'", Integer.class)).isEqualTo(2);
            verify(fixture.ai, times(2)).generateSystemText(anyString(), anyString(), anyString(), anyString(), eq("explain_article_paragraph"), eq("english_article"));
        } finally { fixture.close(); }
    }

    private static final class Fixture {
        final EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("paragraph-explanation-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
                .addScript("schema.sql").build();
        final JdbcTemplate jdbc = new JdbcTemplate(db);
        final AiService ai = mock(AiService.class);
        final ArticleParagraphExplanationService service = new ArticleParagraphExplanationService(jdbc, new ObjectMapper(), ai);
        final String article = CryptoUtils.randomId();

        Fixture() {
            String source = CryptoUtils.randomId(), version = CryptoUtils.randomId();
            jdbc.update("INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'Test','original','Test')", source);
            jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'english_article',?,?,'published',?,?)",
                    article, source, CryptoUtils.sha256(article), version, version);
            jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,body,license_snapshot,body_hash,review_status,created_by,article_blocks) " +
                            "VALUES(?,?,1,'Test','Hello.','Test',?,'approved',?,?)", version, article, CryptoUtils.sha256(version), source,
                    "[{\"paragraph_id\":\"p1\",\"text\":\"Hello.\"}]");
        }

        void close() { db.shutdown(); }
    }
}
