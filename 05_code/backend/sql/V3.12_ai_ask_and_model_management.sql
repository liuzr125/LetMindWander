-- V3.12 问一问、模型管理与费用记录（MySQL 5.7.25）
CREATE TABLE IF NOT EXISTS `ai_model_config` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `provider_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `model_code` VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `display_name` VARCHAR(100) NOT NULL,
  `specification` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'flash/pro/自定义规格',
  `base_url` VARCHAR(500) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `api_key_param_key` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '仅引用 app_parameter 的密钥键',
  `enabled` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `is_default` TINYINT UNSIGNED NOT NULL DEFAULT 0,
  `max_output_tokens` SMALLINT UNSIGNED NOT NULL DEFAULT 1200,
  `timeout_seconds` SMALLINT UNSIGNED NOT NULL DEFAULT 60,
  `version_no` INT UNSIGNED NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ai_model_config` (`provider_code`,`model_code`),
  KEY `idx_ai_model_available` (`enabled`,`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='AI 模型运行配置';

INSERT INTO `ai_model_config` (`id`,`provider_code`,`model_code`,`display_name`,`specification`,`base_url`,`api_key_param_key`,`enabled`,`is_default`,`max_output_tokens`,`timeout_seconds`)
SELECT '00000000000000000000000000000031','deepseek','deepseek-flash','DeepSeek Flash','flash','https://api.deepseek.com/chat/completions','AI_DEEPSEEK_API_KEY',1,1,1200,60
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_model_config` WHERE `provider_code`='deepseek' AND `model_code`='deepseek-flash');

INSERT INTO `ai_model_config` (`id`,`provider_code`,`model_code`,`display_name`,`specification`,`base_url`,`api_key_param_key`,`enabled`,`is_default`,`max_output_tokens`,`timeout_seconds`)
SELECT '00000000000000000000000000000032','deepseek','deepseek-v4-pro','DeepSeek Pro','pro','https://api.deepseek.com/chat/completions','AI_DEEPSEEK_API_KEY',1,0,1200,60
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_model_config` WHERE `provider_code`='deepseek' AND `model_code`='deepseek-v4-pro');

-- 价格为 0 表示尚未由管理员核实；后端会阻止调用，避免使用过期价格导致费用失真。
INSERT INTO `ai_model_price` (`id`,`provider_code`,`model_code`,`version_no`,`currency`,`input_per_million`,`output_per_million`,`pricing_json`,`effective_at`)
SELECT '00000000000000000000000000000041','deepseek','deepseek-flash',1,'CNY',0,0,JSON_OBJECT('status','pending_review','source','https://api-docs.deepseek.com/quick_start/pricing/'),CURRENT_TIMESTAMP(3)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_model_price` WHERE `provider_code`='deepseek' AND `model_code`='deepseek-flash');

INSERT INTO `ai_model_price` (`id`,`provider_code`,`model_code`,`version_no`,`currency`,`input_per_million`,`output_per_million`,`pricing_json`,`effective_at`)
SELECT '00000000000000000000000000000042','deepseek','deepseek-v4-pro',1,'CNY',0,0,JSON_OBJECT('status','pending_review','source','https://api-docs.deepseek.com/quick_start/pricing/'),CURRENT_TIMESTAMP(3)
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `ai_model_price` WHERE `provider_code`='deepseek' AND `model_code`='deepseek-v4-pro');
