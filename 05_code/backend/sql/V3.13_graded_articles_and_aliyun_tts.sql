-- 知行日课 V3.13：分级英语短文库 + 阿里云 TTS 音频缓存（MySQL 5.7.25）
-- 可重复执行；不在 SQL 中保存 AppKey、AccessKey 或 Token。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

DROP PROCEDURE IF EXISTS ensure_v313_column;
DELIMITER $$
CREATE PROCEDURE ensure_v313_column(IN p_table VARCHAR(64),IN p_column VARCHAR(64),IN p_definition VARCHAR(1000))
BEGIN
  IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_column) THEN
    SET @ddl=CONCAT('ALTER TABLE `',p_table,'` ADD COLUMN `',p_column,'` ',p_definition);
    PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL ensure_v313_column('content_version','article_audio_asset_id','CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''整篇朗读音频 media_asset.id''');
CALL ensure_v313_column('content_version','article_audio_voice','VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''合成音色；音色变更后重新生成''');
CALL ensure_v313_column('content_version','article_audio_generated_at','DATETIME(3) NULL COMMENT ''整篇音频生成时间 UTC''');
DROP PROCEDURE ensure_v313_column;

CREATE TABLE IF NOT EXISTS `tts_usage_log` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `target_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'english_article/word',
  `target_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_version_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'aliyun_nls',
  `voice` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `char_count` INT UNSIGNED NOT NULL,
  `state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'succeeded/failed',
  `provider_request_id` VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `asset_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `error_code` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_tts_usage_time` (`created_at`),
  KEY `idx_tts_usage_target` (`target_type`,`target_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='语音合成调用日志；不保存原文和凭据';

INSERT INTO `app_parameter` (`id`,`param_key`,`param_value`,`is_secret`,`description`,`state`,`version_no`) VALUES
(MD5('tts.aliyun.endpoint'),'tts.aliyun.endpoint','https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts',0,'阿里云 NLS 语音合成 HTTPS 地址','active',1),
(MD5('tts.aliyun.voice'),'tts.aliyun.voice','aixia',0,'阿里云 NLS 默认英语音色','active',1),
(MD5('tts.aliyun.sample_rate'),'tts.aliyun.sample_rate','16000',0,'阿里云 NLS 采样率','active',1)
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`state`='active';

INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`license_note`,`enabled`) VALUES
('e0000000000000000000000000000013','知行日课分级英语短文','manual',NULL,'原创分级学习短文，可在本产品内展示、朗读与跟读',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`license_note`=VALUES(`license_note`),`enabled`=1;

DROP PROCEDURE IF EXISTS seed_v313_articles;
DELIMITER $$
CREATE PROCEDURE seed_v313_articles()
BEGIN
  DECLARE n INT DEFAULT 1;
  DECLARE person_en VARCHAR(20); DECLARE person_zh VARCHAR(20);
  DECLARE place_en VARCHAR(40); DECLARE place_zh VARCHAR(40);
  DECLARE action_en VARCHAR(120); DECLARE action_zh VARCHAR(120);
  DECLARE result_en VARCHAR(120); DECLARE result_zh VARCHAR(120);
  DECLARE cid CHAR(32); DECLARE vid CHAR(32); DECLARE title_en VARCHAR(100);
  DECLARE text_en VARCHAR(1000); DECLARE text_zh VARCHAR(1000); DECLARE diff VARCHAR(32);

  WHILE n<=202 DO
    SET diff=IF(n<=101,'intro','advanced');
    SET person_en=ELT(1+MOD(n-1,11),'Mia','Leo','Anna','Ben','Lily','Tom','Nina','Jack','Emma','Ryan','Lucy');
    SET person_zh=ELT(1+MOD(n-1,11),'米娅','利奥','安娜','本','莉莉','汤姆','妮娜','杰克','艾玛','瑞恩','露西');
    SET place_en=ELT(1+MOD(FLOOR((n-1)/11),10),'library','park','kitchen','classroom','garden','station','shop','museum','home','playground');
    SET place_zh=ELT(1+MOD(FLOOR((n-1)/11),10),'图书馆','公园','厨房','教室','花园','车站','商店','博物馆','家里','操场');
    SET action_en=ELT(1+MOD(FLOOR((n-1)/7),10),'reads a short note','watches a small change','follows three clear steps','asks one useful question','compares two simple ideas','checks a map carefully','writes a new sentence','explains a picture','cleans a quiet corner','practices with a friend');
    SET action_zh=ELT(1+MOD(FLOOR((n-1)/7),10),'读一则短笔记','观察一个小变化','按照三个清晰步骤操作','提出一个有用的问题','比较两个简单观点','仔细查看地图','写下一个新句子','解释一幅图','收拾一个安静的角落','和朋友一起练习');
    SET result_en=ELT(1+MOD(FLOOR((n-1)/5),10),'finds one helpful detail','remembers the main idea','finishes the task calmly','shares a clear answer','learns from a small mistake','chooses the next step','feels ready to continue','helps another learner','records a useful example','ends the day with confidence');
    SET result_zh=ELT(1+MOD(FLOOR((n-1)/5),10),'找到一个有帮助的细节','记住了主要观点','从容地完成任务','分享一个清晰的答案','从一个小错误中学习','选好下一步','准备好继续前进','帮助了另一位学习者','记录一个有用的例子','充满信心地结束一天');
    IF diff='intro' THEN
      SET title_en=CONCAT(person_en,' at the ',UPPER(LEFT(place_en,1)),SUBSTRING(place_en,2),' ',LPAD(n,3,'0'));
      SET text_en=CONCAT('Today, ',person_en,' visits the ',place_en,'. ',person_en,' ',action_en,'. Before leaving, ',person_en,' ',result_en,'.');
      SET text_zh=CONCAT('今天，',person_zh,'来到',place_zh,'。',person_zh,action_zh,'。离开前，',person_zh,result_zh,'。');
    ELSE
      SET title_en=CONCAT('A Careful Decision ',LPAD(n-101,3,'0'),': ',UPPER(LEFT(place_en,1)),SUBSTRING(place_en,2));
      SET text_en=CONCAT('While working in the ',place_en,', ',person_en,' ',action_en,'. Instead of accepting the first result, ',person_en,' checks the evidence and ',result_en,'. The careful process makes the next decision easier to explain.');
      SET text_zh=CONCAT('在',place_zh,'工作时，',person_zh,action_zh,'。',person_zh,'没有直接接受第一个结果，而是核对证据，并',result_zh,'。这个严谨过程让下一个决定更容易解释。');
    END IF;
    SET cid=MD5(CONCAT('v313:article:',diff,':',n));
    SET vid=MD5(CONCAT('v313:article-version:',diff,':',n));
    INSERT INTO learning_content(id,content_type,source_id,dedup_hash,state,current_version_id,published_version_id,published_at,row_version)
    VALUES(cid,'english_article','e0000000000000000000000000000013',UNHEX(SHA2(CONCAT('v313:',diff,':',n),256)),'published',vid,vid,UTC_TIMESTAMP(3),1)
    ON DUPLICATE KEY UPDATE state='published',current_version_id=VALUES(current_version_id),published_version_id=VALUES(published_version_id),published_at=COALESCE(published_at,VALUES(published_at));
    INSERT INTO content_version(id,content_id,version_no,title,summary,body,difficulty,estimated_seconds,origin_author,license_snapshot,body_hash,review_status,reviewed_at,created_by,article_blocks)
    VALUES(vid,cid,1,title_en,IF(diff='intro','入门分级英语短文','进阶分级英语短文'),text_en,diff,IF(diff='intro',75,120),'知行日课','原创分级学习短文',UNHEX(SHA2(text_en,256)),'approved',UTC_TIMESTAMP(3),'00000000000000000000000000000002',JSON_ARRAY(JSON_OBJECT('paragraph_id','p1','text',text_en,'translation',text_zh,'words',JSON_ARRAY())))
    ON DUPLICATE KEY UPDATE title=VALUES(title),summary=VALUES(summary),body=VALUES(body),difficulty=VALUES(difficulty),estimated_seconds=VALUES(estimated_seconds),article_blocks=VALUES(article_blocks),review_status='approved';
    INSERT INTO content_topic(id,content_version_id,topic_id)
    SELECT MD5(CONCAT('v313:topic:',diff,':',n)),vid,t.id FROM learning_topic t WHERE t.scope_key='system' AND t.normalized_name='技术英语' LIMIT 1
    ON DUPLICATE KEY UPDATE topic_id=VALUES(topic_id);
    SET n=n+1;
  END WHILE;
END$$
DELIMITER ;

START TRANSACTION;
CALL seed_v313_articles();
COMMIT;
DROP PROCEDURE seed_v313_articles;

-- 验收：intro 和 advanced 均应不少于 101 篇。
SELECT cv.difficulty,COUNT(*) AS article_count
FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id
WHERE lc.content_type='english_article' AND lc.state='published'
GROUP BY cv.difficulty ORDER BY cv.difficulty;
