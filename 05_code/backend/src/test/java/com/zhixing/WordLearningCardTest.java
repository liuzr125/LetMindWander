package com.zhixing;

import com.zhixing.service.*;
import com.zhixing.controller.WordLearningCardController;
import com.zhixing.common.*;
import com.zhixing.model.AuthenticatedSession;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.HttpStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WordLearningCardTest {
    JdbcTemplate jdbc; WordLearningCardService cards; WordLearningCardSeedImporter seeds; WordLearningCardPreferenceService preferences;
    @BeforeAll static void quiet(){((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.WARN);}
    @BeforeEach void setup() {
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:cards"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        ResourceDatabasePopulator schema=new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));schema.setSqlScriptEncoding("UTF-8");schema.execute(ds);
        jdbc=new JdbcTemplate(ds);cards=new WordLearningCardService(jdbc);seeds=new WordLearningCardSeedImporter(jdbc,new DataSourceTransactionManager(ds));preferences=new WordLearningCardPreferenceService(jdbc);
        word("candy","糖果");
    }
    void word(String word,String meaning){
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,published_version_id) VALUES(?,'word','source',?,'published',?)",word,CryptoUtils.sha256(word),word+"-v1");
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'test',?,'approved','test')",word+"-v1",word,word,word,meaning,CryptoUtils.sha256(word));
    }
    @Test void seedsKeepStructureAndAreIdempotentWithoutTouchingLearning() throws Exception {
        word("apple","苹果");word("beautiful","美丽的");word("learn","学习");
        assertEquals(18,seeds.seed());assertEquals(0,seeds.seed());
        assertEquals(7,cards.cards("candy").size());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM learning_record",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM pronunciation",Integer.class));
        Map<String,Object> usage=cards.cards("candy").stream().filter(c->"usage".equals(c.get("cardType"))).findFirst().get();
        assertNotNull(usage.get("exampleTranslation"));assertNotNull(usage.get("recallPrompt"));assertFalse(usage.containsKey("reviewedBy"));
    }
    @Test void excludedStatesAndSoftDeletionNeverReturnOrResurrect() throws Exception {
        seeds.seed();
        jdbc.update("UPDATE word_learning_card SET del_is=1 WHERE card_key='scene-v1'");
        jdbc.update("UPDATE word_learning_card SET review_status='draft' WHERE card_key='sweets-v1'");
        jdbc.update("UPDATE word_learning_card SET state='withdrawn' WHERE card_key='candle-v1'");
        jdbc.update("UPDATE word_learning_card SET rights_status='unknown' WHERE card_key='candied-v1'");
        jdbc.update("UPDATE word_learning_card SET source_verified=0 WHERE card_key='candies-v1'");
        assertEquals(0,seeds.seed());assertEquals(2,cards.cards("candy").size());
    }
    @Test void doesNotBorrowCardsAcrossVersionsOrUnrelatedMeanings() throws Exception {
        seeds.seed();
        jdbc.update("UPDATE content_version SET word_term='Candy',meaning='人名' WHERE id='candy-v1'");
        assertEquals(0,seeds.seed());
        jdbc.update("UPDATE learning_content SET published_version_id='missing' WHERE id='candy'");
        assertThrows(ApiException.class,()->cards.cards("candy"));
        jdbc.update("UPDATE learning_content SET published_version_id='candy-v2' WHERE id='candy'");
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,word_term,meaning,license_snapshot,body_hash,review_status,created_by) VALUES('candy-v2','candy',2,'candy','candy','糖果','test',?,'approved','test')",CryptoUtils.sha256("v2"));
        assertTrue(cards.cards("candy").isEmpty());
    }
    @Test void quotationsRequireRightsEvidenceAndVerifiedPreciseSource() throws Exception {
        seeds.seed();
        jdbc.update("UPDATE word_learning_card SET card_type='quote',source_kind='lyric',source_url='https://example.com/song',source_verified=1 WHERE card_key='scene-v1'");
        assertEquals(6,cards.cards("candy").size());
        jdbc.update("UPDATE word_learning_card SET rights_status='licensed',source_locator='Licensed test lyric, verse 1',rights_note='Test-only permission record' WHERE card_key='scene-v1'");
        assertEquals(7,cards.cards("candy").size());
        jdbc.update("UPDATE word_learning_card SET source_url='javascript:alert(1)' WHERE card_key='scene-v1'");
        assertEquals(6,cards.cards("candy").size());
        jdbc.update("UPDATE word_learning_card SET source_kind='original' WHERE card_key='scene-v1'");
        assertEquals(6,cards.cards("candy").size());
    }
    @Test void privateOrUnreviewedWordsAreNotExposed() throws Exception {
        seeds.seed();jdbc.update("UPDATE content_version SET review_status='draft'");
        assertThrows(ApiException.class,()->cards.cards("candy"));
        jdbc.update("UPDATE content_version SET review_status='approved'");jdbc.update("UPDATE learning_content SET state='withdrawn'");
        assertThrows(ApiException.class,()->cards.cards("candy"));
    }
    @Test void endpointRequiresUserSessionAndUsesCamelCaseContract() throws Exception {
        seeds.seed();SessionService sessions=mock(SessionService.class);
        when(sessions.requireUser(null)).thenThrow(new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","请登录"));
        when(sessions.requireUser("Bearer test")).thenReturn(new AuthenticatedSession("session","user"));
        org.springframework.test.web.servlet.MockMvc mvc=MockMvcBuilders.standaloneSetup(new WordLearningCardController(sessions,cards,preferences)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/learning/contents/candy/learning-cards")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/learning/contents/candy/learning-cards").header("Authorization","Bearer test")).andExpect(status().isOk()).andExpect(jsonPath("$[0].cardType").value("mnemonic")).andExpect(jsonPath("$[0].body").isNotEmpty());
    }
    @Test void accountPreferencesDefaultToAllAndCanFilterWithoutDeletingCards() throws Exception {
        seeds.seed();
        assertEquals(6,preferences.enabledTypes("user").size());
        Map<String,Object> request=new LinkedHashMap<String,Object>();request.put("enabledTypes",Arrays.asList("mnemonic","usage"));
        Map<String,Object> saved=preferences.update("user",request);
        assertEquals(Arrays.asList("mnemonic","usage"),saved.get("enabledTypes"));
        List<Map<String,Object>> filtered=cards.cards("candy",preferences.enabledTypes("user"));
        assertEquals(3,filtered.size());assertTrue(filtered.stream().allMatch(card->Arrays.asList("mnemonic","usage").contains(card.get("cardType"))));
        assertEquals(7,jdbc.queryForObject("SELECT COUNT(*) FROM word_learning_card",Integer.class));
        assertEquals(6,jdbc.queryForObject("SELECT COUNT(*) FROM user_word_learning_card_preference WHERE owner_id='user' AND del_is=0",Integer.class));
    }
    @Test void differentSenseDoesNotReceiveSeedCards() throws Exception {
        jdbc.update("UPDATE content_version SET meaning='人名' WHERE id='candy-v1'");
        assertEquals(0,seeds.seed());assertTrue(cards.cards("candy").isEmpty());
    }
    @Test void withdrawnTimestampIsRejectedAndPublishedVersionAliasIsSupported() throws Exception {
        seeds.seed();jdbc.update("UPDATE learning_content SET withdrawn_at=CURRENT_TIMESTAMP WHERE id='candy'");
        assertThrows(ApiException.class,()->cards.cards("candy"));
        jdbc.update("UPDATE learning_content SET withdrawn_at=NULL WHERE id='candy'");
        jdbc.update("UPDATE content_version SET content_id='canonical-candy' WHERE id='candy-v1'");
        assertFalse(cards.cards("candy").isEmpty());
    }
}
