# HIS 系统续接工作上下文 V3（新对话粘此文件即可无缝续接）

## 你是谁、在做什么

你是一个医院信息系统（HIS）的全栈开发者。此项目已完成一期（门诊闭环）、二期（住院闭环）、三期三批（LIS+危急值+药库 / 手术+影像+急诊 / 人事+物资+体检+CDSS+绩效）、四期（输血闭环+耗材批次+CDSS 全面化+互认+门诊RIS+法定上报+会诊+院感+不良事件），经过 **45 轮系统化查验**（120+ 真实缺陷修复），当前处于**收敛维护态**（功能开发完毕，持续走查+优化）。

## 项目位置与关键路径

- **工程根**：`C:\Users\Administrator\Desktop\医院信息系统技术指导文档\his-project\`
- **设计文档**：`C:\Users\Administrator\Desktop\医院信息系统技术指导文档\设计文档\`（24 份，含数据字典 103 表 1333 列）
- **运行时**：`C:\his-runtime\`（MySQL 8.0.36 / Redis 5 / nginx / 后端 jar）
- **GitHub 仓库**：https://github.com/shushi2369/hospital-information-system（私有，main 分支，最新 `46bf2aa`）
- **本机 IP**：172.17.176.47，系统入口 http://localhost/（nginx 80 → 前端 dist + /api 代理到 8080）
- **服务**：HIS-MySQL / HIS-Redis / HIS-Backend(WinSW) / HIS-Nginx / HIS-Backup 全部 Windows 服务

## 技术栈

- 后端：Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5.5 + MySQL 8.0.36 + Redis 5 + Flyway **V1~V37**
- 前端：Vue 3.4 + TypeScript + Element Plus（按需引入）+ Pinia + Vite 5
- 架构：前后端分离模块化单体，18 个业务模块，**271+ REST 接口**
- **UI 风格**：明日方舟风格暗色主题（琥珀橙 #e8a040 + 切角几何）+ 日间浅蓝主题，头部 sun/moon 一键切换

## 数据库迁移版本（V1~V37 全部已应用）

| 版本 | 内容 |
|---|---|
| V1~V5 | 一期全量 35 表 + 角色/菜单 |
| V6~V12 | 二期住院 20 表 + 退费可空化 + EMR 权限 |
| V13~V16 | 三期一批 LIS/危急值/药库 8 表 + LIS_USER 角色 |
| V17~V21 | 三期二批 ORIS/RIS/EMC 15 表 + BB_NURSE/RIS_USER/EMC_NURSE 角色 |
| V22~V25 | 三期三批 HR/MAT/PE/CDSS 12 表 + PE_USER 角色 |
| V26~V31 | 过敏史 CDSS + 出院钩子 + 槽位释放 |
| V32~V36 | 门诊 RIS 闭环 + 法定上报/AE/会诊 4 表 + PUB_USER + 索引优化 |
| V37 | 四期三模块角色绑定收尾（pub.user 账号 + DOCTOR 补 cnt:execute） |

## 内置账号（密码均 His@2026，共 18 个）

admin、dr.wang、dr.li、dr.chen、cashier.li、pharm.zhao、auditor.sun、nurse.wang、nurse.liu、mrc.zhou、yb.sun、lab.chen、or.nurse、ris.zhang、emc.li、pe.nurse、bb.tech、**pub.user**

角色 15 个（ADMIN~PUB_USER），权限节点 155 个。

## 测试体系（十套 + CI 四 job 全绿）

| 套件 | 断言数 | 覆盖 |
|---|---|---|
| e2e_acceptance.py | 40 | 一期门诊 |
| e2e_phase2.py | 42 | 二期住院 |
| e2e_phase3.py | 28 | 三期一批 LIS/危急值/药库 |
| e2e_phase3b.py | 57 | 三期二批 手术/影像/急诊 |
| e2e_phase3c.py | 45 | 三期三批 HR/物资/体检/CDSS/KPI |
| e2e_phase4.py | 43 | 四期 输血/批次/互认/门诊RIS |
| e2e_phase4b.py | 27 | 四期 pub/cnt/ae 三模块 |
| concurrency_test.py | 12 | 并发安全（含双入院/双执行竞态） |
| fix_regression.py | 24 | 修复回归复验 |
| ui_smoke.py | 44 页 | Playwright 全菜单 404 猎手 |
| ui_chain_smoke.py | ~8 | UI 链路回归（收费/接诊/PHI/角色授权） |
| mvn test（单测） | 72 | 11 个测试类核心逻辑 |
| **合计** | **≈560** | **全绿** |

CI（GitHub Actions）四 job：后端单测 70 项 → 前端构建 → API e2e 九套 318 断言（fail-fast）→ 数据一致性巡检 46 段。

## 运维交付

- deploy/deploy.sh：一键部署（停服→备份→替换→启动→健康检查→Flyway 检查）
- deploy/health_check.sh：6 项告警（后端/nginx 自动拉起/备份新鲜度/磁盘/Flyway/日志 ERROR）
- deploy/db/consistency_check.sql：46 段孤儿引用 + 状态一致性
- deploy/backup/his-backup.bat：每日 02:00 本地 7 天 + 异盘 D:\his-backup 同步
- deploy/perf/perf_test.py + perf_concurrent.py（40 线程写并发 120/120 成功）
- 恢复演练：RTO < 60s（含一致性验证），备份异盘

## 文档交付

- docs/lessons/bug-patterns.md：**19 条系统性 bug 模式**（每条含机理/真实案例/检查方法）
- docs/lessons/ops-runbook.md：运维手册（含 8 类故障速查 + 踩坑清单）
- docs/lessons/demo-script.md：10 分钟演示剧本（求职/教学）
- docs/lessons/arknights-ui-proposal.md：明日方舟 UI 改造方案（调研+设计令牌+动画规范）
- 设计文档/：24 份（01~23 + 数据字典 103 表 1333 列）

## UI 风格

- **双主题**：日间（白底 #ffffff + 浅蓝 #3b9bd5 + 黑字）+ 夜间（暗色 #141414 + 琥珀橙 #e8a040 + 灰字）
- **切换**：Layout.vue 头部 sun/moon 图标，localStorage `his_theme` 持久化
- **几何**：全局去圆角，按钮/卡片/对话框/标签 clip-path 切角
- **动画**：路由右滑入 / 对话框 overshoot 弹入 / 按钮悬停扫入 / 列表交错 / Toast 右上滑入 / 卡片扫线
- **PHI 脱敏**：患者列表/详情 手机号 `138****5678` / 身份证 `3401**********4567`
- **字体**：Rajdhani（EN/数字）+ Noto Sans SC（中文）

## 已完成的查验与修复（45 轮，120+ 缺陷）

### 累计修复清单（按类别）

- **安全**：越权/IDOR/归属/Rh/过敏守门/密码强度/签名真实性/Mass Assignment/PHI 脱敏/JSON 注入 7 处/EMPI 自合并
- **并发**：唯一约束兜底/乐观锁/原子扣减/预检+插入竞态/双入院患者行锁/双护士执行单条件更新/登录限流误伤修复
- **状态机**：设计承诺兑现/门禁成对/跨模块联动/状态推进/公卫卡顺序接反/出院结算零费用卡死
- **金额**：0元脏账/负单价/快照缺失/退款扣减/限流计数
- **数据一致性**：46 段孤儿引用扫描零发现
- **UI**：全站白屏 P0/离室核查入口缺失/双签本人强制/三页 404/visitId 契约/角色授权回显 P1/手术表单缺计费字段/分诊弹窗不关
- **性能**：床位一览 N+1 -83%/病案惰性补建 -78%/收费待缴批量化
- **运维**：备份异盘/nginx 自拉起/安全头继承/登录限流

### bug 模式 19 条沉淀在 docs/lessons/bug-patterns.md

涵盖丢失更新/数据形态/权限矩阵/设计承诺/实体完整性/Flyway/唯一索引/事件转义/UID撞/DateTime精度/事件注入/幂等释放/UI自动化/批量脚本/按钮门禁成对/API e2e 盲区/唯一索引槽位/跨用户并发/运行库漂移/参数退化

## 你接下来应该做什么

用户说"继续"时的候选优先级：
1. **五期实施**（区域平台上报网关 P0——方案已出，需外部接口规范到位）
2. **急诊绿通/多中心** UI 深水区（双 visit 同时在院业务规则）
3. **EMC 节点字典扩展**（现仅 10 节点，真实胸痛中心需 15+ 节点）
4. **链路脚本 4-6 步选择器精调**（处方弹窗/收费确认框在独立 Chromium 中的自动化）
5. **用户报告问题** → 先读日志（/c/his-runtime/backend/logs/his-backend.log）定位

## 操作方式

- 服务管理：`net stop/start HIS-MySQL|HIS-Redis|HIS-Backend`
- nginx：`cd /c/his-runtime/nginx && start nginx`（可能崩溃需手动，health_check 1b 节自动拉起）
- e2e：`python deploy/e2e/e2e_acceptance.py` … 逐套单独跑全过
- 并发测试：`python deploy/e2e/concurrency_test.py`
- 修复回归：`python deploy/e2e/fix_regression.py`
- UI 冒烟：`python deploy/e2e/ui_smoke.py`（需 playwright）
- UI 链路：`python deploy/e2e/ui_chain_smoke.py`
- 一致性巡检：`mysql his < deploy/db/consistency_check.sql`
- 压测：`python deploy/perf/perf_test.py` + `perf_concurrent.py`
- 前端更新：`cd his-web && npm run build && cp -r dist/* /c/his-runtime/frontend/`
- 部署：`bash deploy/deploy.sh`（一键含健康检查）
- 推送：`export HTTPS_PROXY=http://127.0.0.1:7897 && git push origin main`
- Maven 不在 PATH：`export PATH="/c/Users/Administrator/apache-maven-3.3.9/bin:$PATH"`

## 踩坑教训（续接必读，完整版见 docs/lessons/ops-runbook.md）

1. **改 SQL/资源后必须 `mvn clean package`**，否则旧 jar 重复执行半套 DDL
2. **已执行的 Flyway 迁移文件不可触碰**（checksum 校验失败阻塞启动）——改动一律走增量迁移 V+1
3. Flyway 失败后：删 history 失败行 + DROP 残留表 + 修代码 + clean package + 重启
4. `sys_menu.permission_code` 有唯一约束 `uk_perm_code`，新菜单权限码须先查重
5. 8080 端口残留进程：`netstat -ano | grep :8080` + `taskkill //F //PID`
6. 启动确认：`grep "Started HisApplication" /c/his-runtime/backend/logs/his-backend.log`
7. **MySQL DATETIME(0) 四舍五入**：LocalDateTime.now() 纳秒写入会进位，时间基准须 `truncatedTo(SECONDS)`
8. **MyBatis-Plus updateById 忽略 null 字段**——置空须用 LambdaUpdateWrapper.set(column, null)
9. **UI 自动化填 el-input-number**：DOM 赋值不触发 Vue 模型，须真实键盘路径或原生事件
10. **e2e 排台台次须跨轮唯一**：uk_schedule_slot 唯一索引不含状态，已完成行永久占位
11. **多套 e2e 连跑共享资源会耗尽**（床/袋/槽位）——脚本内须自动补建或跨日跨房间搜索
12. **phone 生成须 ≤11 位且唯一**：多脚本连跑时 uid 变长会超限
13. **nginx 需手动启动**：`cd /c/his-runtime/nginx && start nginx`
14. **Maven 不在 Git Bash PATH**：`export PATH="/c/Users/Administrator/apache-maven-3.3.9/bin:$PATH"`
15. **`mysql -N -e` 输出带 \r**：做路径判断前 `tr -d '\r'`
16. **`mysql -e` 多语句中途出错**：后续不执行、已执行不回滚——修复脚本须逐步核对
17. **Git Bash curl -d 带中文**：GBK 编码毁 JSON，中文载荷走 python urllib
18. **GitHub Actions bash -e**：`V=$(mysql ...)` 赋值失败即整步退出，须 `|| true`
19. **nginx add_header 继承陷阱**：location 内出现 add_header 后 server 级头不继承，须逐 location 补
20. **EP el-menu background-color prop 不可动态切**：用 CSS 变量 `--el-menu-bg-color` 替代
21. **登录后必须验证 /auth/me 身份**：IAB UI 登出/登录可能静默失败
22. **UI 断言勿依赖长驻页**：Element Plus 组件在长驻页可能僵死（刷新自愈），用独立 Playwright

## 沟通风格

- 用户偏好中文；每轮结束后给出结构化汇总表
- 修复必须有实测验证（不能只编译通过）
- 诚实标注"已验证"与"未验证"边界
- 用户说"继续"时按上文优先级推进
- 简短指令如"继续"/"1"/"要" = 按你建议的方案推进
