-- 知行日课 V3.5 学习页兼容增量（MySQL 5.7.25）
-- 原则：仅补缺失字段并插入幂等示例短文；不删除、不重建任何现有表。
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION time_zone = '+00:00';

-- V3.1 已包含 stage；此处为漏跑历史增量的环境兜底。
SET @has_stage := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='learning_content' AND column_name='stage');
SET @ddl_stage := IF(@has_stage=0,
  'ALTER TABLE `learning_content` ADD COLUMN `stage` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT ''学段：primary/junior/senior，仅 word 使用''',
  'DO 1');
PREPARE stmt_stage FROM @ddl_stage; EXECUTE stmt_stage; DEALLOCATE PREPARE stmt_stage;

SET @has_stage_idx := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='learning_content' AND index_name='idx_stage');
SET @ddl_stage_idx := IF(@has_stage_idx=0,
  'ALTER TABLE `learning_content` ADD KEY `idx_stage` (`content_type`,`stage`,`state`,`published_at`)',
  'DO 1');
PREPARE stmt_stage_idx FROM @ddl_stage_idx; EXECUTE stmt_stage_idx; DEALLOCATE PREPARE stmt_stage_idx;

-- V3.0 UI 对齐增量已包含 article_blocks；此处同样只在缺失时添加。
SET @has_article_blocks := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='content_version' AND column_name='article_blocks');
SET @ddl_article_blocks := IF(@has_article_blocks=0,
  'ALTER TABLE `content_version` ADD COLUMN `article_blocks` JSON NULL COMMENT ''英语短文段落：paragraph_id、text、translation、显式关联词条''',
  'DO 1');
PREPARE stmt_article_blocks FROM @ddl_article_blocks; EXECUTE stmt_article_blocks; DEALLOCATE PREPARE stmt_article_blocks;

START TRANSACTION;

