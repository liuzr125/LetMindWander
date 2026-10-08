package com.zhixing.mapper;

import com.zhixing.model.AdminTechnicalContentView;
import com.zhixing.model.ContentCoverageView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminContentMapper {
  @Select("SELECT COUNT(*) FROM learning_content WHERE state='published'")
  Integer countPublished();

  @Select("SELECT COUNT(*) FROM content_source WHERE enabled=1")
  Integer countSources();

  @Select(
      "SELECT t.name,COUNT(DISTINCT lc.id) AS item_count FROM learning_topic t LEFT JOIN content_topic ct ON ct.topic_id=t.id "
          + "LEFT JOIN learning_content lc ON lc.published_version_id=ct.content_version_id AND lc.content_type='tech' AND lc.state='published' "
          + "WHERE t.scope_key='system' AND t.del_is=0 AND t.state='active' AND t.topic_type='technical' GROUP BY t.id,t.name ORDER BY t.name")
  List<ContentCoverageView.CoverageItem> selectTechnicalTopics();

  @Select(
      "SELECT CASE stage WHEN 'primary' THEN '小学' WHEN 'junior' THEN '初中' WHEN 'senior' THEN '高中' ELSE '未分级' END AS name,"
          + "COUNT(*) AS item_count FROM learning_content WHERE content_type='word' AND state='published' GROUP BY stage ORDER BY stage")
  List<ContentCoverageView.CoverageItem> selectWordStages();

  @Select(
      "SELECT cv.difficulty AS name,"
          + "COUNT(*) AS item_count FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id "
          + "WHERE lc.content_type='english_article' AND lc.state='published' GROUP BY cv.difficulty ORDER BY cv.difficulty")
  List<ContentCoverageView.CoverageItem> selectArticleDifficulties();

  @Select({
    "<script>",
    "SELECT COUNT(*) FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id JOIN content_source cs ON cs.id=lc.source_id ",
    "WHERE lc.content_type='tech' AND lc.state='published' ",
    "<if test=\"startAt != null\">AND COALESCE(cv.origin_published_at,lc.published_at) &gt;= #{startAt} </if>",
    "<if test=\"endAt != null\">AND COALESCE(cv.origin_published_at,lc.published_at) &lt; #{endAt} </if>",
    "<if test=\"topic != null and topic != ''\">AND EXISTS(SELECT 1 FROM content_topic ct JOIN learning_topic t ON t.id=ct.topic_id WHERE ct.content_version_id=cv.id AND t.del_is=0 AND t.name=#{topic}) </if>",
    "<if test=\"keyword != null and keyword != ''\">AND (cv.title LIKE CONCAT('%',#{keyword},'%') OR cv.summary LIKE CONCAT('%',#{keyword},'%') OR cv.body LIKE CONCAT('%',#{keyword},'%')) </if>",
    "</script>"
  })
  int countTechnical(
      @Param("topic") String topic,
      @Param("keyword") String keyword,
      @Param("startAt") java.sql.Timestamp startAt,
      @Param("endAt") java.sql.Timestamp endAt);

  @Select({
    "<script>",
    "SELECT lc.id content_id,cv.id version_id,cv.version_no,cv.title,cv.summary,cv.difficulty,cv.estimated_seconds,cv.review_status,",
    "lc.published_at,cv.origin_published_at,cs.name source_name,cs.source_type FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id ",
    "JOIN content_source cs ON cs.id=lc.source_id WHERE lc.content_type='tech' AND lc.state='published' ",
    "<if test=\"startAt != null\">AND COALESCE(cv.origin_published_at,lc.published_at) &gt;= #{startAt} </if>",
    "<if test=\"endAt != null\">AND COALESCE(cv.origin_published_at,lc.published_at) &lt; #{endAt} </if>",
    "<if test=\"topic != null and topic != ''\">AND EXISTS(SELECT 1 FROM content_topic ct JOIN learning_topic t ON t.id=ct.topic_id WHERE ct.content_version_id=cv.id AND t.del_is=0 AND t.name=#{topic}) </if>",
    "<if test=\"keyword != null and keyword != ''\">AND (cv.title LIKE CONCAT('%',#{keyword},'%') OR cv.summary LIKE CONCAT('%',#{keyword},'%') OR cv.body LIKE CONCAT('%',#{keyword},'%')) </if>",
    "ORDER BY CASE WHEN COALESCE(cv.origin_published_at,lc.published_at) IS NULL THEN 1 ELSE 0 END ASC,COALESCE(cv.origin_published_at,lc.published_at) ",
    "<choose><when test=\"sortOrder == 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>",
    ",cv.title ASC,lc.id ASC LIMIT #{offset},#{limit}",
    "</script>"
  })
  List<AdminTechnicalContentView> selectTechnical(
      @Param("topic") String topic,
      @Param("keyword") String keyword,
      @Param("startAt") java.sql.Timestamp startAt,
      @Param("endAt") java.sql.Timestamp endAt,
      @Param("sortOrder") String sortOrder,
      @Param("offset") int offset,
      @Param("limit") int limit);

  @Select(
      "SELECT lc.id content_id,cv.id version_id,cv.version_no,cv.title,cv.summary,cv.body,cv.difficulty,cv.estimated_seconds,cv.review_status,"
          + "lc.published_at,cv.origin_url,cv.origin_author,cv.origin_published_at,cv.license_snapshot,cv.created_at version_created_at,"
          + "cs.name source_name,cs.source_type,cs.url source_url FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id "
          + "JOIN content_source cs ON cs.id=lc.source_id WHERE lc.id=#{contentId} AND lc.content_type='tech' AND lc.state='published'")
  AdminTechnicalContentView selectTechnicalDetail(@Param("contentId") String contentId);

  @Select({
    "<script>",
    "SELECT COUNT(*) FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id ",
    "WHERE lc.content_type='english_article' AND lc.state='published' ",
    "<if test=\"difficulty != null and difficulty != ''\">AND cv.difficulty=#{difficulty} </if>",
    "<if test=\"bookId != null and bookId != ''\">AND (EXISTS(SELECT 1 FROM english_article_category eac WHERE eac.content_id=lc.id AND eac.book_id=#{bookId} AND eac.del_is=0) OR (NOT EXISTS(SELECT 1 FROM english_article_category marker WHERE marker.content_id=lc.id) AND EXISTS(SELECT 1 FROM english_article_book eab WHERE eab.content_id=lc.id AND eab.book_id=#{bookId}))) </if>",
    "<if test=\"keyword != null and keyword != ''\">AND (cv.title LIKE CONCAT('%',#{keyword},'%') OR cv.summary LIKE CONCAT('%',#{keyword},'%') OR cv.body LIKE CONCAT('%',#{keyword},'%')) </if>",
    "<if test=\"audioStatus == 'has_audio'\">AND EXISTS(SELECT 1 FROM media_asset ma WHERE ma.id=cv.article_audio_asset_id AND ma.state='ready') </if>",
    "<if test=\"audioStatus == 'missing_audio'\">AND NOT EXISTS(SELECT 1 FROM media_asset ma WHERE ma.id=cv.article_audio_asset_id AND ma.state='ready') </if>",
    "</script>"
  })
  int countArticles(
      @Param("difficulty") String difficulty,
      @Param("bookId") String bookId,
      @Param("keyword") String keyword,
      @Param("audioStatus") String audioStatus);

  @Select({
    "<script>",
    "SELECT lc.id content_id,cv.id version_id,cv.version_no,cv.title,cv.summary,cv.difficulty,cv.estimated_seconds,cv.review_status,cv.article_audio_asset_id,",
    "COALESCE((SELECT eac.book_id FROM english_article_category eac WHERE eac.content_id=lc.id AND eac.del_is=0 LIMIT 1),(SELECT eab.book_id FROM english_article_book eab JOIN vocabulary_book vb ON vb.id=eab.book_id WHERE eab.content_id=lc.id AND NOT EXISTS(SELECT 1 FROM english_article_category marker WHERE marker.content_id=lc.id) ORDER BY vb.sort_no,vb.id LIMIT 1)) audience_book_id,",
    "COALESCE((SELECT vb.book_name FROM english_article_category eac JOIN vocabulary_book vb ON vb.id=eac.book_id WHERE eac.content_id=lc.id AND eac.del_is=0 LIMIT 1),(SELECT vb.book_name FROM english_article_book eab JOIN vocabulary_book vb ON vb.id=eab.book_id WHERE eab.content_id=lc.id AND NOT EXISTS(SELECT 1 FROM english_article_category marker WHERE marker.content_id=lc.id) ORDER BY vb.sort_no,vb.id LIMIT 1)) audience_book_names,",
    "lc.published_at,cv.origin_published_at,cs.name source_name,cs.source_type FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id ",
    "JOIN content_source cs ON cs.id=lc.source_id WHERE lc.content_type='english_article' AND lc.state='published' ",
    "<if test=\"difficulty != null and difficulty != ''\">AND cv.difficulty=#{difficulty} </if>",
    "<if test=\"bookId != null and bookId != ''\">AND (EXISTS(SELECT 1 FROM english_article_category eac WHERE eac.content_id=lc.id AND eac.book_id=#{bookId} AND eac.del_is=0) OR (NOT EXISTS(SELECT 1 FROM english_article_category marker WHERE marker.content_id=lc.id) AND EXISTS(SELECT 1 FROM english_article_book eab WHERE eab.content_id=lc.id AND eab.book_id=#{bookId}))) </if>",
    "<if test=\"keyword != null and keyword != ''\">AND (cv.title LIKE CONCAT('%',#{keyword},'%') OR cv.summary LIKE CONCAT('%',#{keyword},'%') OR cv.body LIKE CONCAT('%',#{keyword},'%')) </if>",
    "<if test=\"audioStatus == 'has_audio'\">AND EXISTS(SELECT 1 FROM media_asset ma WHERE ma.id=cv.article_audio_asset_id AND ma.state='ready') </if>",
    "<if test=\"audioStatus == 'missing_audio'\">AND NOT EXISTS(SELECT 1 FROM media_asset ma WHERE ma.id=cv.article_audio_asset_id AND ma.state='ready') </if>",
    "ORDER BY cv.title,lc.id",
    "</script>"
  })
  List<AdminTechnicalContentView> selectArticles(
      @Param("difficulty") String difficulty,
      @Param("bookId") String bookId,
      @Param("keyword") String keyword,
      @Param("audioStatus") String audioStatus);

  @Select(
      "SELECT lc.id content_id,cv.id version_id,cv.version_no,cv.title,cv.summary,cv.body,cv.difficulty,cv.estimated_seconds,cv.review_status,cv.article_audio_asset_id,"
          + "COALESCE((SELECT eac.book_id FROM english_article_category eac WHERE eac.content_id=lc.id AND eac.del_is=0 LIMIT 1),(SELECT eab.book_id FROM english_article_book eab JOIN vocabulary_book vb ON vb.id=eab.book_id WHERE eab.content_id=lc.id AND NOT EXISTS(SELECT 1 FROM english_article_category marker WHERE marker.content_id=lc.id) ORDER BY vb.sort_no,vb.id LIMIT 1)) audience_book_id,"
          + "COALESCE((SELECT vb.book_name FROM english_article_category eac JOIN vocabulary_book vb ON vb.id=eac.book_id WHERE eac.content_id=lc.id AND eac.del_is=0 LIMIT 1),(SELECT vb.book_name FROM english_article_book eab JOIN vocabulary_book vb ON vb.id=eab.book_id WHERE eab.content_id=lc.id AND NOT EXISTS(SELECT 1 FROM english_article_category marker WHERE marker.content_id=lc.id) ORDER BY vb.sort_no,vb.id LIMIT 1)) audience_book_names,"
          + "lc.published_at,cv.origin_url,cv.origin_author,cv.origin_published_at,cv.license_snapshot,cv.created_at version_created_at,"
          + "cs.name source_name,cs.source_type,cs.url source_url FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id "
          + "JOIN content_source cs ON cs.id=lc.source_id WHERE lc.id=#{contentId} AND lc.content_type='english_article' AND lc.state='published'")
  AdminTechnicalContentView selectArticleDetail(@Param("contentId") String contentId);

  @Select(
      "SELECT t.name FROM content_topic ct JOIN learning_topic t ON t.id=ct.topic_id WHERE ct.content_version_id=#{versionId} AND t.del_is=0 ORDER BY t.name")
  List<String> selectTechnicalTopicNames(@Param("versionId") String versionId);

  int updateArticleMetadata(
      @Param("difficulty") String difficulty,
      @Param("estimatedSeconds") int estimatedSeconds,
      @Param("versionId") String versionId,
      @Param("contentId") String contentId);

  List<String> selectPublishedArticleVersionIds(@Param("contentId") String contentId);

  int countActiveBook(@Param("bookId") String bookId);

  int updateArticleCategory(
      @Param("bookId") String bookId,
      @Param("adminId") String adminId,
      @Param("deleted") int deleted,
      @Param("contentId") String contentId);

  int insertArticleCategory(
      @Param("contentId") String contentId,
      @Param("bookId") String bookId,
      @Param("adminId") String adminId,
      @Param("deleted") int deleted);
}
