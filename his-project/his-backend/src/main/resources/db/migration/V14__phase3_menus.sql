-- =====================================================================
-- HIS V14 三期菜单/角色/权限（所有环境加载）
-- =====================================================================
INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
(9, 'LIS_USER', '检验技师', '标本/结果/报告/危急值通知', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(170, 0,  '检验工作台', 1, NULL, '/lis', NULL, 52, 1),
(171, 170, '检验工作台', 2, NULL, 'workbench', 'lis/LisWorkbench', 1, 1),
(172, 171, '检验查询',     3, 'lab:request:query',     NULL, NULL, 1, 1),
(173, 171, '标本采集',     3, 'lab:specimen:collect', NULL, NULL, 2, 1),
(174, 171, '结果录入',     3, 'lab:result:entry',     NULL, NULL, 3, 1),
(175, 171, '报告发布',     3, 'lab:report:publish',   NULL, NULL, 4, 1),
(176, 171, '危急值通知',   3, 'alert:notify',         NULL, NULL, 5, 1),
(177, 170, '危急值处理', 2, NULL, 'alerts', 'lis/AlertList', 2, 1),
(178, 177, '危急值查询',   3, 'alert:query',    NULL, NULL, 1, 1),
(179, 177, '危急值确认',   3, 'alert:confirm',  NULL, NULL, 2, 1),
(180, 170, '危急阈值', 2, NULL, 'thresholds', 'lis/ThresholdList', 3, 1),
(181, 180, '阈值查询',     3, 'lab:threshold:query',  NULL, NULL, 1, 1),
(182, 180, '阈值维护',     3, 'lab:threshold:manage', NULL, NULL, 2, 1),
(183, 0,  '药库管理', 1, NULL, '/whse', NULL, 62, 1),
(184, 183, '采购管理', 2, NULL, 'purchase', 'whse/PurchaseList', 1, 1),
(185, 184, '采购查询',     3, 'whse:po:query',     NULL, NULL, 1, 1),
(186, 184, '采购创建',     3, 'whse:po:create',    NULL, NULL, 2, 1),
(187, 184, '采购审批',     3, 'whse:po:approve',   NULL, NULL, 3, 1),
(188, 183, '供应商字典', 2, NULL, 'suppliers', 'whse/SupplierList', 2, 1),
(189, 188, '供应商维护',   3, 'whse:supplier:manage', NULL, NULL, 1, 1);

-- 角色绑定
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);
-- 检验技师：检验工作台+危急值查询/通知（确认归医生）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(9, 170), (9, 171), (9, 172), (9, 173), (9, 174), (9, 175), (9, 176), (9, 177), (9, 178);
-- 医生：检验报告查询 + 危急值确认
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 171), (2, 172), (2, 177), (2, 178), (2, 179);
-- 护士：标本采集 + 危急值通知登记
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(6, 171), (6, 172), (6, 173), (6, 176);
