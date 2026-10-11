-- V56（一百一十二轮子代理 A1/A3/A6）：
-- 表单下拉化后数据源缺权限的专项修补（V55 同类债的两处漏网 + V55 自身的 role 绑定勘误）。
--
-- A1 勘误：V55 注释称"分诊护士(6 EMC_NURSE)"，实际 role 6 = NURSE，EMC_NURSE = 12（V18）。
--   V55 把 patient:archive:query 绑给了全体普通护士（超最小权限）而真正需要的 12 没绑上。
--   处理：给 12 补绑；6 的绑定保留（护士只读患者索引对护理工作台无害，收回影响面大于收益）。
-- A3：病案员(7 MRCLERK) 借阅弹窗借阅人下拉数据源 GET /basedata/doctors 需要 basedata:doctor:query(28)。
-- A6：公卫人员(15 PUB_USER) 院感上报在院住院单下拉数据源 GET /inp/admissions 需要 inp:admission:query(103)。

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 12, 43 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=12 AND menu_id=43);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 7, 28 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=7 AND menu_id=28);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 15, 103 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=15 AND menu_id=103);
