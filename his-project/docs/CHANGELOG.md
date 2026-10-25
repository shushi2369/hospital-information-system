# 变更日志（CHANGELOG）

> 唯一追加式变更日志：每轮修复/变更在顶部追加一节。格式：日期｜commit｜要点。
> 历史轮次（1~109）的详细记录见 git log 与各轮 commit message；本文件自 110 轮文档整合起维护。

## 2026-10-25 ｜ 144 轮（见 commit）：严格标准准出回归——八阶段测试，发现 3 项并修复 1 项

- **八阶段全过准出**：环境预检/构建部署对齐（V61 应用）/e2e 363/UI 44/巡检/
  安全 5 探针/性能（查询 p95 59ms、挂号 p95 399ms，100 并发零错误）/报告。
  报告全文：docs/测试报告-20261025.md。
- **发现 1（已修，P2 测试资产）**：巡检段 orphan_cnt_doctor 对可空列缺
  `IS NOT NULL` 护栏 → 5 行未接诊会诊单误报孤儿；补护栏（同文件 pub_card_visit
  段本有正确范式）。**教训：LEFT JOIN 孤儿检查必须排除 NULL 外键**。
- **发现 2（P3 flaky，观察项）**：e2e_phase2.py 首跑 IndexError 复跑 4 连绿——
  脚本 8 处未护栏 `[...][0]` 取首元素；未盲改，留护栏整改建议。
- **发现 3（存量残留定性）**：cnt_request#69/#70 伪造链路 + mat_purchase#513
  幽灵供应商共 3 行，均为修复前 e2e 残留；探针实证现行代码拒绝同类输入。
- **过程教训**：Git Bash 部署命令 Windows 路径未加引号 → 反斜杠被吞、jar 静默
  落错文件名、服务仍跑旧 jar；已用 /c/ 路径重部署并记入运行时记忆。

## 2026-10-25 ｜ 142 轮：双并发检验——单测质量审计 + 独立复算（316→321）

- **双代理检验结论**：独立复算 316/316 零偏差；CHANGELOG 六节用例数与 @Test 实数
  零偏差；11 类 130 用例无假测试、错误码与生产 throw 点 100% 对得上。
- **弱用例修复**（审计 A 代理 Top 缺口）：whse cancel 空洞用例补写库证据（删掉
  条件更新不再绿）、pe finish/updatePackage/register 补 captor 状态翻转断言、
  lis publish 补危急项汇总断言 + receive 补标本状态 2、mrc giveBack 补双写证据、
  pharmacy review 补留痕内容（pass→1/reject→2）。
- **缺口用例补齐（+6）**：whse cancel 10/20→40 双翻转、pe saveResult 撞
  uk_peresult→A0008、medins apply 撞 uk→B6401（防并发双申报唯一兜底）、
  pharmacy 效期=今天批次行锁复检跳过、review reject 留痕、
  **emc timeline 达标边界**（elapsed==target 达标 / +1 分钟不达标——国考口径唯一
  未测业务计算）。
- **文档收敛**（审计 B 代理发现）：HANDOVER L3 轮次滞后 20 轮、L9 "363" 系 e2e
  总数误标且分量和差 8（255+44+31+41=371≠363）→ 重算为 768 项（142 轮口径，
  143 轮复测修正为 **769 项**，见下节）；必跑清单补 e2e_acceptance.py（与 CI
  十套口径对齐）；ci.yml "318 assertions"→362；本 CHANGELOG 六节补 commit 锚点。
- 全量回归 321/321 全绿。单测总账：116→321。
- 【142 轮勘误】验收套件静态 check 计数 42，运行时实际 43（循环内动态断言），
  API e2e 十套实跑合计 **363**（43+45+28+57+45+43+28+9+31+34，本地全绿实证），
  回归总数应为 769 = 363 + 44 + 41 + 321；ci.yml/HANDOVER 数字 143 轮已同步。

