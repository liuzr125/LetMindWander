-- 仅回滚 V3.11 新增的学习页筛选数据字典。
DELETE FROM app_parameter WHERE param_key IN (
'learning.difficulties','learning.stages','learning.notebook_statuses');
