-- =====================================================================
-- HIS 数据一致性巡检（deploy/db/consistency_check.sql）
-- 孤儿引用扫描：InnoDB 无外键约束架构下的例行 DBA 巡检
-- 每段应返回 0 行；返回行即为孤儿数据
-- =====================================================================

-- 1. 医嘱 → 患者/住院
SELECT 'orphan_order_patient' AS chk, o.id FROM doc_order o
LEFT JOIN pat_patient p ON o.patient_id = p.id WHERE p.id IS NULL LIMIT 5;
SELECT 'orphan_order_admission' AS chk, o.id FROM doc_order o
LEFT JOIN inp_admission a ON o.admission_id = a.id WHERE a.id IS NULL LIMIT 5;

-- 2. 医嘱执行 → 医嘱/明细
SELECT 'orphan_exec_order' AS chk, e.id FROM doc_order_exec e
LEFT JOIN doc_order o ON e.order_id = o.id WHERE o.id IS NULL LIMIT 5;
SELECT 'orphan_exec_item' AS chk, e.id FROM doc_order_exec e
LEFT JOIN doc_order_item i ON e.item_id = i.id WHERE i.id IS NULL AND e.item_id IS NOT NULL LIMIT 5;

-- 3. 费用明细 → 账单
SELECT 'orphan_charge_detail' AS chk, d.id FROM bil_charge_detail d
LEFT JOIN bil_charge_bill b ON d.bill_id = b.id WHERE b.id IS NULL LIMIT 5;

-- 4. 退款 → 账单
SELECT 'orphan_refund_bill' AS chk, r.id FROM bil_refund_bill r
LEFT JOIN bil_charge_bill b ON r.bill_id = b.id WHERE b.id IS NULL LIMIT 5;

-- 5. LIS 链：结果/报告 → 申请；标本 → 申请
SELECT 'orphan_lis_result' AS chk, r.id FROM lis_result r
LEFT JOIN lis_request q ON r.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_lis_report' AS chk, r.id FROM lis_report r
LEFT JOIN lis_request q ON r.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_lis_specimen' AS chk, s.id FROM lis_specimen s
LEFT JOIN lis_request q ON s.request_id = q.id WHERE q.id IS NULL LIMIT 5;

-- 6. RIS 链：预约/影像/报告 → 申请
SELECT 'orphan_ris_appointment' AS chk, a.id FROM ris_appointment a
LEFT JOIN ris_request q ON a.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_ris_image' AS chk, i.id FROM ris_image i
LEFT JOIN ris_request q ON i.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_ris_report' AS chk, r.id FROM ris_report r
LEFT JOIN ris_request q ON r.request_id = q.id WHERE q.id IS NULL LIMIT 5;

-- 7. ORIS 链：排台/核查/麻醉/术后 → 申请
SELECT 'orphan_schedule' AS chk, s.id FROM or_schedule s
LEFT JOIN or_surgery_request q ON s.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_check' AS chk, c.id FROM or_check_record c
LEFT JOIN or_surgery_request q ON c.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_anesthesia' AS chk, a.id FROM or_anesthesia_record a
LEFT JOIN or_surgery_request q ON a.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_postop' AS chk, p.id FROM or_postop_record p
LEFT JOIN or_surgery_request q ON p.request_id = q.id WHERE q.id IS NULL LIMIT 5;

-- 8. 输血链：配血/发血/执行/不良反应 → 申请；配血/发血 → 血袋
SELECT 'orphan_cross' AS chk, c.id FROM bb_cross_match c
LEFT JOIN bb_request q ON c.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_issue' AS chk, i.id FROM bb_issue i
LEFT JOIN bb_request q ON i.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_issue_bag' AS chk, i.id FROM bb_issue i
LEFT JOIN bb_blood_bag g ON i.bag_id = g.id WHERE g.id IS NULL LIMIT 5;
SELECT 'orphan_transfusion' AS chk, t.id FROM bb_transfusion t
LEFT JOIN bb_request q ON t.request_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_adverse' AS chk, a.id FROM bb_adverse a
LEFT JOIN bb_request q ON a.request_id = q.id WHERE q.id IS NULL LIMIT 5;

-- 9. 体检链：结果/报告 → 登记
SELECT 'orphan_pe_result' AS chk, r.id FROM pe_result r
LEFT JOIN pe_record q ON r.record_id = q.id WHERE q.id IS NULL LIMIT 5;
SELECT 'orphan_pe_report' AS chk, r.id FROM pe_report r
LEFT JOIN pe_record q ON r.record_id = q.id WHERE q.id IS NULL LIMIT 5;

-- 10. CDSS：命中 → 医嘱/规则
SELECT 'orphan_cdss_hit' AS chk, h.id FROM cdss_hit h
LEFT JOIN doc_order o ON h.order_id = o.id WHERE o.id IS NULL LIMIT 5;
SELECT 'orphan_cdss_rule' AS chk, h.id FROM cdss_hit h
LEFT JOIN cdss_rule r ON h.rule_id = r.id WHERE r.id IS NULL AND h.rule_id <> 0 LIMIT 5;

-- 11. 挂号/就诊：挂号 → 患者；就诊 → 挂号
SELECT 'orphan_registration' AS chk, r.id FROM reg_registration r
LEFT JOIN pat_patient p ON r.patient_id = p.id WHERE p.id IS NULL LIMIT 5;
SELECT 'orphan_visit' AS chk, v.id FROM cli_visit v
LEFT JOIN reg_registration r ON v.registration_id = r.id WHERE r.id IS NULL LIMIT 5;

-- 12. 住院押金/每日费用 → 住院
SELECT 'orphan_deposit' AS chk, d.id FROM inp_deposit d
LEFT JOIN inp_admission a ON d.admission_id = a.id WHERE a.id IS NULL LIMIT 5;
SELECT 'orphan_dailyfee' AS chk, f.id FROM inp_daily_fee f
LEFT JOIN inp_admission a ON f.admission_id = a.id WHERE a.id IS NULL LIMIT 5;

-- 13. 状态一致性抽查：已发血申请必有发血记录；配血完成必有相容记录
SELECT 'inconsistent_issued_no_issue' AS chk, r.id FROM bb_request r
WHERE r.status IN (40, 50, 60)
  AND NOT EXISTS (SELECT 1 FROM bb_issue i WHERE i.request_id = r.id) LIMIT 5;
SELECT 'inconsistent_done_no_transfusion' AS chk, r.id FROM bb_request r
WHERE r.status = 60
  AND NOT EXISTS (SELECT 1 FROM bb_transfusion t WHERE t.request_id = r.id) LIMIT 5;
SELECT 'inconsistent_30_no_compatible' AS chk, r.id FROM bb_request r
WHERE r.status = 30
  AND NOT EXISTS (SELECT 1 FROM bb_cross_match c
                  WHERE c.request_id = r.id AND c.cross_result = 1) LIMIT 5;
-- 完成手术必有麻醉记录（手术记录完整性）
SELECT 'inconsistent_done_no_anesthesia' AS chk, r.id FROM or_surgery_request r
WHERE r.status = 60
  AND NOT EXISTS (SELECT 1 FROM or_anesthesia_record a WHERE a.request_id = r.id) LIMIT 5;
-- 已发布 LIS 报告必有结果行
SELECT 'inconsistent_published_no_result' AS chk, q.id FROM lis_request q
JOIN lis_report rp ON rp.request_id = q.id
WHERE q.status = 40
  AND NOT EXISTS (SELECT 1 FROM lis_result r WHERE r.request_id = q.id) LIMIT 5;
