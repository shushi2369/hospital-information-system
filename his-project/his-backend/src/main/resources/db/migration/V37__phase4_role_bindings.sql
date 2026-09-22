-- =====================================================================
-- HIS V37 四期三模块角色绑定审计收尾（三十二轮深挖）
-- 审计结论：V35 已绑定 PUB_USER(15)=273~278 全量、NURSE(6)/DOCTOR(2)=不良事件
-- 上报+查询；本迁移仅补真正缺口并兜底幂等：
--   ① DOCTOR 缺 288(cnt:execute)——会诊医师无法接受/完成会诊
--   ② 公卫人员账号缺失（角色 15 无用户可登录）
--   ③ 质控分派(282)/闭环确认(283) 属质控科职能，演示环境留管理员，不绑定
-- =====================================================================

-- 1. 公卫人员账号（INIT: 占位密码由 InitPasswordRunner 启动时替换为 BCrypt 散列；幂等）
INSERT IGNORE INTO sys_user (username, password_hash, real_name, phone, status) VALUES
('pub.user', 'INIT:His@2026', '公卫科医师', NULL, 1);

INSERT IGNORE INTO sys_user_role (user_id, role_id)
SELECT id, 15 FROM sys_user WHERE username = 'pub.user';

-- 2. DOCTOR 补会诊执行权限（接受/完成）
INSERT IGNORE INTO sys_role_menu (role_id, menu_id) VALUES (2, 288);
