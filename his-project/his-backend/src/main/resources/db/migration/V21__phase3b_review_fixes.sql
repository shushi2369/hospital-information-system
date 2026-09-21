-- =====================================================================
-- HIS V21 十四轮查验补漏
-- =====================================================================
-- emc_visit.triage_id 无唯一约束：预检(selectCount)+插入存在并发窗口，
-- 双击/并发登记会为同一分诊单生成两条五大中心病例。补唯一约束兜底（对齐
-- or_schedule.uk_schedule_req / ris_appointment.uk_ris_appt_req 同款防线）。
ALTER TABLE emc_visit
    ADD UNIQUE KEY uk_emc_visit_triage (triage_id);
