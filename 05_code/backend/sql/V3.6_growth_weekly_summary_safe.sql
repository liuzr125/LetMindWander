-- V3.6 成长模块非破坏性迁移（MySQL 5.7.25）
-- 生产及已有数据环境使用本文件；不会 DROP 任何业务表。

CREATE TABLE IF NOT EXISTS `weekly_summary` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `week_start` DATE NOT NULL,
  `current_revision_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
  `confirmed_revision_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
  `source_fingerprint` BINARY(32) NOT NULL,
  `is_stale` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1` (`owner_id`,`week_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='周总结主记录';

CREATE TABLE IF NOT EXISTS `weekly_revision` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `summary_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision_no` INT UNSIGNED NOT NULL,
  `metrics_json` JSON NOT NULL,
  `body` TEXT NULL,
  `sources_json` JSON NOT NULL,
  `source_fingerprint` BINARY(32) NOT NULL,
  `ai_job_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL,
  `confirmed_at` DATETIME(3) NULL DEFAULT NULL,
  `invalidated_at` DATETIME(3) NULL DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_1` (`summary_id`,`revision_no`),
  KEY `idx_2` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='周总结修订';

-- weekly_action 已存在时仅补缺失字段，不重建、不丢数据。
SET @growth_schema = DATABASE();
SET @growth_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=@growth_schema AND table_name='weekly_action')
  AND NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=@growth_schema AND table_name='weekly_action' AND column_name='confirmed_at'),
  'ALTER TABLE `weekly_action` ADD COLUMN `confirmed_at` DATETIME(3) NULL DEFAULT NULL COMMENT ''确认时间'' AFTER `state`',
  'SELECT ''weekly_action.confirmed_at already exists or weekly_action is absent'''
);
PREPARE growth_stmt FROM @growth_sql;
EXECUTE growth_stmt;
DEALLOCATE PREPARE growth_stmt;
