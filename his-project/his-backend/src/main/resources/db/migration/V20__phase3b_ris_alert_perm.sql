-- =====================================================================
-- HIS V20 三期第二批权限补漏（对齐 V11/V16 补漏惯例）
-- =====================================================================
-- RIS_USER（影像技师）发布危急征象报告后需登记危急值通知并跟踪状态：
-- V14 已给检验技师(role 9)/护士(role 6) 绑定危急值通知(176)/查询(178)，漏了影像技师(role 11)。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 11, m.id FROM sys_menu m
WHERE m.id IN (176, 178)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 11 AND rm.menu_id = m.id);
