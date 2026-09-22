INSERT INTO admission_counter (id, scope_code, invited_limit, invited_used)
SELECT '00000000000000000000000000000001', 'trial', 20, 0
WHERE NOT EXISTS (SELECT 1 FROM admission_counter WHERE scope_code = 'trial');

INSERT INTO learning_topic (id, scope_key, owner_id, name, normalized_name, state)
SELECT '00000000000000000000000000000011', 'system', NULL, 'AI', 'ai', 'active'
WHERE NOT EXISTS (SELECT 1 FROM learning_topic WHERE scope_key = 'system' AND normalized_name = 'ai');

INSERT INTO learning_topic (id, scope_key, owner_id, name, normalized_name, state)
SELECT '00000000000000000000000000000012', 'system', NULL, '计算机基础', '计算机基础', 'active'
WHERE NOT EXISTS (SELECT 1 FROM learning_topic WHERE scope_key = 'system' AND normalized_name = '计算机基础');

INSERT INTO admin_role (id,code,name,description,is_system,enabled,sort_order)
SELECT '00000000000000000000000000a1','SUPER_ADMIN','超级管理员','管理账号、角色、菜单及全部业务权限',1,1,10
WHERE NOT EXISTS (SELECT 1 FROM admin_role WHERE code='SUPER_ADMIN');
INSERT INTO admin_role (id,code,name,description,is_system,enabled,sort_order)
SELECT '00000000000000000000000000a2','ADMIN','管理员','默认业务管理权限，不含账号与角色权限',0,1,20
WHERE NOT EXISTS (SELECT 1 FROM admin_role WHERE code='ADMIN');

INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b1','overview','概览','/overview','⌂',10,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='overview');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b2','content','内容与来源','/content','◇',20,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='content');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b3','words','英语单词','/words','Aa',30,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='words');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b4','imports','词库批次','/vocabulary-imports','⇄',40,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='imports');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b5','articles','英语短文','/articles','En',50,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='articles');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b6','users','用户状态','/users','●',60,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='users');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b7','jobs','任务与运行','/jobs','□',70,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='jobs');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b8','ai','AI 模型与费用','/ai','AI',80,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='ai');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000bb','ai_audit','AI 使用追溯','/ai-audit','◉',90,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='ai_audit');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000b9','parameters','系统参数','/parameters','⚙',100,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='parameters');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled) SELECT '00000000000000000000000000ba','roles','角色与权限','/roles','♙',110,1 WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='roles');

-- 一级菜单（分组）：侧栏按类型折叠展示，一级菜单上带该组的概述
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description) SELECT '00000000000000000000000000c1','group_content','内容与来源','','◇',20,1,NULL,'词书、词条、批次与短文内容的来源与发布状态' WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_content');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description) SELECT '00000000000000000000000000c2','group_users','用户与学习','','●',60,1,NULL,'用户账号状态与个人学习进度' WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_users');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description) SELECT '00000000000000000000000000c3','group_ai','AI 与费用','','AI',80,1,NULL,'模型配置、月预算、用量日志与调用追溯' WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_ai');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description) SELECT '00000000000000000000000000c4','group_system','系统运维','','⚙',100,1,NULL,'任务运行、系统参数与角色权限' WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_system');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description) SELECT '00000000000000000000000000bc','ai_usage','AI 用量日志','/ai-usage','▦',91,1,'00000000000000000000000000c3','每天每个用户的 AI 用量、成功失败与费用' WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='ai_usage');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description) SELECT '00000000000000000000000000bd','feedback','用户反馈','/feedback','✉',61,1,'00000000000000000000000000c2','用户提交的问题反馈正文、详情与处理状态' WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='feedback');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description) SELECT '00000000000000000000000000be','study_records','词书学习记录','/study-records','▤',62,1,'00000000000000000000000000c2','每个用户每本英语词书的学习记录、每日明细与已学词条' WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='study_records');

UPDATE admin_menu SET parent_id='00000000000000000000000000c1' WHERE code IN ('content','words','imports','articles');
UPDATE admin_menu SET parent_id='00000000000000000000000000c2' WHERE code='users';
UPDATE admin_menu SET parent_id='00000000000000000000000000c3' WHERE code IN ('ai','ai_audit','ai_usage');
UPDATE admin_menu SET parent_id='00000000000000000000000000c4' WHERE code IN ('jobs','parameters','roles');

INSERT INTO admin_role_menu (role_id,menu_id)
SELECT '00000000000000000000000000a1',id FROM admin_menu
WHERE NOT EXISTS (SELECT 1 FROM admin_role_menu WHERE role_id='00000000000000000000000000a1' AND menu_id=admin_menu.id);
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT '00000000000000000000000000a2',id FROM admin_menu
WHERE code IN ('overview','content','words','imports','articles','users','feedback','study_records','jobs','ai','ai_audit','ai_usage','parameters','group_content','group_users','group_ai','group_system')
AND NOT EXISTS (SELECT 1 FROM admin_role_menu WHERE role_id='00000000000000000000000000a2' AND menu_id=admin_menu.id);

