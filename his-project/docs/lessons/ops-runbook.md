# HIS 运维手册（Runbook）

> 面向：接手本系统的运维/开发人员。目标：10 分钟内完成日常操作，30 分钟内定位常见故障。
> 环境：Windows 单机演示部署（C:\his-runtime\），GitHub Actions CI（Ubuntu）。

## 1. 架构速览

```
浏览器 → nginx(:80 静态+反代 /api → :8080) → Spring Boot(his-backend.jar, :8080)
                                                ├─ MySQL 8.0.36 (C:\his-runtime\mysql-data)
                                                ├─ Redis 5.0（会话/幂等/单号序列）
                                                └─ Flyway V1~V37（迁移即Schema唯一真相）
Windows 服务：HIS-MySQL / HIS-Redis / HIS-Backend(WinSW) / HIS-Nginx(schtasks) / HIS-Backup(schtasks 02:00)
```

- 演示账号 18 个（初始密码 `His@2026`），迁移内以 `INIT:明文` 占位，启动器（InitPasswordRunner）自动替换为 BCrypt。
- 备份：每日 02:00 `his-backup.bat` → 本地 7 天保留 → **异盘同步 D:\his-backup**（防同盘单点）。

## 2. 日常操作

| 操作 | 命令 |
|---|---|
| 启停后端 | `net stop/start HIS-Backend` |
| 启动 nginx | `cd /c/his-runtime/nginx && start nginx`（计划任务不一定拉起，**必须手动确认**） |
| 部署新 jar | `mvn clean package` → `net stop HIS-Backend` → cp jar → `net start` → **核对日志 `Started HisApplication`** |
| 前端发布 | `npm run build` → `cp -r dist/* /c/his-runtime/frontend/`（nginx 已对 index.html 发 no-cache，浏览器不会用旧入口） |
| 一键部署 | `bash deploy/deploy.sh`（停服→备份→替换→启动→健康检查→**自动回收旧前端产物**） |
| 健康检查 | `bash deploy/health_check.sh`（后端存活/备份新鲜度/磁盘/Flyway/日志 ERROR 五项） |
| 资产回收 | `python deploy/cleanup_frontend_assets.py`（默认 dry-run；`--apply` 删除引用闭包外且 >3 天的旧代 Vite 产物。deploy.sh 已在前端发布后自动调用） |
| 一致性巡检 | `mysql his < deploy/db/consistency_check.sql`（46 段，应 0 行输出） |
| e2e 回归 | `python deploy/e2e/e2e_phaseX.py`（九套 318 断言） |
| UI 冒烟 | `python deploy/e2e/ui_smoke.py`（Playwright 全菜单 404 猎手） |
| UI 链路 | `python deploy/e2e/ui_chain_smoke.py`（Playwright 七链 25 断言：PHI/收费/接诊/角色/住院/手术/EMC） |
| CI | push 触发：单测 65 → 前端构建 → 一致性巡检 → API e2e 全量（fail-fast） |

## 3. 备份与恢复（含异地）

- 本地备份：`C:\his-runtime\backup\his_YYYYMMDD_HHMM.sql`（--single-transaction，保留 7 天）。
- 异地同步：bat 内 `robocopy` 同步到 `D:\his-backup`（同盘备份 = 假容灾，必须异盘）。
- **恢复演练标准流程**（六十九轮实测：备份 3s + 恢复 24s + 巡检 0 行，RTO ≈ 27s）：
  1. `mysql -e "CREATE DATABASE his_restore"`；
  2. `mysql --default-character-set=binary --binary-mode --force his_restore < D:\his-backup\his_latest.sql`（恢复 <30s）；
     **两个 flag 缺一不可**：事件留痕 payload 含 `\'`/`\"` 转义，Windows 客户端批处理会把它当"未知客户端命令"整语句失败（实测 7842 错）——V41 已把 payload 列 JSON→LONGTEXT，恢复必须 `--binary-mode` + binary 字符集才能无损往返；
  3. 起一个 8081 实例指向 his_restore（改 SPRING_DATASOURCE_URL/SERVER_PORT）；
  4. 跑一致性巡检（57 段 0 行）+ 登录冒烟 → 数据完整即通过。
- 注意：备份与数据同盘是单点；异地副本才是真容灾。

## 4. 常见故障速查

