-- V3.16.9: account-level new-word presentation and pronunciation preferences.
-- Additive and idempotent; preferences are soft-deleted only.

CREATE TABLE IF NOT EXISTS user_word_study_preference (
  owner_id CHAR(32) NOT NULL,
  default_answer_mode VARCHAR(16) NOT NULL DEFAULT 'visible',
  auto_play_enabled SMALLINT NOT NULL DEFAULT 1,
  auto_play_accent VARCHAR(8) NOT NULL DEFAULT 'uk',
  auto_play_count SMALLINT NOT NULL DEFAULT 3,
  auto_play_interval_ms INT NOT NULL DEFAULT 1500,
  row_version INT NOT NULL DEFAULT 1,
  del_is SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (owner_id),
  KEY idx_user_word_study_preference_active (del_is,updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
