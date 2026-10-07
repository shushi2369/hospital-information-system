-- V53（一百零三轮实体↔表漂移审计：patient_id 查询缺索引 5 表）
-- 各申请表的 patient_id 直查路径（RisService:133/363、LisService:277、DocOrderService:461、
-- PeService:87、CntService:56）在规模化后全表扫描——患者维度追查是医院最高频查询之一。
ALTER TABLE ris_request  ADD INDEX idx_risreq_patient  (patient_id);
ALTER TABLE lis_request  ADD INDEX idx_lisreq_patient  (patient_id);
ALTER TABLE doc_order    ADD INDEX idx_docorder_patient (patient_id);
ALTER TABLE pe_record    ADD INDEX idx_perecord_patient (patient_id);
ALTER TABLE cnt_request  ADD INDEX idx_cntreq_patient  (patient_id);