INSERT INTO `content_source` (`id`,`name`,`source_type`,`url`,`license_note`,`enabled`) VALUES
('e0000000000000000000000000000001','知行日课原创英语短文','manual',NULL,'原创学习短文，可在本产品内展示与学习',1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`license_note`=VALUES(`license_note`),`enabled`=1;

INSERT INTO `learning_topic` (`id`,`scope_key`,`owner_id`,`name`,`normalized_name`,`state`) VALUES
('e0000000000000000000000000000002','system',NULL,'技术英语','技术英语','active')
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`state`='active';

INSERT INTO `learning_content` (`id`,`content_type`,`source_id`,`dedup_hash`,`state`,`current_version_id`,`published_version_id`,`published_at`,`row_version`) VALUES
('e1000000000000000000000000000001','english_article','e0000000000000000000000000000001',UNHEX(SHA2('article:how-rag-works:v1',256)),'published','e2000000000000000000000000000001','e2000000000000000000000000000001',UTC_TIMESTAMP(3),1),
('e1000000000000000000000000000002','english_article','e0000000000000000000000000000001',UNHEX(SHA2('article:understanding-context:v1',256)),'published','e2000000000000000000000000000002','e2000000000000000000000000000002',UTC_TIMESTAMP(3),1),
('e1000000000000000000000000000003','english_article','e0000000000000000000000000000001',UNHEX(SHA2('article:a-better-learning-habit:v1',256)),'published','e2000000000000000000000000000003','e2000000000000000000000000000003',UTC_TIMESTAMP(3),1)
ON DUPLICATE KEY UPDATE `state`='published',`current_version_id`=VALUES(`current_version_id`),`published_version_id`=VALUES(`published_version_id`),`published_at`=VALUES(`published_at`);

INSERT INTO `content_version` (`id`,`content_id`,`version_no`,`title`,`summary`,`body`,`difficulty`,`estimated_seconds`,`origin_author`,`license_snapshot`,`body_hash`,`review_status`,`reviewed_at`,`created_by`,`article_blocks`) VALUES
('e2000000000000000000000000000001','e1000000000000000000000000000001',1,'How RAG Works','用一篇短文理解检索增强生成','RAG helps a model find useful information before it answers a question.\n\nFirst, the system retrieves relevant documents. These documents provide context for the model.\n\nThe model then uses this context to create a more useful answer.','intro',180,'知行日课','原创学习短文',UNHEX(SHA2('how-rag-works-v1',256)),'approved',UTC_TIMESTAMP(3),'00000000000000000000000000000002',JSON_ARRAY(
 JSON_OBJECT('paragraph_id','p1','text','RAG helps a model find useful information before it answers a question.','translation','RAG 帮助模型在回答问题前找到有用的信息。','words',JSON_ARRAY()),
 JSON_OBJECT('paragraph_id','p2','text','First, the system retrieves relevant documents. These documents provide context for the model.','translation','首先，系统检索相关文档。这些文档为模型提供上下文。','words',JSON_ARRAY(JSON_OBJECT('content_id','2801816f69dbe4e0211027f8c2617bd9','term','context','meaning','上下文'))),
 JSON_OBJECT('paragraph_id','p3','text','The model then uses this context to create a more useful answer.','translation','随后，模型利用这些上下文生成更有用的回答。','words',JSON_ARRAY(JSON_OBJECT('content_id','2801816f69dbe4e0211027f8c2617bd9','term','context','meaning','上下文')))
)),
('e2000000000000000000000000000002','e1000000000000000000000000000002',1,'Understanding Context','理解上下文在模型回答中的作用','Context tells us what words and ideas mean in a specific situation.\n\nA model needs enough context to connect a question with the right evidence.\n\nClear context reduces ambiguity and makes an answer easier to verify.','intro',120,'知行日课','原创学习短文',UNHEX(SHA2('understanding-context-v1',256)),'approved',UTC_TIMESTAMP(3),'00000000000000000000000000000002',JSON_ARRAY(
 JSON_OBJECT('paragraph_id','p1','text','Context tells us what words and ideas mean in a specific situation.','translation','上下文告诉我们词语和观点在具体情境中的含义。','words',JSON_ARRAY(JSON_OBJECT('content_id','2801816f69dbe4e0211027f8c2617bd9','term','context','meaning','上下文'))),
 JSON_OBJECT('paragraph_id','p2','text','A model needs enough context to connect a question with the right evidence.','translation','模型需要足够的上下文，才能把问题与正确证据联系起来。','words',JSON_ARRAY()),
 JSON_OBJECT('paragraph_id','p3','text','Clear context reduces ambiguity and makes an answer easier to verify.','translation','清晰的上下文可以减少歧义，也让回答更容易核验。','words',JSON_ARRAY())
)),
('e2000000000000000000000000000003','e1000000000000000000000000000003',1,'A Better Learning Habit','让每天的积累成为习惯','A small daily goal is easier to start than a large plan.\n\nRead one idea, explain it in your own words, and save one useful note.\n\nConsistent practice turns separate facts into knowledge you can use.','advanced',240,'知行日课','原创学习短文',UNHEX(SHA2('a-better-learning-habit-v1',256)),'approved',UTC_TIMESTAMP(3),'00000000000000000000000000000002',JSON_ARRAY(
 JSON_OBJECT('paragraph_id','p1','text','A small daily goal is easier to start than a large plan.','translation','一个小的每日目标，比宏大的计划更容易开始。','words',JSON_ARRAY()),
 JSON_OBJECT('paragraph_id','p2','text','Read one idea, explain it in your own words, and save one useful note.','translation','阅读一个观点，用自己的话解释它，再保存一条有用的笔记。','words',JSON_ARRAY()),
 JSON_OBJECT('paragraph_id','p3','text','Consistent practice turns separate facts into knowledge you can use.','translation','持续练习会把零散事实转化为可以使用的知识。','words',JSON_ARRAY())
))
ON DUPLICATE KEY UPDATE `title`=VALUES(`title`),`summary`=VALUES(`summary`),`body`=VALUES(`body`),`difficulty`=VALUES(`difficulty`),`estimated_seconds`=VALUES(`estimated_seconds`),`article_blocks`=VALUES(`article_blocks`),`review_status`='approved';

INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`)
SELECT 'e3000000000000000000000000000001','e2000000000000000000000000000001',t.id FROM learning_topic t WHERE t.scope_key='system' AND t.normalized_name='技术英语'
ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`)
SELECT 'e3000000000000000000000000000002','e2000000000000000000000000000002',t.id FROM learning_topic t WHERE t.scope_key='system' AND t.normalized_name='技术英语'
ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);
INSERT INTO `content_topic` (`id`,`content_version_id`,`topic_id`)
SELECT 'e3000000000000000000000000000003','e2000000000000000000000000000003',t.id FROM learning_topic t WHERE t.scope_key='system' AND t.normalized_name='技术英语'
ON DUPLICATE KEY UPDATE `topic_id`=VALUES(`topic_id`);

COMMIT;

-- 验收查询：应返回 stage/article_blocks 两行字段检查，以及 3 条已发布短文。
SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='learning_content' AND column_name='stage') OR (table_name='content_version' AND column_name='article_blocks')) ORDER BY table_name,column_name;
SELECT lc.id,cv.title,cv.difficulty,JSON_LENGTH(cv.article_blocks) AS paragraph_count FROM learning_content lc JOIN content_version cv ON cv.id=lc.published_version_id WHERE lc.content_type='english_article' AND lc.state='published' ORDER BY lc.published_at DESC,lc.id;
