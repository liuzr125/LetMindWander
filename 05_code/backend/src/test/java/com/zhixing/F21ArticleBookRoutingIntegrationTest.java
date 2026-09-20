package com.zhixing;

import com.zhixing.common.CryptoUtils;
import com.zhixing.model.AdminTechnicalContentPageView;
import com.zhixing.model.LearningPageView;
import com.zhixing.service.AdminContentService;
import com.zhixing.service.LearningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:f21;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE","spring.sql.init.mode=always","app.article-generation.enabled=false"})
@ActiveProfiles("dev")
class F21ArticleBookRoutingIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired LearningService learning;
    @Autowired AdminContentService admin;

    @Test void miniProgramUsesCurrentBookAndAdminCanFilterCategory(){
        String owner=id("owner"),source=id("source"),primary=id("primary"),cet=id("cet");
        jdbc.update("INSERT INTO content_source(id,name,source_type,license_note) VALUES(?,'F21 原创短文','ai_original','F21 原创测试')",source);
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,state,sort_no) VALUES(?,'F21_PRIMARY','F21 小学','official','primary','active',1)",primary);
        jdbc.update("INSERT INTO vocabulary_book(id,book_code,book_name,book_type,level_code,state,sort_no) VALUES(?,'F21_CET4','F21 四级','official','cet4','active',2)",cet);
        String primaryArticle=article(source,primary,"Primary Daily Story",1),cetArticle=article(source,cet,"CET4 Daily Story",1);
        jdbc.update("INSERT INTO user_vocabulary_book(id,owner_id,book_id,state) VALUES(?,?,?,'active')",id("selection-primary"),owner,primary);

        LearningPageView primaryPage=learning.page(owner,"english_article",null,null,null,false,null,null,1,20);
        assertEquals(1,primaryPage.getItems().size());assertEquals(primaryArticle,primaryPage.getItems().get(0).getContentId());
        AdminTechnicalContentPageView filtered=admin.articles(null,primary,null,1,20);
        assertEquals(1,filtered.getTotal());assertEquals("F21 小学",filtered.getItems().get(0).getAudienceBookNames());

        jdbc.update("UPDATE user_vocabulary_book SET state='paused' WHERE owner_id=?",owner);
        jdbc.update("INSERT INTO user_vocabulary_book(id,owner_id,book_id,state) VALUES(?,?,?,'active')",id("selection-cet"),owner,cet);
        LearningPageView cetPage=learning.page(owner,"english_article",null,null,null,false,null,null,1,20);
        assertEquals(1,cetPage.getItems().size());assertEquals(cetArticle,cetPage.getItems().get(0).getContentId());
    }

    private String article(String source,String book,String title,int slot){
        String content=id("content-"+title),version=id("version-"+title),mapping=id("mapping-"+title);
        jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,stage,state,current_version_id,published_version_id,published_at) VALUES(?,'english_article',?,?, 'primary','published',?,?,CURRENT_TIMESTAMP)",content,source,CryptoUtils.sha256(content),version,version);
        jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,license_snapshot,body_hash,review_status,created_by) VALUES(?,?,1,?,?,?,'intro',60,'F21 原创测试',?,'approved',?)",version,content,title,title,title+" body",CryptoUtils.sha256(title),id("admin"));
        jdbc.update("INSERT INTO english_article_book(id,content_id,book_id,generated_date,slot_no,target_words_json,topic_snapshot_json) VALUES(?,?,?,CURRENT_DATE,?,'[]','{}')",mapping,content,book,slot);
        return content;
    }
    private String id(String value){return java.util.UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-","");}
}
