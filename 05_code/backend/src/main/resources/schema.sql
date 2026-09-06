CREATE TABLE IF NOT EXISTS app_user (
  id CHAR(32) NOT NULL PRIMARY KEY,
  seq_no INT,
  short_id VARCHAR(16),
  wx_app_id VARCHAR(32) NOT NULL,
  wx_open_id VARCHAR(64) NOT NULL,
  nickname VARCHAR(30) NOT NULL,
  mobile VARCHAR(20),
  avatar_url VARCHAR(2048),
  real_name VARCHAR(50),
  english_name VARCHAR(100),
  birthday DATE,
  gender TINYINT NOT NULL DEFAULT 0,
  hobbies_json VARCHAR(2048),
  introduction VARCHAR(500),
  profile_visibility VARCHAR(1000),
  row_version INT NOT NULL DEFAULT 1,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  current_plan_id CHAR(32),
  ai_consent_version VARCHAR(40),
  ai_consented_at TIMESTAMP(3),
  last_login_at TIMESTAMP(3),
  mobile_bound_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_app_user_wechat UNIQUE (wx_app_id, wx_open_id),
  CONSTRAINT uk_app_user_seq_no UNIQUE (seq_no),
  CONSTRAINT uk_app_user_short_id UNIQUE (short_id)
);

