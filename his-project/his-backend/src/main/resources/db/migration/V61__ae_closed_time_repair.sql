-- V61（一百四十三轮 CI 修复轮）：V35 演示样例只给了 status=40 未给 closed_time，
-- 违反"已闭环必有闭环时间"不变量（巡检段 inconsistent_ae_40_no_closed_time）。
-- 以 event_time 兜底回填：演示样例闭环时间=发生时间。
UPDATE ae_event SET closed_time = event_time
WHERE status = 40 AND closed_time IS NULL;
