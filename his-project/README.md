# HIS 一期工程（阶段一~四全部完成）

依据《医院信息系统技术指导文档》与 `设计文档/` 设计包实现。阶段一（基础底座）、阶段二（门诊闭环）、阶段三（收费与药房）、阶段四（报表与交付）均已完成，并通过真实环境端到端验收。

- **后端 his-backend**：Spring Boot 3.2 + Java 17 + MyBatis-Plus + MySQL 8 + Redis + Flyway；统一响应/错误码/异常、traceId、幂等切面、审计切面、JWT+Redis 会话认证、RBAC 权限码校验。
  - 阶段一模块：system（认证/用户/角色/菜单/双日志）、basedata（机构/科室/医生/药品/收费项目 + 跨模块应用服务）。
  - 阶段二模块：patient（建档/身份证加密与摘要唯一校验/查询/补卡，P-01~P-05）、registration（挂号/退号/候诊队列/号源行锁控制/23:55 过号任务，R-01~R-05）、clinic（接诊/病历/诊断/医嘱/处方后端取价重算/检查检验申请/提交病历，C-01~C-12，医生数据范围隔离）。
  - 阶段三模块：billing（待缴费汇总/收费/明细级退费（药品费整方退、已发药先退药 B3004、已完成就诊号费不可退 B3005）/收费员日结，B-01~B-08）、pharmacy（处方审核留痕/发药 FEFO 拆批条件扣减/整方退药回补/入库/批次/库存预警/流水/0:05 过期任务，F-01~F-11）。
  - 阶段四模块：report（日挂号量/科室排名/日门诊量/日收入与类别分布/收入明细下钻/药品库存汇总，T-01~T-05，收入明细 CSV 导出带 BOM）；交付物：本 README、docs/用户操作手册.md（五角色）、deploy/e2e/e2e_acceptance.py 端到端验收脚本。
- **运行验证**：已在本地 MySQL 8.0.36 + Redis 环境完成 §9 全验收场景端到端验证（建档→挂号→接诊→开方→收费→审核→发药→退药→退费→日结→日志→报表），并固化自动化脚本 `deploy/e2e/e2e_acceptance.py`（40 项断言，可重复执行，当前 40/40 通过）。
- **性能验证（§7）**：`deploy/perf/perf_test.py` 实测通过——创建 100 个真实用户独立会话；100 并发混合查询 300 请求 0 错误、p95=70ms（阈值 2000ms）；100 并发挂号 100/100 成功、p95=459ms（阈值 3000ms）。
- **备份恢复演练（§7）**：`mysqldump --single-transaction` 全量备份 445KB → 恢复至临时库 → 36 张表、行数与金额抽查逐项一致，演练后临时库已清理。
- **接口文档**：`docs/openapi.json`（OpenAPI 3，66 个接口路径，springdoc 运行时生成）。

## 端到端验收脚本

```bash
python deploy/e2e/e2e_acceptance.py [BASE_URL]   # 默认 http://localhost:8080/api/v1
```
覆盖 40 项断言：五角色权限（垂直+数据范围）、幂等重放、全部异常拦截码（B1001/B1002/B2001/B3001/B3003~B3006/B4003/B4006/B4008）、发药库存精确扣减与退药回补、日结快照与明细交叉复核、五张报表、CSV 导出、审计日志。可重复执行（每次生成唯一测试患者；当日已日结时自动走 B3006 快照分支）。

## 性能压测脚本

```bash
python deploy/perf/perf_test.py [BASE_URL]
```
创建 100 个真实用户（四角色）与独立会话，执行 100 并发混合查询与 100 并发挂号提交，输出 p50/p95/p99 与错误率，自动判定 §7 三项验收线。注意：脚本会向库中写入压测用户/患者/挂号数据，仅用于测试环境。
- **前端 his-web**：Vue 3 + TS + Element Plus + Pinia，登录、动态菜单路由；阶段一系统管理与基础资料页面；阶段二患者建档、挂号管理（现场挂号+记录+退号）、医生候诊队列、就诊工作台（病历/诊断/医嘱/处方/检查申请/提交）。
- **数据库**：V1 全量 35 张表；V2 核心数据（角色/全部菜单权限/管理员，所有环境加载）；V3 演示数据（6 演示账号/科室/医生/药品/收费项目，仅 dev/test 加载）。
- **部署 deploy**：docker-compose（mysql/redis/minio/backend/nginx）、.env.example、每日备份脚本。

