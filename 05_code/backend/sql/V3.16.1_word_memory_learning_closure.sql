-- V3.16.1 单词记忆训练闭环：固化“已查看答案”证据。
-- 兼容 MySQL 5.7/8.0，可重复执行。
SET @answer_revealed_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'word_memory_evidence'
    AND COLUMN_NAME = 'answer_revealed'
);
SET @answer_revealed_sql = IF(
  @answer_revealed_exists = 0,
  'ALTER TABLE word_memory_evidence ADD COLUMN answer_revealed TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER hint_used',
  'SELECT 1'
);
PREPARE answer_revealed_stmt FROM @answer_revealed_sql;
EXECUTE answer_revealed_stmt;
DEALLOCATE PREPARE answer_revealed_stmt;
