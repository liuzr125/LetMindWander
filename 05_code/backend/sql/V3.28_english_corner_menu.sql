-- V3.28 英语角一级菜单。先应用 V3.18 菜单分组迁移；无新增表、列。
-- 仅调整导航归属，不新增或移除任何角色的业务页面权限；可重复执行。
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000c6','group_english','英语角','','En',25,1,NULL,'英语单词、词库批次、英语短文与词书学习记录'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_english');

SET @english_corner_id = (SELECT id FROM admin_menu WHERE code='group_english');
UPDATE admin_menu SET parent_id=@english_corner_id
WHERE code IN ('words','imports','articles','study_records');
UPDATE admin_menu SET description='技术内容、主题、公告与来源管理' WHERE code='group_content';

-- 有英语页面权限的角色仅补授空路径容器，不补授兄弟页面。
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT r.id,g.id FROM admin_role r CROSS JOIN admin_menu g
WHERE g.code='group_english'
  AND (r.code='SUPER_ADMIN' OR EXISTS (
    SELECT 1 FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id
    WHERE rm.role_id=r.id AND m.enabled=1 AND m.code IN ('words','imports','articles','study_records')
  ))
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu existing WHERE existing.role_id=r.id AND existing.menu_id=g.id);
