-- V63（一百六十二轮 D-42）：sys_operation_log 分页治理。
-- ① 排序键索引：ORDER BY created_at,id 无索引时无过滤深翻页全表扫描+filesort。
-- ② 审计查询补 id 稳定分页 tiebreaker（代码侧）：秒级 created_at 并发落库同值，
--    无 tiebreaker 翻页会重排跳行——正确性修复而非仅性能。
-- 注：无过滤深翻页 OFFSET 83K 仍约 260ms（延迟关联可再降至 ~80ms，API 兼容改写 defer）。
CREATE INDEX idx_oplog_created ON sys_operation_log (created_at, id);
