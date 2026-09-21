-- =====================================================================
-- HIS V31 手术槽位占位模型优化（登记权衡项升级修复）
-- =====================================================================
-- 问题：uk_schedule_slot(room_id, surgery_date, seq_no) 不含状态，取消/已完成
-- 的排台行永久占用槽位，同槽位无法重排（用户体验缺陷，查验二十五轮登记）。
-- 方案：可空占位列 slot_active——占用=1，释放（完成/取消）=NULL；
-- MySQL 唯一索引对 NULL 不生效 → 释放后同槽位可重排，占用中仍唯一。
ALTER TABLE or_schedule
    ADD COLUMN slot_active TINYINT NULL COMMENT '槽位占用标记:1占用 NULL已释放(完成/取消)' AFTER status;

-- 存量迁移：已完成/已取消排台释放，其余占用
UPDATE or_schedule SET slot_active = CASE WHEN status IN (2, 3) THEN NULL ELSE 1 END;

DROP INDEX uk_schedule_slot ON or_schedule;
ALTER TABLE or_schedule
    ADD UNIQUE KEY uk_schedule_slot2 (room_id, surgery_date, seq_no, slot_active);
