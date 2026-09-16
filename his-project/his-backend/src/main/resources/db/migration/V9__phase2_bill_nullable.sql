-- 住院结算复用账单模型：visit_id 允许为空（住院单据以 admission_id 标识）
ALTER TABLE bil_charge_bill MODIFY visit_id BIGINT UNSIGNED NULL COMMENT '门诊来源(住院时为空)';
ALTER TABLE bil_charge_detail MODIFY visit_id BIGINT UNSIGNED NULL COMMENT '门诊来源(住院时为空)';
