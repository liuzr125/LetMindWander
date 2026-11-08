-- 英语新词首次点击固定当日随机词表。MySQL 5.7，可重复执行。
-- 现有任务和学习事实保留；旧数据保持 NULL，已有进度的词表首次进入时直接冻结。
SET NAMES utf8mb4;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='daily_package' AND COLUMN_NAME='words_selected_at')=0,
 'ALTER TABLE daily_package ADD COLUMN words_selected_at DATETIME(3) NULL COMMENT ''今日英语新词首次确定时间 UTC''','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='daily_package' AND COLUMN_NAME='words_book_id')=0,
 'ALTER TABLE daily_package ADD COLUMN words_book_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''首次点击时的词书快照''','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
