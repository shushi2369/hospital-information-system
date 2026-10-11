-- V58（一百一十七轮迁移漂移专项）：
-- 修复 V54 两条"绑页面不绑数据码"的半程修复（页面菜单 ≠ 数据源权限码，成对缺口）：
-- 1. 护士(6)：V54 D5 补了危急值处理页(177)，但其列表数据源 GET /alerts、GET /alerts/stats
--    要求 alert:query(178)——缺则页面打开即 403（V54 自己要消灭的缺陷类型）
-- 2. 对账员(5)：V54 D3 补了检验工作台(170/171)与危急值处理(177)三个页面，
--    但无 lab:request:query(172) 与 alert:query(178)——同型死页面；
--    补只读码让页面可用（保留 V54 绑定，最小权限口径下均为查询码）

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, m.id FROM sys_menu m
WHERE m.permission_code = 'alert:query'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=6 AND rm.menu_id=m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 5, m.id FROM sys_menu m
WHERE m.permission_code IN ('lab:request:query', 'alert:query')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=5 AND rm.menu_id=m.id);
