-- V50（八十七轮资源审计 P2-1）：上报队列失败退避——防坏数据队头阻塞。
-- 原调度投递按 id 升序取最老 50 条 PENDING：K 条注定失败的行要耗满 5 次重试才让位，
-- 新上报（传染病卡/出院结算）首投延迟 ≈ ceil(K/50)×5×30s（K=1000 约 50 分钟）。
-- next_retry_at 只约束调度通道；手动 /deliver 不看退避（操作员/e2e 明确要立即投递）。
ALTER TABLE rpt_upload
    ADD COLUMN next_retry_at DATETIME NULL COMMENT '下次可重试时刻（失败退避；NULL=立即可投）',
    ADD INDEX idx_rpt_status_next (status, next_retry_at);
