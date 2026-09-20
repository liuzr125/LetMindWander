-- V3.16.10: versioned lexical evidence used by learning-card enrichment.
-- Evidence rows are append/update only; obsolete rows are soft-deleted with del_is.
CREATE TABLE IF NOT EXISTS lexical_dataset_snapshot (
 dataset_code VARCHAR(64) NOT NULL PRIMARY KEY,
 dataset_name VARCHAR(160) NOT NULL,
 dataset_version VARCHAR(40) NOT NULL,
 source_url VARCHAR(2048) NOT NULL,
 license_code VARCHAR(40) NOT NULL,
 license_url VARCHAR(2048) NOT NULL,
 attribution VARCHAR(1000) NOT NULL,
 checksum_sha256 CHAR(64) NOT NULL,
 import_status VARCHAR(24) NOT NULL DEFAULT 'pending',
 relation_count INT NOT NULL DEFAULT 0,
 imported_at TIMESTAMP(3) NULL,
 del_is SMALLINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS word_lexical_relation_evidence (
 id CHAR(32) NOT NULL PRIMARY KEY,
 dataset_code VARCHAR(64) NOT NULL,
 headword_norm VARCHAR(160) NOT NULL,
 relation_type VARCHAR(24) NOT NULL,
 related_term VARCHAR(160) NOT NULL,
 part_of_speech VARCHAR(8) NOT NULL DEFAULT '',
 source_sense_id VARCHAR(200) NULL,
 source_synset_id VARCHAR(32) NULL,
 sense_rank SMALLINT NOT NULL DEFAULT 0,
 relation_rank SMALLINT NOT NULL DEFAULT 0,
 source_locator VARCHAR(240) NOT NULL,
 evidence_note VARCHAR(500) NOT NULL,
 del_is SMALLINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_lexical_relation(dataset_code,headword_norm,relation_type,related_term,part_of_speech),
 KEY idx_lexical_relation_headword(headword_norm,del_is,relation_type),
 KEY idx_lexical_relation_dataset(dataset_code,del_is)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @lexical_sense_rank_col=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='word_lexical_relation_evidence' AND column_name='sense_rank');
SET @lexical_sense_rank_ddl=IF(@lexical_sense_rank_col=0,'ALTER TABLE word_lexical_relation_evidence ADD COLUMN sense_rank SMALLINT NOT NULL DEFAULT 0 AFTER source_synset_id','SELECT 1');
PREPARE lexical_sense_rank_stmt FROM @lexical_sense_rank_ddl; EXECUTE lexical_sense_rank_stmt; DEALLOCATE PREPARE lexical_sense_rank_stmt;

SET @lexical_relation_rank_col=(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='word_lexical_relation_evidence' AND column_name='relation_rank');
SET @lexical_relation_rank_ddl=IF(@lexical_relation_rank_col=0,'ALTER TABLE word_lexical_relation_evidence ADD COLUMN relation_rank SMALLINT NOT NULL DEFAULT 0 AFTER sense_rank','SELECT 1');
PREPARE lexical_relation_rank_stmt FROM @lexical_relation_rank_ddl; EXECUTE lexical_relation_rank_stmt; DEALLOCATE PREPARE lexical_relation_rank_stmt;
