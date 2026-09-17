-- 学习页筛选数据字典 / MySQL 5.7.25
-- 页面只读取 /api/learning/filters 返回的白名单结构；不直接暴露 app_parameter。
SET NAMES utf8mb4;

INSERT INTO app_parameter (id,param_key,param_value,is_secret,description,state,version_no,created_at,updated_at) VALUES
('00000000000000000000000000000059','learning.difficulties','[{"value":"intro","label":"入门"},{"value":"advanced","label":"进阶"}]',0,'学习内容难度筛选 JSON','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000060','learning.stages','[{"value":"primary","label":"小学"},{"value":"junior","label":"初中"},{"value":"senior","label":"高中"}]',0,'英语词汇学段筛选 JSON','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000061','learning.notebook_statuses','[{"value":"review","label":"待复习"},{"value":"familiar","label":"已熟悉"}]',0,'生词本状态筛选 JSON','active',1,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE
 version_no=IF(param_value=VALUES(param_value),version_no,version_no+1),
 param_value=VALUES(param_value),is_secret=0,description=VALUES(description),state='active',updated_at=NOW(3);