INSERT INTO ai_model_config (id,provider_code,model_code,display_name,specification,base_url,api_key_param_key,enabled,is_default,max_output_tokens,timeout_seconds)
SELECT '00000000000000000000000000000031','deepseek','deepseek-flash','DeepSeek Flash','flash','https://api.deepseek.com/chat/completions','AI_DEEPSEEK_API_KEY',1,1,1200,60
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE provider_code='deepseek' AND model_code='deepseek-flash');

INSERT INTO ai_model_config (id,provider_code,model_code,display_name,specification,base_url,api_key_param_key,enabled,is_default,max_output_tokens,timeout_seconds)
SELECT '00000000000000000000000000000032','deepseek','deepseek-v4-pro','DeepSeek Pro','pro','https://api.deepseek.com/chat/completions','AI_DEEPSEEK_API_KEY',1,0,1200,60
WHERE NOT EXISTS (SELECT 1 FROM ai_model_config WHERE provider_code='deepseek' AND model_code='deepseek-v4-pro');

INSERT INTO ai_model_price (id,provider_code,model_code,version_no,currency,input_per_million,output_per_million,pricing_json,effective_at)
SELECT '00000000000000000000000000000041','deepseek','deepseek-flash',1,'CNY',2,8,'{"schema":"deepseek-official-v1","source":"https://api-docs.deepseek.com/zh-cn/quick_start/pricing/","verified":true,"currency":"CNY","unit":"per_million_tokens","modelVersion":"DeepSeek-V4.1-Flash","timezone":"Asia/Shanghai","peakWindows":[{"days":"MON-FRI","start":"09:00","end":"12:00"},{"days":"MON-FRI","start":"14:00","end":"18:00"}],"offPeak":{"inputCacheHit":0.02,"inputCacheMiss":1,"output":4},"peak":{"inputCacheHit":0.04,"inputCacheMiss":2,"output":8},"fingerprint":"deepseek-flash|DeepSeek-V4.1-Flash|0.02|1|4|0.04|2|8"}',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_model_price WHERE provider_code='deepseek' AND model_code='deepseek-flash');

INSERT INTO ai_model_price (id,provider_code,model_code,version_no,currency,input_per_million,output_per_million,pricing_json,effective_at)
SELECT '00000000000000000000000000000042','deepseek','deepseek-v4-pro',1,'CNY',9,27,'{"schema":"deepseek-official-v1","source":"https://api-docs.deepseek.com/zh-cn/quick_start/pricing/","verified":true,"currency":"CNY","unit":"per_million_tokens","modelVersion":"DeepSeek-V4-Pro-0813","timezone":"Asia/Shanghai","peakWindows":[{"days":"MON-FRI","start":"09:00","end":"12:00"},{"days":"MON-FRI","start":"14:00","end":"18:00"}],"offPeak":{"inputCacheHit":0.15,"inputCacheMiss":4.5,"output":13.5},"peak":{"inputCacheHit":0.30,"inputCacheMiss":9.0,"output":27.0},"fingerprint":"deepseek-v4-pro|DeepSeek-V4-Pro-0813|0.15|4.5|13.5|0.30|9.0|27.0"}',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_model_price WHERE provider_code='deepseek' AND model_code='deepseek-v4-pro');

INSERT INTO app_parameter(id,param_key,param_value,is_secret,description,state,version_no)
SELECT '00000000000000000000000000000062','tts.aliyun.endpoint','https://nls-gateway-cn-shanghai.aliyuncs.com/stream/v1/tts',0,'Aliyun NLS TTS endpoint','active',1
WHERE NOT EXISTS (SELECT 1 FROM app_parameter WHERE param_key='tts.aliyun.endpoint');
INSERT INTO app_parameter(id,param_key,param_value,is_secret,description,state,version_no)
SELECT '00000000000000000000000000000063','tts.aliyun.voice','aixia',0,'Aliyun NLS English voice','active',1
WHERE NOT EXISTS (SELECT 1 FROM app_parameter WHERE param_key='tts.aliyun.voice');
INSERT INTO app_parameter(id,param_key,param_value,is_secret,description,state,version_no)
SELECT '00000000000000000000000000000064','tts.aliyun.sample_rate','16000',0,'Aliyun NLS sample rate','active',1
WHERE NOT EXISTS (SELECT 1 FROM app_parameter WHERE param_key='tts.aliyun.sample_rate');
