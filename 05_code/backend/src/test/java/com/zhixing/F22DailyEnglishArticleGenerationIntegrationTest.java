package com.zhixing;

import com.zhixing.common.CryptoUtils;
import com.zhixing.service.AiService;
import com.zhixing.service.DailyEnglishArticleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f22;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.article-generation.enabled=true","app.article-generation.per-book-count=5"})
@ActiveProfiles("dev")
class F22DailyEnglishArticleGenerationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DailyEnglishArticleService articles;
    @MockBean AiService ai;

    @Test void generatesFiveValidatedArticlesForEachEligibleBook(){
        String generatedSource="e0000000000000000000000000000017",rssSource=id("rss-source"),book=id("book");
        jdbc.update("INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'每日原创','ai_original','原创')",generatedSource);
        jdbc.update("INSERT INTO content_source(id,name,source_type,url,license_note) VALUES(?,'官方 RSS','rss','https://example.test/feed','仅作选题')",rssSource);
        String topic=id("topic"),topicVersion=id("topic-version");
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at) VALUES(?,'tech',?,?,'published',?,?,CURRENT_TIMESTAMP)",topic,rssSource,CryptoUtils.sha256(topic),topicVersion,topicVersion);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,origin_url,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,'A New School Garden','topic','topic','intro',60,'https://example.test/topic','仅作选题',?,'approved',?)",topicVersion,topic,CryptoUtils.sha256(topicVersion),id("admin"));
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,state,sort_no) VALUES(?,'F22_PRIMARY','F22 小学英语','official','primary','active',1)",book);
        String[] terms={"apple","book","clean","dream","family","garden","happy","learn","morning","school"};
        for(int i=0;i<terms.length;i++)word(book,terms[i],i+1);
        when(ai.generateSystemContent(anyString(),anyString(),anyString())).thenReturn(response());

        Map<String,Object> run=articles.generate(LocalDate.of(2026,9,20),"test");

        assertEquals("success",String.valueOf(run.get("state")));
        assertEquals(5,count("SELECT COUNT(*) FROM english_article_book WHERE book_id=?",book));
        assertEquals(5,count("SELECT COUNT(*) FROM learning_content WHERE content_type='english_article' AND state='published'"));
        assertEquals(5,count("SELECT generated_count FROM english_article_generation_run WHERE trigger_key='test:2026-09-20'"));
        assertEquals(7,articles.updatePerBookCount(7).getPerBookCount());
        assertEquals(7,Integer.parseInt(jdbc.queryForObject("SELECT param_value FROM app_parameter WHERE param_key='article_generation.per_book_count'",String.class)));
        assertFalse(articles.updateEnabled(false).isEnabled());
        assertEquals("false",jdbc.queryForObject("SELECT param_value FROM app_parameter WHERE param_key='article_generation.enabled'",String.class));
    }

    private void word(String book,String term,int sort){
        String content=id("word-"+term),version=id("version-"+term);
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,'word',?,?,?,'primary','published',?,?,CURRENT_TIMESTAMP)",content,"e0000000000000000000000000000017",CryptoUtils.sha256(content),CryptoUtils.sha256(term),version,version);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'intro',60,?,?,'原创',?,'approved',?)",version,content,term,term,term,term,"释义",CryptoUtils.sha256(version),id("admin"));
        jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,1,1)",id("map-"+term),book,content,sort);
    }

    private String response(){
        String body="Every morning our happy family walks to school. I carry a book and an apple. We learn how to keep the garden clean, share simple work, and help new friends. The small project gives us a dream for a greener home and a kinder day together.";
        StringBuilder out=new StringBuilder("{\"articles\":[");
        for(int i=1;i<=5;i++){if(i>1)out.append(',');out.append("{\"title\":\"Garden Story ").append(i).append("\",\"summaryZh\":\"校园花园学习故事\",\"body\":\"").append(body).append("\"}");}
        return out.append("]}").toString();
    }
    private int count(String sql,Object...args){Integer value=jdbc.queryForObject(sql,Integer.class,args);return value==null?0:value;}
    private String id(String value){return java.util.UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-","");}
}
