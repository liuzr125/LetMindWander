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
SELECT '00000000000000000000000000000041','deepseek','deepseek-flash',1,'CNY',0,0,'{"status":"pending_review","source":"https://api-docs.deepseek.com/quick_start/pricing/"}',CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM ai_model_price WHERE provider_code='deepseek' AND model_code='deepseek-flash');

INSERT INTO ai_model_price (id,provider_code,model_code,version_no,currency,input_per_million,output_per_million,pricing_json,effective_at)
SELECT '00000000000000000000000000000042','deepseek','deepseek-v4-pro',1,'CNY',0,0,'{"status":"pending_review","source":"https://api-docs.deepseek.com/quick_start/pricing/"}',CURRENT_TIMESTAMP
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
