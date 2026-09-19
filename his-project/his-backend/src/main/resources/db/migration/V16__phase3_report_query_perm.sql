-- V14 遗漏 lab:report:query 权限点（控制器 /lis/reports 使用该码但菜单未建）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(190, 171, '报告查询权限', 3, 'lab:report:query', NULL, NULL, 6, 1);
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, 190 UNION SELECT 2, 190 UNION SELECT 6, 190 UNION SELECT 9, 190 UNION SELECT 5, 190;
