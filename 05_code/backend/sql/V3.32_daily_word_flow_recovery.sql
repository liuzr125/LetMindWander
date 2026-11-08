-- 依赖 V3.31。保存首次选词的计划数量与不足说明，旧快照保持 NULL，不猜测历史目标。
-- MySQL 5.7 幂等迁移；不重排既有任务，不改学习反馈或词条范围。
SET NAMES utf8mb4;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='daily_package' AND COLUMN_NAME='words_target_count')=0,
 'ALTER TABLE daily_package ADD COLUMN words_target_count INT NULL COMMENT ''首次确定词表时的计划数量''','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='daily_package' AND COLUMN_NAME='words_selection_note')=0,
 'ALTER TABLE daily_package ADD COLUMN words_selection_note VARCHAR(200) NULL COMMENT ''首次确定词表的不足或兼容说明''','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
