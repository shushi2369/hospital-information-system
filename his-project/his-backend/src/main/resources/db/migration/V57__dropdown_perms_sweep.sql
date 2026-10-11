-- V57（一百一十三轮"权限数据源"专项对账）：
-- 表单下拉数据源端点 × 使用角色的权限矩阵全量对账后补缺（V55/V56 的方法论推广到全库）。
-- 对账口径：页面实际使用者（按业务权限码归属）∩ 该页下拉调用的 GET 端点权限码。
--
-- 缺口清单：
-- 1. NURSE(6)：BedBoard 床位费项目下拉 / OrsWorkbench 领用场景 / AeEvents 发生科室下拉 /
--    MatWorkbench 领用科室下拉 / ScheduleList 科室——缺 basedata:item:query + basedata:dept:query
-- 2. PHARMACIST(4)：MatWorkbench 采购/领用科室下拉——缺 basedata:dept:query
-- 3. OR_NURSE(10)：OrsWorkbench 手术(cat9)/麻醉(cat10)项目下拉——缺 basedata:item:query
-- 4. PE_USER(13)：PeWorkbench 套餐/分项项目下拉——缺 basedata:item:query
-- （DOCTOR 的 dept/doctor/item 三项齐全； clinic 无患者搜索下拉，DOCTOR 不需要 patient:archive:query；
--   MRCLERK/PUB_USER/EMC_NURSE 已由 V56 补齐。）

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 6, m.id FROM sys_menu m
WHERE m.permission_code IN ('basedata:item:query', 'basedata:dept:query')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=6 AND rm.menu_id=m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 4, m.id FROM sys_menu m
WHERE m.permission_code = 'basedata:dept:query'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=4 AND rm.menu_id=m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 10, m.id FROM sys_menu m
WHERE m.permission_code = 'basedata:item:query'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=10 AND rm.menu_id=m.id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 13, m.id FROM sys_menu m
WHERE m.permission_code = 'basedata:item:query'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=13 AND rm.menu_id=m.id);
