-- 运行参数表（MySQL 5.7）。敏感值按当前部署要求以明文保存。
CREATE TABLE IF NOT EXISTS app_parameter (
  id CHAR(32) NOT NULL PRIMARY KEY,
  param_key VARCHAR(100) NOT NULL,
  param_value VARCHAR(4096) NOT NULL,
  is_secret TINYINT UNSIGNED NOT NULL DEFAULT 1,
  description VARCHAR(200) NOT NULL,
  state VARCHAR(16) NOT NULL DEFAULT 'active',
  version_no INT UNSIGNED NOT NULL DEFAULT 1,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_app_parameter_key (param_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用运行参数';

-- 将尖括号中的占位值替换为真实值后执行；请勿将填充后的 SQL 提交到 Git。
INSERT INTO app_parameter (id, param_key, param_value, is_secret, description, state, version_no) VALUES
('00000000000000000000000000000031', 'wechat.app_id', '<WECHAT_APP_ID>', 1, '微信小程序 AppID', 'active', 1),
('00000000000000000000000000000032', 'wechat.app_secret', '<WECHAT_APP_SECRET>', 1, '微信小程序 AppSecret', 'active', 1),
('00000000000000000000000000000033', 'oss.access_key_id', '<OSS_ACCESS_KEY_ID>', 1, 'OSS AccessKey ID', 'active', 1),
('00000000000000000000000000000034', 'oss.access_key_secret', '<OSS_ACCESS_KEY_SECRET>', 1, 'OSS AccessKey Secret', 'active', 1)
ON DUPLICATE KEY UPDATE
  param_value = VALUES(param_value),
  is_secret = VALUES(is_secret),
  description = VALUES(description),
  state = 'active',
  version_no = version_no + 1,
  updated_at = CURRENT_TIMESTAMP(3);

-- 应用数据库账号只需要读取参数；不要为小程序、管理端或报表账号授予本表权限。
-- GRANT SELECT ON letMindWander.app_parameter TO 'let_mind_wander_app'@'%';
