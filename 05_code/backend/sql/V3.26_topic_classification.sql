-- MySQL 5.7：区分技术知识主题与词书标签。仅首次增列时分类，重跑不覆盖人工设置。
SET @topic_type_missing := (SELECT COUNT(*)=0 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='learning_topic' AND COLUMN_NAME='topic_type');
SET @topic_type_ddl := IF(@topic_type_missing,'ALTER TABLE learning_topic ADD COLUMN topic_type VARCHAR(32) NOT NULL DEFAULT ''technical'' COMMENT ''technical技术主题，vocabulary词书标签'' AFTER state','DO 0');
PREPARE topic_type_stmt FROM @topic_type_ddl;
EXECUTE topic_type_stmt;
DEALLOCATE PREPARE topic_type_stmt;
UPDATE learning_topic t SET t.topic_type='vocabulary'
WHERE @topic_type_missing=1 AND t.scope_key='system'
  AND EXISTS(SELECT 1 FROM content_topic ct JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id WHERE ct.topic_id=t.id AND lc.content_type='word')
  AND NOT EXISTS(SELECT 1 FROM content_topic ct JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.id=cv.content_id WHERE ct.topic_id=t.id AND lc.content_type<>'word')
  AND NOT EXISTS(SELECT 1 FROM learning_plan_topic pt WHERE pt.topic_id=t.id);
-- 不修改 content_topic、vocabulary_book_word、学习计划、内容或软删除标记。
