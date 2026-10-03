-- 六十八轮：事件留痕 payload JSON → LONGTEXT。
-- 动机：Windows mysql 客户端批处理模式下会把 mysqldump 转义出的 \' / \" 解析为
-- "未知客户端命令"，含转义的 JSON 列在恢复时整语句失败（备份恢复演练实测 7842 错）。
-- payload 由应用层 JsonEscapeUtil 保证合法 JSON、前端 JSON.parse 消费，
-- 库内无 JSON 函数依赖 → TEXT 化后备份/恢复走 --binary-mode + binary 字符集即可无损往返。
ALTER TABLE plt_event_log MODIFY payload LONGTEXT NULL;
