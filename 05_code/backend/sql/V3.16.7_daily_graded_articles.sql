-- V3.16.7 每日分词书英语短文：联网主题只作选题索引，正文由系统原创生成。
-- 可重复执行；不在 SQL 中保存模型凭据。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS `english_article_book` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `book_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `generated_date` DATE NOT NULL,
  `slot_no` TINYINT UNSIGNED NOT NULL,
  `target_words_json` JSON NOT NULL,
  `topic_snapshot_json` JSON NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_book_daily_slot` (`book_id`,`generated_date`,`slot_no`),
  KEY `idx_article_book_content` (`content_id`,`book_id`),
  KEY `idx_article_book_daily` (`generated_date`,`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
  COMMENT='英语短文与目标词书关系；每本词书每日使用可配置固定槽位';

CREATE TABLE IF NOT EXISTS `english_article_generation_run` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trigger_key` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `run_date` DATE NOT NULL,
  `state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'running',
  `book_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `generated_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `skipped_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `failed_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `error_message` VARCHAR(1000) NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_generation_trigger` (`trigger_key`),
  KEY `idx_article_generation_date` (`run_date`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
  COMMENT='每日分词书英语短文生成运行日志';

INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`license_note`,`enabled`) VALUES
('e0000000000000000000000000000017','知行日课每日分词书英语短文','ai_original',NULL,
 '系统仅参考官方 RSS 的标题和受限摘要选题；英文正文按词书目标词原创生成，不复制第三方正文。',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`source_type`=VALUES(`source_type`),
 `license_note`=VALUES(`license_note`),`enabled`=1;

INSERT INTO `app_parameter` (`id`,`param_key`,`param_value`,`is_secret`,`description`,`state`,`del_is`,`version_no`) VALUES
('e0000000000000000000000000000018','article_generation.per_book_count','5',0,
 '每日分词书英语短文：每本有效词书每天的目标篇数（1-20）','active',0,1),
('e0000000000000000000000000000019','article_generation.enabled','true',0,
 '每日分词书英语短文定时任务开关；false 时到点不执行且不调用模型','active',0,1)
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`state`='active',`del_is`=0;
