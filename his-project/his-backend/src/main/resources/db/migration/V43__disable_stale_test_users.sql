-- 七十九轮：验收测试一次性账号（rj* 动态收费员）存量停用。
-- 这类账号由验收脚本每轮创建（ADR-12），带账单引用不可删除；停用保留审计链，
-- 且无法再登录。新账号由脚本自清机制接管（验收启动时停用上轮 rj% 账号）。
UPDATE sys_user SET status = 0, updated_at = NOW()
WHERE username LIKE 'rj%' AND status = 1
  AND created_at < NOW() - INTERVAL 1 DAY;
