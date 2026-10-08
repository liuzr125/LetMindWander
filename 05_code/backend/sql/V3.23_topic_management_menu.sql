-- 技术知识主题管理：复用 learning_topic/content_topic，不新增表或字段。
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000c5','topics','主题管理','/topics','▤',21,1,
  (SELECT id FROM admin_menu WHERE code='group_content'),'技术知识主题与关联内容统计'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='topics');
-- 承接内容管理权限，之后可独立配置。
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id, target.id
FROM admin_role_menu rm JOIN admin_menu old_menu ON old_menu.id=rm.menu_id
JOIN admin_menu target ON target.code='topics'
WHERE old_menu.code='content'
AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id=target.id);
