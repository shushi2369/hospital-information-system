-- 七十轮：退押金权限（按钮级），绑定 ADMIN / CASHIER（对齐押金缴纳的授权面）。
-- INSERT IGNORE 幂等（对齐 V37 纪律）。
INSERT IGNORE INTO sys_menu (parent_id, menu_name, menu_type, permission_code, path, component, sort_no, status, created_by)
SELECT 101, '押金退还', 3, 'inp:deposit:refund', NULL, NULL, 5, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission_code = 'inp:deposit:refund');

SET @refund_menu = (SELECT id FROM sys_menu WHERE permission_code = 'inp:deposit:refund' LIMIT 1);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT r.id, @refund_menu FROM sys_role r WHERE r.role_code IN ('ADMIN', 'CASHIER')
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = r.id AND rm.menu_id = @refund_menu);