## 2026-10-25 ｜ 143 轮：CI 五十连挂修复轮——phase5 端口错/巡检 SQL 语法错/种子数据违规

- **CI 失败史**：10 月 7 日后 79 连挂，CI 升级（8dd428f）后从未绿过。三类独立根因：
- **根因一（e2e job）**：e2e_phase5.py 默认 BASE 是 `http://localhost`（80 端口，
  本地 nginx 反代），其余九套默认 8080 直连后端——CI 无 nginx，phase5 首连必拒
  （Connection refused，且前七套 45+28+57+45+43+28 全过、崩溃点 100% 确定）。
  修复：phase5 默认端口对齐 8080。**本地 363/363 十套全绿实证不复现**。
- **根因二（巡检 job）**：consistency_check.sql 第 262 行段（128 轮加）EXISTS 子句
  后残留 `AS chk` → 1064 语法错，mysql 客户端中断后续段。修复：删残留别名。
- **根因三（巡检 job）**：V35 种子"不良事件演示（已闭环）"只给 status=40 未给
  closed_time，违反"已闭环必有闭环时间"不变量 → inconsistent_ae_40_no_closed_time=1。
  修复：新增 **V61** 迁移（closed_time=event_time 兜底回填）；本地库手工同步，全量
  巡检 SQL 复跑 EXIT=0（orphan_* 为本地数百轮 e2e 残留，CI 全新库不受影响）。
- 文档数字三处修正（362→363、768→769、验收 42→43）。待 CI 首绿验证。

## 2026-10-25 ｜ 141 轮（6ef8897）：单测收尾弹——人事/字典薄层（292→316），零单测清单清零

- **DoctorServiceTest（9 用例）**：临床科室门禁（非临床/停用科室拒建医生档案）、
  登录账号绑定查重 B5004、医生编码查重 B5001、唯一索引竞态、修改时换科室重查
  临床资质、部分字段更新口径（未传不改）。
- **DrugServiceTest（7 用例）**：编码查重 B5001（预检+竞态双路径）、状态取值门禁、
  缺省值兜底（库存预警 0/抗生素 0）、部分字段更新。
