-- 门诊危急值：ris_request/lis_request 的门诊申请 admission_id 为空，
-- 原 NOT NULL 约束使门诊危急值插入失败并回滚整个报告审核/结果保存事务。
ALTER TABLE alert_critical
    MODIFY COLUMN admission_id BIGINT UNSIGNED NULL COMMENT '住院(门诊为空)';
