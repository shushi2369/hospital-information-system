-- =====================================================================
-- HIS V30 四期演示数据（仅 dev/test 加载，《22》§1）
-- =====================================================================
INSERT INTO sys_user (username, password_hash, real_name, phone, status) VALUES
('bb.tech', 'INIT:His@2026', '毕血库', '13800000041', 1);
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 14 FROM sys_user WHERE username = 'bb.tech';

-- 血袋库存（O 型红细胞×2、A 型血浆×1，效期错开供 FEFO 演练）
INSERT INTO bb_blood_bag (bag_no, blood_type, rh, component, volume_ml, blood_station, collect_date, expire_date, status) VALUES
('XDJ20260001', 4, 1, 1, 200, '市中心血站（演示）', '2026-09-10', '2026-10-05', 1),
('XDJ20260002', 4, 1, 1, 200, '市中心血站（演示）', '2026-09-15', '2026-10-20', 1),
('XDJ20260003', 1, 1, 2, 200, '市中心血站（演示）', '2026-09-12', '2026-09-28', 1);

-- 血费收费项目（类别 11）
INSERT INTO bas_charge_item (item_code, item_name, category, price, unit, status) VALUES
('BB-FEE001', '交叉配血费', 11, 40.00, '次', 1),
('BB-FEE002', '红细胞悬液输注费', 11, 60.00, '次', 1);
