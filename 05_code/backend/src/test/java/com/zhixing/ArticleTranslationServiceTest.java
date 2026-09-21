package com.zhixing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import com.zhixing.service.AiService;
import com.zhixing.service.ArticleTranslationService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ArticleTranslationServiceTest {
    @Test void missingTitleTranslationIsPersistedOnceWithoutUsingSummary() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("titletranslation" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
                .addScript("schema.sql").build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(db);
            String source = CryptoUtils.randomId(), article = CryptoUtils.randomId(), version = CryptoUtils.randomId();
            jdbc.update("INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'Test','original','Test')", source);
            jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'english_article',?,?,'published',?,?)", article, source, CryptoUtils.sha256(article), version, version);
            jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,'My Schoolbag','我的书包里有一本故事书。','My schoolbag has a book.','Test',?,'approved',?)", version, article, CryptoUtils.sha256(version), source);
            AiService ai = mock(AiService.class);
            when(ai.generateSystemContent(anyString(), anyString(), anyString())).thenReturn("{\"titleTranslationZh\":\"我的书包\"}");
            ArticleTranslationService service = new ArticleTranslationService(jdbc, new ObjectMapper(), ai);
            service.ensureTitleTranslation(article);
            service.ensureTitleTranslation(article);
            assertThat(jdbc.queryForObject("SELECT title_translation FROM content_version WHERE id=?", String.class, version)).isEqualTo("我的书包");
            assertThat(jdbc.queryForObject("SELECT summary FROM content_version WHERE id=?", String.class, version)).isEqualTo("我的书包里有一本故事书。");
            verify(ai, times(1)).generateSystemContent(anyString(), anyString(), anyString());
        } finally { db.shutdown(); }
    }

    @Test void invalidTitleTranslationDoesNotOverwriteExistingArticleData() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("invalidtitle" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
                .addScript("schema.sql").build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(db);
            String source = CryptoUtils.randomId(), article = CryptoUtils.randomId(), version = CryptoUtils.randomId();
            jdbc.update("INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'Test','original','Test')", source);
            jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'english_article',?,?,'published',?,?)", article, source, CryptoUtils.sha256(article), version, version);
            jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,'My Schoolbag','我的书包里有一本故事书。','My schoolbag has a book.','Test',?,'approved',?)", version, article, CryptoUtils.sha256(version), source);
            AiService ai = mock(AiService.class);
            when(ai.generateSystemContent(anyString(), anyString(), anyString())).thenReturn("{\"titleTranslationZh\":\"An English summary\"}");
            ArticleTranslationService service = new ArticleTranslationService(jdbc, new ObjectMapper(), ai);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ensureTitleTranslation(article))
                    .isInstanceOf(com.zhixing.common.ApiException.class);
            assertThat(jdbc.queryForObject("SELECT title_translation FROM content_version WHERE id=?", String.class, version)).isNull();
        } finally { db.shutdown(); }
    }

    @Test void missingTranslationIsPersistedOnceWithoutReplacingEnglishOrExistingTranslation() throws Exception {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("translation" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
                .addScript("schema.sql").build();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(db);
            String source = CryptoUtils.randomId(), article = CryptoUtils.randomId(), version = CryptoUtils.randomId();
            jdbc.update("INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'Test','original','Test')", source);
            jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'english_article',?,?,'published',?,?)", article, source, CryptoUtils.sha256(article), version, version);
            jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,body,license_snapshot,body_hash,review_status,created_by,article_blocks) VALUES(?,?,1,'Test','Hello. Bye.','Test',?,'approved',?,?)", version, article, CryptoUtils.sha256(version), source,
                    "[{\"paragraph_id\":\"p1\",\"text\":\"Hello.\",\"translation\":\"你好。\"},{\"paragraph_id\":\"p2\",\"text\":\"Bye.\"}]");
            AiService ai = mock(AiService.class);
            when(ai.generateSystemContent(anyString(), anyString(), anyString())).thenReturn("{\"translations\":[\"我们道别了。\"]}");
            ArticleTranslationService service = new ArticleTranslationService(jdbc, new ObjectMapper(), ai);
            service.ensureTranslation(article);
            service.ensureTranslation(article);
            JsonNode blocks = new ObjectMapper().readTree(jdbc.queryForObject("SELECT article_blocks FROM content_version WHERE id=?", String.class, version));
            assertThat(blocks.get(0).path("translation").asText()).isEqualTo("你好。");
            assertThat(blocks.get(1).path("text").asText()).isEqualTo("Bye.");
            assertThat(blocks.get(1).path("translation").asText()).isEqualTo("我们道别了。");
            verify(ai, times(1)).generateSystemContent(anyString(), anyString(), anyString());
        } finally { db.shutdown(); }
    }
}
