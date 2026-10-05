-- V48（八十六轮并发审计 P2-3）：同一检验申请只能有一条标本记录。
-- 并发采集会双插标本条码（原 idx_spec_req 非唯一拦不住）；升级为唯一索引兜底。
ALTER TABLE lis_specimen
    DROP INDEX idx_spec_req,
    ADD UNIQUE KEY uk_specimen_req (request_id);
