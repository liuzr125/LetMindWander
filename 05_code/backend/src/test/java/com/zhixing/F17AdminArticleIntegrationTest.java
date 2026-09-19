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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f17;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class F17AdminArticleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private static final String SOURCE="f1700000000000000000000000000001";
    private static final String CONTENT="f1700000000000000000000000000002";
    private static final String VERSION="f1700000000000000000000000000003";

    @BeforeEach
    void seed(){
        jdbc.update("DELETE FROM content_version WHERE id=?",VERSION);
        jdbc.update("DELETE FROM learning_content WHERE id=?",CONTENT);
        jdbc.update("DELETE FROM content_source WHERE id=?",SOURCE);
        jdbc.update("INSERT INTO content_source(id,name,source_type,url,license_note,enabled) VALUES(?,?,?, ?,?,1)",SOURCE,"English Lab","original","https://example.com/articles","Original test content");
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at) VALUES(?,'english_article',?,?, 'published',?,?,CURRENT_TIMESTAMP)",CONTENT,SOURCE,CryptoUtils.sha256("f17-content"),VERSION,VERSION);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,origin_url,origin_author,origin_published_at,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'intro',95,?,?,CURRENT_TIMESTAMP,?,?,'approved','00000000000000000000000000000002')",VERSION,CONTENT,"A Quiet Morning F17","A short reading test","The morning was quiet, and Mia opened her book.","https://example.com/articles/f17","Test Author","Original test content",CryptoUtils.sha256("f17-body"));
        jdbc.update("UPDATE content_version SET article_audio_asset_id=?,article_audio_voice='eva' WHERE id=?","f1700000000000000000000000000004",VERSION);
    }

    @Test
    void adminCanFilterAndReadPublishedArticles() throws Exception {
        mvc.perform(get("/api/admin/content/articles")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/content/articles").header("X-Admin-Token","dev-admin-token").param("difficulty","intro").param("keyword","F17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.items[0].title").value("A Quiet Morning F17"))
                .andExpect(jsonPath("$.items[0].difficulty").value("intro"))
                .andExpect(jsonPath("$.items[0].articleAudioAssetId").value("f1700000000000000000000000000004"))
                .andExpect(jsonPath("$.items[0].articleAudioUrl").exists());
        mvc.perform(get("/api/admin/content/articles/{id}",CONTENT).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.body").value("The morning was quiet, and Mia opened her book."))
                .andExpect(jsonPath("$.sourceName").value("English Lab"))
                .andExpect(jsonPath("$.licenseSnapshot").value("Original test content"));
    }

    @Test
    void invalidArticleFiltersAndUnknownContentAreReported() throws Exception {
        mvc.perform(get("/api/admin/content/articles").header("X-Admin-Token","dev-admin-token").param("difficulty","expert"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_DIFFICULTY"));
        mvc.perform(get("/api/admin/content/articles/{id}","00000000000000000000000000000000").header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ARTICLE_CONTENT_NOT_FOUND"));
    }

    @Test
    void articlesAreOrderedByEmbeddedNumberInsteadOfTitleText() throws Exception {
        seedNumberedArticle("f1700000000000000000000000000011","f1700000000000000000000000000012","F17 Order 101: Home");
        seedNumberedArticle("f1700000000000000000000000000013","f1700000000000000000000000000014","F17 Order 089: Museum");
        seedNumberedArticle("f1700000000000000000000000000015","f1700000000000000000000000000016","F17 Order 090: Museum");
        mvc.perform(get("/api/admin/content/articles").header("X-Admin-Token","dev-admin-token").param("keyword","F17 Order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("F17 Order 089: Museum"))
                .andExpect(jsonPath("$.items[1].title").value("F17 Order 090: Museum"))
                .andExpect(jsonPath("$.items[2].title").value("F17 Order 101: Home"));
    }

    private void seedNumberedArticle(String contentId,String versionId,String title){
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at) VALUES(?,'english_article',?,?, 'published',?,?,CURRENT_TIMESTAMP)",contentId,SOURCE,CryptoUtils.sha256(contentId),versionId,versionId);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'advanced',95,?,?,'approved','00000000000000000000000000000002')",versionId,contentId,title,"F17 Order summary","F17 Order body","Original test content",CryptoUtils.sha256(versionId));
    }
}
