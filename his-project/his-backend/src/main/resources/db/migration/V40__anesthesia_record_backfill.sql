-- 五十九轮：历史数据补录——finish 门禁（结束手术须有麻醉记录）上线前完成的手术
-- SS20260926000004（id=388，查验三十轮前后产物）缺麻醉记录，按其切皮/结束时间补录，
-- 药品备注标注补录来源，保持巡检不变量 inconsistent_done_no_anesthesia 可全量生效。
INSERT INTO or_anesthesia_record
    (record_no, request_id, anesthesia_method, asa_grade, anesthetist_id,
     start_time, end_time, drug_note, status, created_by)
SELECT CONCAT('MZ', DATE_FORMAT(r.incision_time, '%Y%m%d'), 'V40'),
       r.id, r.anesthesia_method, 2, 2,
       r.incision_time, r.end_time, 'V40 历史数据补录（原单查验三十轮前后未强制麻醉记录）', 1, 1
FROM or_surgery_request r
WHERE r.request_no = 'SS20260926000004'
  AND NOT EXISTS (SELECT 1 FROM or_anesthesia_record a WHERE a.request_id = r.id);