| 症状 | 根因 | 处置 |
|---|---|---|
| 网站打不开（连接拒绝） | nginx 没起 | `cd /c/his-runtime/nginx && start nginx` |
| 后端起不来，日志见 Flyway validate failed | 迁移失败行残留 | 见《bug-patterns.md》#14：修 SQL→删 failed 行→清残留→重启；**失败重放的已成功 DDL 会残留，必须重新盘点** |
| 部署后新页面 404 | 浏览器缓存旧 index.html | nginx 已发 no-cache；仍命中则强刷 |
| 登录报 C9001 会话写入失败 | Redis 不可用 | `net start HIS-Redis`；Redis 5 无 GETEX，代码已规避 |
| 单号重复/断号 | Redis 不可用降级为实例内序号 | 恢复 Redis 后自然切回（集群部署必须 Redis） |
| Redis 恢复后收费报单号重复 | 恢复期 Redis 计数回退（重启丢失），INCR 从低位重新开始 | 五十一轮已修：发号器降级段走 500001+ 高基址、恢复时补偿跳号（IdGenerator）；若仍出现请核对 Redis 持久化配置 |
| Redis 恢复后头几分钟接口仍失败 | 后端连接池懒重连 | 恢复后等待 5~10 秒再验证；首次登录失败属预期，重试即成功 |

### Redis 停机演练实录（五十一轮，2026-10-03）

`net stop HIS-Redis` → 探针 → `net start HIS-Redis`，七步结论：

| 步骤 | 结果 |
|---|---|
| 宕机前合法令牌查询 | 200 OK |
| 宕机期·同一合法令牌查询 | **401 A0002**（会话读取失败→按未登录处理，全系统认证瘫痪） |
| 宕机期·登录 | **500 C9001 会话写入失败**（fail-closed，设计使然） |
| 恢复后·宕机前旧令牌 | 200 OK（`net stop` 优雅关闭持久化了会话，恢复后原会话回归） |
| 恢复后·重新登录 | 200 OK（注意恢复后首几秒连接池懒重连窗口，重试即成） |

结论：**Redis 是认证单点**——全量宕机期间幂等/单号的降级代码不可达（都在认证之后），仅在部分降级（超时抖动）时生效。幂等 fail-open 由数据库唯一约束兜底；单号降级段 500001+ 与正常段物理隔离，恢复补偿跳号防撞号。集群部署必须 Redis 哨兵/集群。
| 定时任务没跑 | schtasks 未触发 | 手动 `schtasks /run /tn HIS-Backup`；任务幂等可安全重跑 |
| CI 巡检 job 失败 `Table doesn't exist` | 后端未就绪就跑了 SQL | 已改为轮询 Flyway V37 完成标记；勿回退为固定 sleep |
| CI 登录探活报 C9001 | 该 job 没有 Redis service | 探活方式必须匹配 job 依赖；或补 redis service |

## 5. 踩坑清单（工程环境）

- Git Bash 无 `python3` 别名（用 `python`）；CI Ubuntu 有。
- `mysql -N -e` 输出带 `\r`，做路径/字符串判断前 `tr -d '\r'`。
- `mysql -e "a;b;c"` 多语句中途出错：已执行的不回滚、后续不执行——修复脚本必须逐步核对生效范围。
- Maven 不在 Git Bash PATH：`export PATH="/c/Users/Administrator/apache-maven-3.3.9/bin:$PATH"`；`mvn | tail` 会吞退出码，必须查日志确认 BUILD SUCCESS。
- 测试脚本幂等键（X-Idempotency-Key）只能 ASCII，中文进 HTTP 头直接 latin-1 崩溃；无 body 的 POST 端点要显式指定 method，否则会被发成 GET。
- 删除文件前先取 size：`os.remove` 成功后再 `getsize` 同一路径必报 WinError 2（文件已不存在）。
- 床号上限 16 位：测试自动补建床的编号用 uid 尾部拼接（`"CC"+uid[-6:]`），uid 全拼会超限 400。
- GitHub Steps 默认 `bash -e`：`V=$(mysql ...)` 赋值失败即整步退出，须 `|| true`；探活方式必须匹配 job 的 service 依赖。
- 前端自动化：Element Plus 的 select/radio-button 会被内层元素遮挡（Playwright actionability 卡住），用原生事件 dispatch 或坐标点击兜底。
- 批量改文件后跑 `wc -c` 清单核对（python open('w') 异常路径会把源文件截断为 0 字节）。
- 血袋分页按效期升序（近效期优先），新入库袋在结果尾部——跨轮数据累积过百后首页检索不到本轮新袋，测试断言用 `GET /bb/bags?bagNo=` 精确定位（五十一轮）。

## 6. 版本与迁移纪律

1. 迁移文件只增不改（V38、V39……）；已执行迁移的 checksum 不可触碰。
2. 迁移尽量幂等（INSERT IGNORE / 条件更新），防部分失败后的重放残留。
3. 每轮查验/优化的教训同步进《bug-patterns.md》——经验不入库等于没有。
