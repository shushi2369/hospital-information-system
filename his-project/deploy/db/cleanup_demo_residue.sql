-- 演示库测试残留清理（一百四十八轮）
-- 目标：清掉混入演示数据的 e2e/压测残留噪音，不破坏历史业务记录的引用完整性。
-- 约定：执行前必须先打操作前快照（mysqldump --single-transaction）。
-- 全部语句幂等，可重复执行。
-- 注意：中文改写语句须经 --default-character-set=utf8mb4 执行（客户端默认字符集会 1366 报错）。

-- 1) 孤儿会诊单（伪造链路 ID，修复前残留，148 轮巡检定性）：直接删除
DELETE FROM cnt_request WHERE id IN (69, 70);

-- 2) 孤儿采购单（幽灵供应商 99999，状态 40 已取消）：直接删除
DELETE FROM mat_purchase WHERE id = 513;

-- 3) e2e 验收用户（real_name 以「验收」开头，如 rj05233478）：禁用而非删除，
--    保历史单据的操作人姓名回填；用户管理列表通过状态筛选即可排除
UPDATE sys_user SET status = 0 WHERE real_name LIKE '验收%' AND status = 1;

-- 4) 审计脚本残留床号（AU 开头）规范化为「备+主键」，避免护理工作台床号列出现乱码式编号
--    唯一键 (ward_id, bed_no)：'备'+id 全局唯一，不会冲突
UPDATE inp_bed SET bed_no = CONCAT('备', id) WHERE bed_no LIKE 'AU%';

-- 5) 「压测失败」患者名降噪（最刺眼的前缀），其余测试语义名字保留以维持演示数据量感
UPDATE pat_patient SET name = REPLACE(name, '压测失败', '压测') WHERE name LIKE '压测失败%';
