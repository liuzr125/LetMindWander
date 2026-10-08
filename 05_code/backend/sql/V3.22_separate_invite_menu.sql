-- 账号列表与邀请码拆为独立菜单；仅数据变更，不新增表或字段。
UPDATE admin_menu SET name='账号列表',description='用户账号状态、AI 授权与学习进度' WHERE code='users';
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000bf','invites','邀请码','/invites','＋',60,1,
  (SELECT id FROM admin_menu WHERE code='group_users'),'邀请码与准入名额管理'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='invites');
-- 拆分时承接原 users 权限，之后可在角色与权限中分别管理。
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id, target.id
FROM admin_role_menu rm JOIN admin_menu old_menu ON old_menu.id=rm.menu_id
JOIN admin_menu target ON target.code='invites'
WHERE old_menu.code='users'
AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=target.id);
