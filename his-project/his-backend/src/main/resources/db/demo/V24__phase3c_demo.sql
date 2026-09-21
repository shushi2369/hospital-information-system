-- =====================================================================
-- HIS V24 三期第三批演示数据（仅 dev/test 加载，《19》§1）
-- =====================================================================
-- 账号（密码占位由 InitPasswordRunner 替换；不用显式 id）
INSERT INTO sys_user (username, password_hash, real_name, phone, status) VALUES
('pe.nurse', 'INIT:His@2026', '裴体检', '13800000031', 1);
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 13 FROM sys_user WHERE username = 'pe.nurse';

-- 物资字典 + 初始库存（显式 id 字典表）
INSERT INTO mat_material (id, material_code, name, category, unit, price, safe_stock, status) VALUES
(1, 'MAT001', '一次性注射器 5ml', 1, '支', 0.85, 500, 1),
(2, 'MAT002', '医用纱布块',       1, '包', 3.50, 200, 1),
(3, 'MAT003', '真空采血管 EDTA',  4, '支', 1.20, 1000, 1);
INSERT INTO mat_stock (material_id, quantity) VALUES
(1, 2000), (2, 800), (3, 5000);

-- 体检套餐（入职体检：血常规+胸片+肝功，引用收费项目字典）
INSERT INTO pe_package (package_no, name, price, items, status)
VALUES ('TC2026001', '入职体检套餐', 180.00,
        '[{"chargeItemId":3,"itemName":"血常规","price":25.00},{"chargeItemId":2,"itemName":"胸部DR","price":120.00},{"chargeItemId":4,"itemName":"肝功能","price":35.00}]',
        1);

-- CDSS 样例规则（重复检查：血常规 24h 重复；收费项目 id=3 为 e2e 通用检验项目）
INSERT INTO cdss_rule (rule_code, rule_type, ref_a_id, ref_b_id, level, message, status) VALUES
('CDSS-DUP-001', 2, 3, 3, 1, '24 小时内重复开立同类检验，请确认是否必要', 1);
