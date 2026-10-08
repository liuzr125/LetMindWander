-- 自有英语短文来源统一为“脑袋开小灶”；仅更新名称，不变更表结构。
-- 两个来源 ID 分别对应分级原创短文和每日 AI 原创短文。
-- 可重复执行；保留来源 ID、类型、许可及内容关联。
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE `content_source`
SET `name` = '脑袋开小灶'
WHERE `id` IN ('e0000000000000000000000000000013', 'e0000000000000000000000000000017')
  AND `name` <> '脑袋开小灶';
COMMIT;

-- 验证当前全部英语短文的来源名称及篇数。
SELECT cs.id, cs.name, cs.source_type, COUNT(lc.id) AS article_count
FROM content_source cs JOIN learning_content lc ON lc.source_id = cs.id
WHERE lc.content_type = 'english_article'
GROUP BY cs.id, cs.name, cs.source_type ORDER BY cs.id;
