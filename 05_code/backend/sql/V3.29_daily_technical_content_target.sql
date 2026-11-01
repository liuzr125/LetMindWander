-- 每天北京时间合计新增 10 条：官方 RSS 优先，不足由 AI 原创技术解读补齐。
-- 依赖 V3.3；保留原内容、调度计划、启停状态和历史账本，可重复执行。
SET NAMES utf8mb4;
SET SESSION time_zone = '+00:00';
CREATE TABLE IF NOT EXISTS content_collection_day (
 schedule_id CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 business_date DATE NOT NULL,
 target_count INT NOT NULL DEFAULT 10,
 lease_owner CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
 lease_until DATETIME(3) NULL,
 auto_attempts INT NOT NULL DEFAULT 0,
 next_retry_at DATETIME(3) NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 PRIMARY KEY(schedule_id,business_date),
 KEY idx_collection_day_retry(business_date,next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日技术内容目标与执行租约，不保存 AI 密钥';

SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='content_collection_run' AND COLUMN_NAME='business_date')=0,'ALTER TABLE content_collection_run ADD COLUMN business_date DATE NULL','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='content_collection_run' AND COLUMN_NAME='target_count')=0,'ALTER TABLE content_collection_run ADD COLUMN target_count INT NULL','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='content_collection_run' AND COLUMN_NAME='daily_inserted_count')=0,'ALTER TABLE content_collection_run ADD COLUMN daily_inserted_count INT NULL','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='content_collection_run' AND COLUMN_NAME='generated_count')=0,'ALTER TABLE content_collection_run ADD COLUMN generated_count INT NULL','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='content_collection_run' AND COLUMN_NAME='remaining_count')=0,'ALTER TABLE content_collection_run ADD COLUMN remaining_count INT NULL','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl=IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_content' AND INDEX_NAME='idx_learning_content_source_created')=0,'CREATE INDEX idx_learning_content_source_created ON learning_content(source_id,created_at)','DO 0');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
