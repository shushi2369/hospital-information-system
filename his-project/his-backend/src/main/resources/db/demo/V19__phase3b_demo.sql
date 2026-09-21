-- =====================================================================
-- HIS V19 三期第二批演示数据（仅 dev/test 加载，《16》§1）
-- =====================================================================
-- 账号（密码占位由 InitPasswordRunner 替换；不用显式 id，压测用户已推进自增）
INSERT INTO sys_user (username, password_hash, real_name, phone, status) VALUES
('or.nurse',  'INIT:His@2026', '欧护士', '13800000021', 1),
('ris.zhang', 'INIT:His@2026', '张影像', '13800000022', 1),
('emc.li',    'INIT:His@2026', '李急诊', '13800000023', 1);
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 10 FROM sys_user WHERE username = 'or.nurse';
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 11 FROM sys_user WHERE username = 'ris.zhang';
INSERT INTO sys_user_role (user_id, role_id)
SELECT id, 12 FROM sys_user WHERE username = 'emc.li';

-- 手术间（显式 id，字典表）
INSERT INTO or_operate_room (id, room_no, room_name, status) VALUES
(1, 'OR01', '第一手术间', 1),
(2, 'OR02', '第二手术间', 1),
(3, 'OR03', '第三手术间', 1);

-- 影像设备
INSERT INTO ris_device (id, device_no, device_name, modality, status) VALUES
(1, 'DEV-DR01', '数字化X光机DR-01', 1, 1),
(2, 'DEV-CT01', '多层螺旋CT-01',    2, 1),
(3, 'DEV-MR01', '磁共振MR-01',      3, 1);

-- 手术/麻醉收费项目（类别 9/10，价格演示口径）
INSERT INTO bas_charge_item (item_code, item_name, category, price, unit, status) VALUES
('OR-OP001', '阑尾切除术',   9, 1200.00, '次', 1),
('OR-OP002', '腹腔镜胆囊切除术', 9, 3800.00, '次', 1),
('OR-AN001', '全身麻醉',     10, 900.00, '次', 1),
('OR-AN002', '椎管内麻醉',   10, 600.00, '次', 1);

-- 五大中心节点字典（时限为演示口径，目标分钟自 emc_visit.start_time 起算）
INSERT INTO emc_node_dict (node_code, node_name, center_type, seq_no, target_minutes, status) VALUES
('XT_FMC',   '首次医疗接触',     1, 1, 5,   1),
('XT_ECG',   '首份心电图完成',   1, 2, 10,  1),
('XT_TPN',   '肌钙蛋白回报',     1, 3, 20,  1),
('XT_BAL',   '球囊扩张开始',     1, 4, 90,  1),
('ZZ_CT',    '头颅CT完成',       2, 1, 20,  1),
('ZZ_TPA',   '静脉溶栓开始',     2, 2, 45,  1),
('CC_RESUS', '进入抢救室',       3, 1, 10,  1),
('CC_OR',    '损害控制手术开始', 3, 2, 60,  1),
('YC_US',    '产科超声完成',     4, 1, 15,  1),
('XE_WARM',  '新生儿复苏完成',   5, 1, 10,  1);
