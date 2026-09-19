-- 知行日课 V3.15 -> V3.16
-- MySQL 5.7.25；执行前请备份。时间字段继续按 UTC 写入。

CREATE TABLE IF NOT EXISTS `word_alias` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '32 位小写十六进制 ID',
  `content_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范词条 learning_content.id',
  `alias_term` VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始别名、错拼、旧拼写或缩写',
  `normalized_alias` VARCHAR(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'trim、lowercase、统一空白后的别名',
  `alias_hash` BINARY(32) NOT NULL COMMENT 'SHA-256(normalized_alias)',
  `alias_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'spelling_variant/misspelling/old_spelling/abbreviation/alternate_form',
  `state` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
  `source_note` VARCHAR(500) NULL COMMENT '来源及人工审核说明',
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word_alias_hash` (`alias_hash`),
  KEY `idx_word_alias_content` (`content_id`,`state`),
  KEY `idx_word_alias_normalized` (`normalized_alias`,`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci ROW_FORMAT=DYNAMIC COMMENT='单词别名与规范词映射';

-- V3.16 对发音的约束由业务与验收共同保证：
-- 1. 正式单词音频 target_key 使用 sense:<sense_id>，且 sense_id 非空；
-- 2. 例句音频 target_key 使用 example:<example_id>，且 example_id 非空；
-- 3. 多词性或异读词不得以首条发音作为其他词义的兜底。
