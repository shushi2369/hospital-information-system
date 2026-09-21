-- =====================================================================
-- HIS V18 三期第二批菜单/角色/权限（所有环境加载，《16》§1）
-- =====================================================================
-- 权限码前缀 or:/ris:/emc: 均为新码，无 uk_perm_code 冲突（已对照 V2/V7/V14/V16）
INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
(10, 'OR_NURSE',  '手术室护士', '手术排台/三方核查/阶段推进', 1),
(11, 'RIS_USER',  '影像技师',   '检查预约/执行/影像归档',     1),
(12, 'EMC_NURSE', '急诊分诊护士','急诊分诊/五大中心登记与时间节点', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(200, 0,  '手术麻醉中心', 1, NULL, '/ors', NULL, 55, 1),
(201, 200, '手术工作台', 2, NULL, 'workbench', 'ors/OrsWorkbench', 1, 1),
(202, 201, '手术查询',     3, 'or:request:query',      NULL, NULL, 1, 1),
(203, 201, '手术申请',     3, 'or:request:create',     NULL, NULL, 2, 1),
(204, 201, '手术审核',     3, 'or:request:review',     NULL, NULL, 3, 1),
(205, 201, '排台管理',     3, 'or:schedule:manage',    NULL, NULL, 4, 1),
(206, 201, '核查提交',     3, 'or:check:submit',       NULL, NULL, 5, 1),
(207, 201, '阶段推进',     3, 'or:stage:operate',      NULL, NULL, 6, 1),
(208, 201, '麻醉记录',     3, 'or:anesthesia:write',   NULL, NULL, 7, 1),
(209, 201, '关档记账',     3, 'or:request:complete',   NULL, NULL, 8, 1),
(210, 201, '手术间查询',   3, 'or:room:query',         NULL, NULL, 9, 1),
(211, 0,  '影像检查中心', 1, NULL, '/ris', NULL, 56, 1),
(212, 211, '影像工作台', 2, NULL, 'workbench', 'ris/RisWorkbench', 1, 1),
(213, 212, '检查查询',     3, 'ris:request:query',     NULL, NULL, 1, 1),
(214, 212, '预约管理',     3, 'ris:appt:manage',       NULL, NULL, 2, 1),
(215, 212, '检查执行',     3, 'ris:exam:execute',      NULL, NULL, 3, 1),
(216, 212, '报告书写',     3, 'ris:report:write',      NULL, NULL, 4, 1),
(217, 212, '报告审核',     3, 'ris:report:review',     NULL, NULL, 5, 1),
(218, 212, '报告查询',     3, 'ris:report:query',      NULL, NULL, 6, 1),
(219, 212, '设备查询',     3, 'ris:device:query',      NULL, NULL, 7, 1),
(220, 0,  '急诊五大中心', 1, NULL, '/emc', NULL, 57, 1),
(221, 220, '急诊工作台', 2, NULL, 'workbench', 'emc/EmcWorkbench', 1, 1),
(222, 221, '分诊登记',     3, 'emc:triage:create',     NULL, NULL, 1, 1),
(223, 221, '分诊查询',     3, 'emc:triage:query',      NULL, NULL, 2, 1),
(224, 221, '中心登记',     3, 'emc:visit:create',      NULL, NULL, 3, 1),
(225, 221, '病例查询',     3, 'emc:visit:query',       NULL, NULL, 4, 1),
(226, 221, '节点录入',     3, 'emc:timepoint:record',  NULL, NULL, 5, 1),
(227, 221, '病例关档',     3, 'emc:visit:close',       NULL, NULL, 6, 1),
(228, 221, '达标统计',     3, 'emc:stats:query',       NULL, NULL, 7, 1);

-- 管理员全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 200 AND 228
  AND id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);
-- 医生：手术查询/申请/审核/核查/麻醉记录 + 检查与报告书写审核 + 五大中心登记/节点/关档
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 200), (2, 201), (2, 202), (2, 203), (2, 204), (2, 206), (2, 208),
(2, 211), (2, 212), (2, 213), (2, 216), (2, 217), (2, 218),
(2, 220), (2, 221), (2, 224), (2, 225), (2, 226), (2, 227);
-- 护士：手术/检查/报告查询（执行医嘱自动生成申请，只读查看）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(6, 200), (6, 201), (6, 202), (6, 211), (6, 212), (6, 213), (6, 218);
-- 手术室护士：排台/核查/阶段推进/关档/手术间
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(10, 200), (10, 201), (10, 202), (10, 205), (10, 206), (10, 207), (10, 209), (10, 210);
-- 影像技师：检查查询/预约/执行/报告查询/设备
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(11, 211), (11, 212), (11, 213), (11, 214), (11, 215), (11, 218), (11, 219);
-- 急诊分诊护士：分诊/登记/节点/关档/病例查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(12, 220), (12, 221), (12, 222), (12, 223), (12, 224), (12, 225), (12, 226), (12, 227);
