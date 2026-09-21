-- =====================================================================
-- HIS V32 四期二批：门诊检查 RIS 闭环（《22》§7 增补）
-- =====================================================================
ALTER TABLE ris_request
    ADD COLUMN visit_id BIGINT UNSIGNED NULL COMMENT '门诊就诊(门诊段)' AFTER admission_id,
    MODIFY COLUMN admission_id BIGINT UNSIGNED NULL COMMENT '住院(门诊段为空)';

ALTER TABLE cli_exam_application
    ADD COLUMN ris_request_id BIGINT UNSIGNED NULL COMMENT '联动检查申请id(幂等回填标记)' AFTER status,
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 10 COMMENT '10已申请 20已收费 30已执行 50已作废';
