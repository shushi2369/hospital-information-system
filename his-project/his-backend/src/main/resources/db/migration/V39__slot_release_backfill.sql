-- 五十九轮：历史数据回溯——三十一轮"完成/取消释放槽位"修复上线前，
-- 已完成/已取消手术的槽位残留 slot_active=1（该修复未回溯数据）。
-- 数据修复随迁移发布：终态请求（60 完成 / 70 取消）的槽位一律释放。
UPDATE or_schedule s
JOIN or_surgery_request r ON s.request_id = r.id
SET s.slot_active = 0, s.updated_at = NOW()
WHERE s.slot_active = 1 AND r.status IN (60, 70);
