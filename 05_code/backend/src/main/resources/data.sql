INSERT INTO admission_counter (id, scope_code, invited_limit, invited_used)
SELECT '00000000000000000000000000000001', 'trial', 20, 0
WHERE NOT EXISTS (SELECT 1 FROM admission_counter WHERE scope_code = 'trial');

INSERT INTO learning_topic (id, scope_key, owner_id, name, normalized_name, state)
SELECT '00000000000000000000000000000011', 'system', NULL, 'AI', 'ai', 'active'
WHERE NOT EXISTS (SELECT 1 FROM learning_topic WHERE scope_key = 'system' AND normalized_name = 'ai');

INSERT INTO learning_topic (id, scope_key, owner_id, name, normalized_name, state)
SELECT '00000000000000000000000000000012', 'system', NULL, '计算机基础', '计算机基础', 'active'
WHERE NOT EXISTS (SELECT 1 FROM learning_topic WHERE scope_key = 'system' AND normalized_name = '计算机基础');
