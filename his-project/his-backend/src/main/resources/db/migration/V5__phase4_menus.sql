-- =====================================================================
-- HIS V5 阶段四菜单：统计报表(90-92)，所有环境加载
-- =====================================================================

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(90, 0, '统计报表', 1, NULL, '/report', NULL, 40, 1),
(91, 90, '经营统计', 2, NULL, 'dashboard', 'report/ReportDashboard', 1, 1),
(92, 91, '报表查询', 3, 'report:query', NULL, NULL, 1, 1);

-- 管理员/对账员：报表查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 90), (1, 91), (1, 92), (5, 90), (5, 91), (5, 92);
