-- =====================================================================
-- HIS V33 门诊段适配补漏（V32 漏项）：ris_request.order_id 门诊段无医嘱
-- =====================================================================
ALTER TABLE ris_request
    MODIFY COLUMN order_id BIGINT UNSIGNED NULL COMMENT '来源检查医嘱(住院段;门诊段为空)';