## 环境要求

- JDK 17、Maven 3.9（或使用 IDE）
- Node 18+ 与 npm（前端）
- Docker + Docker Compose（MySQL/Redis/MinIO；也可自装 MySQL 8 与 Redis 7）

## 本地启动（开发）

1. 启动依赖（任选其一）：
   ```bash
   cd deploy && docker compose up -d mysql redis minio
   ```
2. 启动后端（Flyway 自动建表 + 载入演示数据）：
   ```bash
   cd his-backend
   mvn spring-boot:run
   ```
   默认连接 `localhost:3306`，账号 root / root123，库名 his（可用环境变量 DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD 覆盖）。
3. 启动前端：
   ```bash
   cd his-web
   npm install
   npm run dev
   ```
   打开 http://localhost:5173 ，代理已指向 http://localhost:8080 。
4. 内置账号（初始密码 `His@2026`，首登请修改）：
   admin（管理员）/ dr.wang、dr.li、dr.chen（医生）/ cashier.li（收费员）/ pharm.zhao（药师）/ auditor.sun（对账员）

## 打包与容器化部署

```bash
cd his-backend && mvn -DskipTests package
cd ../his-web && npm run build
cd ../deploy && cp .env.example .env  # 修改密钥后
docker compose up -d --build
```
访问 http://localhost/ ；生产环境将 `FLYWAY_LOCATIONS` 改为 `classpath:db/migration`（不加载演示数据）。

## 备份恢复

- 每日备份：`deploy/backup/backup-mysql.sh`（crontab 02:00，保留 7 天）
- 恢复：`gunzip < his_YYYYmmdd_HHMMSS.sql.gz | docker exec -i his-mysql sh -c 'exec mysql -uroot -p"密码" his'`，恢复后启动应用验证核心流程。

## 安全提示

- `.env` 中的 JWT_SECRET / AES_KEY / DB_PASSWORD 生产必须全部替换；`.env` 已被 .gitignore 排除，不入库。
- 演示数据与占位密码初始化（InitPasswordRunner）仅用于 dev/test。

## 阶段一验收对照（指导文档 §6 阶段一）

| 验收项 | 状态 |
|---|---|
| 管理员可创建用户、角色、科室、医生和药品 | 已实现（页面 + 接口） |
| 无权限用户不能访问受限接口 | 已实现（@PreAuthorize 权限码 + 数据范围），接口测试用例待补（CI 阶段） |
| 关键操作可查询日志 | 已实现（@AuditLog 切面 + 日志查询页） |

## 阶段二验收对照（指导文档 §6 阶段二）

| 验收项 | 状态 |
|---|---|
| 患者建档到医生提交就诊记录全程可完成 | 已实现（建档→挂号→接诊→病历/诊断/处方/检查申请→提交） |
| 重复挂号、重复提交、未保存病历均有明确提示 | 已实现（B1002 / 幂等键 A0004·A0005 / B2003） |
| 就诊状态流转正确 | 已实现（挂号单与就诊状态机按《05》§2 逐态校验） |

## 已知限制（收尾项）

- 接口四类测试（成功/参数错误/无权限/重复提交）与关键流程回归用例将在收尾前补齐并纳入 CI。
- 号源控制为"医生行锁 + 计数"实现，极端并发下理论存在微小竞态窗口，二期引入号源表彻底消除。
- 诊查费取自收费项目字典的固定编码（his.registration.* 可配置，默认 ITEM001/ITEM002），二期做成规则配置。
- 本机开发环境无 Docker/MySQL 时，后端编译通过但未做运行时验证；上线前需在测试环境完成阶段验收。
