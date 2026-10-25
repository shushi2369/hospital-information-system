# 交接文档（HANDOVER）

> 新接手者按本文 30 分钟完成上手。最后更新：2026-10-25（142 轮后）。
> 配套阅读顺序：本文 → [docs/README.md](README.md)（文档地图）→ [README.md](README.md)（业务全景）。

## 1. 一句话认知

教学用医院信息系统（求职作品集 + 课程实验）：Spring Boot 3.2 模块化单体（30 模块）+ Vue 3.4 + MySQL 8 + Redis，
门诊→住院→手术→检验→危急值→用血→医保→区域上报全闭环；
**769 项回归全绿**（API e2e 十套 363：验收 43 + 七期 255 + 修复回归 34 + 并发 31；UI 链 44 + 教学核验 41 + 单测 321），CI 每次推送全量回归。

## 2. 本机环境（Windows）

| 项 | 位置/值 |
|---|---|
| 代码仓库 | `C:\Users\Administrator\Desktop\医院信息系统技术指导文档`（git 根，项目在 `his-project/` 子目录） |
| 运行时 | `C:\his-runtime\`（ASCII 路径，中文路径会让 mysqld 参数解析失败） |
| MySQL | HIS-MySQL 服务，root/root123，库 `his`（客户端在 `C:\his-runtime\mysql-8.0.36-winx64\bin\`，不在 PATH） |
| Redis | HIS-Redis 服务（tporadowski 5.0.14，认证硬单点：宕机全端 401） |
| 后端 | HIS-Backend（WinSW 服务），jar 在 `C:\his-runtime\backend\app.jar`，日志 `logs/his-backend.log` |
| 前端 | HIS-Nginx（80 端口，`C:\his-runtime\nginx\`），静态产物 `C:\his-runtime\frontend\` |
| Maven | `C:\Users\Administrator\apache-maven-3.3.9\bin`（不在 PATH，需 export） |
| 健康检查 | `curl http://localhost:8080/actuator/health` |

## 3. 构建与部署（改代码后）

```bash
# 后端
export PATH="/c/Users/Administrator/apache-maven-3.3.9/bin:$PATH"
cd his-backend && mvn -q package -DskipTests
net stop HIS-Backend && cp target/his-backend-0.1.0-SNAPSHOT.jar /c/his-runtime/backend/app.jar && net start HIS-Backend
# 等待 ~25s，curl /actuator/health 到 UP

# 前端
cd his-web && npm run build
cp -r dist/* /c/his-runtime/frontend/
```

完整生产部署见 [deploy/部署指南.md](../deploy/部署指南.md)。一键脚本 `deploy/deploy.sh`（含健康检查与回滚提示）。

## 4. 回归测试（改代码后必跑）

```bash
cd deploy/e2e
python e2e_acceptance.py        # 全链路验收（CI 十套之首，本地勿漏）
python e2e_phase2.py && python e2e_phase3.py && python e2e_phase3b.py && python e2e_phase3c.py
python e2e_phase4.py && python e2e_phase4b.py && python e2e_phase5.py && python fix_regression.py
python ui_chain_smoke.py        # 需本机 Chrome/Playwright
python concurrency_test.py && python verify_course.py
```

- 基线：43（验收）/ 45 / 28 / 57 / 45 / 43 / 28 / 9 / 34（API 十套）+ 44（UI）/ 31（并发）/ 41（教学）全绿。
- 十一套共享一库、顺序执行；`verify_course` 与 `ui_chain_smoke` 的 teardown 会自动释放测试排台与账号。
- CI：`.github/workflows/ci.yml` 推送触发（单测 → 前端构建 → 一致性巡检 → 十套 e2e fail-fast）。

## 5. 账号矩阵

全部账号密码 `His@2026`（演示口径，生产必须换）。核心：admin（超管）/ dr.li（医生）/
cashier.li（收费）/ pharm.zhao（药师）/ nurse.wang（护士）/ mrc.zhou（病案员）/ yb.sun（医保专员）/
emc.li（急诊）/ lab.chen（检验）/ or.nurse（手术）/ pub.user（公卫）/ pe.nurse（体检）。
完整角色-权限矩阵见 `../设计文档/04-权限与安全设计.md` 与 sys_menu 种子。

## 6. 目录地图

```
his-project/
├── his-backend/        # Spring Boot（modules/ 30 业务模块 + infrastructure 安全横切）
│   └── src/main/resources/db/migration/   # Flyway V1~V58（增量迁移，禁止改历史文件）
├── his-web/            # Vue 3 + Element Plus（views/ 按模块，api/ 按后端域）
├── deploy/             # 部署脚本、nginx、e2e 十套、db 工具（备份/恢复/AES 轮换/一致性巡检）
└── docs/               # 文档（入口：docs/README.md 索引；CHANGELOG.md 逐轮变更）
```

## 7. 关键纪律（踩过的坑，勿再踩）

1. **状态迁移一律条件更新+断言行数**（`WHERE status=旧值`），禁止"读-判-updateById"——ors/bb/doc 曾因同源缺陷批量翻车。
2. **跨模块"X→Y"链**：入口判在院 ≠ 事务尾判在院；事务尾一律 `touchInHospital` 断言（doc/ors/bb/nur 已对齐）。
3. **Flyway 迁移只增不改**：多语句失败半程的恢复三步见 ops-runbook；权限类迁移必须"页面菜单 ↔ 数据源权限码"成对校验（V54~V58 教训）。
4. **资金路径**（收费/退费/结算/押金）：金额 BigDecimal、累计退款 ≤ 应收断言、条件更新断言缺一不可。
5. **文档纪律**：每轮修复记入 `docs/CHANGELOG.md`；一次性报告合并入常驻文档后删除；新增文档登记进 `docs/README.md`。
6. **私人文件**（简历/面试/账号密码/IDENTITY/USER/prompt*）永不提交，已在 .gitignore。

## 8. 已知接受项与待办

- 接受项清单见 [CHANGELOG.md](CHANGELOG.md) 尾部（XSS 纵深/EP 全量样式/EMC-PE 手输 ID 等）。
- 待办：跨模块并发链轮中"待验证"两条（F-4 addDiagnosis 断言、F-5 Hikari 池耗尽）；号源余量与开方库存的前端展示。
- 深度问题库：[lessons/bug-patterns.md](lessons/bug-patterns.md)。
