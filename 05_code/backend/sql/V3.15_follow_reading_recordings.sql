-- V3.15 跟读录音：OSS 私有对象元数据关联；每位用户每篇短文仅保留最新录音。
CREATE TABLE IF NOT EXISTS follow_recording (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  content_id CHAR(32) NOT NULL,
  content_version_id CHAR(32) NOT NULL,
  asset_id CHAR(32) NOT NULL,
  duration_ms INT NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  deleted_at TIMESTAMP(3) NULL,
  UNIQUE KEY uk_follow_recording_owner_content (owner_id, content_id),
  KEY idx_follow_recording_asset (asset_id, state),
  KEY idx_follow_recording_owner_state (owner_id, state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
