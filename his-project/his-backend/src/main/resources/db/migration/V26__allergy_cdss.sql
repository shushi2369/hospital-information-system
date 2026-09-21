-- =====================================================================
-- HIS V26 过敏史 CDSS 守门提示（业界患者安全对标，查验二十二轮）
-- =====================================================================
-- CdssService.check 新增患者级过敏史提示：药品医嘱 + pat_patient.allergy_history 非空
-- → 写 cdss_hit(rule_id=0, 患者级)。提示级不阻断，与 CDSS 定位一致。
-- rule_type 枚举注释扩展（预留规则化过敏规则，本实现为患者动态数据检查）：
ALTER TABLE cdss_rule
    MODIFY COLUMN rule_type TINYINT NOT NULL COMMENT '1配伍禁忌 2重复检查 3剂量上限 4过敏史(预留)';
