-- V51（九十轮数据生命周期审计 P1）：审计检索按纯日期/空条件查询时
-- 无 created_at 前导索引可用（既有索引均以 user_id/username/module 前导），
-- sys_operation_log 是增长最快的表之一（~1,960 行/天），默认管理端日志页会退化为全扫+filesort。
ALTER TABLE sys_operation_log ADD INDEX idx_oplog_created (created_at);
