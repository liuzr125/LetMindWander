package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.ArticleTranslationPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface ArticleTranslationPersistenceMapper
    extends BaseMapper<ArticleTranslationPersistenceEntity> {
  @Select(
      "SELECT cv.id,cv.title,cv.title_translation FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id WHERE lc.id=#{id} AND lc.content_type='english_article' AND lc.state='published'")
  Map<String, Object> selectPublishedArticleTitle(@Param("id") Object id);

  @Update(
      "UPDATE content_version SET title_translation=#{titleTranslation},updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND (title_translation IS NULL OR title_translation='')")
  int fillMissingTitleTranslation(
      @Param("titleTranslation") Object titleTranslation, @Param("id") Object id);

  @Select(
      "SELECT cv.id,cv.body,cv.article_blocks FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id WHERE lc.id=#{id} AND lc.content_type='english_article' AND lc.state='published'")
  Map<String, Object> selectPublishedArticleBlocks(@Param("id") Object id);

  @Update(
      "UPDATE content_version SET article_blocks=#{articleBlocks},updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int updateArticleBlocks(@Param("articleBlocks") Object articleBlocks, @Param("id") Object id);
}
