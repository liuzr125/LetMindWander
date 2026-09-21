package com.zhixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.service.AiService;
import com.zhixing.service.ArticleWordGlossaryService;
import com.zhixing.service.MediaService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ArticleWordGlossaryServiceTest {
    private static final String ARTICLE = "a1000000000000000000000000000001";

    @Test void generatesOncePersistsAndReturnsMissingWord() {
        EmbeddedDatabase db = database();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(db);
            AiService ai = mock(AiService.class);
            when(ai.generateSystemContent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString()))
                    .thenReturn("{\"term\":\"likes\",\"phonetic\":\"/laɪks/\",\"meaning\":\"喜欢；喜爱（like 的第三人称单数）\"}");
            ArticleWordGlossaryService service = new ArticleWordGlossaryService(jdbc,new ObjectMapper(),ai,mock(MediaService.class));

            Map<String,Object> first = service.ensure("owner",ARTICLE,"Likes");
            Map<String,Object> second = service.ensure("owner",ARTICLE,"likes");

            assertThat(first.get("phonetic")).isEqualTo("/laɪks/");
            assertThat(first.get("meaning")).isEqualTo("喜欢；喜爱（like 的第三人称单数）");
            assertThat(first.get("speechKey")).isEqualTo("glossary:likes");
            assertThat(second.get("sourceKind")).isEqualTo("ai_generated");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article_word_glossary WHERE term='likes'",Integer.class)).isEqualTo(1);
            verify(ai,times(1)).generateSystemContent(anyString(),anyString(),anyString(),eq(ARTICLE),eq("article_word_glossary"),eq("english_article"));
        } finally { db.shutdown(); }
    }

    @Test void rejectsMissingArticleWordAndInvalidModelOutputWithoutSaving() {
        EmbeddedDatabase db = database();
        try {
            JdbcTemplate jdbc = new JdbcTemplate(db);
            AiService ai = mock(AiService.class);
            ArticleWordGlossaryService service = new ArticleWordGlossaryService(jdbc,new ObjectMapper(),ai,mock(MediaService.class));
            assertThrows(ApiException.class,() -> service.ensure("owner",ARTICLE,"unknown"));
            verifyNoInteractions(ai);
            when(ai.generateSystemContent(anyString(),anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn("{\"term\":\"likes\",\"phonetic\":\"\",\"meaning\":\"\"}");
            assertThrows(ApiException.class,() -> service.ensure("owner",ARTICLE,"likes"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM article_word_glossary",Integer.class)).isZero();
        } finally { db.shutdown(); }
    }

    private EmbeddedDatabase database() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("glossary"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE")
                .addScript("schema.sql").build();
        JdbcTemplate jdbc = new JdbcTemplate(db);
        String source = "a1000000000000000000000000000002", version = "a1000000000000000000000000000003";
        jdbc.update("INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'Test','original','Test')",source);
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id) VALUES(?,'english_article',?,?,'published',?,?)",ARTICLE,source,CryptoUtils.sha256(ARTICLE),version,version);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,body,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,'Test','My cousin likes the beach.','Test',?,'approved',?)",version,ARTICLE,CryptoUtils.sha256(version),source);
        return db;
    }
}
