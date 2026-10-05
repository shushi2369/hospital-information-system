-- =====================================================================
-- HIS V3 演示数据（仅 dev/test 加载；生产不加载本目录）
-- 演示账号密码占位 'INIT:His@2026' 由启动组件替换为 BCrypt 散列
-- =====================================================================

-- ---------- 演示账号 ----------
INSERT INTO sys_user (id, username, password_hash, real_name, phone, status) VALUES
(2, 'dr.wang',    'INIT:His@2026', '王志远', '13800000002', 1),
(3, 'dr.li',      'INIT:His@2026', '李建国', '13800000003', 1),
(4, 'dr.chen',    'INIT:His@2026', '陈静',   '13800000004', 1),
(5, 'cashier.li', 'INIT:His@2026', '李楠',   '13800000005', 1),
(6, 'pharm.zhao', 'INIT:His@2026', '赵敏',   '13800000006', 1),
(7, 'auditor.sun','INIT:His@2026', '孙平',   '13800000007', 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
(2, 2), (3, 2), (4, 2), (5, 3), (6, 4), (7, 5);

-- ---------- 组织与科室 ----------
INSERT INTO bas_organization (id, org_code, org_name, address, phone, status) VALUES
(1, 'ORG001', '示例医院', '示例市健康路 1 号', '0551-1234567', 1);

INSERT INTO bas_department (id, org_id, dept_code, dept_name, dept_type, location, status) VALUES
(1, 1, 'DEPT001', '内科',   1, '门诊楼 2 层', 1),
(2, 1, 'DEPT002', '外科',   1, '门诊楼 2 层', 1),
(3, 1, 'DEPT003', '儿科',   1, '门诊楼 3 层', 1),
(4, 1, 'DEPT004', '药房',   3, '门诊楼 1 层', 1),
(5, 1, 'DEPT005', '收费处', 4, '门诊楼 1 层', 1);

-- ---------- 医生（绑定登录账号） ----------
INSERT INTO bas_doctor (id, user_id, dept_id, doctor_code, doctor_name, title, is_expert, normal_fee, expert_fee, daily_quota, status) VALUES
(1, 2, 1, 'D0001', '王志远', '主任医师', 1, 10.00, 20.00, 500, 1),
(2, 3, 1, 'D0002', '李建国', '主治医师', 0, 10.00, 20.00, 500, 1),
(3, 4, 3, 'D0003', '陈静',   '主治医师', 0, 10.00, 20.00, 500, 1);

-- ---------- 药品（《05》演示脚本口径） ----------
INSERT INTO bas_drug (id, drug_code, drug_name, generic_name, spec, dosage_form, category, manufacturer, unit, retail_price, stock_warning_qty, is_antibiotic, status) VALUES
(1, 'DRUG001', '阿莫西林胶囊',     '阿莫西林', '0.25g×24粒', '胶囊剂',   1, '华北制药', '盒', 15.60, 50, 1, 1),
(2, 'DRUG002', '布洛芬缓释胶囊',   '布洛芬',   '0.3g×20粒',  '缓释胶囊', 1, '中美史克', '盒', 22.00, 50, 0, 1),
(3, 'DRUG003', '感冒灵颗粒',       '感冒灵',   '10g×9袋',    '颗粒剂',   2, '999集团',  '盒', 12.50, 30, 0, 1),
(4, 'DRUG004', '奥美拉唑肠溶胶囊', '奥美拉唑', '20mg×14粒',  '肠溶胶囊', 1, '阿斯利康', '盒', 28.00, 50, 0, 1),
(5, 'DRUG005', '开喉剑喷雾剂',     '开喉剑',   '20ml',       '喷雾剂',   2, '三力制药', '瓶', 32.00, 30, 0, 1);

-- ---------- 收费项目 ----------
INSERT INTO bas_charge_item (id, item_code, item_name, category, price, unit, status) VALUES
(1, 'ITEM001', '普通门诊诊查费', 2, 10.00,  '次',  1),
(2, 'ITEM002', '专家门诊诊查费', 2, 20.00,  '次',  1),
(3, 'ITEM003', '血常规',         4, 25.00,  '次',  1),
(4, 'ITEM004', '尿常规',         4, 15.00,  '次',  1),
(5, 'ITEM005', '胸部DR',         3, 120.00, '次',  1);
