package com.zhixing;

import com.zhixing.common.ApiException;
import com.zhixing.service.WordStudyPreferenceService;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WordStudyPreferenceServiceTest {
    JdbcTemplate jdbc; WordStudyPreferenceService service;
    @BeforeEach void setup(){
        DriverManagerDataSource ds=new DriverManagerDataSource("jdbc:h2:mem:study"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        ResourceDatabasePopulator schema=new ResourceDatabasePopulator(new ClassPathResource("schema.sql"));schema.setSqlScriptEncoding("UTF-8");schema.execute(ds);
        jdbc=new JdbcTemplate(ds);service=new WordStudyPreferenceService(jdbc);
    }
    @Test void defaultsAreVisibleBritishThreeTimesAndFifteenHundredMilliseconds(){
        Map<String,Object> value=service.preferences("user");
        assertEquals("visible",value.get("defaultAnswerMode"));assertEquals(true,value.get("autoPlayEnabled"));assertEquals("uk",value.get("autoPlayAccent"));assertEquals(3,value.get("autoPlayCount"));assertEquals(1500,value.get("autoPlayIntervalMs"));
    }
    @Test void accountCanChooseHiddenDisableAutoplayOrChangeCountAndInterval(){
        Map<String,Object> request=new LinkedHashMap<String,Object>();request.put("defaultAnswerMode","hidden");request.put("autoPlayEnabled",false);request.put("autoPlayAccent","us");request.put("autoPlayCount",5);request.put("autoPlayIntervalMs",2000);
        Map<String,Object> saved=service.update("user",request);
        assertEquals("hidden",saved.get("defaultAnswerMode"));assertEquals(false,saved.get("autoPlayEnabled"));assertEquals("us",saved.get("autoPlayAccent"));assertEquals(5,saved.get("autoPlayCount"));assertEquals(2000,saved.get("autoPlayIntervalMs"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM user_word_study_preference WHERE owner_id='user' AND del_is=0",Integer.class));
    }
    @Test void rejectsOverlappingOrExcessivePlaybackSettings(){
        Map<String,Object> shortGap=new HashMap<String,Object>();shortGap.put("autoPlayIntervalMs",999);assertThrows(ApiException.class,()->service.update("user",shortGap));
        Map<String,Object> tooMany=new HashMap<String,Object>();tooMany.put("autoPlayCount",6);assertThrows(ApiException.class,()->service.update("user",tooMany));
    }
}
