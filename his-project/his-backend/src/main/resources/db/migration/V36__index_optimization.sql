-- =====================================================================
-- HIS V36 索引优化（二十九轮容量验证后 EXPLAIN 分析）
-- =====================================================================
-- 1. or_schedule status 查询全表扫描（EXPLAIN type=ALL）——补状态索引
ALTER TABLE or_schedule ADD INDEX idx_schedule_status (status);

-- 2. plt_event_log event_type 查询 ORDER BY id DESC 走主键反扫（全表扫 + 过滤）
--    → 升级为复合索引 (event_type, id) 消除排序
DROP INDEX idx_event_type ON plt_event_log;
ALTER TABLE plt_event_log ADD INDEX idx_event_type_id (event_type, id);

-- 3. bb_request 补状态单列索引（当前 PRIMARY 反扫，量增大后退化）
ALTER TABLE bb_request ADD INDEX idx_bbreq_status (status);

-- 4. cli_exam_application 补 apply_type 查询索引
ALTER TABLE cli_exam_application ADD INDEX idx_exam_type_visit (apply_type, visit_id);
