-- 主题软删除：保留主题和关联；所有业务查询排除 del_is=1。
-- MySQL 5.7 幂等增列，不修改既有主题数据。
SET @topic_del_column := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_topic' AND COLUMN_NAME='del_is')=0,'ALTER TABLE learning_topic ADD COLUMN del_is TINYINT NOT NULL DEFAULT 0 COMMENT ''删除标记：0正常，1已删除'' AFTER state','DO 0');
PREPARE topic_del_stmt FROM @topic_del_column;
EXECUTE topic_del_stmt;
DEALLOCATE PREPARE topic_del_stmt;
