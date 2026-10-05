-- V49（八十七轮 SQL 审计）：索引配套补齐 + 历史重复索引清理
-- 1) 报表/高频查询缺索引（V38 只覆盖了收费侧）：
ALTER TABLE cli_visit        ADD INDEX idx_visit_status_end (status, end_time);        -- T-02 日门诊量
ALTER TABLE reg_registration ADD INDEX idx_reg_date (reg_date);                        -- T-01 挂号日报/科室排名
ALTER TABLE bil_refund_bill  ADD INDEX idx_refund_operator_time (operator_id, refund_time); -- 日结聚合/收费员退费视图
ALTER TABLE doc_order_exec   ADD INDEX idx_exec_date_status (exec_date, status);       -- 护士待执行单
ALTER TABLE sys_operation_log ADD INDEX idx_oplog_username (username, created_at);     -- 审计检索（原仅 user_id 前导）
ALTER TABLE inv_inventory_batch ADD INDEX idx_batch_status_expiry (status, expiry_date); -- 效期核销任务

-- 2) V6 与 V27 建出列序完全相同的唯一索引（uk_exec / uk_exec_slot），新装环境双份维护写放大；
--    统一保留 V6 的 uk_exec。
ALTER TABLE doc_order_exec DROP INDEX uk_exec_slot;
