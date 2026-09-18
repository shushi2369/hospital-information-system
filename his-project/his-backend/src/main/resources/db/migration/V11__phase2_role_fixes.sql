-- 二期前端集成权限缺口修复（页面数据源对目标角色 403，且病案员 EMR 质控
-- 与《11-二期实施计划》§4 权限矩阵不符）
-- 1) 医保专员：医保对账页需读取住院账单列表
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (8, 62);
-- 2) 药师：住院医嘱审核/摆药页需读取在院患者列表
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (4, 103);
-- 3) 病案员：EMR 质控（矩阵授权）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (7, 164);
-- 4) 医生：EMR 模板管理（矩阵授权）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (2, 165);
