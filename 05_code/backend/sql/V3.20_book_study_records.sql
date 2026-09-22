-- V3.20 词书学习记录（用户 × 词书）+ 管理端「词书学习记录」菜单 + 历史数据回填
-- 背景：用户每学习一本英语词书都要留下记录，并能到 web 端查看每本书的学习记录与详情。
-- 幂等：可重复执行。

CREATE TABLE IF NOT EXISTS vocabulary_book_study_record (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  book_id CHAR(32) NOT NULL,
  first_studied_at DATETIME(3) NOT NULL,
  last_studied_at DATETIME(3) NOT NULL,
  study_count INT NOT NULL DEFAULT 0,
  reviewed_count INT NOT NULL DEFAULT 0,
  study_day_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_book_study_record UNIQUE (owner_id,book_id)
);

CREATE TABLE IF NOT EXISTS vocabulary_book_study_daily (
  id CHAR(32) NOT NULL PRIMARY KEY,
  owner_id CHAR(32) NOT NULL,
  book_id CHAR(32) NOT NULL,
  business_date DATE NOT NULL,
  study_count INT NOT NULL DEFAULT 0,
  reviewed_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  CONSTRAINT uk_book_study_daily UNIQUE (owner_id,book_id,business_date)
);

SET @add_idx := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND INDEX_NAME='idx_book_study_record_owner')=0,'CREATE INDEX idx_book_study_record_owner ON vocabulary_book_study_record (owner_id,last_studied_at)','SELECT 1');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @add_idx := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_record' AND INDEX_NAME='idx_book_study_record_book')=0,'CREATE INDEX idx_book_study_record_book ON vocabulary_book_study_record (book_id,last_studied_at)','SELECT 1');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @add_idx := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='vocabulary_book_study_daily' AND INDEX_NAME='idx_book_study_daily_owner')=0,'CREATE INDEX idx_book_study_daily_owner ON vocabulary_book_study_daily (owner_id,business_date)','SELECT 1');
PREPARE stmt FROM @add_idx; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 管理端菜单：用户与学习 → 词书学习记录
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000be','study_records','词书学习记录','/study-records','▤',62,1,'00000000000000000000000000c2','每个用户每本英语词书的学习记录、每日明细与已学词条'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='study_records');

INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id,'00000000000000000000000000be' FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id
WHERE m.code='users'
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id='00000000000000000000000000be');

-- 历史回填：把已有学习事件按「用户 × 词书 × 天」聚合，不是编造数据
INSERT INTO vocabulary_book_study_daily (id,owner_id,book_id,business_date,study_count,reviewed_count)
SELECT REPLACE(UUID(),'-',''),e.owner_id,w.book_id,e.business_date,COUNT(*),0
FROM learning_event e
JOIN learning_record lr ON lr.id=e.record_id
JOIN vocabulary_book_word w ON w.content_id=lr.content_id
WHERE e.business_date IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM vocabulary_book_study_daily d WHERE d.owner_id=e.owner_id AND d.book_id=w.book_id AND d.business_date=e.business_date)
GROUP BY e.owner_id,w.book_id,e.business_date;

INSERT INTO vocabulary_book_study_record (id,owner_id,book_id,first_studied_at,last_studied_at,study_count,reviewed_count,study_day_count)
SELECT REPLACE(UUID(),'-',''),e.owner_id,w.book_id,MIN(e.occurred_at),MAX(e.occurred_at),COUNT(*),0,COUNT(DISTINCT e.business_date)
FROM learning_event e
JOIN learning_record lr ON lr.id=e.record_id
JOIN vocabulary_book_word w ON w.content_id=lr.content_id
WHERE NOT EXISTS (SELECT 1 FROM vocabulary_book_study_record r WHERE r.owner_id=e.owner_id AND r.book_id=w.book_id)
GROUP BY e.owner_id,w.book_id;
