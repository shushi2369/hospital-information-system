-- =====================================================================
-- HIS V7 二期菜单/角色/权限（所有环境加载）
-- =====================================================================
INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
(6, 'NURSE',    '护士',   '医嘱核对执行/体征/排班', 1),
(7, 'MRCLERK',  '病案员', '病案归档/编码/借阅',     1),
(8, 'MEDCLERK', '医保专员','医保申报/对账',          1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(100, 0,  '住院管理', 1, NULL, '/inpatient', NULL, 35, 1),
(101, 100, '入院登记', 2, NULL, 'admissions', 'inpatient/AdmissionList', 1, 1),
(102, 101, '入院登记操作', 3, 'inp:admission:create', NULL, NULL, 1, 1),
(103, 101, '住院查询',     3, 'inp:admission:query',  NULL, NULL, 2, 1),
(104, 101, '押金缴纳',     3, 'inp:deposit:create',   NULL, NULL, 3, 1),
(105, 101, '转科操作',     3, 'inp:transfer:create',  NULL, NULL, 4, 1),
(106, 101, '出院申请',     3, 'inp:discharge:create', NULL, NULL, 5, 1),
(107, 100, '床位管理', 2, NULL, 'beds', 'inpatient/BedBoard', 2, 1),
(108, 107, '床位查询', 3, 'inp:bed:query',   NULL, NULL, 1, 1),
(109, 107, '床位管理', 3, 'inp:bed:manage',  NULL, NULL, 2, 1),
(110, 100, '费用管理', 2, NULL, 'dailyfees', 'inpatient/DailyFeeList', 3, 1),
(111, 110, '一日清查询', 3, 'inp:fee:query',   NULL, NULL, 1, 1),
(112, 110, '手工记账',   3, 'inp:fee:create',  NULL, NULL, 2, 1),
(113, 100, '出院结算', 2, NULL, 'settle', 'inpatient/SettleList', 4, 1),
(114, 113, '出院结算操作(复用收费权限)', 3, NULL, NULL, NULL, 1, 1),
(115, 113, '出院结算查询(复用住院查询权限)', 3, NULL, NULL, NULL, 2, 1),
(120, 50,  '住院医嘱', 2, NULL, 'orders', 'clinic/InpOrders', 3, 1),
(121, 120, '开医嘱/停嘱', 3, 'doc:order:create', NULL, NULL, 1, 1),
(122, 120, '医嘱查询',     3, 'doc:order:query',  NULL, NULL, 2, 1),
(123, 120, '医嘱停嘱操作', 3, 'doc:order:stop',   NULL, NULL, 3, 1),
(125, 50,  'EMR 书写', 2, NULL, 'emr', 'emr/EmrWorkbench', 4, 1),
(126, 125, 'EMR 新建', 3, 'emr:record:create', NULL, NULL, 1, 1),
(127, 125, 'EMR 编辑', 3, 'emr:record:update', NULL, NULL, 2, 1),
(128, 125, 'EMR 提交', 3, 'emr:record:submit', NULL, NULL, 3, 1),
(129, 125, 'EMR 查询', 3, 'emr:record:query',  NULL, NULL, 4, 1),
(130, 0,  '护理工作台', 1, NULL, '/nursing', NULL, 45, 1),
(131, 130, '执行与体征', 2, NULL, 'workbench', 'nursing/NursingWorkbench', 1, 1),
(132, 131, '医嘱核对执行', 3, 'nur:exec:do',      NULL, NULL, 1, 1),
(133, 131, '体征录入',     3, 'nur:vital:create', NULL, NULL, 2, 1),
(134, 131, '体征查询',     3, 'nur:vital:query',  NULL, NULL, 3, 1),
(135, 130, '排班管理', 2, NULL, 'schedules', 'nursing/ScheduleList', 2, 1),
(136, 135, '排班管理', 3, 'nur:schedule:manage', NULL, NULL, 1, 1),
(140, 0,  '病案管理', 1, NULL, '/mrc', NULL, 55, 1),
(141, 140, '病案管理', 2, NULL, 'records', 'mrc/MrcList', 1, 1),
(142, 141, '病案查询',     3, 'mrc:archive:query',   NULL, NULL, 1, 1),
(143, 141, '病案归档',     3, 'mrc:archive:do',      NULL, NULL, 2, 1),
(144, 141, '首页编码',     3, 'mrc:homepage:code',   NULL, NULL, 3, 1),
(145, 141, '首页质控',     3, 'mrc:homepage:qc',     NULL, NULL, 4, 1),
(146, 141, '病案借阅',     3, 'mrc:borrow:create',   NULL, NULL, 5, 1),
(150, 0,  '医保对账', 1, NULL, '/medins', NULL, 65, 1),
(151, 150, '结算申报', 2, NULL, 'settles', 'medins/SettleList', 1, 1),
(152, 151, '医保申报',     3, 'medins:settle:create',    NULL, NULL, 1, 1),
(153, 151, '医保查询',     3, 'medins:settle:query',     NULL, NULL, 2, 1),
(154, 151, '医保对账',     3, 'medins:settle:reconcile', NULL, NULL, 3, 1),
(160, 1,  '患者主索引', 2, NULL, 'empi', 'plt/EmpiSearch', 4, 1),
(161, 160, '主索引查询', 3, 'plt:index:query', NULL, NULL, 1, 1),
(162, 160, '事件查询',   3, 'plt:event:query', NULL, NULL, 2, 1),
(163, 160, '患者合并',   3, 'plt:index:merge', NULL, NULL, 3, 1);

-- 角色绑定
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu WHERE id NOT IN (SELECT menu_id FROM sys_role_menu WHERE role_id = 1);
-- 医生：住院医嘱+EMR+住院查询/出院申请
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 120), (2, 121), (2, 122), (2, 123), (2, 125), (2, 126), (2, 127), (2, 128), (2, 129), (2, 103), (2, 106);
-- 收费员：住院管理全部
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 100), (3, 101), (3, 102), (3, 103), (3, 104), (3, 105), (3, 106), (3, 107), (3, 108), (3, 109), (3, 110), (3, 111), (3, 112), (3, 113), (3, 114), (3, 115);
-- 护士：护理工作台
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(6, 130), (6, 131), (6, 132), (6, 133), (6, 134), (6, 135), (6, 136);
-- 病案员：病案管理
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(7, 140), (7, 141), (7, 142), (7, 143), (7, 144), (7, 145), (7, 146);
-- 医保专员：医保对账
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(8, 150), (8, 151), (8, 152), (8, 153), (8, 154);
-- 药师：住院医嘱审核（复用药房审核权限码）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (4, 120), (4, 122);
-- 对账员：医保查询+事件查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (5, 153), (5, 162);

-- 演示护士/病案员/医保专员账号在 db/demo V8 提供
