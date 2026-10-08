-- Prepare only. Back up and rehearse on a copy before applying to MySQL 5.7.
-- Additive, rerunnable; existing learning history and current due dates are preserved.

-- Word consolidation is independent of daily new-word quota and survives book switches.
CREATE TABLE IF NOT EXISTS word_study_cursor (
 owner_id CHAR(32) NOT NULL PRIMARY KEY, position_no BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS word_study_mode (
 setting_id INT NOT NULL PRIMARY KEY, interval_mode VARCHAR(16) NOT NULL DEFAULT 'fixed'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS word_study_state (
 owner_id CHAR(32) NOT NULL, content_id CHAR(32) NOT NULL,
 source_kind VARCHAR(16) NOT NULL DEFAULT 'new', task_id CHAR(32),origin_task_id CHAR(32),schedule_id CHAR(32),
 free_study SMALLINT NOT NULL DEFAULT 0,pending SMALLINT NOT NULL DEFAULT 1, exposed SMALLINT NOT NULL DEFAULT 0, streak INT NOT NULL DEFAULT 0,
 recovery VARCHAR(16), business_date DATE NOT NULL, first_seen_date DATE NOT NULL,
 eligible_position BIGINT NOT NULL DEFAULT 0, eligible_at DATETIME(3) NULL,
 scheduled_interval INT NOT NULL DEFAULT 1, schedule_mode VARCHAR(16) NOT NULL DEFAULT 'fixed', last_completed_date DATE,
 version_no INT NOT NULL DEFAULT 1,
 PRIMARY KEY (owner_id,content_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS word_study_attempt (
 owner_id CHAR(32) NOT NULL, attempt_id VARCHAR(96) NOT NULL,
 content_id CHAR(32) NOT NULL, request_json TEXT NOT NULL, result_json TEXT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY (owner_id,attempt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Previously completed word schedules become maintenance schedules. Preserve active/paused dates.
UPDATE review_schedule rs JOIN knowledge_item k ON k.id=rs.knowledge_id
JOIN learning_content lc ON lc.id=k.source_content_id AND lc.content_type='word'
LEFT JOIN review_feedback f ON f.id=rs.last_feedback_id
SET rs.state='active',rs.stage=5,rs.due_date=DATE_ADD(COALESCE(f.business_date,DATE(DATE_ADD(COALESCE(rs.updated_at,UTC_TIMESTAMP(3)),INTERVAL 8 HOUR))),INTERVAL 60 DAY),rs.version_no=rs.version_no+1
WHERE rs.state='completed';

CREATE TABLE IF NOT EXISTS word_study_event (
 id CHAR(32) NOT NULL PRIMARY KEY,owner_id CHAR(32) NOT NULL,content_id CHAR(32) NOT NULL,
 attempt_id VARCHAR(96) NOT NULL,feedback VARCHAR(16) NOT NULL,source_kind VARCHAR(16) NOT NULL,word_term VARCHAR(255) NOT NULL,
 interval_mode VARCHAR(16) NOT NULL,before_stage INT,after_stage INT,
 before_interval INT NOT NULL,after_interval INT NOT NULL,next_due DATE,
 streak INT NOT NULL,completed SMALLINT NOT NULL,event_at DATETIME(3) NOT NULL,
 UNIQUE (owner_id,attempt_id), KEY idx_word_study_event_time(event_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS word_study_event_round (
 event_id CHAR(32) NOT NULL,round_id CHAR(32) NOT NULL,
 PRIMARY KEY(event_id,round_id), KEY idx_word_study_event_round(round_id,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
