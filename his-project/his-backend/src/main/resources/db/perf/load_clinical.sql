-- 住院+医嘱+费用压测数据
SET autocommit=0;
START TRANSACTION;
INSERT INTO inp_admission (patient_id, dept_id, doctor_id, bed_no, planned_diagnosis, admission_type, status) SELECT id, 1, 2, CONCAT('LOAD-BED-', patient_no), '压测诊断', 1, 20 FROM pat_patient WHERE patient_no LIKE 'PLOAD%' LIMIT 5000;
COMMIT;
SELECT COUNT(*) AS admissions FROM inp_admission WHERE bed_no LIKE 'LOAD-BED-%';
START TRANSACTION;
INSERT INTO doc_order (order_no, admission_id, patient_id, doctor_id, order_class, category, frequency, status)
SELECT CONCAT('YZL', a.id), a.id, a.patient_id, 2, 1, 1, 'qd', 40
FROM inp_admission a WHERE a.bed_no LIKE 'LOAD-BED-%';
INSERT INTO doc_order (order_no, admission_id, patient_id, doctor_id, order_class, category, frequency, status)
SELECT CONCAT('YZL2-', a.id), a.id, a.patient_id, 2, 2, 3, 'qd', 40
FROM inp_admission a WHERE a.bed_no LIKE 'LOAD-BED-%';
COMMIT;
SELECT COUNT(*) AS orders FROM doc_order WHERE order_no LIKE 'YZL%';
START TRANSACTION;
INSERT INTO inp_daily_fee (admission_id, fee_date, fee_type, source_type, item_name, quantity, unit_price, amount, charge_status, status)
SELECT a.id, CURDATE(), 5, 2, '压测费用', 1, 100.00, 100.00, 0, 1
FROM inp_admission a WHERE a.bed_no LIKE 'LOAD-BED-%';
INSERT INTO inp_daily_fee (admission_id, fee_date, fee_type, source_type, item_name, quantity, unit_price, amount, charge_status, status)
SELECT a.id, CURDATE(), 4, 2, '压测检验费', 1, 50.00, 50.00, 0, 1
FROM inp_admission a WHERE a.bed_no LIKE 'LOAD-BED-%';
COMMIT;
SELECT COUNT(*) AS fees FROM inp_daily_fee f JOIN inp_admission a ON f.admission_id=a.id WHERE a.bed_no LIKE 'LOAD-BED-%';