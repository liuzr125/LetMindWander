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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f14;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always"})
@ActiveProfiles("dev") @AutoConfigureMockMvc
class F14AdminVocabularyIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
    private static final String BOOK="f1400000000000000000000000000001",SOURCE="f1400000000000000000000000000002";

    @BeforeEach void seed(){
        jdbc.update("DELETE FROM pronunciation WHERE content_version_id LIKE 'e14%'");
        jdbc.update("DELETE FROM word_example WHERE sense_id LIKE 'a14%'");
        jdbc.update("DELETE FROM word_sense WHERE content_version_id LIKE 'e14%'");
        jdbc.update("DELETE FROM vocabulary_book_word WHERE book_id=?",BOOK);
        jdbc.update("DELETE FROM content_version WHERE content_id LIKE 'f14%'");
        jdbc.update("DELETE FROM learning_content WHERE id LIKE 'f14%'");
        jdbc.update("DELETE FROM vocabulary_book WHERE id=?",BOOK);
        jdbc.update("DELETE FROM content_source WHERE id=?",SOURCE);
        jdbc.update("INSERT INTO content_source(id,name,source_type,license_note,enabled) VALUES(?,?,'manual',?,1)",SOURCE,"管理端词书测试","测试许可");
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,description,word_count,sort_no,state) VALUES(?,?,?,?,?,?,30,1,'active')",BOOK,"f14-book","分页测试词书","school","primary","验证成员实时计数");
        for(int i=1;i<=25;i++){
            String content=String.format("f14%029d",i),version=String.format("e14%029d",i),member=String.format("d14%029d",i);
            String term="word"+i;
            jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,?,?,?,?,'primary','published',?,?,CURRENT_TIMESTAMP)",content,"word",SOURCE,CryptoUtils.sha256("content-"+i),CryptoUtils.sha256(term),version,version);
            jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,difficulty,estimated_seconds,word_term,phonetic,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,'intro',30,?,?,?,'测试许可',?,'approved','00000000000000000000000000000002')",version,content,term,"第"+i+"个测试单词",term,"/wɜːd/","测试词义"+i,CryptoUtils.sha256(term));
            jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core,source_level,source_ref) VALUES(?,?,?,?,?,?,'primary',?)",member,BOOK,content,i,100-i,i<=5?1:0,"row-"+i);
        }
        String version=String.format("e14%029d",1);
        jdbc.update("UPDATE content_version SET example_text='Wear a warm coat.',example_translation='穿一件暖和的外套。' WHERE id=?",version);
        jdbc.update("INSERT INTO word_sense(id,content_version_id,part_of_speech,meaning,sort_no) VALUES('a1400000000000000000000000000001',?,'verb','穿；戴',1)",version);
        jdbc.update("INSERT INTO word_example(id,sense_id,sentence,translation,sort_no) VALUES('a1400000000000000000000000000002','a1400000000000000000000000000001','Wear a warm coat.','穿一件暖和的外套。',1)");
        jdbc.update("INSERT INTO pronunciation(id,content_version_id,target_key,accent,phonetic,asset_id,state) VALUES('a1400000000000000000000000000003',?,'word:uk','uk','/weə/','a1400000000000000000000000000004','ready')",version);
    }

    @Test void adminCanBrowseBookWordsWithLiveCountsAndConfigurablePaging() throws Exception {
        mvc.perform(get("/api/admin/vocabulary-books")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/vocabulary-books").header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].bookName").value("分页测试词书"))
                .andExpect(jsonPath("$[0].declaredWordCount").value(30)).andExpect(jsonPath("$[0].memberCount").value(25))
                .andExpect(jsonPath("$[0].availableCount").value(25)).andExpect(jsonPath("$[0].countMismatch").value(true));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pageSize").value(20)).andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items.length()").value(20));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token").param("page","2").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(3)).andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].wordTerm").value("word11"));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token").param("pageSize","101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAGE"));
        mvc.perform(get("/api/admin/vocabulary-books/words/{id}",String.format("f14%029d",1)).header("X-Admin-Token","dev-admin-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.wordTerm").value("word1"))
                .andExpect(jsonPath("$.exampleText").value("Wear a warm coat."))
                .andExpect(jsonPath("$.senses[0].partOfSpeech").value("verb"))
                .andExpect(jsonPath("$.senses[0].examples[0].translation").value("穿一件暖和的外套。"))
                .andExpect(jsonPath("$.pronunciations[0].accent").value("uk"))
                .andExpect(jsonPath("$.sourceName").value("管理端词书测试"));
    }

    @Test void adminCanFilterBookWordsByStage() throws Exception {
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token").param("stage","primary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stage").value("primary"))
                .andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.items[0].stage").value("primary"));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token").param("stage","junior"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token").param("stage","unclassified"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token").param("stage","primary").param("keyword","word1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(11)).andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token").param("stage","primary").param("page","2").param("pageSize","10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(25)).andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].wordTerm").value("word11"));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",BOOK).header("X-Admin-Token","dev-admin-token").param("stage",new String(new char[33]).replace('\0','j')))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STAGE"));
    }

    /**
     * 学段口径 = 词书成员关系（词书 level_code）：
     * 词条自己的 learning_content.stage 不再决定筛选结果，避免出现「词书 1496 条、学段筛选只有几十条」的不一致。
     */
    @Test void bookMembershipDrivesStageFilter() throws Exception {
        String seniorBook="f14b000000000000000000000000001",seniorContent="f14b000000000000000000000000002",seniorVersion="f14b000000000000000000000000003",seniorMember="f14b000000000000000000000000004";
        String plainBook="f14b000000000000000000000000005",plainContent="f14b000000000000000000000000006",plainVersion="f14b000000000000000000000000007",plainMember="f14b000000000000000000000000008";
        jdbc.update("DELETE FROM vocabulary_book_word WHERE book_id IN (?,?)",seniorBook,plainBook);
        jdbc.update("DELETE FROM vocabulary_book WHERE id IN (?,?)",seniorBook,plainBook);
        jdbc.update("DELETE FROM content_version WHERE content_id IN (?,?)",seniorContent,plainContent);
        jdbc.update("DELETE FROM learning_content WHERE id IN (?,?)",seniorContent,plainContent);

        // 高中词书（level_code=senior）成员：learning_content.stage 留空，仍应被 stage=senior 命中
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,word_count,sort_no,state) VALUES(?,?,?,?,?,1,0,'active')",seniorBook,"F14B-SENIOR","高中测试词书","k12","senior");
        insertWord(seniorContent,seniorVersion,seniorMember,seniorBook,"seniorword","高中词",null,1);
        // 没有学段的词书成员：即使 learning_content.stage='primary' 也不应被 stage=primary 命中，且算「未分级」
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,word_count,sort_no,state) VALUES(?,?,?,?,NULL,1,0,'active')",plainBook,"F14B-NOLEVEL","无学段测试词书","school");
        insertWord(plainContent,plainVersion,plainMember,plainBook,"plainword","无学段词",null,2);

        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",seniorBook).header("X-Admin-Token","dev-admin-token").param("stage","senior"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].wordTerm").value("seniorword"));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",seniorBook).header("X-Admin-Token","dev-admin-token").param("stage","primary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",seniorBook).header("X-Admin-Token","dev-admin-token").param("stage","unclassified"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));

        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",plainBook).header("X-Admin-Token","dev-admin-token").param("stage","unclassified"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].wordTerm").value("plainword"));
        mvc.perform(get("/api/admin/vocabulary-books/{id}/words",plainBook).header("X-Admin-Token","dev-admin-token").param("stage","primary"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    private void insertWord(String content,String version,String member,String book,String term,String meaning,String stage,int sortNo){
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,word_key_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,?,?,?,?,?,'published',?,?,CURRENT_TIMESTAMP)",content,"word",SOURCE,CryptoUtils.sha256(content),CryptoUtils.sha256(term),stage,version,version);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,difficulty,estimated_seconds,word_term,phonetic,meaning,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,'intro',30,?,?,?,'测试许可',?,'approved',?)",version,content,term,meaning,term,"/"+term+"/",meaning,CryptoUtils.sha256(version),SOURCE);
        jdbc.update("INSERT INTO vocabulary_book_word(id,book_id,content_id,sort_no,importance,is_core) VALUES(?,?,?,?,1,0)",member,book,content,sortNo);
    }
}
