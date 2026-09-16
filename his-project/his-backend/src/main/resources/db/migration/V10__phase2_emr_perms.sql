-- EMR 质控与模板权限点补充（V7 遗漏）
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status) VALUES
(164, 125, 'EMR 质控',     3, 'emr:qc:do',        NULL, NULL, 5, 1),
(165, 125, 'EMR 模板管理', 3, 'emr:template:manage', NULL, NULL, 6, 1);
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1, 164), (1, 165);
