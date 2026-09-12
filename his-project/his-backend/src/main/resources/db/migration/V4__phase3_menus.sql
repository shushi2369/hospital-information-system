-- =====================================================================
-- HIS V4 阶段三菜单：收费结算(60-66) + 药房工作台(70-80)，所有环境加载
-- =====================================================================

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(60, 40, '收费结算', 2, NULL, 'billing', 'billing/BillingWorkbench', 3, 1),
(61, 60, '收费操作', 3, 'billing:charge:create', NULL, NULL, 1, 1),
(62, 60, '收费查询', 3, 'billing:charge:query',  NULL, NULL, 2, 1),
(63, 60, '退费操作', 3, 'billing:refund:create', NULL, NULL, 3, 1),
(64, 60, '退费查询', 3, 'billing:refund:query',  NULL, NULL, 4, 1),
(65, 60, '日结操作', 3, 'billing:settle:do',     NULL, NULL, 5, 1),
(66, 60, '日结查询', 3, 'billing:settle:query',  NULL, NULL, 6, 1),
(70, 0, '药房工作台', 1, NULL, '/pharmacy', NULL, 30, 1),
(71, 70, '处方审核发药', 2, NULL, 'dispense', 'pharmacy/PharmacyWorkbench', 1, 1),
(72, 71, '审核队列查询', 3, 'pharmacy:review:query',   NULL, NULL, 1, 1),
(73, 71, '处方审核',    3, 'pharmacy:review:do',      NULL, NULL, 2, 1),
(74, 71, '发药队列查询', 3, 'pharmacy:dispense:query', NULL, NULL, 3, 1),
(75, 71, '发药操作',    3, 'pharmacy:dispense:do',    NULL, NULL, 4, 1),
(76, 71, '退药操作',    3, 'pharmacy:return:do',      NULL, NULL, 5, 1),
(77, 71, '退药查询',    3, 'pharmacy:return:query',   NULL, NULL, 6, 1),
(78, 70, '库存管理', 2, NULL, 'inventory', 'pharmacy/InventoryManager', 2, 1),
(79, 78, '库存查询', 3, 'inventory:query',         NULL, NULL, 1, 1),
(80, 78, '入库操作', 3, 'inventory:inbound:create', NULL, NULL, 2, 1);

-- 收费员：收费结算全部
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 60), (3, 61), (3, 62), (3, 63), (3, 64), (3, 65), (3, 66);
-- 药师：药房工作台全部
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(4, 70), (4, 71), (4, 72), (4, 73), (4, 74), (4, 75), (4, 76), (4, 77), (4, 78), (4, 79), (4, 80);
-- 对账员：收费/退费/日结查询（只读）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (5, 62), (5, 64), (5, 66);
-- 管理员：补齐全部新菜单
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu
WHERE id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);
