-- 计划设置数据字典 / MySQL 5.7.25
-- 页面只读取 /api/plans/settings 返回的白名单结构；不直接暴露 app_parameter。
SET NAMES utf8mb4;

INSERT INTO app_parameter (id,param_key,param_value,is_secret,description,state,version_no,created_at,updated_at) VALUES
('00000000000000000000000000000041','plan.daily_budget_min','1',0,'每日学习时间最小值（分钟）','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000042','plan.daily_budget_max','1440',0,'每日学习时间最大值（分钟）','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000043','plan.daily_budget_step','5',0,'每日学习时间加减步长（分钟）','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000044','plan.daily_budget_presets','[5,10,15,20,30]',0,'每日学习时间快捷值 JSON','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000045','plan.weekdays','[{"value":"0","label":"一"},{"value":"1","label":"二"},{"value":"2","label":"三"},{"value":"3","label":"四"},{"value":"4","label":"五"},{"value":"5","label":"六"},{"value":"6","label":"日"}]',0,'学习日选项 JSON；value 为周一至周日的位序','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000046','plan.difficulties','[{"value":"intro","label":"入门"},{"value":"advanced","label":"进阶"}]',0,'学习难度选项 JSON','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000047','plan.default_daily_budget_min','10',0,'默认每日学习分钟数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000048','plan.default_weekdays_mask','31',0,'默认学习日位图','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000049','plan.default_difficulty','intro',0,'默认学习难度','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000050','plan.default_tech_count','1',0,'默认每日技术新学数量','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000051','plan.default_new_word_count','3',0,'默认每日英语新词数量','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000052','plan.default_review_limit','5',0,'默认每日复习上限','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000053','plan.default_journal_enabled','true',0,'默认启用每日复盘','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000054','plan.default_review_enabled','true',0,'默认启用到期复习','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000055','plan.estimate_tech_seconds','180',0,'前端预算提示：每条技术内容预计秒数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000056','plan.estimate_word_seconds','30',0,'前端预算提示：每个英语新词预计秒数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000057','plan.estimate_journal_seconds','120',0,'前端预算提示：每日复盘预计秒数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000058','plan.estimate_review_seconds','30',0,'前端预算提示：每项复习预计秒数','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000035','plan.tech_count_max','255',0,'技术新学每日数量上限','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000036','plan.new_word_count_max','255',0,'英语新词每日数量上限','active',1,NOW(3),NOW(3)),
('00000000000000000000000000000037','plan.review_limit_max','255',0,'复习每日数量上限','active',1,NOW(3),NOW(3))
ON DUPLICATE KEY UPDATE
 version_no=IF(param_value=VALUES(param_value),version_no,version_no+1),
 param_value=VALUES(param_value),is_secret=0,description=VALUES(description),state='active',updated_at=NOW(3);
