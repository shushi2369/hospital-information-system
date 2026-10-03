-- 五十三轮：索引审计（EXPLAIN 驱动）
-- ① 患者电话检索：/patients?phone= 走全表扫描（5 万行），配合 PatientService 的 likeRight 前缀检索
ALTER TABLE pat_patient ADD INDEX idx_patient_phone (phone);
-- ② 收费员日视图 + 日结聚合主查询模式：WHERE cashier_id=? AND pay_time BETWEEN → 复合索引
ALTER TABLE bil_charge_bill ADD INDEX idx_bill_cashier_paytime (cashier_id, pay_time);
