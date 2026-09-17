package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.LearningContentEntity;
import com.zhixing.model.ContentDetailView;
import com.zhixing.model.ArticleWordLookupRow;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.List;

/** U03/U05 的全部数据库访问集中在 Mapper；Service 只处理权限、幂等与业务联动。 */
@Mapper
public interface ContentMapper extends BaseMapper<LearningContentEntity> {
    @Select("SELECT lc.id AS content_id, cv.id AS version_id, lc.content_type, lc.stage, cv.title, cv.summary, cv.body, " +
            "cv.difficulty, cv.estimated_seconds, cv.word_term, cv.phonetic, cv.meaning, cv.example_text, cv.example_translation, " +
            "cv.origin_url, cv.origin_author, cv.origin_published_at, cv.license_snapshot, cs.name AS source_name, cs.source_type, cs.url AS source_url, cv.article_blocks AS article_blocks_json, cv.article_audio_asset_id, cv.article_audio_voice, " +
            "lr.familiarity_percent, COALESCE(lr.version_no,0) AS record_version, " +
            "CASE WHEN lr.learning_status IN ('understood','mastered') THEN TRUE ELSE FALSE END AS understood, " +
            "CASE WHEN uf.state='active' THEN TRUE ELSE FALSE END AS favorite, " +
            "CASE WHEN rs.state='active' THEN TRUE ELSE FALSE END AS in_review, " +
            "CASE WHEN wn.state='active' THEN TRUE ELSE FALSE END AS in_word_book " +
            "FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id JOIN content_source cs ON cs.id=lc.source_id " +
            "LEFT JOIN learning_record lr ON lr.owner_id=#{ownerId} AND lr.content_id=lc.id " +
            "LEFT JOIN user_favorite uf ON uf.owner_id=#{ownerId} AND uf.target_type=" +
            "CASE lc.content_type WHEN 'tech' THEN 'content' WHEN 'english_article' THEN 'article' ELSE lc.content_type END AND uf.target_id=lc.id " +
            "LEFT JOIN knowledge_item ki ON ki.owner_id=#{ownerId} AND ki.bookmark_content_id=lc.id AND ki.state<>'deleted' " +
            "LEFT JOIN review_schedule rs ON rs.owner_id=#{ownerId} AND rs.knowledge_id=ki.id " +
            "LEFT JOIN word_notebook wn ON wn.owner_id=#{ownerId} AND wn.content_id=lc.id " +
            "WHERE lc.id=#{contentId} AND lc.state='published'")
    ContentDetailView selectDetail(@Param("ownerId") String ownerId, @Param("contentId") String contentId);

    @Select("SELECT t.name FROM content_topic ct JOIN learning_topic t ON t.id=ct.topic_id " +
            "WHERE ct.content_version_id=#{versionId} AND t.state='active' ORDER BY t.name")
    List<String> selectTopics(@Param("versionId") String versionId);

    @Select("SELECT id, part_of_speech, meaning, sort_no FROM word_sense WHERE content_version_id=#{versionId} ORDER BY sort_no,id")
    List<ContentDetailView.WordSenseView> selectSenses(@Param("versionId") String versionId);

    @Select("SELECT id, sentence, translation, sort_no FROM word_example WHERE sense_id=#{senseId} ORDER BY sort_no,id")
    List<ContentDetailView.WordExampleView> selectExamples(@Param("senseId") String senseId);

    @Select("SELECT sense_id, example_id, accent, phonetic, asset_id FROM pronunciation WHERE content_version_id=#{versionId} AND state='ready' ORDER BY accent, sense_id, example_id")
    List<com.zhixing.model.PronunciationRow> selectPronunciations(@Param("versionId") String versionId);

    @Select({"<script>","SELECT lc.id content_id,NULL speech_key,cv.word_term term,cv.phonetic,cv.meaning,",
            "(SELECT p.asset_id FROM pronunciation p JOIN media_asset ma ON ma.id=p.asset_id AND ma.state='ready' WHERE p.content_version_id=cv.id AND p.state='ready' ORDER BY CASE p.accent WHEN 'uk' THEN 0 WHEN 'us' THEN 1 ELSE 2 END LIMIT 1) audio_asset_id ",
            "FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id WHERE lc.content_type='word' AND lc.state='published' AND LOWER(cv.word_term) IN ",
            "<foreach collection='terms' item='term' open='(' separator=',' close=')'>#{term}</foreach>",
            "UNION ALL SELECT NULL content_id,CONCAT('glossary:',g.term) speech_key,g.term,g.phonetic,g.meaning,g.audio_asset_id FROM article_word_glossary g WHERE g.term IN ",
            "<foreach collection='terms' item='term' open='(' separator=',' close=')'>#{term}</foreach>",
            "AND NOT EXISTS(SELECT 1 FROM learning_content lc2 JOIN content_version cv2 ON cv2.id=lc2.published_version_id WHERE lc2.content_type='word' AND lc2.state='published' AND LOWER(cv2.word_term)=g.term)",
            "</script>"})
    List<ArticleWordLookupRow> selectWordsByTerms(@Param("terms") List<String> terms);

