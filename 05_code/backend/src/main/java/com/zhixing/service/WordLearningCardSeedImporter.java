package com.zhixing.service;

import com.fasterxml.jackson.databind.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.*;

/** Explicit opt-in only. No startup hook, no network, no AI or audio calls. */
public class WordLearningCardSeedImporter {
    private final JdbcTemplate jdbc; private final TransactionTemplate tx;
    public WordLearningCardSeedImporter(JdbcTemplate jdbc, PlatformTransactionManager manager){this.jdbc=jdbc;this.tx=new TransactionTemplate(manager);}
    public int seed() throws Exception {
        final JsonNode seeds;
        try(InputStream in=new ClassPathResource("word-learning-cards.json").getInputStream()){seeds=new ObjectMapper().readTree(in);}
        return tx.execute(status -> {
            int inserted=0;
            for(JsonNode word:seeds){
                List<String> versions=jdbc.queryForList("SELECT cv.id FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id " +
                    "WHERE cv.content_id=lc.id AND lc.content_type='word' AND lc.state='published' AND lc.withdrawn_at IS NULL AND cv.review_status='approved' AND LOWER(cv.word_term)=? " +
                    "AND (LOCATE(?,cv.meaning)>0 OR EXISTS(SELECT 1 FROM word_sense ws WHERE ws.content_version_id=cv.id AND LOCATE(?,ws.meaning)>0))",
                    String.class,word.path("word").asText(),word.path("meaningContains").asText(),word.path("meaningContains").asText());
                for(String version:versions){int order=0;for(JsonNode card:word.path("cards")){
                    String key=card.path("key").asText(),url=optional(card,"sourceUrl");
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM word_learning_card WHERE content_version_id=? AND card_key=?",Integer.class,version,key)>0)continue;
                    String id=UUID.nameUUIDFromBytes(("word-card:"+version+":"+key).getBytes(StandardCharsets.UTF_8)).toString().replace("-","");
                    inserted+=jdbc.update("INSERT INTO word_learning_card(id,content_version_id,card_key,card_type,sense_label,title,related_term,body,example_text,example_translation,recall_prompt,"+
                        "source_kind,source_title,source_url,source_verified,rights_status,rights_note,review_status,reviewed_by,reviewed_at,state,sort_no) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'original',?,'approved','ai-assisted-editorial-v1',CURRENT_TIMESTAMP,'published',?)",
                        id,version,key,card.path("type").asText(),card.path("sense").asText(),card.path("title").asText(),optional(card,"related"),card.path("body").asText(),
                        optional(card,"example"),optional(card,"translation"),optional(card,"prompt"),url==null?"original":"reference",
                        url==null?"本应用原创 · AI辅助编写":card.path("sourceTitle").asText(),url,url==null?0:1,
                        "本应用原创辅助讲解及例句（AI辅助编写），非词典原文、影视台词或歌词。参考链接仅用于核对知识点，不代表取得该站内容的复制授权。",order++);
                }}
            }
            return inserted;
        });
    }
    private String optional(JsonNode node,String key){String value=node.path(key).asText("").trim();return value.isEmpty()?null:value;}
}
