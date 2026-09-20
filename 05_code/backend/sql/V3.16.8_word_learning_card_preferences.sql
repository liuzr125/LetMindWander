-- V3.16.8: per-card-type coverage plus account-level display preferences.
-- Additive and idempotent. No physical deletion and no generated semantic claims.

CREATE TABLE IF NOT EXISTS word_learning_card_type_coverage (
  content_version_id CHAR(32) NOT NULL,
  card_type VARCHAR(24) NOT NULL,
  requirement_level VARCHAR(24) NOT NULL,
  published_count INT NOT NULL DEFAULT 0,
  coverage_status VARCHAR(32) NOT NULL,
  status_note VARCHAR(300) NOT NULL,
  generator_version VARCHAR(40) NOT NULL,
  del_is SMALLINT NOT NULL DEFAULT 0,
  generated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (content_version_id,card_type),
  KEY idx_word_card_type_coverage_status (card_type,del_is,coverage_status),
  KEY idx_word_card_type_coverage_generator (generator_version,generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS user_word_learning_card_preference (
  owner_id CHAR(32) NOT NULL,
  card_type VARCHAR(24) NOT NULL,
  enabled SMALLINT NOT NULL DEFAULT 1,
  row_version INT NOT NULL DEFAULT 1,
  del_is SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (owner_id,card_type),
  KEY idx_user_word_card_preference_owner (owner_id,del_is,enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
