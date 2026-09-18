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

CREATE TABLE IF NOT EXISTS content_source (
  id CHAR(32) NOT NULL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  source_type VARCHAR(32) NOT NULL DEFAULT 'manual',
  url VARCHAR(2048), license_note VARCHAR(1000) NOT NULL,
  enabled SMALLINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 测试库与生产 V3.0 保持同一内容版本口径，避免 Mapper 在 H2 通过、MySQL 失败或反之。
CREATE TABLE IF NOT EXISTS learning_content (
  id CHAR(32) NOT NULL PRIMARY KEY,
  content_type VARCHAR(32) NOT NULL,
  source_id CHAR(32) NOT NULL,
  dedup_hash BINARY(32) NOT NULL,
  origin_url_hash BINARY(32),
  word_key_hash BINARY(32),
  stage VARCHAR(32),
  state VARCHAR(32) NOT NULL DEFAULT 'draft',
  current_version_id CHAR(32),
  published_version_id CHAR(32),
  published_at TIMESTAMP(3),
  withdrawn_at TIMESTAMP(3),
  withdraw_reason VARCHAR(1000),
  row_version INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_learning_content_dedup UNIQUE (dedup_hash),
  CONSTRAINT uk_learning_content_word UNIQUE (word_key_hash)
);
CREATE INDEX IF NOT EXISTS idx_learning_content_pick ON learning_content (content_type, state, published_at);
CREATE INDEX IF NOT EXISTS idx_learning_content_stage ON learning_content (content_type, stage, state, published_at);

CREATE TABLE IF NOT EXISTS content_version (
  id CHAR(32) NOT NULL PRIMARY KEY,
  content_id CHAR(32) NOT NULL,
  version_no INT NOT NULL,
  title VARCHAR(100) NOT NULL,
  summary VARCHAR(500), body CLOB,
  difficulty VARCHAR(32) NOT NULL DEFAULT 'intro',
  estimated_seconds INT NOT NULL DEFAULT 180,
  word_term VARCHAR(80), phonetic VARCHAR(200), meaning VARCHAR(500),
  example_text VARCHAR(1000), example_translation VARCHAR(1000),
  origin_url VARCHAR(2048), origin_author VARCHAR(200), origin_published_at TIMESTAMP(3),
  license_snapshot VARCHAR(1000) NOT NULL,
  body_hash BINARY(32) NOT NULL,
  review_status VARCHAR(32) NOT NULL DEFAULT 'draft',
  article_blocks CLOB,
  article_audio_asset_id CHAR(32),
  article_audio_voice VARCHAR(64),
  article_audio_generated_at TIMESTAMP(3),
  created_by CHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_content_version UNIQUE (content_id, version_no)
);

CREATE TABLE IF NOT EXISTS content_topic (
  id CHAR(32) NOT NULL PRIMARY KEY,
  content_version_id CHAR(32) NOT NULL,
  topic_id CHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_content_topic UNIQUE (content_version_id, topic_id)
);

CREATE TABLE IF NOT EXISTS learning_record (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  content_id CHAR(32) NOT NULL,
  learning_key VARCHAR(96) NOT NULL,
  last_version_id CHAR(32) NOT NULL,
  learning_status VARCHAR(32) NOT NULL DEFAULT 'learning',
  first_completed_at TIMESTAMP(3), last_feedback_at TIMESTAMP(3),
  review_opt_out SMALLINT NOT NULL DEFAULT 0,
  version_no INT NOT NULL DEFAULT 1,
  familiarity_percent SMALLINT,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_learning_record UNIQUE (owner_id, learning_key)
);

CREATE TABLE IF NOT EXISTS user_favorite (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL, target_type VARCHAR(32) NOT NULL, target_id CHAR(32) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'active', title_snapshot VARCHAR(200) NOT NULL,
  favorited_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_favorite UNIQUE (owner_id,target_type,target_id)
);

CREATE TABLE IF NOT EXISTS knowledge_item (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  item_type VARCHAR(32) NOT NULL,
  title VARCHAR(100) NOT NULL, body CLOB NOT NULL, problem_json CLOB, search_text CLOB NOT NULL,
  learning_status VARCHAR(32) NOT NULL DEFAULT 'unlearned',
  verification_status VARCHAR(32) NOT NULL DEFAULT 'unverified',
  last_verified_date DATE,
  mastered_at TIMESTAMP(3),
  reuse_count INT NOT NULL DEFAULT 0,
  last_reused_at TIMESTAMP(3),
  source_content_id CHAR(32), source_content_version_id CHAR(32),
  source_journal_id CHAR(32), source_journal_revision INT, source_ai_job_id CHAR(32),
  bookmark_content_id CHAR(32), word_key_hash BINARY(32),
  version_no INT NOT NULL DEFAULT 1,
  visibility VARCHAR(32) NOT NULL DEFAULT 'private',
  state VARCHAR(32) NOT NULL DEFAULT 'active',
  note_parent_id CHAR(32),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_knowledge_bookmark UNIQUE (owner_id,bookmark_content_id),
  CONSTRAINT uk_knowledge_word UNIQUE (owner_id,word_key_hash)
);

CREATE TABLE IF NOT EXISTS knowledge_revision (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  knowledge_id CHAR(32) NOT NULL,
  revision_no INT NOT NULL,
  snapshot_json CLOB NOT NULL,
  change_kind VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_knowledge_revision UNIQUE (knowledge_id, revision_no)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_revision_owner ON knowledge_revision (owner_id);

CREATE TABLE IF NOT EXISTS knowledge_tag (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  knowledge_id CHAR(32) NOT NULL,
  tag_name VARCHAR(20) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_knowledge_tag UNIQUE (knowledge_id, tag_name)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_tag_owner_name ON knowledge_tag (owner_id, tag_name);

CREATE TABLE IF NOT EXISTS review_schedule (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL, knowledge_id CHAR(32) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'active', stage SMALLINT NOT NULL DEFAULT 0,
  due_date DATE, last_feedback_id CHAR(32), version_no INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_review_schedule UNIQUE (owner_id,knowledge_id)
);

CREATE TABLE IF NOT EXISTS review_feedback (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL, knowledge_id CHAR(32) NOT NULL, schedule_id CHAR(32) NOT NULL,
  business_date DATE NOT NULL, feedback VARCHAR(32) NOT NULL,
  before_stage SMALLINT NOT NULL, before_state VARCHAR(32) NOT NULL, before_due_date DATE,
  after_stage SMALLINT NOT NULL, after_state VARCHAR(32) NOT NULL, after_due_date DATE,
  revision_no INT NOT NULL DEFAULT 1, task_id CHAR(32), effective_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_review_feedback_day UNIQUE (owner_id,knowledge_id,business_date)
);

CREATE TABLE IF NOT EXISTS word_sense (
  id CHAR(32) NOT NULL PRIMARY KEY, content_version_id CHAR(32) NOT NULL,
  part_of_speech VARCHAR(32) NOT NULL, meaning VARCHAR(1000) NOT NULL, sort_no INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_sense UNIQUE (content_version_id,sort_no)
);
CREATE TABLE IF NOT EXISTS word_example (
  id CHAR(32) NOT NULL PRIMARY KEY, sense_id CHAR(32) NOT NULL,
  sentence VARCHAR(1000) NOT NULL, translation VARCHAR(1000) NOT NULL, sort_no INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_example UNIQUE (sense_id,sort_no)
);
CREATE TABLE IF NOT EXISTS pronunciation (
  id CHAR(32) NOT NULL PRIMARY KEY,
  content_version_id CHAR(32) NOT NULL,
  sense_id CHAR(32), example_id CHAR(32), target_key VARCHAR(40) NOT NULL,
  accent VARCHAR(32) NOT NULL, phonetic VARCHAR(200), asset_id CHAR(32) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'ready',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_pronunciation_target UNIQUE (content_version_id,target_key,accent)
);
CREATE TABLE IF NOT EXISTS word_notebook (
  id CHAR(32) NOT NULL PRIMARY KEY, owner_id CHAR(32) NOT NULL, content_id CHAR(32) NOT NULL,
  word_key_hash BINARY(32) NOT NULL, state VARCHAR(32) NOT NULL DEFAULT 'active',
  added_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_notebook UNIQUE (owner_id,word_key_hash)
);

-- V3.1 英语多词库：单词内容仍以 learning_content 为唯一事实源，词书只保存成员关系。
CREATE TABLE IF NOT EXISTS vocabulary_book (
  id CHAR(32) NOT NULL PRIMARY KEY,
  book_code VARCHAR(40) NOT NULL,
  book_name VARCHAR(100) NOT NULL,
  book_type VARCHAR(32) NOT NULL,
  level_code VARCHAR(32),
  description VARCHAR(500),
  source_name VARCHAR(200),
  source_url VARCHAR(2048),
  license_note VARCHAR(1000),
  word_count INT NOT NULL DEFAULT 0,
  sort_no INT NOT NULL DEFAULT 0,
  is_recommended SMALLINT NOT NULL DEFAULT 0,
  state VARCHAR(32) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_vocabulary_book_code UNIQUE (book_code)
);
CREATE INDEX IF NOT EXISTS idx_vocabulary_book_type_state ON vocabulary_book (book_type,state,sort_no);

CREATE TABLE IF NOT EXISTS vocabulary_book_word (
  id CHAR(32) NOT NULL PRIMARY KEY,
  book_id CHAR(32) NOT NULL,
  content_id CHAR(32) NOT NULL,
  sort_no INT NOT NULL DEFAULT 0,
  importance SMALLINT NOT NULL DEFAULT 0,
  is_core SMALLINT NOT NULL DEFAULT 0,
  source_level VARCHAR(32),
  source_ref VARCHAR(200),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_vocabulary_book_word UNIQUE (book_id,content_id)
);
CREATE INDEX IF NOT EXISTS idx_vbw_content ON vocabulary_book_word (content_id,book_id);
CREATE INDEX IF NOT EXISTS idx_vbw_order ON vocabulary_book_word (book_id,sort_no,id);

CREATE TABLE IF NOT EXISTS user_vocabulary_book (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  book_id CHAR(32) NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'active',
  daily_new_limit SMALLINT NOT NULL DEFAULT 3,
  selected_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  paused_at TIMESTAMP(3),
  completed_at TIMESTAMP(3),
  last_studied_at TIMESTAMP(3),
  row_version INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_user_vocabulary_book UNIQUE (owner_id,book_id)
);
CREATE INDEX IF NOT EXISTS idx_uvb_owner ON user_vocabulary_book (owner_id,state,updated_at);

-- V3.1 英语记忆训练：公共提示和题目只读取已审核版本，用户作答与原学习/复习状态分开记录。
CREATE TABLE IF NOT EXISTS word_memory_hint (
  id CHAR(32) NOT NULL PRIMARY KEY,
  content_version_id CHAR(32) NOT NULL,
  sense_id CHAR(32),
  method_type VARCHAR(32) NOT NULL,
  hint_body VARCHAR(1000) NOT NULL,
  level_code VARCHAR(32),
  source_type VARCHAR(32) NOT NULL DEFAULT 'editorial',
  state VARCHAR(32) NOT NULL DEFAULT 'draft',
  hint_version INT NOT NULL DEFAULT 1,
  withdrawn_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_memory_hint UNIQUE (content_version_id,sense_id,method_type,hint_version)
);
CREATE INDEX IF NOT EXISTS idx_word_memory_hint_pick ON word_memory_hint (content_version_id,state,method_type);

CREATE TABLE IF NOT EXISTS word_memory_question (
  id CHAR(32) NOT NULL PRIMARY KEY,
  content_version_id CHAR(32) NOT NULL,
  sense_id CHAR(32),
  dimension VARCHAR(32) NOT NULL,
  prompt_text VARCHAR(1000) NOT NULL,
  expected_answer VARCHAR(1000) NOT NULL,
  accepted_answers_json CLOB,
  answer_policy VARCHAR(32) NOT NULL DEFAULT 'exact',
  hint_text VARCHAR(1000),
  audio_required SMALLINT NOT NULL DEFAULT 0,
  state VARCHAR(32) NOT NULL DEFAULT 'draft',
  question_version INT NOT NULL DEFAULT 1,
  withdrawn_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_memory_question UNIQUE (content_version_id,sense_id,dimension,question_version)
);
CREATE INDEX IF NOT EXISTS idx_word_memory_question_pick ON word_memory_question (content_version_id,dimension,state);

CREATE TABLE IF NOT EXISTS word_memory_session (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  return_to VARCHAR(500),
  task_id CHAR(32),
  business_date DATE NOT NULL,
  target_count SMALLINT NOT NULL,
  required_dimensions VARCHAR(200) NOT NULL,
  add_to_review SMALLINT NOT NULL DEFAULT 0,
  state VARCHAR(32) NOT NULL DEFAULT 'active',
  version_no INT NOT NULL DEFAULT 1,
  idempotency_key VARCHAR(100) NOT NULL,
  completed_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_memory_session_idem UNIQUE (owner_id,idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_word_memory_session_resume ON word_memory_session (owner_id,state,updated_at);

CREATE TABLE IF NOT EXISTS word_memory_episode (
  id CHAR(32) NOT NULL PRIMARY KEY,
  session_id CHAR(32) NOT NULL,
  content_id CHAR(32) NOT NULL,
  content_version_id CHAR(32) NOT NULL,
  sense_id CHAR(32),
  question_id CHAR(32) NOT NULL,
  question_version INT NOT NULL,
  dimension VARCHAR(32) NOT NULL,
  position_no INT NOT NULL,
  state VARCHAR(32) NOT NULL DEFAULT 'pending',
  hint_used SMALLINT NOT NULL DEFAULT 0,
  answer_revealed SMALLINT NOT NULL DEFAULT 0,
  attempt_count SMALLINT NOT NULL DEFAULT 0,
  first_result VARCHAR(32),
  final_result VARCHAR(32),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_memory_episode UNIQUE (session_id,question_id)
);
CREATE INDEX IF NOT EXISTS idx_word_memory_episode_next ON word_memory_episode (session_id,state,position_no);

CREATE TABLE IF NOT EXISTS word_memory_hint_event (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  session_id CHAR(32) NOT NULL,
  episode_id CHAR(32) NOT NULL,
  hint_type VARCHAR(32) NOT NULL,
  answer_revealed SMALLINT NOT NULL DEFAULT 0,
  occurred_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_word_memory_hint_event_episode ON word_memory_hint_event (episode_id,occurred_at);

CREATE TABLE IF NOT EXISTS word_memory_attempt (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  session_id CHAR(32) NOT NULL,
  episode_id CHAR(32) NOT NULL,
  attempt_no SMALLINT NOT NULL,
  answer_text VARCHAR(1000),
  verdict VARCHAR(32) NOT NULL,
  result_type VARCHAR(32) NOT NULL,
  hint_used SMALLINT NOT NULL DEFAULT 0,
  first_attempt SMALLINT NOT NULL DEFAULT 0,
  duration_ms INT,
  idempotency_key VARCHAR(100) NOT NULL,
  submitted_at TIMESTAMP(3) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_memory_attempt_no UNIQUE (episode_id,attempt_no),
  CONSTRAINT uk_word_memory_attempt_idem UNIQUE (owner_id,idempotency_key)
);
CREATE INDEX IF NOT EXISTS idx_word_memory_attempt_session ON word_memory_attempt (session_id,episode_id);

CREATE TABLE IF NOT EXISTS word_memory_evidence (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  content_id CHAR(32) NOT NULL,
  sense_id CHAR(32),
  dimension VARCHAR(32) NOT NULL,
  business_date DATE NOT NULL,
  episode_id CHAR(32) NOT NULL,
  first_attempt_id CHAR(32) NOT NULL,
  first_result VARCHAR(32) NOT NULL,
  hint_used SMALLINT NOT NULL DEFAULT 0,
  rule_version VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_word_memory_evidence_episode UNIQUE (episode_id)
);
CREATE INDEX IF NOT EXISTS idx_word_memory_evidence_dimension ON word_memory_evidence (owner_id,content_id,sense_id,dimension,business_date);

CREATE TABLE IF NOT EXISTS weekly_summary (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  week_start DATE NOT NULL,
  current_revision_id CHAR(32),
  confirmed_revision_id CHAR(32),
  source_fingerprint BINARY(32) NOT NULL,
  is_stale SMALLINT NOT NULL DEFAULT 0,
  version_no INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_weekly_summary UNIQUE (owner_id,week_start)
);

CREATE TABLE IF NOT EXISTS weekly_revision (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  summary_id CHAR(32) NOT NULL,
  revision_no INT NOT NULL,
  metrics_json CLOB NOT NULL,
  body CLOB,
  sources_json CLOB NOT NULL,
  source_fingerprint BINARY(32) NOT NULL,
  ai_job_id CHAR(32),
  confirmed_at TIMESTAMP(3),
  invalidated_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_weekly_revision UNIQUE (summary_id,revision_no)
);
CREATE INDEX IF NOT EXISTS idx_weekly_revision_owner ON weekly_revision (owner_id);

CREATE TABLE IF NOT EXISTS weekly_action (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL, summary_id CHAR(32) NOT NULL, action_no SMALLINT NOT NULL,
  title VARCHAR(100) NOT NULL, note VARCHAR(1000), scheduled_date DATE NOT NULL,
  estimated_minutes SMALLINT NOT NULL, state VARCHAR(32) NOT NULL DEFAULT 'draft',
  confirmed_at TIMESTAMP(3),
  version_no INT NOT NULL DEFAULT 1,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_weekly_action UNIQUE (summary_id,action_no)
);
CREATE INDEX IF NOT EXISTS idx_weekly_action_pick ON weekly_action (owner_id,scheduled_date,state);

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

CREATE TABLE IF NOT EXISTS daily_journal (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL, business_date DATE NOT NULL,
  current_revision_id CHAR(32), submitted_revision_id CHAR(32),
  version_no INT NOT NULL DEFAULT 1, state VARCHAR(32) NOT NULL DEFAULT 'draft',
  first_submitted_at TIMESTAMP(3), last_submitted_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_daily_journal UNIQUE (owner_id,business_date)
);

CREATE TABLE IF NOT EXISTS journal_revision (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL, journal_id CHAR(32) NOT NULL, revision_no INT NOT NULL,
  done_text CLOB, blocker_text CLOB, learned_text CLOB, next_step_text CLOB,
  ai_summary CLOB, summary_source_revision INT, summary_ai_job_id CHAR(32),
  content_hash BINARY(32) NOT NULL, save_kind VARCHAR(32) NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_journal_revision UNIQUE (journal_id,revision_no)
);

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

CREATE TABLE IF NOT EXISTS follow_recording (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  content_id CHAR(32) NOT NULL,
  content_version_id CHAR(32) NOT NULL,
  asset_id CHAR(32) NOT NULL,
  duration_ms INT NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'active',
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at TIMESTAMP(3),
  CONSTRAINT uk_follow_recording_owner_content UNIQUE (owner_id, content_id)
);
CREATE INDEX IF NOT EXISTS idx_follow_recording_asset ON follow_recording (asset_id, state);
CREATE INDEX IF NOT EXISTS idx_follow_recording_owner_state ON follow_recording (owner_id, state);

CREATE TABLE IF NOT EXISTS article_word_glossary (
  id CHAR(32) NOT NULL PRIMARY KEY,
  term VARCHAR(80) NOT NULL,
  phonetic VARCHAR(200) NOT NULL,
  meaning VARCHAR(500) NOT NULL,
  audio_asset_id CHAR(32), audio_voice VARCHAR(64), audio_generated_at TIMESTAMP(3),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_article_word_glossary_term UNIQUE (term)
);

CREATE TABLE IF NOT EXISTS tts_usage_log (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32), target_type VARCHAR(16) NOT NULL, target_id CHAR(32) NOT NULL,
  content_version_id CHAR(32) NOT NULL, provider_code VARCHAR(32) NOT NULL,
  voice VARCHAR(64) NOT NULL, char_count INT NOT NULL, state VARCHAR(16) NOT NULL,
  provider_request_id VARCHAR(160), asset_id CHAR(32), error_code VARCHAR(80),
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_tts_usage_time ON tts_usage_log (created_at);

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

CREATE TABLE IF NOT EXISTS knowledge_share_rule (
 id CHAR(32) NOT NULL PRIMARY KEY,
 knowledge_id CHAR(32) NOT NULL,
 friend_id CHAR(32) NOT NULL,
 relation_id CHAR(32) NOT NULL,
 relation_generation INT NOT NULL,
 effect VARCHAR(32) NOT NULL,
 version_no INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_knowledge_share_friend UNIQUE(knowledge_id, friend_id)
);

CREATE INDEX IF NOT EXISTS idx_knowledge_share_friend_effect ON knowledge_share_rule (friend_id, effect);

-- “我的”模块本地集成测试兼容表；生产 MySQL V3.0 初始化脚本已包含同名表。
CREATE TABLE IF NOT EXISTS friend_request (
 id CHAR(32) NOT NULL PRIMARY KEY, relation_id CHAR(32) NOT NULL, sender_id CHAR(32) NOT NULL,
 receiver_id CHAR(32) NOT NULL, remark VARCHAR(200) NOT NULL, state VARCHAR(32) NOT NULL DEFAULT 'pending',
 pending_slot SMALLINT, decided_at TIMESTAMP(3), version_no INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_friend_request_pending UNIQUE(relation_id,pending_slot)
);
CREATE INDEX IF NOT EXISTS idx_friend_request_receiver ON friend_request(receiver_id,state,created_at);
CREATE INDEX IF NOT EXISTS idx_friend_request_sender ON friend_request(sender_id,state,created_at);

CREATE TABLE IF NOT EXISTS ai_daily_quota (
 id CHAR(32) NOT NULL PRIMARY KEY, quota_date DATE NOT NULL, scope_key CHAR(32) NOT NULL,
 limit_count INT NOT NULL, used_count INT NOT NULL DEFAULT 0, reserved_count INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_ai_daily_quota UNIQUE(quota_date,scope_key)
);

CREATE TABLE IF NOT EXISTS ai_model_config (
 id CHAR(32) NOT NULL PRIMARY KEY,
 provider_code VARCHAR(32) NOT NULL,
 model_code VARCHAR(120) NOT NULL,
 display_name VARCHAR(100) NOT NULL,
 specification VARCHAR(32) NOT NULL,
 base_url VARCHAR(500) NOT NULL,
 api_key_param_key VARCHAR(100) NOT NULL,
 enabled SMALLINT NOT NULL DEFAULT 0,
 is_default SMALLINT NOT NULL DEFAULT 0,
 max_output_tokens INT NOT NULL DEFAULT 1200,
 timeout_seconds INT NOT NULL DEFAULT 60,
 version_no INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_ai_model_config UNIQUE(provider_code,model_code)
);

CREATE TABLE IF NOT EXISTS ai_model_price (
 id CHAR(32) NOT NULL PRIMARY KEY, provider_code VARCHAR(32) NOT NULL, model_code VARCHAR(120) NOT NULL,
 version_no INT NOT NULL, currency CHAR(3) NOT NULL, input_per_million DECIMAL(18,6) NOT NULL,
 output_per_million DECIMAL(18,6) NOT NULL, pricing_json CLOB NOT NULL, effective_at TIMESTAMP(3) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_ai_model_price UNIQUE(provider_code,model_code,version_no)
);

CREATE TABLE IF NOT EXISTS ai_job (
 id CHAR(32) NOT NULL PRIMARY KEY, owner_id CHAR(32), scope_key CHAR(32) NOT NULL,
 action_code VARCHAR(32) NOT NULL, source_type VARCHAR(32) NOT NULL, source_id CHAR(32) NOT NULL,
 source_version INT NOT NULL, source_fingerprint BINARY(32) NOT NULL, consent_version VARCHAR(40),
 prompt_version VARCHAR(40) NOT NULL, input_text CLOB, output_json CLOB, state VARCHAR(32) NOT NULL DEFAULT 'queued',
 cancel_requested SMALLINT NOT NULL DEFAULT 0, attempt_count INT NOT NULL DEFAULT 0,
 queue_expires_at TIMESTAMP(3) NOT NULL, payload_expires_at TIMESTAMP(3) NOT NULL,
 accepted_at TIMESTAMP(3), accepted_target_type VARCHAR(32), accepted_target_id CHAR(32), error_code VARCHAR(32),
 request_key_hash BINARY(32) NOT NULL, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_ai_job_request UNIQUE(scope_key,action_code,request_key_hash)
);
CREATE INDEX IF NOT EXISTS idx_ai_job_owner ON ai_job(owner_id,created_at);

CREATE TABLE IF NOT EXISTS ai_month_budget (
 id CHAR(32) NOT NULL PRIMARY KEY, month_start DATE NOT NULL, currency CHAR(3) NOT NULL,
 limit_amount DECIMAL(18,6) NOT NULL, reserved_amount DECIMAL(18,6) NOT NULL DEFAULT 0,
 spent_amount DECIMAL(18,6) NOT NULL DEFAULT 0, warned_at TIMESTAMP(3), row_version INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_ai_month_budget UNIQUE(month_start)
);

CREATE TABLE IF NOT EXISTS ai_attempt (
 id CHAR(32) NOT NULL PRIMARY KEY, job_id CHAR(32), attempt_no INT NOT NULL, trigger_type VARCHAR(32) NOT NULL,
 owner_id CHAR(32), price_id CHAR(32) NOT NULL, budget_id CHAR(32) NOT NULL, quota_date DATE NOT NULL,
 state VARCHAR(32) NOT NULL DEFAULT 'reserved', provider_request_id VARCHAR(128),
 reserved_amount DECIMAL(18,6) NOT NULL, settled_amount DECIMAL(18,6), input_tokens INT, output_tokens INT,
 usage_json CLOB, started_at TIMESTAMP(3), finished_at TIMESTAMP(3), timeout_at TIMESTAMP(3),
 quota_reserved SMALLINT NOT NULL DEFAULT 1, concurrency_held SMALLINT NOT NULL DEFAULT 1,
 reconciled_at TIMESTAMP(3), error_code VARCHAR(32), created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_ai_attempt UNIQUE(job_id,attempt_no)
);

CREATE TABLE IF NOT EXISTS ai_concurrency_guard (
 id CHAR(32) NOT NULL PRIMARY KEY, scope_key CHAR(32) NOT NULL, limit_count INT NOT NULL,
 running_count INT NOT NULL DEFAULT 0, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_ai_concurrency_guard UNIQUE(scope_key)
);

CREATE TABLE IF NOT EXISTS admin_audit (
 id CHAR(32) NOT NULL PRIMARY KEY, admin_id CHAR(32) NOT NULL, action_code VARCHAR(32) NOT NULL,
 target_type VARCHAR(32) NOT NULL, target_id CHAR(32), result_code VARCHAR(32) NOT NULL,
 metadata_json CLOB, expires_at TIMESTAMP(3) NOT NULL, created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_feedback (
 id CHAR(32) NOT NULL PRIMARY KEY, owner_id CHAR(32) NOT NULL, category VARCHAR(32) NOT NULL,
 content_id CHAR(32), body CLOB NOT NULL, state VARCHAR(32) NOT NULL DEFAULT 'open', request_id VARCHAR(64),
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS data_export (
 id CHAR(32) NOT NULL PRIMARY KEY, owner_id CHAR(32) NOT NULL, state VARCHAR(32) NOT NULL DEFAULT 'queued',
 object_key VARCHAR(255), file_size_bytes BIGINT, file_hash BINARY(32), snapshot_at TIMESTAMP(3), ready_at TIMESTAMP(3),
 expires_at TIMESTAMP(3), error_code VARCHAR(32), created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS data_deletion (
 id CHAR(32) NOT NULL PRIMARY KEY, owner_id CHAR(32), scope_type VARCHAR(32) NOT NULL, target_id CHAR(32),
 receipt_hash BINARY(32) NOT NULL, state VARCHAR(32) NOT NULL DEFAULT 'requested', primary_deadline_at TIMESTAMP(3) NOT NULL,
 primary_cleaned_at TIMESTAMP(3), backup_clear_after TIMESTAMP(3) NOT NULL, completed_at TIMESTAMP(3),
 receipt_expires_at TIMESTAMP(3), error_code VARCHAR(32), created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT uk_data_deletion_receipt UNIQUE(receipt_hash)
);

CREATE TABLE IF NOT EXISTS resource_schedule (
 id CHAR(32) NOT NULL PRIMARY KEY, owner_id CHAR(32) NOT NULL, name VARCHAR(20) NOT NULL,
 resource_kind VARCHAR(32) NOT NULL, keywords VARCHAR(500) NOT NULL, weekdays_mask SMALLINT NOT NULL,
 minute_of_day SMALLINT NOT NULL, timezone VARCHAR(40) NOT NULL DEFAULT 'Asia/Shanghai', per_run_limit SMALLINT NOT NULL DEFAULT 5,
 state VARCHAR(32) NOT NULL DEFAULT 'active', next_run_at TIMESTAMP(3), version_no INT NOT NULL DEFAULT 1,
 dedup_enabled SMALLINT NOT NULL DEFAULT 1, summary_enabled SMALLINT NOT NULL DEFAULT 0, topics_json VARCHAR(2000),
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS resource_schedule_source (
 id CHAR(32) NOT NULL PRIMARY KEY, schedule_id CHAR(32) NOT NULL, source_id CHAR(32) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_resource_schedule_source UNIQUE(schedule_id,source_id)
);
CREATE TABLE IF NOT EXISTS resource_run (
 id CHAR(32) NOT NULL PRIMARY KEY, owner_id CHAR(32) NOT NULL, schedule_id CHAR(32) NOT NULL,
 trigger_key VARCHAR(80) NOT NULL, schedule_version INT NOT NULL, config_snapshot CLOB NOT NULL,
 state VARCHAR(32) NOT NULL DEFAULT 'queued', attempt_count INT NOT NULL DEFAULT 0, started_at TIMESTAMP(3),
 finished_at TIMESTAMP(3), error_code VARCHAR(32), result_count INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_resource_run_trigger UNIQUE(schedule_id,trigger_key)
);
