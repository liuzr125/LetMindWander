-- 仅用于确认无业务数据的本地开发空库。
-- 会删除周总结及其修订；生产、测试共享库和已有数据环境禁止执行。

DROP TABLE IF EXISTS `weekly_revision`;
DROP TABLE IF EXISTS `weekly_summary`;

CREATE TABLE `weekly_summary` (
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

CREATE TABLE `weekly_revision` (
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
