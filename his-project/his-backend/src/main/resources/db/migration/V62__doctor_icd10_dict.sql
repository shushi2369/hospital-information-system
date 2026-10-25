-- V62（一百四十五轮 U-6）：诊断弹窗接 ICD-10 字典下拉需要 /mrc/icd10（mrc:archive:query），
-- 医生角色写诊断是高频路径，原仅病案员/管理员可查字典。绑定按钮级菜单 142（病案查询，
-- menu_type=3 不产生导航入口），不扩大页面权限。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 2, 142 WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id=2 AND menu_id=142);
