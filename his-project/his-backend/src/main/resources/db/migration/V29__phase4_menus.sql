-- =====================================================================
-- HIS V29 四期菜单/角色/权限（所有环境加载，《22》§1）
-- =====================================================================
INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
(14, 'BB_USER', '血库人员', '用血审核/配血/发血/血袋管理', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(260, 0,  '血库管理', 1, NULL, '/bb', NULL, 63, 1),
(261, 260, '血库工作台', 2, NULL, 'workbench', 'bb/BbWorkbench', 1, 1),
(262, 261, '用血查询',     3, 'bb:request:query',      NULL, NULL, 1, 1),
(263, 261, '用血申请',     3, 'bb:request:create',     NULL, NULL, 2, 1),
(264, 261, '用血审核',     3, 'bb:request:review',     NULL, NULL, 3, 1),
(265, 261, '血袋管理',     3, 'bb:bags:manage',       NULL, NULL, 4, 1),
(266, 261, '交叉配血',     3, 'bb:cross:match',       NULL, NULL, 5, 1),
(267, 261, '发血管理',     3, 'bb:issue:manage',      NULL, NULL, 6, 1),
(268, 261, '输血执行',     3, 'bb:transfusion:execute', NULL, NULL, 7, 1),
(269, 261, '不良反应报告', 3, 'bb:adverse:report',    NULL, NULL, 8, 1),
(270, 261, '物资批次',     3, 'mat:batch:query',      NULL, NULL, 9, 1);

-- 管理员全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 260 AND 270
  AND id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);
-- 医生：用血申请/取消 + 不良反应报告
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 260), (2, 261), (2, 262), (2, 263), (2, 269);
-- 护士：输血执行 + 不良反应报告 + 物资批次查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(6, 260), (6, 261), (6, 262), (6, 268), (6, 269), (6, 270);
-- 血库人员：审核/血袋/配血/发血/查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(14, 260), (14, 261), (14, 262), (14, 264), (14, 265), (14, 266), (14, 267);
