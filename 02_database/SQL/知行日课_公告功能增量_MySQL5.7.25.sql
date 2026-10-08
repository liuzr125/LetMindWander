-- 在最新基础结构和管理员权限表已建好后执行；只创建结构和菜单，不发布公告。
CREATE TABLE IF NOT EXISTS announcement (
 id CHAR(32) NOT NULL PRIMARY KEY,
 title VARCHAR(120) NOT NULL,
 body TEXT NOT NULL,
 state VARCHAR(16) NOT NULL DEFAULT 'draft',
 publish_at DATETIME(3) NULL DEFAULT NULL,
 created_by CHAR(32) NOT NULL,
 updated_by CHAR(32) NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 INDEX idx_announcement_public (state,publish_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,parent_id,description,enabled)
SELECT '00000000000000000000000000da','announcements','公告管理','/announcements','✉',55,(SELECT id FROM admin_menu WHERE code='group_content'),'公告草稿、发布与撤回',1
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='announcements');
INSERT INTO admin_role_menu(role_id,menu_id)
SELECT r.id,m.id FROM admin_role r CROSS JOIN admin_menu m
WHERE r.code='SUPER_ADMIN' AND m.code='announcements'
AND NOT EXISTS (SELECT 1 FROM admin_role_menu rm WHERE rm.role_id=r.id AND rm.menu_id=m.id);
