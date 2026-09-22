-- V3.22 「重新学习」记录
--   每次在小程序「词书学习进度」点「重新学习」，把某本词书已学的词条划回未学，这里留一条记录（时间、重置词数、取消的复习排期数），
--   管理端「词书学习记录」详情就能看到这本书这一轮重新学习过几次、什么时候、重置了多少词。
--   幂等：可重复执行。

CREATE TABLE IF NOT EXISTS vocabulary_book_reset_log (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  book_id CHAR(32) NOT NULL,
  round_id CHAR(32) NOT NULL,
  reset_count INT NOT NULL DEFAULT 0,
  paused_review_count INT NOT NULL DEFAULT 0,
  reset_at DATETIME(3) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_book_reset_log UNIQUE (owner_id,book_id,reset_at)
);

SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_reset_log' AND INDEX_NAME='idx_book_reset_log_round')=0,
  'CREATE INDEX idx_book_reset_log_round ON vocabulary_book_reset_log (round_id,reset_at)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_reset_log' AND INDEX_NAME='idx_book_reset_log_owner')=0,
  'CREATE INDEX idx_book_reset_log_owner ON vocabulary_book_reset_log (owner_id,reset_at)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 历史回填：被「重新学习」划回未学的词条，learning_status='unlearned' 且 updated_at 晚于 created_at
-- （同一次重置是同一个时间戳参数，所以能按 (owner,book,updated_at) 精确分组），轮次取重置时刻正在进行的那一轮。
INSERT INTO vocabulary_book_reset_log (id,owner_id,book_id,round_id,reset_count,paused_review_count,reset_at)
SELECT REPLACE(UUID(),'-',''),g.owner_id,g.book_id,
       COALESCE((SELECT r.id FROM vocabulary_book_study_record r WHERE r.owner_id=g.owner_id AND r.book_id=g.book_id
                  AND (r.selected_at IS NULL OR r.selected_at<=g.reset_at) AND (r.ended_at IS NULL OR r.ended_at>g.reset_at)
                  ORDER BY r.round_no DESC LIMIT 1),(SELECT r2.id FROM vocabulary_book_study_record r2 WHERE r2.owner_id=g.owner_id AND r2.book_id=g.book_id ORDER BY r2.round_no DESC LIMIT 1)),
       g.reset_count,0,g.reset_at
FROM (SELECT lr.owner_id,w.book_id,lr.updated_at AS reset_at,COUNT(DISTINCT lr.content_id) AS reset_count
        FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id
       WHERE lr.learning_status='unlearned' AND lr.updated_at>lr.created_at
       GROUP BY lr.owner_id,w.book_id,lr.updated_at) g
WHERE NOT EXISTS (SELECT 1 FROM vocabulary_book_reset_log l WHERE l.owner_id=g.owner_id AND l.book_id=g.book_id AND l.reset_at=g.reset_at);
