-- V3.13 回滚：会删除 V3.13 预置短文及 TTS 日志，仅在明确需要回退时执行。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
START TRANSACTION;
DELETE ct FROM content_topic ct JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id WHERE lc.source_id='e0000000000000000000000000000013';
DELETE FROM content_version WHERE content_id IN (SELECT id FROM learning_content WHERE source_id='e0000000000000000000000000000013');
DELETE FROM learning_content WHERE source_id='e0000000000000000000000000000013';
DELETE FROM content_source WHERE id='e0000000000000000000000000000013';
DELETE FROM app_parameter WHERE param_key IN ('tts.aliyun.endpoint','tts.aliyun.voice','tts.aliyun.sample_rate');
COMMIT;
DROP TABLE IF EXISTS tts_usage_log;
ALTER TABLE content_version DROP COLUMN article_audio_asset_id,DROP COLUMN article_audio_voice,DROP COLUMN article_audio_generated_at;
