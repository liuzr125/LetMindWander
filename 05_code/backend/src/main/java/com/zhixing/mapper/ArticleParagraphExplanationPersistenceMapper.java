package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.ArticleParagraphExplanationPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface ArticleParagraphExplanationPersistenceMapper
    extends BaseMapper<ArticleParagraphExplanationPersistenceEntity> {
  @Select(
      "SELECT cv.id,cv.body,cv.article_blocks FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id WHERE lc.id=#{id} AND lc.content_type='english_article' AND lc.state='published' AND cv.review_status='approved'")
  List<Map<String, Object>> selectPublishedArticle(@Param("id") Object id);

  @Insert(
      "INSERT INTO article_paragraph_explanation(id,content_version_id,paragraph_id,source_hash,state,claim_token,lease_until) VALUES(#{id},#{contentVersionId},#{paragraphId},#{sourceHash.bytes,jdbcType=BINARY},'generating',#{claimToken},#{leaseUntil})")
  int insertGeneratingExplanation(
      @Param("id") Object id,
      @Param("contentVersionId") Object contentVersionId,
      @Param("paragraphId") Object paragraphId,
      @Param("sourceHash") com.zhixing.mapper.type.BinaryHash sourceHash,
      @Param("claimToken") Object claimToken,
      @Param("leaseUntil") java.sql.Timestamp leaseUntil);

  @Select(
      "SELECT id,state,explanation,lease_until FROM article_paragraph_explanation WHERE content_version_id=#{contentVersionId} AND paragraph_id=#{paragraphId} AND source_hash=#{sourceHash.bytes,jdbcType=BINARY}")
  List<Map<String, Object>> selectExplanationBySource(
      @Param("contentVersionId") Object contentVersionId,
      @Param("paragraphId") Object paragraphId,
      @Param("sourceHash") com.zhixing.mapper.type.BinaryHash sourceHash);

  @Update(
      "UPDATE article_paragraph_explanation SET claim_token=#{claimToken},lease_until=#{leaseUntil},updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND state='generating' AND lease_until<#{leaseUntilValue4}")
  int claimExpiredExplanation(
      @Param("claimToken") Object claimToken,
      @Param("leaseUntil") java.sql.Timestamp leaseUntil,
      @Param("id") Object id,
      @Param("leaseUntilValue4") Object leaseUntilValue4);

  @Update(
      "UPDATE article_paragraph_explanation SET explanation=#{explanation},state='ready',claim_token=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=#{id} AND claim_token=#{claimToken} AND state='generating'")
  int completeClaimedExplanation(
      @Param("explanation") Object explanation,
      @Param("id") Object id,
      @Param("claimToken") Object claimToken);

  @Delete(
      "DELETE FROM article_paragraph_explanation WHERE id=#{id} AND claim_token=#{claimToken} AND state='generating'")
  int deleteFailedClaim(@Param("id") Object id, @Param("claimToken") Object claimToken);
}
