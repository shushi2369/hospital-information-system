-- V54（一百零五轮权限走查审计 D1/D2/D3/D5）：
-- 角色绑了权限码但对应页面菜单未绑定 → 死权限/首屏 403/过滤失效。
-- 只补 sys_role_menu 绑定行，不改权限码本身。

-- D1: dr.li(2) EMC 工作台 onMounted fetchTriages 403（缺分诊查询权限码/页面）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 223 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=2 AND menu_id=223);

-- D2: nurse.wang(6) 护理工作台病区下拉空（缺床位查询权限码）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, 108 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=6 AND menu_id=108);

-- D5: nurse.wang(6) 危急值通知登记按钮在危急值处理页(177)上，护士未绑该页
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, 177 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=6 AND menu_id=177);

-- D3: auditor.sun(5) 对账员死权限——按 V2/V4 设计意图补只读页面绑定
-- 日志查询(4) → 需同时绑父目录 系统管理(1)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 1 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=1);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 4 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=4);
-- 收费结算(60) + 挂号管理(45) → 需父目录 挂号收费(40)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 40 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=40);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 60 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=60);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 45 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=45);
-- 结算申报(151) → 需父目录 医保对账(150)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 150 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=150);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 151 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=151);
-- 检验工作台(171) + 危急值处理(177) → 需父目录 检验工作台(170)
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 170 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=170);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 171 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=171);
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, 177 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=5 AND menu_id=177);
