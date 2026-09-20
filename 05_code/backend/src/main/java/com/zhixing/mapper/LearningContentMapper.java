package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.LearningContentEntity;
import com.zhixing.model.LearningListItemView;
import com.zhixing.model.LearningTopicView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;

/** Learning-page reads stay in the Mapper layer; services only validate and coordinate. */
@Mapper
public interface LearningContentMapper extends BaseMapper<LearningContentEntity> {
    @Select({"<script>",
            "SELECT lc.id AS content_id,cv.id AS version_id,lc.content_type,cv.title,cv.summary,cv.difficulty,lc.stage,",
            "cv.word_term,COALESCE(NULLIF(TRIM(cv.phonetic),''),(SELECT pf.phonetic FROM pronunciation pf WHERE pf.content_version_id=cv.id AND pf.state='ready' AND pf.phonetic IS NOT NULL AND TRIM(pf.phonetic)&lt;&gt;'' ORDER BY CASE pf.accent WHEN 'uk' THEN 0 WHEN 'us' THEN 1 ELSE 2 END,pf.id LIMIT 1),'') AS phonetic,cv.meaning,cv.example_text,cv.estimated_seconds,lc.published_at,lr.learning_status,lr.familiarity_percent,",
            "CASE WHEN lr.learning_status IN ('understood','mastered') THEN TRUE ELSE FALSE END AS understood,",
            "CASE WHEN rs.state='active' THEN TRUE ELSE FALSE END AS in_review,CASE WHEN wn.state='active' THEN TRUE ELSE FALSE END AS in_word_book,",
            "<choose><when test=\"keyword != null and keyword != ''\">CASE WHEN EXISTS (SELECT 1 FROM word_alias wam WHERE wam.content_id=lc.id AND wam.state='active' AND wam.normalized_alias=LOWER(TRIM(#{keyword}))) THEN TRUE ELSE FALSE END AS alias_match,</when><otherwise>FALSE AS alias_match,</otherwise></choose>",
            "(SELECT MIN(lt.name) FROM content_topic ct JOIN learning_topic lt ON lt.id=ct.topic_id AND lt.state='active' WHERE ct.content_version_id=cv.id) AS topic_name,",
            "CASE WHEN lc.content_type='english_article' AND cv.body IS NOT NULL AND TRIM(cv.body)&lt;&gt;'' ",
            "THEN LENGTH(TRIM(cv.body))-LENGTH(REPLACE(TRIM(cv.body),' ',''))+1 ELSE 0 END AS word_count ",
            "FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id ",
            "LEFT JOIN learning_record lr ON lr.owner_id=#{ownerId} AND lr.content_id=lc.id ",
            "LEFT JOIN word_notebook wn ON wn.owner_id=#{ownerId} AND wn.content_id=lc.id ",
            "LEFT JOIN knowledge_item ki ON ki.owner_id=#{ownerId} AND ki.bookmark_content_id=lc.id AND ki.state&lt;&gt;'deleted' ",
            "LEFT JOIN review_schedule rs ON rs.owner_id=#{ownerId} AND rs.knowledge_id=ki.id ",
            "WHERE lc.state='published' AND lc.content_type=#{type} ",
            "<if test=\"type == 'english_article'\">AND EXISTS (SELECT 1 FROM english_article_book eab JOIN user_vocabulary_book uvb ON uvb.book_id=eab.book_id AND uvb.owner_id=#{ownerId} AND uvb.state='active' JOIN vocabulary_book active_book ON active_book.id=uvb.book_id AND active_book.state='active' WHERE eab.content_id=lc.id) </if>",
            "<if test=\"difficulty != null and difficulty != ''\">AND cv.difficulty=#{difficulty} </if>",
            "<if test=\"stage != null and stage != ''\">AND lc.stage=#{stage} </if>",
            "<if test=\"topicId != null and topicId != ''\">AND EXISTS (SELECT 1 FROM content_topic x WHERE x.content_version_id=cv.id AND x.topic_id=#{topicId}) </if>",
            "<if test=\"notebook\">AND wn.state='active' </if>",
            "<if test=\"status == 'review'\">AND rs.state='active' AND rs.due_date&lt;=#{today} </if>",
            "<if test=\"status == 'familiar'\">AND COALESCE(lr.familiarity_percent,0)&gt;=80 </if>",
            "<if test=\"keyword != null and keyword != ''\">AND (LOWER(cv.word_term) LIKE CONCAT('%',LOWER(TRIM(#{keyword})),'%') OR LOWER(cv.meaning) LIKE CONCAT('%',LOWER(TRIM(#{keyword})),'%') OR EXISTS (SELECT 1 FROM word_alias wa WHERE wa.content_id=lc.id AND wa.state='active' AND wa.normalized_alias=LOWER(TRIM(#{keyword})))) </if>",
            "ORDER BY <if test=\"keyword != null and keyword != ''\">CASE WHEN LOWER(cv.word_term)=LOWER(TRIM(#{keyword})) THEN 0 WHEN EXISTS (SELECT 1 FROM word_alias wa2 WHERE wa2.content_id=lc.id AND wa2.state='active' AND wa2.normalized_alias=LOWER(TRIM(#{keyword}))) THEN 1 ELSE 2 END,</if> lc.published_at DESC,lc.id DESC ",
            "<if test=\"!allRows\">LIMIT #{limit} OFFSET #{offset}</if>",
            "</script>"})
    List<LearningListItemView> selectLearningPage(@Param("ownerId") String ownerId,@Param("type") String type,
            @Param("topicId") String topicId,@Param("difficulty") String difficulty,@Param("stage") String stage,
            @Param("notebook") boolean notebook,@Param("status") String status,@Param("keyword") String keyword,
            @Param("today") LocalDate today,@Param("allRows") boolean allRows,@Param("offset") int offset,@Param("limit") int limit);

    @Select("SELECT DISTINCT lt.id,lt.name FROM learning_topic lt JOIN content_topic ct ON ct.topic_id=lt.id " +
            "JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.published_version_id=cv.id " +
            "WHERE lt.state='active' AND (lt.scope_key='system' OR lt.owner_id=#{ownerId}) AND lc.content_type='tech' AND lc.state='published' ORDER BY lt.name")
    List<LearningTopicView> selectTopics(@Param("ownerId") String ownerId);

    @Select("SELECT COUNT(*) FROM word_notebook wn JOIN learning_content lc ON lc.id=wn.content_id " +
            "WHERE wn.owner_id=#{ownerId} AND wn.state='active' AND lc.state='published' AND lc.content_type='word'")
    int selectNotebookTotal(@Param("ownerId") String ownerId);

    @Select("SELECT COUNT(DISTINCT wn.id) FROM word_notebook wn JOIN learning_content lc ON lc.id=wn.content_id " +
            "JOIN knowledge_item ki ON ki.owner_id=wn.owner_id AND ki.bookmark_content_id=wn.content_id AND ki.state<>'deleted' " +
            "JOIN review_schedule rs ON rs.owner_id=wn.owner_id AND rs.knowledge_id=ki.id " +
            "WHERE wn.owner_id=#{ownerId} AND wn.state='active' AND lc.state='published' AND rs.state='active' AND rs.due_date<=#{today}")
    int selectNotebookDue(@Param("ownerId") String ownerId,@Param("today") LocalDate today);
}
