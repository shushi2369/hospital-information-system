-- =====================================================================
-- HIS V2 核心初始化数据（所有环境加载：角色/菜单权限/管理员）
-- 密码占位 'INIT:His@2026' 由应用启动时 InitPasswordRunner 替换为 BCrypt 散列
-- =====================================================================

-- ---------- 角色 ----------
INSERT INTO sys_role (id, role_code, role_name, description, status) VALUES
(1, 'ADMIN',      '管理员', '系统与基础数据管理', 1),
(2, 'DOCTOR',     '医生',   '门诊接诊',           1),
(3, 'CASHIER',    '收费员', '挂号收费窗口',       1),
(4, 'PHARMACIST', '药师',   '药房审核发药库存',   1),
(5, 'AUDITOR',    '对账员', '财务复核(只读)',     1);

-- ---------- 菜单与权限点 ----------
-- 阶段一：系统管理(1-11) + 基础资料(20-35)；阶段二：挂号收费(40-48) + 医生工作站(50-59)
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(1,  0, '系统管理',  1, NULL, '/system', NULL, 10, 1),
(2,  1, '用户管理',  2, NULL, 'users', 'system/UserList', 1, 1),
(3,  1, '角色管理',  2, NULL, 'roles', 'system/RoleList', 2, 1),
(4,  1, '日志查询',  2, NULL, 'logs', 'system/LogList', 3, 1),
(5,  2, '用户查询',  3, 'sys:user:query',   NULL, NULL, 1, 1),
(6,  2, '用户新增',  3, 'sys:user:create',  NULL, NULL, 2, 1),
(7,  2, '用户修改',  3, 'sys:user:update',  NULL, NULL, 3, 1),
(8,  2, '用户管理(重置密码/启停)', 3, 'sys:user:manage', NULL, NULL, 4, 1),
(9,  3, '角色查询',  3, 'sys:role:query',   NULL, NULL, 1, 1),
(10, 3, '角色管理',  3, 'sys:role:manage',  NULL, NULL, 2, 1),
(11, 4, '日志查询',  3, 'sys:log:query',    NULL, NULL, 1, 1),
(20, 0, '基础资料',  1, NULL, '/basedata', NULL, 20, 1),
(21, 20, '机构信息', 2, NULL, 'org', 'basedata/OrgForm', 1, 1),
(22, 21, '机构查询', 3, 'basedata:org:query',   NULL, NULL, 1, 1),
(23, 21, '机构维护', 3, 'basedata:org:manage',  NULL, NULL, 2, 1),
(24, 20, '科室管理', 2, NULL, 'departments', 'basedata/DepartmentList', 2, 1),
(25, 24, '科室查询', 3, 'basedata:dept:query',   NULL, NULL, 1, 1),
(26, 24, '科室维护', 3, 'basedata:dept:manage',  NULL, NULL, 2, 1),
(27, 20, '医生管理', 2, NULL, 'doctors', 'basedata/DoctorList', 3, 1),
(28, 27, '医生查询', 3, 'basedata:doctor:query',  NULL, NULL, 1, 1),
(29, 27, '医生维护', 3, 'basedata:doctor:manage', NULL, NULL, 2, 1),
(30, 20, '药品管理', 2, NULL, 'drugs', 'basedata/DrugList', 4, 1),
(31, 30, '药品查询', 3, 'basedata:drug:query',   NULL, NULL, 1, 1),
(32, 30, '药品维护', 3, 'basedata:drug:manage',  NULL, NULL, 2, 1),
(33, 20, '收费项目', 2, NULL, 'charge-items', 'basedata/ChargeItemList', 5, 1),
(34, 33, '项目查询', 3, 'basedata:item:query',   NULL, NULL, 1, 1),
(35, 33, '项目维护', 3, 'basedata:item:manage',  NULL, NULL, 2, 1),
(40, 0, '挂号收费',  1, NULL, '/regdesk', NULL, 15, 1),
(41, 40, '患者建档', 2, NULL, 'patients', 'patient/PatientArchive', 1, 1),
(42, 41, '患者建档操作', 3, 'patient:archive:create', NULL, NULL, 1, 1),
(43, 41, '患者查询', 3, 'patient:archive:query',  NULL, NULL, 2, 1),
(44, 41, '患者档案修改/补卡', 3, 'patient:archive:update', NULL, NULL, 3, 1),
(45, 40, '挂号管理', 2, NULL, 'registrations', 'registration/RegistrationList', 2, 1),
(46, 45, '挂号操作', 3, 'reg:ticket:create', NULL, NULL, 1, 1),
(47, 45, '退号操作', 3, 'reg:ticket:cancel', NULL, NULL, 2, 1),
(48, 45, '挂号查询', 3, 'reg:ticket:query',  NULL, NULL, 3, 1),
(50, 0, '医生工作站', 1, NULL, '/clinic', NULL, 25, 1),
(51, 50, '候诊队列', 2, NULL, 'queue', 'clinic/ClinicQueue', 1, 1),
(52, 50, '就诊工作台', 2, NULL, 'workbench', 'clinic/VisitWorkbench', 2, 1),
(53, 52, '就诊查询', 3, 'clinic:visit:query',        NULL, NULL, 1, 1),
(54, 52, '接诊操作', 3, 'clinic:visit:do',           NULL, NULL, 2, 1),
(55, 52, '病历编辑', 3, 'clinic:record:update',      NULL, NULL, 3, 1),
(56, 52, '病历提交', 3, 'clinic:record:submit',      NULL, NULL, 4, 1),
(57, 52, '诊断/医嘱维护', 3, 'clinic:diagnosis:create', NULL, NULL, 5, 1),
(58, 52, '开处方/作废', 3, 'clinic:prescription:create', NULL, NULL, 6, 1),
(59, 52, '检查检验申请', 3, 'clinic:exam:create',      NULL, NULL, 7, 1);

-- ---------- 角色-菜单绑定（对照《04 权限与安全设计》§3 矩阵） ----------
-- 管理员：全部
INSERT INTO sys_role_menu (role_id, menu_id) SELECT 1, id FROM sys_menu;
-- 医生：医生工作站全部 + 基础资料查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(2, 50), (2, 51), (2, 52), (2, 53), (2, 54), (2, 55), (2, 56), (2, 57), (2, 58), (2, 59),
(2, 22), (2, 25), (2, 28), (2, 31), (2, 34);
-- 收费员：挂号收费全部 + 基础资料查询
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(3, 40), (3, 41), (3, 42), (3, 43), (3, 44), (3, 45), (3, 46), (3, 47), (3, 48),
(3, 22), (3, 25), (3, 28), (3, 31), (3, 34);
-- 药师：基础资料查询（药房功能随阶段三扩充）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (4, 22), (4, 25), (4, 28), (4, 31), (4, 34);
-- 对账员：基础资料查询 + 日志查询 + 挂号单只读
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (5, 22), (5, 25), (5, 28), (5, 31), (5, 34), (5, 11), (5, 48);

-- ---------- 管理员账号 ----------
INSERT INTO sys_user (id, username, password_hash, real_name, phone, status) VALUES
(1, 'admin', 'INIT:His@2026', '系统管理员', NULL, 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);
