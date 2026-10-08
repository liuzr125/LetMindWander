package com.zhixing.service;

import com.zhixing.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AdminTopicServiceTest {
    @Test void createsRenamesAndDeletesUnusedSystemTopicsWithAudit(){
        try(Fixture fixture=new Fixture()){
            EmbeddedDatabase db=fixture.db;
            JdbcTemplate jdbc=new JdbcTemplate(db);AdminTopicService service=new AdminTopicService(jdbc);
            service.save("admin",null,"  Cloud Native  ");
            String id=jdbc.queryForObject("SELECT id FROM learning_topic",String.class);
            assertThat(service.list()).hasSize(1);
            assertThat(((Number)service.list().get(0).get("publishedCount")).intValue()).isZero();
            service.save("admin",id,"云原生");
            assertThat(jdbc.queryForObject("SELECT name FROM learning_topic",String.class)).isEqualTo("云原生");
            assertThat(jdbc.queryForObject("SELECT normalized_name FROM learning_topic",String.class)).isEqualTo("cloud native");
            assertThatThrownBy(()->service.save("admin",null,"云原生")).isInstanceOf(ApiException.class);
            service.delete("admin",id);assertThat(service.list()).isEmpty();
            assertThat(jdbc.queryForObject("SELECT del_is FROM learning_topic WHERE id=?",Integer.class,id)).isEqualTo(1);
            assertThatThrownBy(()->service.save("admin",id,"修改已删除主题")).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->service.delete("admin",id)).isInstanceOf(ApiException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit",Integer.class)).isEqualTo(3);
        }
    }
    @Test void protectsContentPlanReferencesAndPrivateTopics(){
        try(Fixture fixture=new Fixture()){
            EmbeddedDatabase db=fixture.db;
            JdbcTemplate jdbc=new JdbcTemplate(db);AdminTopicService service=new AdminTopicService(jdbc);
            service.save("admin",null,"AI");String id=jdbc.queryForObject("SELECT id FROM learning_topic",String.class);
            jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,published_version_id) VALUES('content','tech','source',?,'published','version')",new byte[32]);
            jdbc.update("INSERT INTO content_topic(id,content_version_id,topic_id) VALUES('link','version',?)",id);
            assertThatThrownBy(()->service.delete("admin",id)).isInstanceOf(ApiException.class).hasMessageContaining("已发布");
            jdbc.update("DELETE FROM content_topic");jdbc.update("INSERT INTO learning_plan_topic(id,plan_id,topic_id) VALUES('plan-link','plan',?)",id);
            assertThatThrownBy(()->service.delete("admin",id)).isInstanceOf(ApiException.class);
            jdbc.update("INSERT INTO learning_topic(id,scope_key,name,normalized_name) VALUES('private','user:1','个人主题','private')");
            assertThat(service.list()).hasSize(1);
            assertThatThrownBy(()->service.save("admin","private","新名称")).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->service.delete("admin","private")).isInstanceOf(ApiException.class);
            assertThatThrownBy(()->service.save("admin",null," ")).isInstanceOf(ApiException.class);
        }
    }
    @Test void zeroTechnicalKnowledgeTopicCanBeDeletedWithoutDeletingItsWords(){
        try(Fixture fixture=new Fixture()){
            JdbcTemplate jdbc=new JdbcTemplate(fixture.db);AdminTopicService service=new AdminTopicService(jdbc);
            service.save("admin",null,"英语-小学");String id=jdbc.queryForObject("SELECT id FROM learning_topic",String.class);
            jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,published_version_id) VALUES('word','word','source',?,'published','word-version')",new byte[32]);
            jdbc.update("INSERT INTO content_topic(id,content_version_id,topic_id) VALUES('link','word-version',?)",id);
            service.delete("admin",id);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_topic WHERE del_is=1",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_topic",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM learning_content WHERE id='word'",Integer.class)).isEqualTo(1);
        }
    }
    @Test void countsOnlyLiveContentAndKeepsClassificationOnLegacyRename(){
        try(Fixture fixture=new Fixture()){
            JdbcTemplate jdbc=new JdbcTemplate(fixture.db);AdminTopicService service=new AdminTopicService(jdbc);
            service.save("admin",null,"英语标签","vocabulary");
            String id=jdbc.queryForObject("SELECT id FROM learning_topic",String.class);
            jdbc.update("INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state) VALUES('live','word','source',?,'published')",new byte[32]);
            for(String version:Arrays.asList("v1","v2","orphan")){
                jdbc.update("INSERT INTO content_version(id,content_id,version_no,title,license_snapshot,body_hash,created_by) VALUES(?,?,?,?,?,?,?)",version,version.equals("orphan")?"missing":"live",version.equals("v2")?2:1,"词条","test",new byte[32],"admin");
                jdbc.update("INSERT INTO content_topic(id,content_version_id,topic_id) VALUES(?,?,?)",version,version,id);
            }
            assertThat(((Number)service.list().get(0).get("contentCount")).intValue()).isEqualTo(1);
            service.save("admin",id,"新版标签");
            assertThat(service.list().get(0).get("topicType")).isEqualTo("vocabulary");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM content_topic",Integer.class)).isEqualTo(3);
            assertThatThrownBy(()->service.save("admin",id,"新版标签","unknown")).isInstanceOf(ApiException.class);
            jdbc.update("INSERT INTO learning_plan_topic(id,plan_id,topic_id) VALUES('p','plan',?)",id);
            assertThatThrownBy(()->service.save("admin",id,"新版标签","vocabulary")).isInstanceOf(ApiException.class);
        }
    }
    private static class Fixture implements AutoCloseable {
        final EmbeddedDatabase db=new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).setName("topics-"+System.nanoTime()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE").addScript("schema.sql").build();
        public void close(){db.shutdown();}
    }
}