    @Insert("INSERT INTO learning_record (id,owner_id,content_id,learning_key,last_version_id,learning_status,first_completed_at,last_feedback_at,version_no) " +
            "VALUES (#{id},#{ownerId},#{contentId},#{learningKey},#{versionId},'understood',#{now},#{now},1) " +
            "ON DUPLICATE KEY UPDATE last_version_id=VALUES(last_version_id),learning_status='understood'," +
            "first_completed_at=COALESCE(first_completed_at,VALUES(first_completed_at)),last_feedback_at=VALUES(last_feedback_at),version_no=version_no+1")
    int markUnderstood(@Param("id") String id, @Param("ownerId") String ownerId, @Param("contentId") String contentId,
                       @Param("learningKey") String learningKey, @Param("versionId") String versionId, @Param("now") Instant now);

    @Insert("INSERT INTO user_favorite (id,owner_id,target_type,target_id,state,title_snapshot,favorited_at) " +
            "VALUES (#{id},#{ownerId},#{targetType},#{contentId},#{state},#{title},#{now}) " +
            "ON DUPLICATE KEY UPDATE state=VALUES(state),title_snapshot=VALUES(title_snapshot),favorited_at=VALUES(favorited_at)")
    int upsertFavorite(@Param("id") String id, @Param("ownerId") String ownerId, @Param("targetType") String targetType,
                       @Param("contentId") String contentId, @Param("state") String state, @Param("title") String title,
                       @Param("now") Instant now);

    @Select("SELECT id FROM knowledge_item WHERE owner_id=#{ownerId} AND bookmark_content_id=#{contentId} AND state<>'deleted' LIMIT 1")
    String selectContentKnowledgeId(@Param("ownerId") String ownerId, @Param("contentId") String contentId);

    @Insert("INSERT INTO knowledge_item (id,owner_id,item_type,title,body,search_text,learning_status,verification_status," +
            "source_content_id,source_content_version_id,bookmark_content_id,version_no,visibility,state) " +
            "VALUES (#{id},#{ownerId},'content_ref',#{title},'',#{title},'learning','unverified',#{contentId},#{versionId},#{contentId},1,'private','active')")
    int insertContentKnowledge(@Param("id") String id, @Param("ownerId") String ownerId, @Param("contentId") String contentId,
                               @Param("versionId") String versionId, @Param("title") String title);

    @Update("UPDATE review_schedule SET state=#{state},due_date=CASE WHEN #{state}='active' THEN COALESCE(due_date,#{dueDate}) ELSE due_date END," +
            "version_no=version_no+1 WHERE owner_id=#{ownerId} AND knowledge_id=#{knowledgeId}")
    int updateReview(@Param("ownerId") String ownerId, @Param("knowledgeId") String knowledgeId,
                     @Param("state") String state, @Param("dueDate") java.time.LocalDate dueDate);

    @Insert("INSERT INTO review_schedule (id,owner_id,knowledge_id,state,stage,due_date,version_no) " +
            "VALUES (#{id},#{ownerId},#{knowledgeId},'active',0,#{dueDate},1)")
    int insertReview(@Param("id") String id, @Param("ownerId") String ownerId,
                     @Param("knowledgeId") String knowledgeId, @Param("dueDate") java.time.LocalDate dueDate);

    @Insert("INSERT INTO word_notebook (id,owner_id,content_id,word_key_hash,state,added_at) " +
            "SELECT #{id},#{ownerId},id,word_key_hash,#{state},#{now} FROM learning_content WHERE id=#{contentId} AND content_type='word' " +
            "ON DUPLICATE KEY UPDATE state=VALUES(state),added_at=VALUES(added_at)")
    int upsertWordNotebook(@Param("id") String id, @Param("ownerId") String ownerId, @Param("contentId") String contentId,
                           @Param("state") String state, @Param("now") Instant now);

    @Update("UPDATE learning_record SET familiarity_percent=#{percent},last_feedback_at=#{now},version_no=version_no+1 " +
            "WHERE owner_id=#{ownerId} AND content_id=#{contentId} AND version_no=#{expectedVersion}")
    int updateFamiliarity(@Param("ownerId") String ownerId,@Param("contentId") String contentId,
                          @Param("percent") int percent,@Param("expectedVersion") int expectedVersion,@Param("now") Instant now);

    @Insert("INSERT INTO learning_record (id,owner_id,content_id,learning_key,last_version_id,learning_status,familiarity_percent,last_feedback_at,version_no) " +
            "SELECT #{id},#{ownerId},lc.id,CASE lc.content_type WHEN 'word' THEN CONCAT('word:',lc.id) " +
            "WHEN 'english_article' THEN CONCAT('article:',lc.id) ELSE CONCAT('tech:',lc.id) END,cv.id,'learning',#{percent},#{now},1 " +
            "FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE lc.id=#{contentId} AND lc.state='published'")
    int insertFamiliarity(@Param("id") String id,@Param("ownerId") String ownerId,@Param("contentId") String contentId,
                          @Param("percent") int percent,@Param("now") Instant now);
}