- **HrServiceTest（8 用例）**：离职停用+离职日期+并发 0 行 A0008、职称变更三重门禁
  （离职拒/同职称拒/**未来生效日拒**——即时生效语义，档案与记录不倒挂）、变更留痕。
- **零单测服务清单正式清零**：全部 30 个模块均已被单测覆盖或判定为无需单测
  （纯委托层/字典薄层已注明理由）。
- 全量回归 316/316 全绿。十一轮单测总账：116→316（+200）。

## 2026-10-25 ｜ 140 轮（0a748aa）：单测第十弹——五大中心状态机（272→292）

- **EmcServiceTest（20 用例）**：分诊级别 1~4 门禁、五大中心登记与分诊预判一致性校验、
  重复登记预检+唯一索引竞态双路径、**节点时间截断到秒**（DATETIME(0) 四舍五入
  对同秒节点误拦——十四轮查验回归固化）、节点字典校验（不存在/停用/他中心节点集）、
  节点早于登记时刻拦截、关档后节点/关联冻结、**跨患者挂单 IDOR**（八十八轮 P1-2）、
  关档行锁互斥 + 分诊单同步 1→2（原 status=2 无写入方修复固化）、并发关档 0 行。
- **零单测服务清单收敛完毕**：emc/emc ✓，report=纯委托层不写（测试 mock 自己无意义），
  余 hr/basedata 为字典 CRUD 薄层（查重+插入，模式与 Whse 同）。
- 全量回归 292/292 全绿。十轮单测总账：116→292（+176）。

## 2026-10-25 ｜ 139 轮（850c9c9）：单测第九弹——体检状态机（256→272）

- **PeServiceTest（16 用例）**：套餐空项目拒建/JSON 序列化落库、**在途登记禁改套餐**
  （112 轮 B10：登记只存 packageId 快照，改套餐会改写齐全口径）、套餐停用拒登记、
  分项录入套餐内校验（套餐外脏数据禁录）、覆盖式 upsert 双路径 + **乐观锁 0 行
  A0008**（112 轮 B08 固化）、完成检查分项齐全判定（缺 N 项提示）、总检发布门禁
  （未完成/重复发布拒）+ 条件更新 30→40、详情聚合 pendingItems/allItems 口径。
- ReportService 审查结论：纯委托层（逐行透传 Registration/Clinic/Billing/Inventory
  AppService），分支逻辑为零，由 e2e 覆盖——不写单测（写即是测试 mock 自己）。
- 全量回归 272/272 全绿。零单测服务余：emc/hr/basedata（均为薄查询层）。

## 2026-10-24 ｜ 138 轮（a091ee4）：单测第八弹——仓库/护理状态机（229→256）

- **WhseServiceTest（17 用例）**：供应商编码重复 B5001、创建采购单幽灵供应商/停用
  供应商/幽灵与停用药品四向拦截（126 轮 R-1 P2 固化）、审批条件更新 0 行 A0008、
  到货入库批号=采购单号+供应商名回填、**双重入库防护**（并发 receive 条件更新
  0 行抛错）、取消门禁（已入库/已取消拒）与并发取消。
- **NurServiceTest（10 用例）**：排班对象必须是启用护士账号（医生/管理员账号不可排班）、
  同日重复排班+唯一索引并发竞态、体征范围校验（体温/血氧/疼痛评分体温单口径、
  收缩压>舒张压、未来时间拒录）、**事务尾在院复核**（落库前并发出院 → B6003 回滚）。
- 全量回归 256/256 全绿。零单测服务余：pe/report/emc/hr/basedata/plt 部分方法。

## 2026-10-24 ｜ 137 轮（b1b2a29）：单测第七弹——药房/检验状态机（201→229）

- **PharmacyServiceTest（14 用例）**：发药三重前置（未审核 B4001/未收费 B4002/已发
  B4003 + 重复发药单 B4003）、负数量明细整体拒绝、FEFO 拆批扣减（早效期耗尽置 4、
  行锁后复检跳过停用批）、库存缺口 B4004 且发药单不落库、整方退药回补效期判定
  （过期批回补为停用过期(3)，可用批回补(1)——113 轮 A-P2-1 固化）、重复退药 B4006。
- **LisServiceTest（14 用例）**：采集/接收状态机（条件更新 0 行 A0008）、危急判定
  阈值边界（超高判危急、空结果不 NPE、非数值不判——114 轮 #4 修复固化）、重录关断
  旧危急值、录入与发布并发锁读守卫（114 轮 #5）、发布无结果/重复发布阻断、
  报告汇总危急项拼接。
- 全量回归 229/229 全绿。零单测核心服务余：whse/nur/pe/report/emc/hr/basedata。

## 2026-10-24 ｜ 136 轮（96982c7）：单测第六弹——医保/病案状态机（179→201）

- **MedinsServiceTest（14 用例）**：申报净额门禁（部分退费按净额、退满拒报 B6402）、
  全额退费(30)/重复申报(B6401)阻断、差异重报复用原单并清空对账痕迹、
  重报/对账并发 0 行 A0008、对账通过备注直存 diffReason、失败默认差异原因。
- **MrcServiceTest（8 用例）**：未归档拒借(B6302)/已借出拒借(B6303)、借阅人缺省取
  当前登录人、归还双条件更新、借阅并发 0 行不悬挂借阅单。
- 全量回归 201/201 全绿。零单测核心服务清单余：lis/pharmacy/whse/mat（下批）。

## 2026-10-24 ｜ 今夜总览（128~135 轮，收敛后收尾）

- 128：一致性巡检第 24 段 5 不变量 + API 契约幽灵字段 + 历史数据治理
- 129：权限矩阵四向对账零缺口 + 教学材料零偏差（**收敛信号轮**）
- 130~131：OpenAPI 重生成 229 路径 + Redis 全景健康 + 单测第一弹 19 用例
- 132：单测第二弹 18 用例（Alert/Ris）
- 133：单测第三弹 9 用例（Registration）+ **排台 teardown 根因修复**（slot_active=0
  撞唯一索引被 subprocess 假成功掩盖——E7.3 二次复发的真根因）
- 134：单测第四弹 12 用例（横切工具类，含 JSON 转义往返解析端到端断言）
- 135：单测第五弹 5 用例（Clinic 处方作废/完成断言）
- **今夜成果**：单测 116→179（+63，8 个此前零单测核心服务全部补齐首层守卫）、
  1 个 P1 级测试基建根因修复、2 项系统级审计零缺陷结论、OpenAPI 文档回到真实

## 2026-10-24 ｜ 135 轮（单测第五弹：ClinicService 处方作废与完成断言）

- ClinicServiceTest ×5：处方作废状态机（已收费拒 B2006/已发药拒 B2006/完成就诊拦
  B2001 优先于可作废判定——126 轮 R-6 旁路封堵首次固化/待审可作废成功走条件更新）+
  complete 乐观锁断言 A0008（126 轮 R-2 双开页面静默丢更新首次固化）
- 单测总数 174→179 全绿；十一套回归全绿

## 2026-10-24 ｜ 134 轮（单测第四弹：横切工具类守卫）

- UtilGuardsTest ×12：LikeEscapeUtil（%/_/反斜杠三转义 + 顺序正确性 + null/空透传 +
  中文原样）、JsonEscapeUtil（引号/反斜杠/换行/制表 + 0x01 控制字符  + 端到端
  往返解析断言——转义产物喂 Jackson readTree 还原原文）、PageQuery（pageSize 双向钳制 +
  pageNum 负数与 99999999 上限 10000）——横切注入面与分页边界首次固化
- 单测总数 162→174 全绿
- Playwright 深走查：已排台行核查弹窗正常打开（手术安全核查双人签名）
- 十一套回归全绿

## 2026-10-24 ｜ 133 轮（单测第三弹 RegistrationService + 排台 teardown 根因修复）

- RegistrationServiceTest ×9：挂号 R1 重复拒/B2 号源满拒 B3/过去日期拒/停诊医生拒/
  专家号资质拒/成功（号费快照+诊查费+排队号 max+1+合计）、退号错态拒/乐观锁冲突拒/
  成功置 20——R1~R4 规则首次单测固化
- **排台 teardown 根因修复（E7.3 复发实锤）**：teardown SQL 把 slot_active 置 0 而
  释放语义是 NULL——0 撞 uk_schedule_slot2 唯一索引报 Duplicate entry，且
  subprocess.run 不抛非零退出码，假成功消息掩盖了失败。两处 teardown（verify_course/
  ui_chain）改 NULL + returncode 检查；存量 10 槽位已用正确 SQL 清理，E7.3 恢复 41/41
- 单测总数 153→162，全部全绿

## 2026-10-24 ｜ 132 轮（单测补强第二弹：AlertService/RisService）

- AlertServiceTest ×11：危急值三段状态机（notify 前态/条件断言/成功、confirm 未通知拒/
  非医生拒/管理员豁免、close 空意见拒/null 拒/超长拒/未确认拒/成功留痕）——
  116 轮 #11 闭环必填与管床校验首次单测固化
- RisServiceTest ×7：驳回四连（空理由拒/null 拒/行数断言/成功留痕）+ 通过三连
  （自审拦截/错态拒/第二签名人成功并推进申请 30→40）——112 轮 B09 与双签校验首次固化
- 单测总数 116→153（+37），全部全绿；过程中修复 AlertService 构造器 8 参对齐与
  实体字段名（requestId 非 visitId）
- 十一套回归全绿

## 2026-10-24 ｜ 130~131 轮（OpenAPI 重生成 + Redis 全景 + 核心服务单测补强）

- OpenAPI 文档重生成（218→229 路径）：原 docs/openapi.json 停在 10-04（105 轮前），
  缺 110~128 轮新增的 11 个端点（alerts/stats、cdss acknowledge、daily-fees void、
  mat/suppliers、mrc/borrows、nur/nurses、rpt uploads 族），零僵尸路径。
  侧端口 8081 临时启 swagger 抓取 /v3/api-docs 全量重写，主服务随即恢复
- Redis 全景审计：键空间 4 类（sess/idem/login:fail/login:ipfail）+ seq 计数器，
  idem TTL 24h 自然过期实测健康、seq 带滚动 TTL，无泄漏键；与 runbook §3b 七探针
  结论对照无新增未演练键空间（ipfail 同属登录链 fail-open 同类）
- 核心服务单测补强（119 轮盲区清单落地第一弹）：
  - UserServiceTest 10 用例：内置超管三防线（停用/重置/修改全拒）、非管理员禁授
    ADMIN 角色、updateStatus 白名单、禁用即踢会话 removeStrict、密码强度三态、
    建档用户名唯一——112 轮 B01 P0 首次单测固化
  - CdssServiceTest 9 用例：updateRule null 跳过语义（局部修改不重置停用态——
    116 轮 P1 回归防护）、显式传值生效、非法 status/乐观锁断言、配伍规则
    refB 必填且≠refA、过敏关键词必填、类型白名单、建码唯一——120 轮校验首次固化
- 全路由 UI 冒烟 45/45 复扫通过
- 子代理连续 5 轮模型并发限额，本轮全部主代理直执行

## 2026-10-24 ｜ 129 轮（权限矩阵四向对账 + 教学材料复查——零缺陷收敛轮）

- **权限矩阵四向对账（157 码全量）：零缺口**——①代码→DB：无死权限码（唯一差集
  'xxx' 为 PermissionService javadoc 示例伪影）；②DB→代码：无僵尸权限码；
  ③角色→端点：V54~V58 历史缺口角色全数复查（对账员/护士/急诊/病案/公卫/手术/
  体检/医保），唯一疑点（护理排班缺 basedata:doctor:query）经查走自有 /nur/nurses
  端点（nur:schedule:manage 已绑）；④菜单 component→前端文件：45 路由全部解析成功。
  历史最高发缺陷类（五连修 V54~V58）正式宣告收口
- **教学材料复查：零偏差**——E9 危急值闭环 handleNote 口径（116 轮 #11 后）、
  教案-02 处方状态机（10→20→30→50）、教案-04 危急值四步+超时升级（116 轮任务）
  全部与当前实现一致
- Playwright 走查：EMPI 合并弹窗（合并按钮/不可逆警示/48 目标候选）、
  ORS 排台日期禁过去（27 格禁用）实测通过
- 本轮双代理三次触发模型并发限额，全部审计由主代理直接执行；
  零代码变更——收敛信号：两个系统级横切面首次审计零缺陷

## 2026-10-24 ｜ 128 轮（一致性巡检覆盖度 + API 契约自审 + Playwright 走查）

- 一致性巡检新增第 24 段（5 条状态机门禁落库不变量，全部实测 0 行）：
  作废医嘱无待执行单（含 23 条历史存量清理）、术中/完成手术核查必齐（口径收窄 40~60，
  探测时误含 70 取消 61 条为口径错误）、闭环危急值必有处置意见、有批次物资库存=活跃批次和
  （15 处偏差全为种子无批次物资——运行时批次+库存同事务自洽，口径限定有批次物资）、
  已发药处方必已收费
- API 契约自动扫描（33 文件 × 逐 interface 对照后端类）：绝大多数"幽灵字段"为解析器
  误报（BaseEntity/PageQuery 继承），实锤 1 处——VisitBrief 声明 4 个后端从不装配的
  字段（patientNo/chiefComplaint/diagnosisNames/createdAt，102 轮幽灵列同款），删除
  并补 startTime/endTime 真实字段
- Playwright 走查：排台弹窗日期禁过去（27 格禁用=全部过去日）实测通过
- 双代理因模型并发限额失败，本轮由主代理直接执行两项审计

## 2026-10-24 ｜ 126~127 轮（二并发 ×2 + Playwright 走查）

- 定时任务与工具类专项 12 条：PatientHashRehashTask 双 P1（脏密文中断整批+LIMIT 2000 无游标
  尾部永不可达→id 游标全量+逐行隔离）；AlertOverdueTask 去重（按 plt 事件查重+ORDER BY）；
  JsonEscapeUtil 控制字符全覆盖；PageQuery pageNum 上限 10000；调度池 4→6+注释勘误
- registration/clinic/whse 深水区 9 条：whse 幽灵药品校验（113 轮只修了供应商半边）、
  clinic complete/saveRecord @Version 断言（双开页面静默丢更新）、过号任务 wrapper 补
  version bump（123 轮 doc.voidOrder 同源）、voidPrescription 补就诊中校验（R15 旁路）、
  markCharged 断言对称；退费死链路口径备案（保守口径+B3005 已拦，代码保留为预收费解禁备用）
- 记录不修：IdGenerator 多实例降级撞号（单机部署）、JDBC socketTimeout（网络分区场景）、
  效期 30 天预警扫描（《05》R14 待产品确认）、FREQUENCY_SLOTS 双份真相（当前一致）、
  夜诊跨零点（无此业务）
- Playwright 走查：核查弹窗禁用联动（拨不通过→禁用/恢复→解禁）实测通过

## 2026-10-24 ｜ 124~125 轮（二并发 ×2 + 浏览器/API 走查）

- auth/system/basedata 四类复查 6 条：LoginRequest @Size 收口（超长 username 曾使登录审计
  全静默丢失）+ saveLoginLog 全字段截断 + failKey TTL 无条件补设（116 轮 ipKey 同款漏修）；
  DTO @Size 批量补齐 16 处（system 6 + basedata 10，含 doctorName 32/location 64 对齐 DB 列宽）
- 九页面弹窗交互 12 条：BB 交叉配血表单全字段重置（不相容残留误禁发血 P1）、手工记账 0 元
  单价校验器（P1）、退押金预览净额口径对齐 D3、出院弹窗费用合计乱序守卫、输血开始预填已发
  血袋、BB 驳回理由 prompt、手术日期禁过去、AE 发生时间禁未来、核查项未通过禁用登记、
  转科目标床位 seq 守卫、差异单重报改按 billId 直查（不依赖 50 笔缓存）
- UI 链适配：ors 填充方式回归文本匹配（el-input-number setter 不更新 v-model，116 轮同坑）
- 修复链 bug：OrsWorkbench 漏 import computed（checkAllPassed 改动引入，Playwright console
  抓出 ReferenceError 定位，构建产物引用链核验后修复）

## 2026-10-24 ｜ 122~123 轮（三并发 ×2 + 浏览器/API 走查）

- e2e 质量专项 12 条：phase2 check39 死断言修复（events 分页第三处适配）、phase3 供应商精确匹配、
  teardown mysql 双降级对齐、phase4b 前置 assert、check37 恒真修复（userId 动态解析）、
  死代码删除、E2.4d 占位改真断言、双 browser.close、phase5 payload 精确匹配（连修两处：data 嵌套 + 字符串类型）
- cnt/doc/rpt 9 条：KPI 跨度钳制 366 天、cnt complete 改 @RequestBody DTO（+前端适配）、
  cnt create 科室校验+住院门诊互斥、doc items @Size(50)、skinTest note 拼入 result、KPI 导出留痕
- 修复复核 9 条（117~119 轮回归）：床位启停 expect 分派补 3 态备案、HMAC 预检查双候选（同证双档 P1）、
  日结复查 READ_COMMITTED、entry 守卫 FOR UPDATE、per-IP expire 无条件补设、幂等锁 CAS、
  闭环率排除系统关闭行、resetAuthState 注释对齐
- 组件级 6 条：VisitWorkbench loadDetail 竞态守卫（跨患者病历错写 P1）、InpOrders/Emr/DailyFee/Nursing
  患者维度加载守卫、doc.voidOrder wrapper 补 version bump（医嘱复活 P1）、emr submit 自动打标合并、
  exec 回写断言、CDSS 处置按钮权限码勘误（cdss:query→cdss:hit:query，原 v-perm 移除按钮）
- 跨模块并发链 5 条：ors/bb 开单事务尾断言、cdss.check 移位、报卡失败留痕、
  addDiagnosis visit 断言（待验证记录）、Hikari 池耗尽（待验证记录）
- 单测：CryptoUtil +3 HMAC 用例、DocOrderServiceTest updateById 打桩配套；**116 项单测全绿**

## 2026-10-24 ｜ 120~121 轮（三并发/二并发 + 浏览器）

- 剩余业务页面 12 条：plt 事件分页补全、MPI 合并入口（不可逆警示 confirm）、CDSS 规则表单类型依赖字段、
  命中处置回写按钮（acknowledge 闭环）、护理/急诊体征范围对齐后端、绿通必须选预判中心、
  /emc/nodes 权限放宽、危急值闭环意见必填前端同步、对账备注 200 校验、EMR 质控问题 JSON 解析、
  阈值 low<high 预校验、BB 取消申请入口
- 跨模块并发链 5 条：ors/bb 开单事务尾在院断言（touchInHospital 范式收口最后一块拼图）、
  cdss.check 移至在院断言之后（防幽灵命中）、报卡自动生成失败事件留痕（法定传染病漏报对账锚点）
- 测试基建：concurrency/ui_chain 前置断言（建档偶发失败静默 IndexError → 显式报错）

## 2026-10-24 ｜ 117~119 轮（双代理 + 浏览器 ×3）

- 117：业务页面 10 条（开医嘱 orderClass 复位 P1、处方作废门禁补 chargeStatus、接诊后队列失联、
  日结日期限当日、挂号费预览口径、日结锁定感知、执行记录项目列）；迁移漂移 8 条（V58 权限成对补齐、
  失败迁移恢复三步入 runbook）
- 118：性能 6 条 N+1 收敛（药房队列 700→5 查、发药单/退药单批查、doctorMap 科室预取、
  长嘱任务去冗余预查）；实测可发药队列 40 行 253ms
- 119：覆盖盲区 G 组 11 断言固化（内置超管保护三连/权限矩阵/手工冲正三连/RIS 驳回缺理由）；
  fix_regression 31→34；三处 events 分页消费方适配

## 2026-10-07 ｜ 110~111 轮

- 110（c310e3b）：院感确认闭环全链路（列表+确认/整改+备注留痕）、AE 科室下拉、退费上限改剩余可退
- 111（cb72314+784c649）：109/108 轮遗留 14 项全清（借阅台账、对账备注落库、差异重报、
  已归档费用合计回补、押金净额口径、联动竞态守卫等）

## 已知接受项（判定不修，持续记录）

- XSS 转义防御纵深缺口（Jackson 2.13 API 限制）
- 首屏 entry CSS ~378kB（Element Plus 全量样式）
- cdss_hit rule_id=0 聚合（UI 无规则列，与现状不符）
- EMC/PE 患者手输 ID（e2e 程序化操作不兼容，V55 权限已铺路）
- whse 调拨/盘点功能未实现（规划范围）
- 开方端不校验库存（发药时 B4004 拦截，口径一致）
- 号源余量/开方库存前端展示未做（需 DTO 透出，记录待办）
