package com.zhixing.mapper;

import com.zhixing.model.ContentCoverageView;
import com.zhixing.model.AdminTechnicalContentView;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AdminContentMapper {
    @Select("SELECT COUNT(*) FROM learning_content WHERE state='published'") Integer countPublished();
    @Select("SELECT COUNT(*) FROM content_source WHERE enabled=1") Integer countSources();
    @Select("SELECT t.name,COUNT(DISTINCT lc.id) AS item_count FROM learning_topic t JOIN content_topic ct ON ct.topic_id=t.id " +
            "JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.published_version_id=cv.id " +
            "WHERE lc.content_type='tech' AND lc.state='published' GROUP BY t.id,t.name ORDER BY t.name")
    List<ContentCoverageView.CoverageItem> selectTechnicalTopics();
    @Select("SELECT CASE stage WHEN 'primary' THEN '小学' WHEN 'junior' THEN '初中' WHEN 'senior' THEN '高中' ELSE '未分级' END AS name," +
            "COUNT(*) AS item_count FROM learning_content WHERE content_type='word' AND state='published' GROUP BY stage ORDER BY stage")
    List<ContentCoverageView.CoverageItem> selectWordStages();
    @Select("SELECT cv.difficulty AS name," +
            "COUNT(*) AS item_count FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE lc.content_type='english_article' AND lc.state='published' GROUP BY cv.difficulty ORDER BY cv.difficulty")
    List<ContentCoverageView.CoverageItem> selectArticleDifficulties();

    @Select({"<script>","SELECT COUNT(*) FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id ",
            "WHERE lc.content_type='tech' AND lc.state='published' ",
            "<if test=\"topic != null and topic != ''\">AND EXISTS(SELECT 1 FROM content_topic ct JOIN learning_topic t ON t.id=ct.topic_id WHERE ct.content_version_id=cv.id AND t.name=#{topic}) </if>",
            "<if test=\"keyword != null and keyword != ''\">AND (cv.title LIKE CONCAT('%',#{keyword},'%') OR cv.summary LIKE CONCAT('%',#{keyword},'%') OR cv.body LIKE CONCAT('%',#{keyword},'%')) </if>",
            "</script>"})
    int countTechnical(@Param("topic")String topic,@Param("keyword")String keyword);

    @Select({"<script>","SELECT lc.id content_id,cv.id version_id,cv.version_no,cv.title,cv.summary,cv.difficulty,cv.estimated_seconds,cv.review_status,",
            "lc.published_at,cs.name source_name,cs.source_type FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id ",
            "JOIN content_source cs ON cs.id=lc.source_id WHERE lc.content_type='tech' AND lc.state='published' ",
            "<if test=\"topic != null and topic != ''\">AND EXISTS(SELECT 1 FROM content_topic ct JOIN learning_topic t ON t.id=ct.topic_id WHERE ct.content_version_id=cv.id AND t.name=#{topic}) </if>",
            "<if test=\"keyword != null and keyword != ''\">AND (cv.title LIKE CONCAT('%',#{keyword},'%') OR cv.summary LIKE CONCAT('%',#{keyword},'%') OR cv.body LIKE CONCAT('%',#{keyword},'%')) </if>",
            "ORDER BY lc.published_at DESC,cv.title LIMIT #{offset},#{limit}","</script>"})
    List<AdminTechnicalContentView> selectTechnical(@Param("topic")String topic,@Param("keyword")String keyword,@Param("offset")int offset,@Param("limit")int limit);

    @Select("SELECT lc.id content_id,cv.id version_id,cv.version_no,cv.title,cv.summary,cv.body,cv.difficulty,cv.estimated_seconds,cv.review_status,"+
            "lc.published_at,cv.origin_url,cv.origin_author,cv.origin_published_at,cv.license_snapshot,cv.created_at version_created_at,"+
            "cs.name source_name,cs.source_type,cs.url source_url FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id "+
            "JOIN content_source cs ON cs.id=lc.source_id WHERE lc.id=#{contentId} AND lc.content_type='tech' AND lc.state='published'")
    AdminTechnicalContentView selectTechnicalDetail(@Param("contentId")String contentId);

    @Select("SELECT t.name FROM content_topic ct JOIN learning_topic t ON t.id=ct.topic_id WHERE ct.content_version_id=#{versionId} ORDER BY t.name")
    List<String> selectTechnicalTopicNames(@Param("versionId")String versionId);
}
