-- V3.16.7: auditable baseline-card coverage for published school/CET vocabulary.
-- Additive only; no published content, audio or learning history is replaced.
SET @card_example_col=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='word_learning_card' AND column_name='example_id');
SET @card_example_ddl=IF(@card_example_col=0,'ALTER TABLE word_learning_card ADD COLUMN example_id CHAR(32) NULL AFTER recall_prompt','SELECT 1');
PREPARE card_example_stmt FROM @card_example_ddl; EXECUTE card_example_stmt; DEALLOCATE PREPARE card_example_stmt;

SET @card_generator_col=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='word_learning_card' AND column_name='generator_version');
SET @card_generator_ddl=IF(@card_generator_col=0,'ALTER TABLE word_learning_card ADD COLUMN generator_version VARCHAR(40) NULL AFTER rights_note','SELECT 1');
PREPARE card_generator_stmt FROM @card_generator_ddl; EXECUTE card_generator_stmt; DEALLOCATE PREPARE card_generator_stmt;

CREATE TABLE IF NOT EXISTS word_learning_card_coverage (
 content_version_id CHAR(32) NOT NULL PRIMARY KEY,
 target_scope_codes VARCHAR(200) NOT NULL,
 required_card_count INT NOT NULL DEFAULT 2,
 published_card_count INT NOT NULL DEFAULT 0,
 baseline_status VARCHAR(24) NOT NULL DEFAULT 'pending',
 example_status VARCHAR(24) NOT NULL DEFAULT 'missing',
 relation_status VARCHAR(32) NOT NULL DEFAULT 'evidence_required',
 quotation_status VARCHAR(32) NOT NULL DEFAULT 'optional_not_required',
 source_example_id CHAR(32) NULL,
 generator_version VARCHAR(40) NOT NULL,
 issues_json LONGTEXT NOT NULL,
 del_is SMALLINT NOT NULL DEFAULT 0,
 generated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_word_card_coverage_status(del_is,baseline_status,relation_status),
 KEY idx_word_card_coverage_generator(generator_version,generated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
