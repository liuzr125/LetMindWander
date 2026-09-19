INSERT INTO admission_counter (id, scope_code, invited_limit, invited_used)
SELECT '00000000000000000000000000000001', 'trial', 20, 0
WHERE NOT EXISTS (SELECT 1 FROM admission_counter WHERE scope_code = 'trial');

INSERT INTO learning_topic (id, scope_key, owner_id, name, normalized_name, state)
SELECT '00000000000000000000000000000011', 'system', NULL, 'AI', 'ai', 'active'
WHERE NOT EXISTS (SELECT 1 FROM learning_topic WHERE scope_key = 'system' AND normalized_name = 'ai');

INSERT INTO learning_topic (id, scope_key, owner_id, name, normalized_name, state)
SELECT '00000000000000000000000000000012', 'system', NULL, '计算机基础', '计算机基础', 'active'
WHERE NOT EXISTS (SELECT 1 FROM learning_topic WHERE scope_key = 'system' AND normalized_name = '计算机基础');

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
