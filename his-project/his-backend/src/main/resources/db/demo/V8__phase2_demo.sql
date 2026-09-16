-- =====================================================================
-- HIS V8 二期演示数据（仅 dev/test 加载）
-- =====================================================================
INSERT INTO sys_user (id, username, password_hash, real_name, phone, status) VALUES
(8, 'nurse.wang',  'INIT:His@2026', '王护士', '13800000008', 1),
(9, 'nurse.liu',   'INIT:His@2026', '刘护士', '13800000009', 1),
(10, 'mrc.zhou',   'INIT:His@2026', '周病案', '13800000010', 1),
(11, 'yb.sun',     'INIT:His@2026', '孙医保', '13800000011', 1);
INSERT INTO sys_user_role (user_id, role_id) VALUES (8, 6), (9, 6), (10, 7), (11, 8);

INSERT INTO bas_charge_item (id, item_code, item_name, category, price, unit, status) VALUES
(10, 'ITEM010', '普通床位费', 8, 30.00, '床日', 1),
(11, 'ITEM011', '三级护理费', 5, 20.00, '日', 1),
(12, 'ITEM012', '静脉输液',   5, 8.00,  '次', 1);

INSERT INTO inp_ward (id, ward_code, ward_name, dept_id, location, status) VALUES
(1, 'W001', '内科一病区', 1, '住院楼 3 层', 1),
(2, 'W002', '儿科病区',   3, '住院楼 5 层', 1);

INSERT INTO inp_bed (id, ward_id, bed_no, bed_status, charge_item_id, status) VALUES
(1, 1, '301-1', 1, 10, 1), (2, 1, '301-2', 1, 10, 1), (3, 1, '301-3', 1, 10, 1),
(4, 1, '302-1', 1, 10, 1), (5, 2, '501-1', 1, 10, 1), (6, 2, '501-2', 1, 10, 1);

INSERT INTO mrc_icd10 (code, name, category, status) VALUES
('J06.9', '急性上呼吸道感染', 'X 呼吸系统', 1),
('J18.9', '肺炎',             'X 呼吸系统', 1),
('I10',   '原发性高血压',     'I 循环系统', 1),
('E11.9', '2型糖尿病',        'IV 内分泌',  1),
('K35.9', '急性阑尾炎',       'XI 消化系统', 1);
