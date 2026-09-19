-- =====================================================================
-- HIS V15 三期演示数据（仅 dev/test 加载）
-- =====================================================================
-- 不用显式 id：压测用户已推进 sys_user 自增（跨运行冲突教训）
INSERT INTO sys_user (username, password_hash, real_name, phone, status) VALUES
('lab.chen', 'INIT:His@2026', '陈检验', '13800000012', 1);
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 9 FROM sys_user WHERE username = 'lab.chen';

INSERT INTO bas_supplier (id, supplier_code, supplier_name, contact, phone, status) VALUES
(1, 'SUP001', '国药控股示例公司', '王经理', '0551-8888888', 1),
(2, 'SUP002', '华东医药示例公司', '李经理', '0551-6666666', 1);

INSERT INTO lis_critical_threshold (item_name, low_value, high_value, status) VALUES
('白细胞计数',   2.00,  30.00, 1),
('血红蛋白',    60.00, 200.00, 1),
('血小板计数',  50.00, 800.00, 1),
('C反应蛋白',   10.00, 100.00, 1),
('血钾',        2.50,   6.50, 1);
