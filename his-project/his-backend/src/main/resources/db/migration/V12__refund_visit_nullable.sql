-- 住院账单退费：退费单 visit_id 允许为空（住院退费以账单关联，V9 同类遗漏）
ALTER TABLE bil_refund_bill MODIFY visit_id BIGINT UNSIGNED NULL COMMENT '门诊来源(住院时为空)';
