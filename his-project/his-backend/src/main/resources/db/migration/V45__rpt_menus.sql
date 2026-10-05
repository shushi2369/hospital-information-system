-- 五期-lite：区域上报管理菜单 + 按钮权限（挂公共卫生目录下），绑定 ADMIN / PUB_USER。
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status, created_by)
SELECT id, '区域上报', 2, NULL, 'uploads', 'rpt/RptUploadList', 6, 1, 0
FROM sys_menu WHERE path = '/pub' AND parent_id = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE component = 'rpt/RptUploadList');

SET @rpt_page = (SELECT id FROM sys_menu WHERE component = 'rpt/RptUploadList' LIMIT 1);

INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status, created_by)
SELECT @rpt_page, '上报查询', 3, 'rpt:upload:query', NULL, NULL, 1, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'rpt:upload:query');
INSERT INTO sys_menu (parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status, created_by)
SELECT @rpt_page, '上报重报', 3, 'rpt:upload:retry', NULL, NULL, 2, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'rpt:upload:retry');

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, mn.id FROM sys_role r
JOIN sys_menu mn ON (mn.id = @rpt_page OR mn.parent_id = @rpt_page)
WHERE r.role_code IN ('ADMIN', 'PUB_USER')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu x WHERE x.role_id = r.id AND x.menu_id = mn.id);
