-- V3.19 AI 价格同步：补充定时/手动运行历史和可持久化任务开关。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS `ai_pricing_sync_run` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `trigger_key` VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'running',
  `changed_model_count` INT UNSIGNED NOT NULL DEFAULT 0,
  `error_code` VARCHAR(80) NULL,
  `error_message` VARCHAR(1000) NULL,
  `started_at` DATETIME(3) NOT NULL,
  `finished_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_pricing_sync_trigger` (`trigger_key`),
  KEY `idx_ai_pricing_sync_run_history` (`created_at`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC
  COMMENT='DeepSeek 官网价格同步的定时与手动运行账本';

INSERT INTO `app_parameter` (`id`,`param_key`,`param_value`,`is_secret`,`description`,`state`,`del_is`,`version_no`) VALUES
('e000000000000000000000000000001a','ai.pricing.deepseek.enabled','true',0,
 'DeepSeek 官网价格同步定时任务开关；false 时 22:00 不访问官网且不更新价格版本','active',0,1)
ON DUPLICATE KEY UPDATE `description`=VALUES(`description`),`state`='active',`del_is`=0;
