-- 计划数量上限参数（供 PlanService 校验读取，避免写死代码；不设置时代码回退默认 255）
-- 幂等：按 param_key 唯一键更新；仅当 param_value 变化时 version_no 递增（符合数据字典语义）。
INSERT INTO app_parameter (id, param_key, param_value, is_secret, description, state, version_no, created_at, updated_at) VALUES
('00000000000000000000000000000035', 'plan.tech_count_max', '255', 0, '技术新学每日数量上限', 'active', 1, NOW(3), NOW(3)),
('00000000000000000000000000000036', 'plan.new_word_count_max', '255', 0, '英语新词每日数量上限', 'active', 1, NOW(3), NOW(3)),
('00000000000000000000000000000037', 'plan.review_limit_max', '255', 0, '复习每日数量上限', 'active', 1, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  version_no = IF(param_value = VALUES(param_value), version_no, version_no + 1),
  param_value = VALUES(param_value),
  description = VALUES(description),
  state = VALUES(state),
  updated_at = NOW(3);
