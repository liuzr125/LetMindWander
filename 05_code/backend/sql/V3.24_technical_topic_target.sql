-- 技术知识主题达标目标；仅参数数据，不新增表或字段。已有设置不覆盖。
INSERT INTO app_parameter(id,param_key,param_value,is_secret,description,state,version_no,created_at,updated_at)
SELECT REPLACE(UUID(),'-',''),'content.technical_topic_target','25',0,'每个系统主题已发布技术知识的达标目标数量','active',1,NOW(3),NOW(3)
WHERE NOT EXISTS (SELECT 1 FROM app_parameter WHERE param_key='content.technical_topic_target');
