-- V3.18 管理端菜单二级化 + AI 用量日志入口
--   1) admin_menu 增加 parent_id / description（一级菜单=分组，二级菜单=具体页面）
--   2) 新增「AI 用量日志」页面入口
--   3) 给已拥有子菜单的角色补授分组菜单，保证侧栏分组可见
-- 幂等：可重复执行。

-- 幂等：可重复执行（列已存在时跳过 ALTER）。
SET @add_parent := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_menu' AND COLUMN_NAME='parent_id')=0,'ALTER TABLE admin_menu ADD COLUMN parent_id CHAR(32) NULL','DO 0');
PREPARE stmt_parent FROM @add_parent; EXECUTE stmt_parent; DEALLOCATE PREPARE stmt_parent;
SET @add_desc := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_menu' AND COLUMN_NAME='description')=0,'ALTER TABLE admin_menu ADD COLUMN description VARCHAR(200) NULL','DO 0');
PREPARE stmt_desc FROM @add_desc; EXECUTE stmt_desc; DEALLOCATE PREPARE stmt_desc;

INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000c1','group_content','内容与来源','','◇',20,1,NULL,'词书、词条、批次与短文内容的来源与发布状态'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_content');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000c2','group_users','用户与学习','','●',60,1,NULL,'用户账号状态与个人学习进度'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_users');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000c3','group_ai','AI 与费用','','AI',80,1,NULL,'模型配置、月预算、用量日志与调用追溯'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_ai');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000c4','group_system','系统运维','','⚙',100,1,NULL,'任务运行、系统参数与角色权限'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='group_system');
INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000bc','ai_usage','AI 用量日志','/ai-usage','▦',91,1,'00000000000000000000000000c3','每天每个用户的 AI 用量、成功失败与费用'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='ai_usage');

UPDATE admin_menu SET parent_id='00000000000000000000000000c1',
  description=CASE code WHEN 'content' THEN '内容来源、词条与文章明细' WHEN 'words' THEN '词书词条、学段与发音' WHEN 'imports' THEN '词库批次导入与发布' WHEN 'articles' THEN '每日英语短文与讲解' END
  WHERE code IN ('content','words','imports','articles');
UPDATE admin_menu SET parent_id='00000000000000000000000000c2',
  description=CASE code WHEN 'users' THEN '用户状态、额度与学习进度' END WHERE code='users';
UPDATE admin_menu SET parent_id='00000000000000000000000000c3',
  description=CASE code WHEN 'ai' THEN '模型配置与月预算' WHEN 'ai_audit' THEN '按问题追溯模型调用' WHEN 'ai_usage' THEN '每天每个用户的用量与费用' END
  WHERE code IN ('ai','ai_audit','ai_usage');
UPDATE admin_menu SET parent_id='00000000000000000000000000c4',
  description=CASE code WHEN 'jobs' THEN '定时任务与运行结果' WHEN 'parameters' THEN 'AI 额度等运行参数' WHEN 'roles' THEN '角色、账号与菜单权限' END
  WHERE code IN ('jobs','parameters','roles');
UPDATE admin_menu SET description='平台总体数据与待处理事项' WHERE code='overview';

-- 已拥有该分组下任一子菜单的角色，自动补授一级菜单
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id, '00000000000000000000000000c1' FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id
WHERE m.code IN ('content','words','imports','articles')
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id='00000000000000000000000000c1');
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id, '00000000000000000000000000c2' FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id
WHERE m.code='users'
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id='00000000000000000000000000c2');
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id, '00000000000000000000000000c3' FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id
WHERE m.code IN ('ai','ai_audit')
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id='00000000000000000000000000c3');
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id, '00000000000000000000000000c4' FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id
WHERE m.code IN ('jobs','parameters','roles')
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id='00000000000000000000000000c4');
-- 拥有「AI 使用追溯」的角色同时获得「AI 用量日志」
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id, '00000000000000000000000000bc' FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id
WHERE m.code='ai_audit'
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id='00000000000000000000000000bc');
