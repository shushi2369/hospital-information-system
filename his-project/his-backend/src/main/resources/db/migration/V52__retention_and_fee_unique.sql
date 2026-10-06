-- V52（九十八轮自查审计 P1-3/P1-5）：
-- 1) 床位费日结并发兜底：启动补偿与 00:20 cron 竞态时 exists 检查窗口双插 → 床位费双份入账。
--    inp_daily_fee 无自然唯一键（手术费 9 + 麻醉费 10 同 source_detail_id 合法共存，实测见
--    admission 9299）——用生成列做部分索引等价（V47 模式）：仅床位费行(source_type=1)参与唯一约束，
--    其他来源行置 NULL（唯一索引允许多 NULL）。
ALTER TABLE inp_daily_fee
    ADD COLUMN bed_fee_dedup VARCHAR(40)
        GENERATED ALWAYS AS (IF(source_type = 1,
            CONCAT(fee_date, '-', admission_id, '-', source_detail_id), NULL)) STORED,
    ADD UNIQUE KEY uk_df_bed_fee (bed_fee_dedup);
-- 2) 保留策略 DELETE 走索引：V51 只覆盖 sys_operation_log，其余三张表的
--    created_at 过滤均为全表扫描（cdss_hit 每批 O(N)，RR 隔离下长时间阻塞写入）。
ALTER TABLE sys_login_log ADD INDEX idx_loginlog_created (created_at);
ALTER TABLE cdss_hit      ADD INDEX idx_cdsshit_created (created_at);
ALTER TABLE plt_event_log ADD INDEX idx_event_created (created_at);
