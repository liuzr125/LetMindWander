package com.zhixing;

import com.zhixing.common.CryptoUtils;
import com.zhixing.service.WordLearningCardEnrichmentService;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WordLearningCardEnrichmentTest {
    JdbcTemplate jdbc;WordLearningCardEnrichmentService service;
    @BeforeEach void setup(){
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:enrichment"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        ResourceDatabasePopulator schema=new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));schema.setSqlScriptEncoding("UTF-8");schema.execute(ds);jdbc=new JdbcTemplate(ds);service=new WordLearningCardEnrichmentService(jdbc,new DataSourceTransactionManager(ds));
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,word_count,sort_no,state) VALUES('book','PRIMARY','Primary','test','primary',4,1,'active')");
        word("apple","苹果","n",1);word("apply","申请；应用","v",2);word("blue","蓝色的","adj",3);word("clue","线索","n",4);
        evidence("apple","synonym","pome","n","synset:1");evidence("apple","inflection","apples","n","entry:apple#n");
        evidenceRanked("blue","synonym","amobarbital sodium","n","synset:drug",0,0);evidenceRanked("blue","synonym","dark","a","synset:later",2,0);evidenceRanked("blue","synonym","blueish","a","synset:color",0,2);evidenceRanked("blue","synonym","bluish","a","synset:color",0,1);evidence("blue","derivation","blueness","a","sense:blue -> blueness");
    }
    void word(String word,String meaning,String pos,int sort){String version=word+"-v1",content=word+"-content";jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,published_version_id) VALUES(?,'word','source',?,'published',?)",content,CryptoUtils.sha256(word),version);jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'test',?,'approved','test')",version,content,word,word,meaning,CryptoUtils.sha256(version));jdbc.update("INSERT INTO word_sense(id,content_version_id,part_of_speech,meaning,sort_no) VALUES(?,?,?,?,1)",word+"-sense",version,pos,meaning);jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,'book',?,?,100,1)","member-"+word,content,sort);}
    void evidence(String head,String type,String related,String pos,String locator){String id=UUID.nameUUIDFromBytes((head+type+related).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-","");jdbc.update("INSERT INTO word_lexical_relation_evidence(id,dataset_code,headword_norm,relation_type,related_term,part_of_speech,source_locator,evidence_note) VALUES(?,'oewn-2025',?,?,?,?,?,'test')",id,head,type,related,pos,locator);}
    void evidenceRanked(String head,String type,String related,String pos,String locator,int senseRank,int relationRank){String id=UUID.nameUUIDFromBytes((head+type+related).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-","");jdbc.update("INSERT INTO word_lexical_relation_evidence(id,dataset_code,headword_norm,relation_type,related_term,part_of_speech,sense_rank,relation_rank,source_locator,evidence_note) VALUES(?,'oewn-2025',?,?,?,?,?,?,?,'test')",id,head,type,related,pos,senseRank,relationRank,locator);}
    int count(String type,String version){return jdbc.queryForObject("SELECT COUNT(*) FROM word_learning_card WHERE content_version_id=? AND card_type=? AND del_is=0",Integer.class,version,type);}
    @Test void addsEvidenceBackedRelationsAndConservativeConfusables(){
        Map<String,Object> result=service.generate();assertTrue(((Number)result.get("insertedCards")).intValue()>=8);
        assertEquals(1,count("synonym","blue-v1"));assertEquals(1,count("derivative","blue-v1"));assertEquals(1,count("confusable","blue-v1"));
        assertEquals("bluish",jdbc.queryForObject("SELECT related_term FROM word_learning_card WHERE content_version_id='blue-v1' AND card_type='synonym'",String.class));
        assertEquals("licensed",jdbc.queryForObject("SELECT rights_status FROM word_learning_card WHERE content_version_id='blue-v1' AND card_type='synonym'",String.class));
        assertTrue(jdbc.queryForObject("SELECT body FROM word_learning_card WHERE content_version_id='blue-v1' AND card_type='confusable'",String.class).contains("不表示近义"));
        assertEquals(0,((Number)service.generate().get("insertedCards")).intValue());
    }
    @Test void approvedCuratedTypeWinsOverGeneratedType(){
        jdbc.update("INSERT INTO word_learning_card(id,content_version_id,card_key,card_type,sense_label,title,body,source_kind,source_title,source_verified,rights_status,rights_note,review_status,reviewed_by,reviewed_at,state,sort_no) VALUES('curated','blue-v1','curated','synonym','adj','人工卡','人工核验','original','editor',0,'original','原创','approved','editor',CURRENT_TIMESTAMP,'published',1)");
        service.generate();assertEquals(1,count("synonym","blue-v1"));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM word_learning_card WHERE content_version_id='blue-v1' AND card_key='system-oewn-related-v4'",Integer.class));
    }
}
