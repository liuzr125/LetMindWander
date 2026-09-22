-- V3.21 词书学习记录按「轮次」组织
--   一轮 = 一次「选定这本词书」；再次切回同一本书会新开一轮，老记录冻结成历史（end_at / 结束时已学快照），后续学习只改当前轮。
--   新开一轮时把上一轮的已学/未学状态带过来：词条学习状态本身不动（learning_record 保留），本轮只记「带入已学 carried_learned_count」。
--   幂等：可重复执行。

-- 1) 列：轮次、选择时间、结束时间、带入已学、结束时已学快照；首次/最近学习允许为空（刚选定还没学）
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND COLUMN_NAME='round_no')=0,
  'ALTER TABLE vocabulary_book_study_record ADD COLUMN round_no INT NOT NULL DEFAULT 1 AFTER book_id','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND COLUMN_NAME='selected_at')=0,
  'ALTER TABLE vocabulary_book_study_record ADD COLUMN selected_at DATETIME(3) NULL AFTER round_no','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND COLUMN_NAME='ended_at')=0,
  'ALTER TABLE vocabulary_book_study_record ADD COLUMN ended_at DATETIME(3) NULL AFTER selected_at','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND COLUMN_NAME='carried_learned_count')=0,
  'ALTER TABLE vocabulary_book_study_record ADD COLUMN carried_learned_count INT NOT NULL DEFAULT 0 AFTER ended_at','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND COLUMN_NAME='final_learned_count')=0,
  'ALTER TABLE vocabulary_book_study_record ADD COLUMN final_learned_count INT NULL AFTER carried_learned_count','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE vocabulary_book_study_record
  MODIFY COLUMN first_studied_at DATETIME(3) NULL,
  MODIFY COLUMN last_studied_at DATETIME(3) NULL;

-- 2) 唯一键：(owner,book) → (owner,book,round_no)
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND INDEX_NAME='uk_book_study_record')>0,
  'ALTER TABLE vocabulary_book_study_record DROP INDEX uk_book_study_record','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND INDEX_NAME='uk_book_study_round')=0,
  'ALTER TABLE vocabulary_book_study_record ADD UNIQUE KEY uk_book_study_round (owner_id,book_id,round_no)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 索引调整（按选择时间排序/筛选）
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND INDEX_NAME='idx_book_study_record_owner')=0,
  'CREATE INDEX idx_book_study_record_owner ON vocabulary_book_study_record (owner_id,selected_at)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND INDEX_NAME='idx_book_study_record_open')=0,
  'CREATE INDEX idx_book_study_record_open ON vocabulary_book_study_record (owner_id,book_id,ended_at)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 老数据回填为第 1 轮：选择时间取用户选该书的 selected_at（真实值，取不到就用首次学习时间），带入已学记 0
UPDATE vocabulary_book_study_record r
   SET r.round_no = 1,
       r.selected_at = COALESCE(r.selected_at,
         (SELECT uvb.selected_at FROM user_vocabulary_book uvb WHERE uvb.owner_id=r.owner_id AND uvb.book_id=r.book_id LIMIT 1),
         r.first_studied_at)
 WHERE r.selected_at IS NULL;

-- 4b) 老数据修正：本轮的「选择时间」不应晚于这一轮的首次学习（历史只有最新选择时间，取更早的那个）
UPDATE vocabulary_book_study_record
   SET selected_at=first_studied_at
 WHERE first_studied_at IS NOT NULL AND (selected_at IS NULL OR selected_at>first_studied_at);

-- 5) 历史轮次收口：不是当前选定词书的、还挂着「进行中」的轮次，按暂停时间结束并补结束快照
UPDATE vocabulary_book_study_record r
  JOIN user_vocabulary_book uvb ON uvb.owner_id=r.owner_id AND uvb.book_id=r.book_id
   SET r.ended_at=GREATEST(COALESCE(uvb.paused_at,r.last_studied_at,r.selected_at,NOW(3)),COALESCE(r.selected_at,NOW(3))),
       r.final_learned_count=(SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id
         WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status IN ('understood','mastered'))
 WHERE r.ended_at IS NULL AND uvb.state<>'active';

-- 5) 每日明细也按轮次：加 round_id，回填成该 (owner,book) 最早的一轮，唯一键改为 (round_id,business_date)
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_daily' AND COLUMN_NAME='round_id')=0,
  'ALTER TABLE vocabulary_book_study_daily ADD COLUMN round_id CHAR(32) NULL AFTER id','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
UPDATE vocabulary_book_study_daily d
   SET d.round_id=(SELECT r.id FROM vocabulary_book_study_record r WHERE r.owner_id=d.owner_id AND r.book_id=d.book_id ORDER BY r.round_no LIMIT 1)
 WHERE d.round_id IS NULL;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_daily' AND INDEX_NAME='uk_book_study_daily')>0,
  'ALTER TABLE vocabulary_book_study_daily DROP INDEX uk_book_study_daily','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_daily' AND INDEX_NAME='uk_book_study_daily_round')=0,
  'ALTER TABLE vocabulary_book_study_daily ADD UNIQUE KEY uk_book_study_daily_round (round_id,business_date)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_daily' AND INDEX_NAME='idx_book_study_daily_round')=0,
  'CREATE INDEX idx_book_study_daily_round ON vocabulary_book_study_daily (round_id,business_date)','SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5b) 修正：结束时间不能早于选择时间（老数据的切换时间可能早于本轮建立时间）
UPDATE vocabulary_book_study_record
   SET ended_at=GREATEST(ended_at,COALESCE(selected_at,ended_at),COALESCE(last_studied_at,ended_at))
 WHERE ended_at IS NOT NULL AND selected_at IS NOT NULL AND ended_at<selected_at;

-- 6) 当前正在学的这本书（active 选择）如果没有「进行中」的轮次，补开一轮，保证后续学习有归属
INSERT INTO vocabulary_book_study_record (id,owner_id,book_id,round_no,selected_at,carried_learned_count,first_studied_at,last_studied_at,study_count,reviewed_count,study_day_count)
SELECT REPLACE(UUID(),'-',''), uvb.owner_id, uvb.book_id,
       COALESCE((SELECT MAX(x.round_no) FROM vocabulary_book_study_record x WHERE x.owner_id=uvb.owner_id AND x.book_id=uvb.book_id),0)+1,
       uvb.selected_at,
       (SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id
         WHERE lr.owner_id=uvb.owner_id AND w.book_id=uvb.book_id AND lr.learning_status IN ('understood','mastered')),
       NULL, NULL, 0, 0, 0
FROM user_vocabulary_book uvb
WHERE uvb.state='active'
  AND NOT EXISTS (SELECT 1 FROM vocabulary_book_study_record r WHERE r.owner_id=uvb.owner_id AND r.book_id=uvb.book_id AND r.ended_at IS NULL);

-- 7) 菜单描述：说明「轮次」口径
UPDATE admin_menu SET description='每个用户每本英语词书每一轮的学习记录：选择时间、每日明细与词条' WHERE code='study_records';
