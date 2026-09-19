-- V3.16.2 词库导入自动化暂存层。
-- 先在备份后的测试库执行；正式词库仅接受 publish 阶段通过门禁的结果。

CREATE TABLE IF NOT EXISTS vocabulary_dataset_catalog (
  id CHAR(32) NOT NULL PRIMARY KEY, dataset_code VARCHAR(64) NOT NULL, dataset_name VARCHAR(160) NOT NULL,
  provider_name VARCHAR(160) NOT NULL, source_type VARCHAR(32) NOT NULL, source_url VARCHAR(2048),
  data_format VARCHAR(32) NOT NULL, parser_code VARCHAR(64) NOT NULL, version_label VARCHAR(64) NOT NULL,
  license_status VARCHAR(32) NOT NULL DEFAULT 'unknown', license_note VARCHAR(1000) NOT NULL,
  checksum BINARY(32), item_count INT NOT NULL DEFAULT 0, source_payload LONGTEXT, enabled TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_vocabulary_dataset_code (dataset_code), KEY idx_vocabulary_dataset_available (enabled,license_status,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vocabulary_import_batch (
  id CHAR(32) NOT NULL PRIMARY KEY, dataset_id CHAR(32) NOT NULL, target_book_id CHAR(32) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'draft', current_step VARCHAR(32) NOT NULL DEFAULT 'created', options_json JSON NOT NULL,
  total_count INT NOT NULL DEFAULT 0, success_count INT NOT NULL DEFAULT 0, failed_count INT NOT NULL DEFAULT 0,
  reused_count INT NOT NULL DEFAULT 0, created_count INT NOT NULL DEFAULT 0, alias_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0, review_count INT NOT NULL DEFAULT 0, sql_object_key VARCHAR(512),
  manifest_object_key VARCHAR(512), report_object_key VARCHAR(512), last_error_code VARCHAR(64), last_error_message VARCHAR(500),
  created_by CHAR(32) NOT NULL, started_at DATETIME(3), finished_at DATETIME(3),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_vocabulary_import_batch_state (state,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS vocabulary_import_item (
  id CHAR(32) NOT NULL PRIMARY KEY, batch_id CHAR(32) NOT NULL, row_no INT NOT NULL, raw_json JSON NOT NULL,
  word_term VARCHAR(80), normalized_word VARCHAR(80), word_key_hash BINARY(32), decision VARCHAR(32) NOT NULL DEFAULT 'pending',
  content_id CHAR(32), review_status VARCHAR(32) NOT NULL DEFAULT 'pending', risk_flags_json JSON, error_code VARCHAR(64), error_message VARCHAR(500),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_vocabulary_import_item_row (batch_id,row_no), KEY idx_vocabulary_import_item_batch (batch_id,decision,review_status,row_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tts_generation_task (
  id CHAR(32) NOT NULL PRIMARY KEY, batch_id CHAR(32) NOT NULL, item_id CHAR(32) NOT NULL, target_type VARCHAR(16) NOT NULL,
  sense_no INT NOT NULL DEFAULT 1, accent VARCHAR(16) NOT NULL, provider_code VARCHAR(32) NOT NULL, model_code VARCHAR(120), voice VARCHAR(64) NOT NULL,
  generation_mode VARCHAR(32) NOT NULL DEFAULT 'plain', phoneme_alphabet VARCHAR(16), phoneme_value VARCHAR(500), object_key VARCHAR(512) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'pending', attempt_count SMALLINT NOT NULL DEFAULT 0, asset_id CHAR(32), error_code VARCHAR(64), error_message VARCHAR(500),
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_tts_generation_object (object_key), KEY idx_tts_generation_retry (batch_id,state,attempt_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
