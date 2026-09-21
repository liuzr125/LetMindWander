package com.zhixing;

import com.zhixing.common.CryptoUtils;
import com.zhixing.service.AiService;
import com.zhixing.service.DailyEnglishArticleService;
import com.zhixing.mapper.ContentMapper;
import com.zhixing.model.ArticleWordLookupRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f22;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.article-generation.enabled=true","app.article-generation.per-book-count=5"})
@ActiveProfiles("dev")
class F22DailyEnglishArticleGenerationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired DailyEnglishArticleService articles;
    @Autowired ContentMapper contentMapper;
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
        jdbc.update("INSERT INTO article_word_glossary(id,term,phonetic,meaning,source_kind) VALUES(?,'apple','/ˈæpl/','苹果','ai_generated')",id("article-apple"));
        ArticleWordLookupRow supplemented=contentMapper.selectWordsByTerms(Collections.singletonList("apple")).get(0);
        assertEquals("/ˈæpl/",supplemented.getPhonetic());
        assertEquals("释义",supplemented.getMeaning());
        assertEquals("ai_generated",supplemented.getSourceKind());
        when(ai.generateSystemContent(anyString(),anyString(),anyString(),anyString())).thenReturn(response());

        Map<String,Object> run=articles.generate(LocalDate.of(2026,9,20),"test");

        assertEquals("success",String.valueOf(run.get("state")));
        String runId=String.valueOf(run.get("id"));
        verify(ai).generateSystemContent(anyString(),anyString(),anyString(),org.mockito.ArgumentMatchers.eq(runId));
        assertEquals(0,((List<?>)articles.runDetail(runId).get("models")).size());
        String jobId=id("article-ai-job"),priceId=jdbc.queryForObject("SELECT id FROM ai_model_price LIMIT 1",String.class);
        jdbc.update("INSERT INTO ai_job(id,scope_key,action_code,source_type,source_id,source_version,source_fingerprint,prompt_version,state,queue_expires_at,payload_expires_at,request_key_hash) VALUES(?,?,'generate_english_articles','article_generation',?,1,?,'TEST','succeeded',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)",jobId,id("scope"),runId,CryptoUtils.sha256("model-test"),CryptoUtils.sha256(jobId));
        jdbc.update("INSERT INTO ai_attempt(id,job_id,attempt_no,trigger_type,price_id,budget_id,quota_date,reserved_amount,timeout_at) VALUES(?,?,1,'scheduled',?,?,CURRENT_DATE,0,CURRENT_TIMESTAMP)",id("article-ai-attempt"),jobId,priceId,id("budget"));
        List<?> models=(List<?>)articles.runDetail(runId).get("models");
        assertEquals(1,models.size());
        Map<?,?> model=(Map<?,?>)models.get(0);
        assertEquals(1,((Number)model.get("callCount")).intValue());
        assertEquals(jdbc.queryForObject("SELECT model_code FROM ai_model_price WHERE id=?",String.class,priceId),model.get("modelCode"));
        assertEquals(5,count("SELECT COUNT(*) FROM english_article_book WHERE book_id=?",book));
        assertEquals(5,count("SELECT COUNT(*) FROM learning_content WHERE content_type='english_article' AND state='published'"));
        assertEquals(5,count("SELECT COUNT(*) FROM content_version WHERE article_blocks LIKE '%translation%' AND article_blocks LIKE '%校园花园%'"));
        assertEquals(5,count("SELECT COUNT(*) FROM content_version WHERE title_translation LIKE '校园花园故事%'"));
        String articleId=jdbc.queryForObject("SELECT content_id FROM english_article_book WHERE book_id=? ORDER BY slot_no LIMIT 1",String.class,book);
        assertEquals("校园花园故事 1",contentMapper.selectDetail(id("reader"),articleId).getTitleTranslation());
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
        for(int i=1;i<=5;i++){if(i>1)out.append(',');out.append("{\"title\":\"Garden Story ").append(i).append("\",\"titleTranslationZh\":\"校园花园故事 ").append(i).append("\",\"summaryZh\":\"校园花园学习故事\",\"body\":\"").append(body).append("\",\"translationZh\":\"每天早晨，我们幸福的一家人走向学校，在校园花园里读书学习并帮助朋友。\"}");}
        return out.append("]}").toString();
    }
    private int count(String sql,Object...args){Integer value=jdbc.queryForObject(sql,Integer.class,args);return value==null?0:value;}
    private String id(String value){return java.util.UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString().replace("-","");}
}
