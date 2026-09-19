-- 仅回退 V3.16.1 新增字段；已有证据中的“已查看答案”信息会丢失。
SET @answer_revealed_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'word_memory_evidence'
    AND COLUMN_NAME = 'answer_revealed'
);
SET @answer_revealed_sql = IF(
  @answer_revealed_exists = 1,
  'ALTER TABLE word_memory_evidence DROP COLUMN answer_revealed',
  'SELECT 1'
);
PREPARE answer_revealed_stmt FROM @answer_revealed_sql;
EXECUTE answer_revealed_stmt;
DEALLOCATE PREPARE answer_revealed_stmt;
