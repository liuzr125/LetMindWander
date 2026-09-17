-- 仅回滚 V3.10 新增的数据字典项；V3.3 已有的三个数量上限不删除。
DELETE FROM app_parameter WHERE param_key IN (
'plan.daily_budget_min','plan.daily_budget_max','plan.daily_budget_step','plan.daily_budget_presets',
'plan.weekdays','plan.difficulties','plan.default_daily_budget_min','plan.default_weekdays_mask',
'plan.default_difficulty','plan.default_tech_count','plan.default_new_word_count','plan.default_review_limit',
'plan.default_journal_enabled','plan.default_review_enabled','plan.estimate_tech_seconds',
'plan.estimate_word_seconds','plan.estimate_journal_seconds','plan.estimate_review_seconds');
