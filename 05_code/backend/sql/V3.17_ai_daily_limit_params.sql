-- AI 每日次数上限参数（管理端「系统参数」可维护；不再写在 application.yml）
--   ai.personal_daily_limit：单个用户每天可发起的 AI 请求次数（默认 10）
--   ai.global_daily_limit  ：全站每天 AI 请求总量上限（默认 200）
-- 幂等：按 param_key 唯一键更新；仅当 param_value 变化时 version_no 递增（符合数据字典语义）。
INSERT INTO app_parameter (id, param_key, param_value, is_secret, description, state, version_no, created_at, updated_at) VALUES
('00000000000000000000000000000062', 'ai.personal_daily_limit', '10', 0, 'AI 每日提问次数上限（单个用户）', 'active', 1, NOW(3), NOW(3)),
('00000000000000000000000000000063', 'ai.global_daily_limit', '200', 0, 'AI 每日调用次数上限（全站）', 'active', 1, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
  version_no = IF(param_value = VALUES(param_value), version_no, version_no + 1),
  param_value = VALUES(param_value),
  description = VALUES(description),
  state = VALUES(state),
  updated_at = NOW(3);
