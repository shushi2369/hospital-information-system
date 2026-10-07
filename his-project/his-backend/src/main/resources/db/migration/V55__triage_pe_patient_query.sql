-- V55（一百零七轮挂账清欠）：
-- 分诊登记（emc:triage:create）/ 体检登记（pe:record:create）表单的患者选择
-- 从裸 ID 手输改为姓名检索下拉，数据源 GET /patients 需要 patient:archive:query。
-- 分诊护士(6 EMC_NURSE)/体检人员(13 PE_USER) 缺该权限 → 下拉恒空、登记流程断裂。
-- 按"操作所需最小读权限"原则补绑定（仅查询，不含建档/修改）。

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, 43 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=6 AND menu_id=43);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 13, 43 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=13 AND menu_id=43);
