-- V47（八十六轮并发审计 P1-6）：入院记录"一患一院一条"的数据库兜底。
-- EmrService.create 对 docType=1 的 selectCount 预检无唯一索引兜底，并发双击产生两份入院记录。
-- MySQL 无部分索引：用生成列把"非入院记录"置 NULL（唯一索引允许多个 NULL），入院记录携带 admission_id 参与唯一约束。
-- 历史双病程数据（如 admission_id=2 doc_type=2）不受影响。
ALTER TABLE emr_record
    ADD COLUMN admission_unique BIGINT UNSIGNED
        GENERATED ALWAYS AS (IF(doc_type = 1, admission_id, NULL)) STORED,
    ADD UNIQUE KEY uk_emr_admission_record (admission_unique);
