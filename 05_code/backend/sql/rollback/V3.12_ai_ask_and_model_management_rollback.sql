-- 回滚仅移除 V3.12 新增的模型配置；AI 日志与费用表为既有基础表，不删除。
DROP TABLE IF EXISTS `ai_model_config`;