CREATE TABLE IF NOT EXISTS user_consent (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  document_version VARCHAR(40) NOT NULL,
  decision VARCHAR(32) NOT NULL,
  occurred_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_consent_owner ON user_consent (owner_id, purpose, occurred_at);

CREATE TABLE IF NOT EXISTS invite_code (
  id CHAR(32) NOT NULL PRIMARY KEY,
  code_hash BINARY(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'available',
  expires_at TIMESTAMP(3) NOT NULL,
  redeemed_by CHAR(32),
  redeemed_at TIMESTAMP(3),
  created_by CHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_invite_code_hash UNIQUE (code_hash)
);

CREATE INDEX IF NOT EXISTS idx_invite_status_expires ON invite_code (status, expires_at);

CREATE TABLE IF NOT EXISTS admission_counter (
  id CHAR(32) NOT NULL PRIMARY KEY,
  scope_code VARCHAR(32) NOT NULL DEFAULT 'trial',
  invited_limit SMALLINT NOT NULL DEFAULT 20,
  invited_used SMALLINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_admission_scope UNIQUE (scope_code)
);

CREATE TABLE IF NOT EXISTS auth_session (
  id CHAR(32) NOT NULL PRIMARY KEY,
  principal_type VARCHAR(32) NOT NULL,
  principal_id CHAR(32) NOT NULL,
  token_hash BINARY(32) NOT NULL,
  expires_at TIMESTAMP(3) NOT NULL,
  last_seen_at TIMESTAMP(3) NOT NULL,
  revoked_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_auth_token UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS idx_auth_principal ON auth_session (principal_type, principal_id);
CREATE INDEX IF NOT EXISTS idx_auth_expires ON auth_session (expires_at);

CREATE TABLE IF NOT EXISTS learning_plan (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  version_no INT NOT NULL,
  effective_date DATE NOT NULL,
  daily_budget_min SMALLINT NOT NULL DEFAULT 10,
  weekdays_mask SMALLINT NOT NULL DEFAULT 31,
  topic_mask SMALLINT NOT NULL DEFAULT 1,
  difficulty VARCHAR(32) NOT NULL DEFAULT 'intro',
  tech_count SMALLINT NOT NULL DEFAULT 1,
  new_word_count SMALLINT NOT NULL DEFAULT 3,
  journal_enabled SMALLINT NOT NULL DEFAULT 1,
  review_enabled SMALLINT NOT NULL DEFAULT 1,
  review_limit SMALLINT NOT NULL DEFAULT 5,
  is_paused SMALLINT NOT NULL DEFAULT 0,
  pause_until DATE,
  change_reason VARCHAR(200),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_learning_plan_version UNIQUE (owner_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_effective ON learning_plan (owner_id, effective_date, version_no);

CREATE TABLE IF NOT EXISTS learning_topic (
  id CHAR(32) NOT NULL PRIMARY KEY,
  scope_key VARCHAR(40) NOT NULL,
  owner_id CHAR(32),
  name VARCHAR(50) NOT NULL,
  normalized_name VARCHAR(50) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_learning_topic_scope_name UNIQUE (scope_key, normalized_name)
);

CREATE TABLE IF NOT EXISTS learning_plan_topic (
  id CHAR(32) NOT NULL PRIMARY KEY,
  plan_id CHAR(32) NOT NULL,
  topic_id CHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_learning_plan_topic UNIQUE (plan_id, topic_id)
);

CREATE TABLE IF NOT EXISTS learning_content (
  id CHAR(32) NOT NULL PRIMARY KEY,
  content_type VARCHAR(16) NOT NULL,
  topic_id CHAR(32),
  title VARCHAR(100) NOT NULL,
  difficulty VARCHAR(32) NOT NULL DEFAULT 'intro',
  estimated_seconds INT NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'published',
  sort_no INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_learning_content_pick ON learning_content (content_type, state, difficulty, sort_no);

CREATE TABLE IF NOT EXISTS knowledge_item (
  id CHAR(32) NOT NULL PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  estimated_seconds INT NOT NULL,
  next_review_date DATE NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'active',
  sort_no INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_knowledge_item_review ON knowledge_item (state, next_review_date, sort_no);

CREATE TABLE IF NOT EXISTS weekly_action (
  id CHAR(32) NOT NULL PRIMARY KEY,
  title VARCHAR(100) NOT NULL,
  estimated_seconds INT NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'active',
  sort_no INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_weekly_action_pick ON weekly_action (state, sort_no);

CREATE TABLE IF NOT EXISTS daily_package (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  business_date DATE NOT NULL,
  plan_id CHAR(32) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'draft',
  is_temporary SMALLINT NOT NULL DEFAULT 0,
  version_no INT NOT NULL DEFAULT 1,
  budget_seconds INT NOT NULL,
  original_count SMALLINT,
  current_count SMALLINT NOT NULL DEFAULT 0,
  final_done_count SMALLINT NOT NULL DEFAULT 0,
  cutoff_total_count SMALLINT,
  cutoff_done_count SMALLINT,
  cutoff_at TIMESTAMP(3),
  metrics_frozen_at TIMESTAMP(3),
  activated_at TIMESTAMP(3),
  gap_json VARCHAR(4000),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_daily_package_owner_date UNIQUE (owner_id, business_date)
);
CREATE INDEX IF NOT EXISTS idx_daily_package_state_date ON daily_package (state, business_date);

CREATE TABLE IF NOT EXISTS package_revision (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  package_id CHAR(32) NOT NULL,
  version_no INT NOT NULL,
  reason VARCHAR(32) NOT NULL,
  before_json VARCHAR(4000),
  after_json VARCHAR(4000) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_package_revision_version UNIQUE (package_id, version_no)
);
CREATE INDEX IF NOT EXISTS idx_package_revision_owner ON package_revision (owner_id);

CREATE TABLE IF NOT EXISTS daily_task (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  package_id CHAR(32) NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  title_snapshot VARCHAR(100) NOT NULL,
  target_key VARCHAR(96) NOT NULL,
  content_id CHAR(32), content_version_id CHAR(32), knowledge_id CHAR(32), journal_id CHAR(32), action_id CHAR(32),
  status VARCHAR(32) NOT NULL DEFAULT 'TODO',
  estimated_seconds INT NOT NULL,
  sort_no SMALLINT NOT NULL,
  cancel_reason VARCHAR(32),
  started_at TIMESTAMP(3), completed_at TIMESTAMP(3),
  version_no INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_daily_task_target UNIQUE (package_id, task_type, target_key)
);
CREATE INDEX IF NOT EXISTS idx_daily_task_owner_status ON daily_task (owner_id, status);
CREATE INDEX IF NOT EXISTS idx_daily_task_content_status ON daily_task (content_id, status);
CREATE INDEX IF NOT EXISTS idx_daily_task_knowledge ON daily_task (knowledge_id);

CREATE TABLE IF NOT EXISTS task_event (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  task_id CHAR(32) NOT NULL,
  task_version INT NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  from_status VARCHAR(32),
  to_status VARCHAR(32) NOT NULL,
  occurred_at TIMESTAMP(3) NOT NULL,
  reason VARCHAR(200),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_task_event_version UNIQUE (task_id, task_version)
);
CREATE INDEX IF NOT EXISTS idx_task_event_owner_time ON task_event (owner_id, occurred_at);

CREATE TABLE IF NOT EXISTS media_asset (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32),
  purpose VARCHAR(32) NOT NULL,
  object_key VARCHAR(512) NOT NULL,
  mime_type VARCHAR(80) NOT NULL,
  byte_size BIGINT NOT NULL,
  sha256 BINARY(32) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'pending',
  origin_url VARCHAR(2048),
  license_note VARCHAR(1000),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_media_owner ON media_asset (owner_id, purpose, state);

-- 运行参数：按当前部署要求明文保存。应用不提供对外读取接口；数据库账户应最小授权。
CREATE TABLE IF NOT EXISTS app_parameter (
  id CHAR(32) NOT NULL PRIMARY KEY,
  param_key VARCHAR(100) NOT NULL,
  param_value VARCHAR(4096) NOT NULL,
  is_secret SMALLINT NOT NULL DEFAULT 1,
  description VARCHAR(200) NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'active',
  version_no INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_app_parameter_key UNIQUE (param_key)
);

CREATE TABLE IF NOT EXISTS friend_relation (
 id CHAR(32) NOT NULL PRIMARY KEY,
 user_low_id CHAR(32) NOT NULL,
 user_high_id CHAR(32) NOT NULL,
 state VARCHAR(32) NOT NULL DEFAULT 'none',
 generation INT NOT NULL DEFAULT 0,
 version_no INT NOT NULL DEFAULT 1,
 accepted_at TIMESTAMP(3), removed_at TIMESTAMP(3),
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_friend_pair UNIQUE(user_low_id, user_high_id)
);
