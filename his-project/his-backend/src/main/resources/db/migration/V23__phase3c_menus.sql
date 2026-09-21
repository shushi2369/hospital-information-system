-- =====================================================================
-- HIS V23 三期第三批菜单/角色/权限（所有环境加载，《19》§1）
-- =====================================================================
-- 权限码前缀 hr:/mat:/pe:/cdss:/kpi: 均为新码（对照 V2/V7/V14/V18/V20 无冲突）
INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
(13, 'PE_USER', '体检人员', '体检套餐/登记/分项录入', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(230, 0,  '人事管理', 1, NULL, '/hr', NULL, 58, 1),
(231, 230, '员工档案', 2, NULL, 'staff', 'hr/HrStaff', 1, 1),
(232, 231, '员工查询',     3, 'hr:staff:query',  NULL, NULL, 1, 1),
(233, 231, '员工维护',     3, 'hr:staff:manage', NULL, NULL, 2, 1),
(234, 0,  '物资管理', 1, NULL, '/mat', NULL, 59, 1),
(235, 234, '物资工作台', 2, NULL, 'workbench', 'mat/MatWorkbench', 1, 1),
(236, 235, '物资字典',     3, 'mat:material:manage',   NULL, NULL, 1, 1),
(237, 235, '库存查询',     3, 'mat:stock:query',       NULL, NULL, 2, 1),
(238, 235, '采购查询',     3, 'mat:purchase:query',    NULL, NULL, 3, 1),
(239, 235, '采购创建',     3, 'mat:purchase:create',   NULL, NULL, 4, 1),
(240, 235, '采购审批',     3, 'mat:purchase:approve',  NULL, NULL, 5, 1),
(241, 235, '科室领用',     3, 'mat:requisition:create', NULL, NULL, 6, 1),
(242, 235, '领用查询',     3, 'mat:requisition:query', NULL, NULL, 7, 1),
(243, 0,  '体检中心', 1, NULL, '/pe', NULL, 60, 1),
(244, 243, '体检工作台', 2, NULL, 'workbench', 'pe/PeWorkbench', 1, 1),
(245, 244, '套餐维护',     3, 'pe:package:manage',  NULL, NULL, 1, 1),
(246, 244, '登记查询',     3, 'pe:record:query',    NULL, NULL, 2, 1),
(247, 244, '体检登记',     3, 'pe:record:create',   NULL, NULL, 3, 1),
(248, 244, '检查管理',     3, 'pe:record:manage',   NULL, NULL, 4, 1),
(249, 244, '分项录入',     3, 'pe:result:entry',    NULL, NULL, 5, 1),
(250, 244, '总检发布',     3, 'pe:report:publish',  NULL, NULL, 6, 1),
(251, 0,  '临床决策', 1, NULL, '/cdss', NULL, 61, 1),
(252, 251, 'CDSS 命中查询', 2, NULL, 'hits', 'cdss/CdssHits', 1, 1),
(253, 252, '命中查询',     3, 'cdss:hit:query',   NULL, NULL, 1, 1),
(254, 252, '规则维护',     3, 'cdss:rule:manage', NULL, NULL, 2, 1),
(255, 0,  '绩效看板', 1, NULL, '/kpi', NULL, 62, 1),
(256, 255, '国考看板', 2, NULL, 'board', 'report/KpiBoard', 1, 1),
(257, 256, 'KPI 查看',     3, 'kpi:view', NULL, NULL, 1, 1);

-- 管理员全量
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 230 AND 257
  AND id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);
-- 医生：体检总检发布 + CDSS 命中查询 + 物资领用
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 243), (2, 244), (2, 246), (2, 250), (2, 251), (2, 252), (2, 253), (2, 241);
-- 药师：物资字典/采购创建/领用/查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(4, 234), (4, 235), (4, 236), (4, 238), (4, 239), (4, 241), (4, 242);
-- 对账员：绩效看板
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(5, 255), (5, 256), (5, 257);
-- 体检人员：套餐/登记/管理/分项（总检归医生）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(13, 243), (13, 244), (13, 245), (13, 246), (13, 247), (13, 248), (13, 249);
