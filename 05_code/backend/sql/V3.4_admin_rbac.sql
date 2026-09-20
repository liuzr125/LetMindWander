-- 管理端账号、角色、菜单和会话（MySQL 5.7.25）。执行后首次登录会按 app.admin-bootstrap-* 创建超级管理员。
-- 生产环境必须设置 ADMIN_BOOTSTRAP_PASSWORD，默认开发密码仅用于本地联调。
CREATE TABLE IF NOT EXISTS `admin_role` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` VARCHAR(80) NOT NULL,
  `description` VARCHAR(255) NULL,
  `is_system` TINYINT(1) NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `sort_order` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_admin_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端角色';

CREATE TABLE IF NOT EXISTS `admin_account` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `username` VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `password_hash` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `role_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `last_login_at` DATETIME(3) NULL,
  `password_updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_admin_account_username` (`username`), KEY `idx_admin_account_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端登录账号';

CREATE TABLE IF NOT EXISTS `admin_menu` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` VARCHAR(80) NOT NULL,
  `path` VARCHAR(120) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `icon` VARCHAR(12) NULL,
  `sort_order` SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  `enabled` TINYINT(1) NOT NULL DEFAULT 1,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_admin_menu_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端菜单';

CREATE TABLE IF NOT EXISTS `admin_role_menu` (
  `role_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `menu_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`role_id`,`menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单授权';

CREATE TABLE IF NOT EXISTS `admin_session` (
  `id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `account_id` CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `token_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `expires_at` DATETIME(3) NOT NULL,
  `revoked_at` DATETIME(3) NULL,
  `last_seen_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`), UNIQUE KEY `uk_admin_session_token` (`token_hash`), KEY `idx_admin_session_active` (`account_id`,`expires_at`,`revoked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端会话，仅保存令牌哈希';

INSERT INTO `admin_role` (`id`,`code`,`name`,`description`,`is_system`,`enabled`,`sort_order`) VALUES
('00000000000000000000000000a1','SUPER_ADMIN','超级管理员','管理账号、角色、菜单及全部业务权限',1,1,10),
('00000000000000000000000000a2','ADMIN','管理员','默认业务管理权限，不含账号与角色权限',0,1,20)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`description`=VALUES(`description`),`is_system`=VALUES(`is_system`),`enabled`=VALUES(`enabled`),`sort_order`=VALUES(`sort_order`);

INSERT INTO `admin_menu` (`id`,`code`,`name`,`path`,`icon`,`sort_order`,`enabled`) VALUES
('00000000000000000000000000b1','overview','概览','/overview','⌂',10,1),
('00000000000000000000000000b2','content','内容与来源','/content','◇',20,1),
('00000000000000000000000000b3','words','英语单词','/words','Aa',30,1),
('00000000000000000000000000b4','imports','词库批次','/vocabulary-imports','⇄',40,1),
('00000000000000000000000000b5','articles','英语短文','/articles','En',50,1),
('00000000000000000000000000b6','users','用户状态','/users','●',60,1),
('00000000000000000000000000b7','jobs','任务与运行','/jobs','□',70,1),
('00000000000000000000000000b8','ai','AI 模型与费用','/ai','AI',80,1),
('00000000000000000000000000bb','ai_audit','AI 使用追溯','/ai-audit','◉',90,1),
('00000000000000000000000000b9','parameters','系统参数','/parameters','⚙',100,1),
('00000000000000000000000000ba','roles','角色与权限','/roles','♙',110,1)
ON DUPLICATE KEY UPDATE `name`=VALUES(`name`),`path`=VALUES(`path`),`icon`=VALUES(`icon`),`sort_order`=VALUES(`sort_order`),`enabled`=VALUES(`enabled`);

-- 超级管理员拥有全部菜单；管理员保留业务菜单，可由超级管理员在 Web 端调整。
INSERT IGNORE INTO `admin_role_menu` (`role_id`,`menu_id`) SELECT '00000000000000000000000000a1',`id` FROM `admin_menu` WHERE `enabled`=1;
INSERT IGNORE INTO `admin_role_menu` (`role_id`,`menu_id`) SELECT '00000000000000000000000000a2',`id` FROM `admin_menu` WHERE `code` IN ('overview','content','words','imports','articles','users','jobs','ai','parameters') AND `enabled`=1;

SELECT r.code,r.name,COUNT(rm.menu_id) menu_count FROM admin_role r LEFT JOIN admin_role_menu rm ON rm.role_id=r.id GROUP BY r.id,r.code,r.name ORDER BY r.sort_order;
