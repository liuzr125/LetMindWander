package com.zhixing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import com.zhixing.service.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WordLearningCardCoverageTest {
    JdbcTemplate jdbc; WordLearningCardCoverageService generator; WordLearningCardService cards;
    @BeforeAll static void quiet(){((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);}
    @BeforeEach void setup(){
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:coverage"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        ResourceDatabasePopulator schema=new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));schema.setSqlScriptEncoding("UTF-8");schema.execute(ds);
        jdbc=new JdbcTemplate(ds);generator=new WordLearningCardCoverageService(jdbc,new DataSourceTransactionManager(ds),new ObjectMapper());cards=new WordLearningCardService(jdbc);
        book("primary","PRIMARY",10);book("cet4","CET4",40);book("other","OTHER",100);
        word("apple","苹果","n","I eat an apple every day.","我每天吃一个苹果。");
        word("quiet","安静的","adj",null,null);word("outside","外面","adv","Wait outside.","在外面等。");
        member("PRIMARY","apple",1);member("CET4","apple",10);member("CET4","quiet",11);member("OTHER","outside",1);
    }
    void book(String level,String code,int sort){jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,word_count,sort_no,state) VALUES(?,?,?,'test',?,0,?,'active')",code,code,code,level,sort);}
    void word(String id,String meaning,String pos,String sentence,String translation){
        String version=id+"-v1",sense=id+"-sense";
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,published_version_id) VALUES(?,'word','source',?,'published',?)",id,CryptoUtils.sha256(id),version);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'test',?,'approved','test')",version,id,id,id,meaning,CryptoUtils.sha256(version));
        jdbc.update("INSERT INTO word_sense(id,content_version_id,part_of_speech,meaning,sort_no) VALUES(?,?,?,?,1)",sense,version,pos,meaning);
        if(sentence!=null)jdbc.update("INSERT INTO word_example(id,sense_id,sentence,translation,sort_no) VALUES(?,?,?,?,1)",id+"-example",sense,sentence,translation);
    }
    void member(String book,String content,int sort){jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,100,1)",book+content,book,content,sort);}
    int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    @Test void generatesTwoBaselineCardsPerUniqueTargetAndLinksExistingExample(){
        Map<String,Object> result=generator.generate();
        assertEquals(2,result.get("targetWords"));assertEquals(4,result.get("insertedCards"));assertEquals(2,result.get("completeWords"));
        assertEquals(4,count("SELECT COUNT(*) FROM word_learning_card"));assertEquals(2,count("SELECT COUNT(*) FROM word_learning_card_coverage"));assertEquals(12,count("SELECT COUNT(*) FROM word_learning_card_type_coverage"));
        assertEquals("primary,cet4",jdbc.queryForObject("SELECT target_scope_codes FROM word_learning_card_coverage WHERE content_version_id='apple-v1'",String.class));
        assertEquals("linked",jdbc.queryForObject("SELECT example_status FROM word_learning_card_coverage WHERE content_version_id='apple-v1'",String.class));
        assertEquals("missing",jdbc.queryForObject("SELECT example_status FROM word_learning_card_coverage WHERE content_version_id='quiet-v1'",String.class));
        assertEquals("available",jdbc.queryForObject("SELECT coverage_status FROM word_learning_card_type_coverage WHERE content_version_id='apple-v1' AND card_type='mnemonic'",String.class));
        assertEquals("evidence_required",jdbc.queryForObject("SELECT coverage_status FROM word_learning_card_type_coverage WHERE content_version_id='apple-v1' AND card_type='synonym'",String.class));
        assertEquals("optional_not_available",jdbc.queryForObject("SELECT coverage_status FROM word_learning_card_type_coverage WHERE content_version_id='apple-v1' AND card_type='quote'",String.class));
        assertEquals(0,count("SELECT COUNT(*) FROM word_learning_card WHERE content_version_id='outside-v1'"));
        Map<String,Object> usage=cards.cards("apple").stream().filter(c->"usage".equals(c.get("cardType"))).findFirst().get();
        assertEquals("I eat an apple every day.",usage.get("exampleText"));assertEquals("inherited",usage.get("rightsStatus"));
    }
    @Test void repeatedGenerationIsIdempotentAndDoesNotMutateLearningOrExamples(){
        generator.generate();Map<String,Object> second=generator.generate();
        assertEquals(0,second.get("insertedCards"));assertEquals(4,count("SELECT COUNT(*) FROM word_learning_card"));
        assertEquals(1,count("SELECT COUNT(*) FROM word_example WHERE sentence='I eat an apple every day.'"));
        assertEquals(0,count("SELECT COUNT(*) FROM learning_record"));assertEquals(0,count("SELECT COUNT(*) FROM pronunciation"));
    }
    @Test void softDeletedGeneratedCardIsNeverResurrectedAndCoverageShowsBlocker(){
        generator.generate();jdbc.update("UPDATE word_learning_card SET del_is=1 WHERE content_version_id='apple-v1' AND card_key='system-recall-v1'");
        Map<String,Object> result=generator.generate();assertEquals(0,result.get("insertedCards"));assertEquals(1,result.get("completeWords"));
        assertEquals("blocked",jdbc.queryForObject("SELECT baseline_status FROM word_learning_card_coverage WHERE content_version_id='apple-v1'",String.class));
        assertTrue(jdbc.queryForObject("SELECT issues_json FROM word_learning_card_coverage WHERE content_version_id='apple-v1'",String.class).contains("missing_mnemonic"));
    }
    @Test void wrongVersionExampleCannotLeakThroughCardEndpoint(){
        generator.generate();jdbc.update("UPDATE word_learning_card SET example_id='apple-example' WHERE content_version_id='quiet-v1' AND card_key='system-usage-v1'");
        Map<String,Object> usage=cards.cards("quiet").stream().filter(c->"usage".equals(c.get("cardType"))).findFirst().get();
        assertNull(usage.get("exampleText"));
    }
    @Test void legacyContentAliasSharesExactPublishedVersionWithoutDuplicatingCards(){
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,published_version_id) VALUES('apple-alias','word','source',?,'published','apple-v1')",CryptoUtils.sha256("apple-alias"));
        member("CET4","apple-alias",99);
        Map<String,Object> result=generator.generate();assertEquals(2,result.get("targetWords"));assertEquals(4,result.get("insertedCards"));
        assertEquals(2,cards.cards("apple-alias").size());assertEquals("primary,cet4",jdbc.queryForObject("SELECT target_scope_codes FROM word_learning_card_coverage WHERE content_version_id='apple-v1'",String.class));
    }
}
