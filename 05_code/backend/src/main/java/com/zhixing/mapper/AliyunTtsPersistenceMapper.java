package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AliyunTtsPersistenceEntity;
import java.util.*;
import org.apache.ibatis.annotations.*;

/** Bound persistence statements; transactions remain in the service layer. */
@Mapper
public interface AliyunTtsPersistenceMapper extends BaseMapper<AliyunTtsPersistenceEntity> {
  @Select(
      "SELECT id,target_type,target_id,provider_code,voice,char_count,state,provider_request_id,asset_id,error_code,created_at FROM tts_usage_log ORDER BY created_at DESC LIMIT 100")
  List<Map<String, Object>> selectRecentUsageLogs();

  @Select(
      "SELECT p.asset_id FROM pronunciation p JOIN media_asset m ON m.id=p.asset_id AND m.state='ready' WHERE p.content_version_id=#{contentVersionId} AND p.target_key=#{targetKey} AND p.accent='tts' AND p.state='ready' LIMIT 1")
  List<String> selectReadyTtsAssets(
      @Param("contentVersionId") Object contentVersionId, @Param("targetKey") Object targetKey);

  @Insert(
      "INSERT INTO pronunciation(id,content_version_id,target_key,accent,asset_id,state) VALUES(#{id},#{contentVersionId},#{targetKey},'tts',#{assetId},'ready') ON DUPLICATE KEY UPDATE asset_id=VALUES(asset_id),state='ready',updated_at=CURRENT_TIMESTAMP")
  int upsertTtsPronunciation(
      @Param("id") Object id,
      @Param("contentVersionId") Object contentVersionId,
      @Param("targetKey") Object targetKey,
      @Param("assetId") Object assetId);

  @Update(
      "UPDATE content_version SET article_audio_asset_id=#{articleAudioAssetId},article_audio_voice=#{articleAudioVoice},article_audio_generated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int updateArticleAudio(
      @Param("articleAudioAssetId") Object articleAudioAssetId,
      @Param("articleAudioVoice") Object articleAudioVoice,
      @Param("id") Object id);

  @Update(
      "UPDATE article_word_glossary SET audio_asset_id=#{audioAssetId},audio_voice=#{audioVoice},audio_generated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE id=#{id}")
  int updateGlossaryAudio(
      @Param("audioAssetId") Object audioAssetId,
      @Param("audioVoice") Object audioVoice,
      @Param("id") Object id);

  @Delete(
      "DELETE FROM pronunciation WHERE content_version_id=#{contentVersionId} AND target_key=#{targetKey}")
  int deleteTargetPronunciation(
      @Param("contentVersionId") Object contentVersionId, @Param("targetKey") Object targetKey);

  @Insert(
      "INSERT INTO pronunciation(id,content_version_id,target_key,accent,asset_id,state) VALUES(#{id},#{contentVersionId},#{targetKey},#{accent},#{assetId},'ready')")
  int insertTargetPronunciation(
      @Param("id") Object id,
      @Param("contentVersionId") Object contentVersionId,
      @Param("targetKey") Object targetKey,
      @Param("accent") Object accent,
      @Param("assetId") Object assetId);

  @Select(
      "SELECT 'glossary_word' content_type,id version_id,id glossary_id,term word_term,audio_asset_id glossary_audio_asset_id,audio_voice glossary_audio_voice FROM article_word_glossary WHERE term=#{term}")
  List<Map<String, Object>> selectGlossaryContent(@Param("term") Object term);

  @Select(
      "SELECT lc.content_type,cv.id version_id,cv.body,cv.word_term,cv.article_audio_asset_id,cv.article_audio_voice FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id WHERE lc.id=#{id} AND lc.state='published'")
  List<Map<String, Object>> selectPublishedContent(@Param("id") Object id);

  @Select(
      "SELECT p.asset_id FROM pronunciation p JOIN media_asset m ON m.id=p.asset_id AND m.state='ready' WHERE p.content_version_id=#{contentVersionId} AND p.target_key=#{targetKey} AND p.state='ready' LIMIT 1")
  List<String> selectReadyTargetAssets(
      @Param("contentVersionId") Object contentVersionId, @Param("targetKey") Object targetKey);

  @Select("SELECT COUNT(*) FROM media_asset WHERE id=#{id} AND state='ready'")
  Integer countReadyMedia(@Param("id") Object id);

  @Insert(
      "INSERT INTO tts_usage_log(id,owner_id,target_type,target_id,content_version_id,provider_code,voice,char_count,state,provider_request_id,asset_id,error_code) VALUES(#{id},#{ownerId},#{targetType},#{targetId},#{contentVersionId},'aliyun_nls',#{voice},#{charCount},#{state},#{providerRequestId},#{assetId},#{errorCode})")
  int insertUsageLog(
      @Param("id") Object id,
      @Param("ownerId") Object ownerId,
      @Param("targetType") Object targetType,
      @Param("targetId") Object targetId,
      @Param("contentVersionId") Object contentVersionId,
      @Param("voice") Object voice,
      @Param("charCount") Object charCount,
      @Param("state") Object state,
      @Param("providerRequestId") Object providerRequestId,
      @Param("assetId") Object assetId,
      @Param("errorCode") Object errorCode);
}
