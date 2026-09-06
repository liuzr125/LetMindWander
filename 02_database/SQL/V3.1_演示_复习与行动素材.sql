-- 演示数据：为测试账号补齐「复习(review)」与「行动(action)」素材
-- 复习走 review_schedule（个人 SRS 队列），行动走 weekly_action（周总结确认的行动）
-- 可重复执行（固定 ID + ON DUPLICATE KEY）
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET @u = 'b77868671d15460f941e1d90fe613134';

-- ---------- 复习素材：knowledge_item + review_schedule ----------
INSERT INTO `knowledge_item` (`id`,`owner_id`,`item_type`,`title`,`body`,`search_text`,`learning_status`,`verification_status`,`state`) VALUES
('d1000000000000000000000000000001',@u,'note','复习 Java 变量作用域','变量在哪个代码块声明，作用域就覆盖到哪里；局部变量只在方法内可见。','变量在哪个代码块声明，作用域就覆盖到哪里；局部变量只在方法内可见。','learning','unverified','active'),
('d1000000000000000000000000000002',@u,'note','复习 HTTP 状态码','2xx 成功、3xx 重定向、4xx 客户端错误、5xx 服务端错误。','2xx 成功、3xx 重定向、4xx 客户端错误、5xx 服务端错误。','learning','unverified','active'),
('d1000000000000000000000000000003',@u,'note','复习 SQL 索引','索引能加速查询，但会拖慢写入并占用空间，需按查询条件建。','索引能加速查询，但会拖慢写入并占用空间，需按查询条件建。','learning','unverified','active')
ON DUPLICATE KEY UPDATE `state`='active';

INSERT INTO `review_schedule` (`id`,`owner_id`,`knowledge_id`,`state`,`stage`,`due_date`) VALUES
('d2000000000000000000000000000001',@u,'d1000000000000000000000000000001','active',0,'2026-09-06'),
('d2000000000000000000000000000002',@u,'d1000000000000000000000000000002','active',0,'2026-09-05'),
('d2000000000000000000000000000003',@u,'d1000000000000000000000000000003','active',0,'2026-09-04')
ON DUPLICATE KEY UPDATE `state`='active';

-- ---------- 行动素材：weekly_summary + weekly_action ----------
INSERT INTO `weekly_summary` (`id`,`owner_id`,`week_start`,`source_fingerprint`,`is_stale`) VALUES
('d3000000000000000000000000000001',@u,'2026-09-07',UNHEX(SHA2('demo-week-2026-09-07',256)),0)
ON DUPLICATE KEY UPDATE `is_stale`=0;

INSERT INTO `weekly_action` (`id`,`owner_id`,`summary_id`,`action_no`,`title`,`note`,`scheduled_date`,`estimated_minutes`,`state`,`confirmed_at`) VALUES
('d4000000000000000000000000000001',@u,'d3000000000000000000000000000001',1,'整理今天的学习目标','复盘今天的任务完成情况，明确明天重点','2026-09-06',10,'confirmed',NOW(3)),
('d4000000000000000000000000000002',@u,'d3000000000000000000000000000001',2,'列出下一步行动','为明天安排具体的学习计划','2026-09-06',15,'confirmed',NOW(3))
ON DUPLICATE KEY UPDATE `state`='confirmed';
