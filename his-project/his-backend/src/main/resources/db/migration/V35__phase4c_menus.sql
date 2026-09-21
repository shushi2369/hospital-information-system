-- =====================================================================
-- HIS V35 四期三批菜单/权限/演示数据
-- =====================================================================
INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
(15, 'PUB_USER', '公卫人员', '传染病审核上报/院感确认', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(273, 0,  '公共卫生', 1, NULL, '/pub', NULL, 64, 1),
(274, 273, '公卫工作台', 2, NULL, 'workbench', 'pub/PubWorkbench', 1, 1),
(275, 274, '报告卡查询',   3, 'pub:card:query',   NULL, NULL, 1, 1),
(276, 274, '上报登记',     3, 'pub:card:report',  NULL, NULL, 2, 1),
(277, 274, '回执登记',     3, 'pub:card:receipt', NULL, NULL, 3, 1),
(278, 274, '院感确认',     3, 'pub:hai:confirm',  NULL, NULL, 4, 1),
(279, 0,  '不良事件', 1, NULL, '/ae', NULL, 65, 1),
(280, 279, '不良事件管理', 2, NULL, 'events', 'ae/AeEvents', 1, 1),
(281, 280, '事件上报',     3, 'ae:report',   NULL, NULL, 1, 1),
(282, 280, '质控分派',     3, 'ae:qc:assign', NULL, NULL, 2, 1),
(283, 280, '闭环确认',     3, 'ae:close',    NULL, NULL, 3, 1),
(284, 280, '事件查询',     3, 'ae:query',    NULL, NULL, 4, 1),
(285, 0,  '会诊管理', 1, NULL, '/cnt', NULL, 66, 1),
(286, 285, '会诊工作台', 2, NULL, 'workbench', 'cnt/CntWorkbench', 1, 1),
(287, 286, '会诊申请',     3, 'cnt:request',  NULL, NULL, 1, 1),
(288, 286, '会诊执行',     3, 'cnt:execute',  NULL, NULL, 2, 1),
(289, 286, '会诊查询',     3, 'cnt:query',    NULL, NULL, 3, 1);

-- 管理员全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 273 AND 289
  AND id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);
-- 医生：报卡/会诊/不良事件上报/查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 273), (2, 274), (2, 275), (2, 279), (2, 280), (2, 281), (2, 284),
(2, 285), (2, 286), (2, 287), (2, 289);
-- 护士：不良事件上报/查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(6, 279), (6, 280), (6, 281), (6, 284);
-- 公卫人员：报告卡全流程 + 院感确认
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(15, 273), (15, 274), (15, 275), (15, 276), (15, 277), (15, 278);

-- 传染病字典（甲/乙类样例）
INSERT INTO pub_disease_dict (disease_name, category, status) VALUES
('霍乱', '甲', 1), ('肺结核', '乙', 1), ('病毒性肝炎', '乙', 1),
('艾滋病', '乙', 1), ('麻疹', '乙', 1), ('流行性感冒', '丙', 1);

-- 不良事件演示（1 条已闭环样例）
INSERT INTO ae_event (event_no, event_type, severity, department_id, event_time, description, reporter_id, status) VALUES
('AE2026001', 2, 1, 1, NOW(), '病房内患者跌倒（演示样例，已闭环）', 1, 40);
