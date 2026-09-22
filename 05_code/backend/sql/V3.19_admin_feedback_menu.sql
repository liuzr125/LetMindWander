-- V3.19 管理端「用户反馈」菜单
--   概览页「待处理事项」统计的是 user_feedback.state='open'，这里给管理端一个入口查看反馈正文与详情。
-- 幂等：可重复执行。

INSERT INTO admin_menu (id,code,name,path,icon,sort_order,enabled,parent_id,description)
SELECT '00000000000000000000000000bd','feedback','用户反馈','/feedback','✉',61,1,'00000000000000000000000000c2','用户提交的问题反馈正文、详情与处理状态'
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE code='feedback');

-- 已拥有「用户状态」的角色自动获得「用户反馈」
INSERT INTO admin_role_menu (role_id,menu_id)
SELECT DISTINCT rm.role_id,'00000000000000000000000000bd' FROM admin_role_menu rm JOIN admin_menu m ON m.id=rm.menu_id
WHERE m.code='users'
  AND NOT EXISTS (SELECT 1 FROM admin_role_menu x WHERE x.role_id=rm.role_id AND x.menu_id='00000000000000000000000000bd');
