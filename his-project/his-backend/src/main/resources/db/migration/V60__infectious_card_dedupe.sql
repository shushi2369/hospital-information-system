-- 同一就诊/住院同一病种只允许一张有效报告卡（诊断重复保存不得重复报卡）。
-- 先清理历史重复卡（保留最早一张），再以生成列唯一键兜底并发插入。
DELETE c FROM pub_infectious_card c
JOIN (
    SELECT MIN(id) AS keep_id, visit_id, disease_name
    FROM pub_infectious_card
    WHERE visit_id IS NOT NULL
    GROUP BY visit_id, disease_name
    HAVING COUNT(*) > 1
) d ON c.visit_id = d.visit_id AND c.disease_name = d.disease_name AND c.id <> d.keep_id;

DELETE c FROM pub_infectious_card c
JOIN (
    SELECT MIN(id) AS keep_id, admission_id, disease_name
    FROM pub_infectious_card
    WHERE admission_id IS NOT NULL
    GROUP BY admission_id, disease_name
    HAVING COUNT(*) > 1
) d ON c.admission_id = d.admission_id AND c.disease_name = d.disease_name AND c.id <> d.keep_id;

ALTER TABLE pub_infectious_card
    ADD COLUMN active_visit_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (IF(status = 50, NULL, visit_id)) STORED COMMENT '有效卡门诊就诊(唯一键)',
    ADD COLUMN active_admission_id BIGINT UNSIGNED
        GENERATED ALWAYS AS (IF(status = 50, NULL, admission_id)) STORED COMMENT '有效卡住院(唯一键)',
    ADD UNIQUE KEY uk_card_visit_disease (active_visit_id, disease_name),
    ADD UNIQUE KEY uk_card_admission_disease (active_admission_id, disease_name);
