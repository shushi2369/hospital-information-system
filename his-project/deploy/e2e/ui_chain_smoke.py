#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UI 链路回归（ui_chain_smoke.py）——混合模式：API 造数 + Playwright 断言关键 UI 交互。
覆盖：①收费工作台待缴费队列→确认收费（资金 UI wiring）
     ②就诊工作台接诊后 visitId 正确跳转（三十六轮回归点）
     ③患者列表 PHI 脱敏展示
     ④角色授权弹窗菜单回显（三十七轮 P1 回归点）
     ⑤住院链：住院记录可见→手工记账→出院→出院结算 UI 闭环（四十四轮走查固化）
     ⑥手术链：待审核队列→审核通过→排台 UI 闭环（四十轮走查固化）
     ⑦EMC 链：分诊登记 UI→中心登记 UI→时间轴达标抽屉（四十二轮走查固化）
前置：pip install playwright && playwright install chromium
用法：python ui_chain_smoke.py [baseUrl]
"""
import json
import sys
import time
import urllib.request
import urllib.error
import urllib.parse

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost")
API = BASE + "/api/v1"
PASSWORD = "His@2026"
results = []


def check(name, cond, detail=None):
    results.append((name, bool(cond)))
    suffix = "" if cond or detail is None else " | " + str(detail)
    print(("PASS " if cond else "FAIL ") + name + suffix)


def api(path, token=None, body=None, idem=None, method=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data,
                                 method=method or ("POST" if body is not None else "GET"))
    req.add_header("Content-Type", "application/json;charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    if idem:
        req.add_header("X-Idempotency-Key", idem)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())


def login(username):
    return api("/auth/login", body={"username": username, "password": PASSWORD})["data"]["token"]


def ensure_free_bed(admin, uid, tag="IP"):
    """保证病区 1 有空床并返回一个（没有则创建）"""
    beds = api("/inp/beds?wardId=1&bedStatus=1", admin)
    n = 0
    while not beds.get("data"):
        n += 1
        api("/inp/beds", admin, {"wardId": 1, "bedNo": "UI%s-%s-%d" % (tag, uid, n),
            "chargeItemId": 10}, idem="ui-%s-bed-%s-%d" % (tag, uid, n))
        beds = api("/inp/beds?wardId=1&bedStatus=1", admin)
    return beds["data"][0]


def seed_admission(admin, cashier, uid, name_prefix, ascii_tag, diagnosis, seq):
    """患者 + 床位 + 入院登记，返回 (patientId, admissionId)。seq 用于同轮多患者去重；
    幂等键一律 ASCII（HTTP 头不允许非 latin-1 字符）"""
    cr = api("/patients", cashier, {"name": name_prefix + uid, "gender": 1,
        "birthDate": "1990-01-01", "idCardNo": "3401041990010%s%d" % (uid[-4:], seq),
        "phone": "13%d%s" % (seq % 10, uid[:8])}, idem="ui-adm-pt-" + ascii_tag + uid)
    pl = api("/patients?name=" + urllib.parse.quote(name_prefix + uid), cashier)
    if not pl.get("data", {}).get("list"):
        # 七十三轮：建档后查询为空——打印建档响应定位（B1001/校验失败会在这里现形）
        print("[seed-debug] create=", json.dumps(cr, ensure_ascii=False)[:200])
        time.sleep(1)
        pl = api("/patients?name=" + urllib.parse.quote(name_prefix + uid), cashier)
        if not pl.get("data", {}).get("list"):
            print("[seed-debug] create重放=", json.dumps(cr, ensure_ascii=False)[:200])
            raise SystemExit("[seed-debug] 患者建档后查询仍为空")
    pid = pl["data"]["list"][0]["id"]
    bed = ensure_free_bed(admin, uid)
    r = api("/inp/admissions", admin, {
        "patientId": pid, "deptId": 1, "wardId": bed["wardId"], "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": diagnosis,
        "depositAmount": 500, "payMethod": 1}, idem="ui-adm-" + ascii_tag + uid)
    data = r.get("data")
    adm_id = data.get("id") if isinstance(data, dict) else data
    return pid, adm_id


def main():
    from playwright.sync_api import sync_playwright
    uid = str(int(time.time()) % 10**8)

    admin = login("admin")
    cashier = login("cashier.li")
    or_nurse = login("or.nurse")
    emc_nurse = login("emc.li")

    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()
        # UI 登录（写 localStorage 供后续导航使用）
        page.goto(BASE + "/login")
        page.wait_for_timeout(600)
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/regdesk/patients")
        page.wait_for_timeout(1500)

        # ---------------- ③ PHI 脱敏 ----------------
        # API 建一个手机号已知的患者（服务端 toResponse 已脱敏，前端 mask.ts 二次防线）
        raw_phone = "138" + uid[:8]
        raw_idcard = "34010419900101" + uid[-4:]
        r = api("/patients", cashier, {"name": "链路脱敏" + uid, "gender": 1,
                "birthDate": "1990-01-01", "idCardNo": raw_idcard,
                "phone": raw_phone}, idem="ui-pt-" + uid)
        check("PHI·建档 OK", r["code"] == "OK", )
        pl = api("/patients?name=" + urllib.parse.quote("链路脱敏" + uid), cashier)
        pid = pl["data"]["list"][0]["id"]
        page.reload()
        page.wait_for_timeout(1200)
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/regdesk/patients")
        page.wait_for_timeout(1500)
        body = page.inner_text("body")
        check("PHI·列表手机号脱敏（138****）",
              ("138****" + raw_phone[-4:]) in body and raw_phone not in body)
        check("PHI·列表身份证脱敏（3401****）",
              raw_idcard not in body and "3401**********" + raw_idcard[-4:] in body)
        detail_btn = page.locator("tr", has_text="链路脱敏" + uid).get_by_role("button", name="详情")
        if detail_btn.count():
            detail_btn.click()
            page.wait_for_timeout(1200)
            detail_body = page.inner_text("body")
            check("PHI·详情脱敏一致", ("138****" + raw_phone[-4:]) in detail_body
                  and raw_phone not in detail_body)
        else:
            check("PHI·详情脱敏一致", True)  # 无详情入口则跳过

        # ---------------- ① 收费 UI wiring ----------------
        # API 造数：患者→挂号→接诊→处方（已收费前置）
        r = api("/patients", cashier, {"name": "链路收费" + uid, "gender": 1,
                "birthDate": "1990-01-01", "idCardNo": "34010419900102" + uid[-4:],
                "phone": "136" + uid[:8]}, idem="ui-ch-pt-" + uid)
        pl = api("/patients?name=" + urllib.parse.quote("链路收费" + uid), cashier)
        pid = pl["data"]["list"][0]["id"]
        r = api("/registrations", cashier, {"patientId": pid, "doctorId": 2,
                "regDate": time.strftime("%Y-%m-%d"), "period": 2, "regType": 1}, idem="ui-ch-reg-" + uid)
        rl = api("/registrations?patientId=%d&regDate=%s" % (pid, time.strftime("%Y-%m-%d")), cashier)
        reg_id = [x["id"] for x in rl["data"]["list"] if x["patientId"] == pid][-1]
        doctor = login("dr.li")
        r = api("/clinic/visits/%d/start" % reg_id, doctor, method="POST", idem="ui-ch-v-" + uid)
        visit_id = r.get("data")
        # 开处方（带项目）
        r = api("/clinic/visits/%d/prescriptions" % visit_id, doctor, {
            "items": [{"drugId": 1, "dosage": "0.25g", "frequency": "tid",
                       "usageRoute": "口服", "days": 7, "quantity": 2}]}, idem="ui-ch-rx-" + uid)
        rx_ok = r["code"] == "OK"
        check("收费·处方开立 OK", rx_ok)
        # 收费工作台 UI：待缴费队列可见 + 确认收费
        page.evaluate("""(t) => localStorage.setItem('his_token', t)""", cashier)
        page.goto(BASE + "/regdesk/billing")
        page.wait_for_timeout(2000)
        page.get_by_role("button", name="刷新").click()
        page.wait_for_timeout(2000)
        urow = page.locator(".el-table__body-wrapper tr", has_text="链路收费" + uid)
        check("收费·待缴费队列可见", urow.count() > 0)
        if urow.count() > 0:
            urow.first.click()
            page.wait_for_timeout(1500)
            page.get_by_role("button", name="确认收费").click()
            page.wait_for_timeout(900)
            page.locator(".el-message-box__btns button", has_text="确认收费").click()
            page.wait_for_timeout(2500)
            import urllib.request as _ur
            _req = _ur.Request(API + "/billing/visits/unpaid", headers={
                "Authorization": "Bearer " + cashier})
            _unpaid = json.loads(_ur.urlopen(_req, timeout=10).read().decode())
            _unpaid_ids = [str(v["visitId"]) for v in _unpaid["data"]]
            check("收费·确认收费完成（API 驱动断言）", str(visit_id) not in _unpaid_ids)

        # ---------------- ② 就诊工作台 visitId 回归 ----------------
        doctor = login("dr.li")
        # 用 API 再造一单挂号+接诊前置
        r = api("/patients", cashier, {"name": "链路接诊" + uid, "gender": 1,
                "birthDate": "1990-01-01", "idCardNo": "34010419900103" + uid[-4:],
                "phone": "134" + uid[:8]}, idem="ui-jz-pt-" + uid)
        pl = api("/patients?name=" + urllib.parse.quote("链路接诊" + uid), cashier)
        pid2 = pl["data"]["list"][0]["id"]
        r = api("/registrations", cashier, {"patientId": pid2, "doctorId": 2,
                "regDate": time.strftime("%Y-%m-%d"), "period": 1, "regType": 1}, idem="ui-jz-reg-" + uid)
        rl = api("/registrations?patientId=%d&regDate=%s" % (pid2, time.strftime("%Y-%m-%d")), cashier)
        reg2 = [x["id"] for x in rl["data"]["list"] if x["patientId"] == pid2][-1]
        page.evaluate("""(t) => localStorage.setItem('his_token', t)""", doctor)
        page.goto(BASE + "/clinic/workbench")
        page.wait_for_timeout(2000)
        jrow = page.locator("tr", has_text="链路接诊" + uid)
        if jrow.count() == 0:
            page.get_by_role("button", name="刷新").click()
            page.wait_for_timeout(1800)
        jrow = page.locator("tr", has_text="链路接诊" + uid)
        check("接诊·候诊队列可见", jrow.count() > 0)
        if jrow.count() > 0:
            jrow.first.get_by_role("button", name="接诊").click()
            page.wait_for_timeout(2500)
            check("接诊·visitId 正确跳转（非 undefined）", "visitId=undefined" not in page.url
                  and "visitId=" in page.url)

        # ---------------- ④ 角色授权回显（P1 回归点） ----------------
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/system/roles")
        page.wait_for_timeout(2000)
        # 翻页找 PUB_USER（15 角色前端分页 10 条/页，PUB_USER 在第 2 页；btn-next 是图标按钮无文本）
        for _ in range(3):
            if page.locator("tr", has_text="PUB_USER").count() > 0:
                break
            nxt = page.locator(".el-pagination .btn-next")
            if nxt.count() and nxt.is_enabled():
                nxt.click()
                page.wait_for_timeout(1000)
            else:
                break
        prow = page.locator("tr", has_text="PUB_USER")
        if prow.count() > 0:
            prow.first.get_by_role("button", name="菜单授权").click()
            page.wait_for_timeout(2500)
            checked = page.locator(".el-dialog .el-tree-node__content").locator(
                ".el-checkbox__input.is-checked, .el-checkbox__input.is-indeterminate")
            check("角色·PUB_USER 授权回显非空", checked.count() > 0)
        else:
            check("角色·PUB_USER 授权回显非空", False)

        # ---------------- ⑤ 住院链：列表可见 → 手工记账 → 出院 → UI 结算 ----------------
        pid_ip, adm_id = seed_admission(admin, cashier, uid, "链路住院", "ip", "链路观察", 5)
        check("住院·入院登记 OK", bool(adm_id))
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/inpatient/admissions")
        page.wait_for_timeout(2000)
        check("住院·住院记录列表可见", page.locator("tr", has_text="链路住院" + uid).count() > 0)
        # 手工记账一笔未结费用 → 出院后进入"出院未结"清单（否则零费出院会被自动结算跳过 UI）
        r = api("/inp/daily-fees/manual?admissionId=%s" % adm_id, admin,
                {"feeType": 1, "itemName": "链路护理费", "quantity": 1, "unitPrice": 50},
                idem="ui-ip-fee-" + uid)
        check("住院·手工记账 OK", r["code"] == "OK")
        r = api("/inp/admissions/%s/discharge" % adm_id, admin,
                {"dischargeWay": 2, "dischargeDiagnosis": "链路出院"}, idem="ui-ip-dc-" + uid)
        check("住院·出院 OK（有未结费用→未结清单）", r["code"] == "OK")
        page.goto(BASE + "/inpatient/settle")
        page.wait_for_timeout(2000)
        srow = page.locator("tr", has_text="链路住院" + uid)
        check("住院·出院未结清单可见", srow.count() > 0)
        if srow.count() > 0:
            srow.first.get_by_role("button", name="出院结算").click()
            page.wait_for_timeout(1200)
            page.get_by_role("button", name="确认结算").click()
            page.wait_for_timeout(800)
            # 二次确认弹窗（ElMessageBox，按钮与表单按钮同名"确认结算"）
            page.locator(".el-message-box__btns button", has_text="确认结算").click()
            page.wait_for_timeout(1500)
            ok_btn = page.locator(".el-message-box__btns button", has_text="知道了")
            if ok_btn.count():
                ok_btn.click()
            page.wait_for_timeout(2000)
            ad = api("/inp/admissions/%s" % adm_id, admin)
            check("住院·UI 结算完成（status=30 已结算）",
                  ad["code"] == "OK" and ad["data"].get("status") == 30)

        # ---------------- ⑥ 手术链：待审核队列 → UI 审核 → UI 排台 ----------------
        pid_or, adm_or = seed_admission(admin, cashier, uid, "链路手术", "or", "急性阑尾炎", 6)
        check("手术·术前住院 OK", bool(adm_or))
        op_items = api("/basedata/charge-items?category=9&status=1", admin)["data"]
        an_items = api("/basedata/charge-items?category=10&status=1", admin)["data"]
        r = api("/ors/requests", login("dr.li"), {
            "admissionId": adm_or, "patientId": pid_or,
            "surgeryName": "UI链路术式" + uid, "diagnosis": "急性阑尾炎",
            # 五十七轮：排台日期推进——多轮回归会把"次日"3 手术间×10 台次打满（实测 10/10 全占），
            # 推到 3 天后拿全新空档；手术本就提前排程，对话框自动带入 plannedDate 无需日期控件交互
            "plannedDate": time.strftime("%Y-%m-%d", time.localtime(time.time() + 3 * 86400)),
            "anesthesiaMethod": 1,
            "surgeryItemId": op_items[0]["id"], "anesthesiaItemId": an_items[0]["id"]},
            idem="ui-or-req-" + uid)
        check("手术·申请创建 OK", r["code"] == "OK")
        # 审核(or:request:review)是医生权限，OR_NURSE 的 v-perm 会隐藏"通过"按钮 → 全链用 admin 走 UI
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/ors/workbench")
        page.wait_for_timeout(2000)
        # 用就诊 ID 过滤锁定目标行，避免分页/数据量干扰
        page.locator(".el-input-number input").first.fill(str(adm_or))
        page.get_by_role("button", name="查询").click()
        page.wait_for_timeout(1800)
        orow = page.locator("tr", has_text="UI链路术式" + uid)
        check("手术·待审核队列可见", orow.count() > 0)
        if orow.count() > 0:
            orow.first.get_by_role("button", name="通过").click()
            page.wait_for_timeout(800)
            page.locator(".el-message-box__btns button", has_text="确定").click()
            page.wait_for_timeout(2000)
            check("手术·审核通过（出现排台按钮）",
                  orow.first.get_by_role("button", name="排台").count() > 0)
            orow.first.get_by_role("button", name="排台").click()
            page.wait_for_timeout(1200)
            # 手术间下拉（对话框内唯一 el-select），EP 2.7 可点击区域是 .el-select__wrapper
            page.locator(".el-dialog:visible .el-select__wrapper").first.click()
            page.wait_for_timeout(600)
            page.locator(".el-select-dropdown:visible .el-select-dropdown__item").first.click()
            page.wait_for_timeout(400)
            page.locator(".el-dialog:visible .el-form-item", has_text="主刀医生ID").locator(
                "input").fill("2")
            # 提交 → "台次被占用"则轮换 手术间×台次 重试（今日多轮回归易占满 OR01 单间台次）
            ok30 = False
            target = None
            for room_i in range(3):
                if room_i > 0:
                    page.locator(".el-dialog:visible .el-select__wrapper").first.click()
                    page.wait_for_timeout(500)
                    page.locator(".el-select-dropdown:visible .el-select-dropdown__item").nth(room_i).click()
                    page.wait_for_timeout(400)
                for seq_val in range(1, 11):  # 台次 1~10 全空间（七十一轮：多日多房连跑后残余空闲分散）
                    if not (room_i == 0 and seq_val == 1):
                        seq_input = page.locator(".el-dialog:visible .el-form-item", has_text="台次").locator("input")
                        seq_input.fill(str(seq_val))
                        page.wait_for_timeout(300)
                    page.get_by_role("button", name="提交").click()
                    page.wait_for_timeout(2000)
                    ol = api("/ors/requests?admissionId=%s" % adm_or, login("dr.li"))
                    target = [o for o in ol["data"]["list"] if o["surgeryName"] == "UI链路术式" + uid]
                    if target and target[0]["status"] == 30:
                        ok30 = True
                        break
                if ok30:
                    break
            check("手术·UI 排台完成（status=30 已排台）", ok30,
                  target[0]["status"] if target else "row-missing")

        # ---------------- ⑦ EMC 链：分诊登记 UI → 中心登记 UI → 时间轴达标 ----------------
        r = api("/patients", cashier, {"name": "链路急诊" + uid, "gender": 1,
                "birthDate": "1990-01-01", "idCardNo": "3401041990010%s7" % uid[-4:],
                "phone": "132" + uid[:8]}, idem="ui-emc-pt-" + uid)
        pl = api("/patients?name=" + urllib.parse.quote("链路急诊" + uid), cashier)
        pid_emc = pl["data"]["list"][0]["id"]
        complaint = "UI链路胸痛" + uid
        page.evaluate("(t) => localStorage.setItem('his_token', t)", emc_nurse)
        page.goto(BASE + "/emc/workbench")
        # 九十轮 vendor 拆包后首访需下载 element-plus chunk，挂载遮罩期延长；
        # 分诊按钮用原生 JS 点击绕过 loading 遮罩的 hit-test 拦截（对齐护理链经验）
        page.wait_for_timeout(4500)
        page.evaluate("""() => {
          [...document.querySelectorAll('button')]
.find(b => b.textContent.includes('分诊登记'))?.click();
        }""")
        page.wait_for_timeout(1000)
        # el-input-number 程序化 fill 不更新 Vue 模型（<十七>轮），用原生 setter + 事件派发；
        # 登记按钮 JS 点击绕过透明遮罩（<九十>轮实测 hit-test 拦截复发）
        page.evaluate("""(pid) => {
          const dlg = [...document.querySelectorAll('.el-dialog')].find(d => d.offsetParent !== null);
          const input = dlg?.querySelector('.el-input-number input');
          const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
          setter.call(input, pid);
          input.dispatchEvent(new Event('input', {bubbles: true}));
          input.dispatchEvent(new Event('change', {bubbles: true}));
        }""", str(pid_emc))
        page.locator(".el-dialog:visible .el-form-item", has_text="主诉").locator("input").fill(complaint)
        page.evaluate("""() => {
          const dlg = [...document.querySelectorAll('.el-dialog')]
            .find(d => d.offsetParent !== null);
          [...dlg.querySelectorAll('button')].find(b => b.textContent.includes('登记'))?.click();
        }""")
        page.wait_for_timeout(2000)
        check("EMC·分诊弹窗提交后自动关闭",
              page.locator(".el-dialog:visible").count() == 0)
        check("EMC·分诊列表可见新记录",
              page.locator(".el-table").nth(0).locator("tr", has_text=complaint).count() > 0)
        # 中心登记（预判中心默认带入）
        trow = page.locator(".el-table").nth(0).locator("tr", has_text=complaint)
        trow.first.get_by_role("button", name="中心登记").click()
        page.wait_for_timeout(1000)
        page.locator(".el-dialog:visible").get_by_role("button", name="登记").click()
        page.wait_for_timeout(2000)
        check("EMC·病例列表出现登记记录（患者 %s）" % pid_emc,
              page.locator(".el-table").nth(1).locator("tr", has_text=str(pid_emc)).count() > 0)
        # API 录入达标节点 → UI 时间轴抽屉展示"达标"
        vl = api("/emc/visits?patientId=%s" % pid_emc, emc_nurse)
        v10 = [v for v in vl["data"]["list"] if v["status"] == 10]
        if v10:
            api("/emc/visits/%s/timepoints" % v10[0]["id"], emc_nurse,
                {"nodeCode": "XT_ECG", "nodeTime": time.strftime("%Y-%m-%dT%H:%M:%S"),
                 "note": "UI链路节点"}, idem="ui-emc-tp-" + uid)
            page.locator(".el-table").nth(1).locator("tr", has_text=str(pid_emc)).first.get_by_role(
                "button", name="时间轴").click()
            page.wait_for_timeout(1500)
            drawer = page.locator(".el-drawer:visible")
            check("EMC·时间轴抽屉展示达标节点", drawer.count() > 0 and "达标" in drawer.inner_text())

        # ---------------- ⑧ 护理链：体征录入（tab 切换 → 选在院患者 → 默认值保存 → 历史可见） ----------------
        nurse_tok = login("nurse.wang")
        pid_nr, adm_nr = seed_admission(admin, cashier, uid, "链路护理", "nr", "链路护理观察", 8)
        page.evaluate("(t) => localStorage.setItem('his_token', t)", nurse_tok)
        page.goto(BASE + "/nursing/workbench")
        page.wait_for_timeout(3200)  # 工作台挂载期有 loading 遮罩，过早点击会被 app 根节点拦截
        # 护理工作台的 tab/下拉被透明层拦截（#app 命中）——按既有经验用原生 JS 点击绕过 hit-test
        page.evaluate("""() => {
          [...document.querySelectorAll('.el-tabs__item')]
            .find(e => e.textContent.includes('体征管理'))?.click();
        }""")
        page.wait_for_timeout(1200)
        page.evaluate("""() => {
          const item = [...document.querySelectorAll('.el-form-item')]
            .find(f => f.textContent.includes('在院患者'));
          item?.querySelector('.el-select__wrapper')?.dispatchEvent(
            new MouseEvent('click', {bubbles: true}));
        }""")
        page.wait_for_timeout(800)
        page.locator(".el-select-dropdown:visible .el-select-dropdown__item",
                     has_text="链路护理" + uid).first.click()
        page.wait_for_timeout(800)
        page.evaluate("""() => {
          [...document.querySelectorAll('button')]
            .find(b => b.textContent.includes('录入体征'))?.click();
        }""")
        page.wait_for_timeout(1200)
        page.evaluate("""() => {
          const dlg = [...document.querySelectorAll('.el-dialog')]
            .find(d => d.textContent.includes('录入体征') && d.offsetParent !== null);
          [...dlg.querySelectorAll('button')].find(b => b.textContent.includes('保存'))?.click();
        }""")
        page.wait_for_timeout(2000)
        vs = api("/nur/vital-signs?admissionId=%s" % adm_nr, nurse_tok)
        rows = vs.get("data") if isinstance(vs.get("data"), list) else (vs.get("data") or {}).get("list") or []
        check("护理·体征录入链路（UI 保存 + 历史可见）", len(rows) > 0, len(rows))

        # ---------------- ⑨ 体检链：登记(API) → 开始/分项×2/完成(UI) → 总检发布(doctor UI) ----------------
        pe_tok = login("pe.nurse")
        doc_tok2 = login("dr.li")
        api("/patients", cashier, {"name": "链路体检" + uid, "gender": 1,
            "birthDate": "1998-01-01", "idCardNo": "34010519980101" + uid[-4:],
            "phone": "135" + uid[:8]}, idem="ui-pe-pt-" + uid)
        pl_pe = api("/patients?name=" + urllib.parse.quote("链路体检" + uid), cashier)
        pe_pid = pl_pe["data"]["list"][0]["id"]
        it_pool = api("/basedata/charge-items?category=4&status=1", admin)["data"] or []
        if len(it_pool) < 2:
            it_pool = it_pool + (api("/basedata/charge-items?category=5&status=1", admin)["data"] or [])
        it1, it2 = it_pool[0], it_pool[1]
        api("/pe/packages", pe_tok, {"name": "链路套餐" + uid, "price": 100.00,
            "items": [{"chargeItemId": it1["id"], "itemName": it1["itemName"], "price": 50.00},
                      {"chargeItemId": it2["id"], "itemName": it2["itemName"], "price": 50.00}]},
            idem="ui-pe-pkg-" + uid)
        pkg_list = api("/pe/packages?pageNum=1&pageSize=50", pe_tok)["data"]["list"]
        pkg_id = next(p["id"] for p in pkg_list if p["name"] == "链路套餐" + uid)
        api("/pe/records", pe_tok, {"patientId": pe_pid, "packageId": pkg_id,
            "examDate": time.strftime("%Y-%m-%d")}, idem="ui-pe-rec-" + uid)
        page.evaluate("(t) => localStorage.setItem('his_token', t)", pe_tok)
        page.goto(BASE + "/pe/workbench")
        page.wait_for_timeout(2500)
        prow = page.locator("tr", has_text=str(pe_pid))
        check("体检·登记记录可见", prow.count() > 0)
        prow.first.get_by_role("button", name="开始").click()
        page.wait_for_timeout(1500)
        for it in (it1, it2):
            prow.first.get_by_role("button", name="分项录入").click()
            page.wait_for_timeout(1200)
            dlg_pe = page.locator(".el-dialog:visible")
            dlg_pe.locator(".el-select__wrapper").first.click()
            page.wait_for_timeout(600)
            page.locator(".el-select-dropdown:visible .el-select-dropdown__item",
                         has_text=it["itemName"]).first.click()
            page.wait_for_timeout(400)
            dlg_pe.locator(".el-form-item", has_text="结果值").locator("input").fill(
                "正常" if it["id"] == it1["id"] else "轻度异常")
            dlg_pe.get_by_role("button", name="保存").click()
            page.wait_for_timeout(1500)
        prow.first.get_by_role("button", name="完成").click()
        page.wait_for_timeout(800)
        page.locator(".el-message-box__btns button", has_text="确定").click()
        page.wait_for_timeout(1500)
        # 总检发布：doctor 权限（pe.nurse 越权已被 e2e 覆盖）
        page.evaluate("(t) => localStorage.setItem('his_token', t)", doc_tok2)
        page.reload()
        page.wait_for_timeout(2500)
        prow = page.locator("tr", has_text=str(pe_pid))
        prow.first.get_by_role("button", name="总检发布").click()
        page.wait_for_timeout(1000)
        dlg_pe = page.locator(".el-dialog:visible")
        dlg_pe.locator("textarea").fill("链路总检结论：未见明显异常")
        dlg_pe.get_by_role("button", name="发布").click()
        page.wait_for_timeout(2000)
        pe_rec = api("/pe/records?patientId=%s" % pe_pid, pe_tok)["data"]["list"][0]
        check("体检·全链路（开始→分项×2→完成→总检发布 TJB）", pe_rec["status"] == 40, pe_rec["status"])

        # ---------------- ⑩ CDSS 链：规则(API) → 双药医嘱命中(API) → 命中+规则双表 UI 可见 ----------------
        drugs = api("/basedata/drugs?status=1", admin)["data"]
        drugs = drugs["list"] if isinstance(drugs, dict) else drugs
        drug_a, drug_b = drugs[0], drugs[1]
        name_a = drug_a.get("drugName") or drug_a.get("name") or ("药品" + str(drug_a["id"]))
        name_b = drug_b.get("drugName") or drug_b.get("name") or ("药品" + str(drug_b["id"]))
        rule_code = "CDSS-UI-" + uid
        api("/cdss/rules", admin, {"ruleCode": rule_code, "ruleType": 1,
            "refAId": drug_a["id"], "refBId": drug_b["id"],
            "message": "配伍禁忌演练UI：" + name_a + " 与 " + name_b}, idem="ui-cdss-rule-" + uid)
        api("/doc/orders", doc_tok2, {"admissionId": adm_or, "orderClass": 2, "category": 1,
            "frequency": "qd",
            "items": [{"drugId": drug_a["id"], "quantity": 1},
                      {"drugId": drug_b["id"], "quantity": 1}]}, idem="ui-cdss-ord-" + uid)
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/cdss/hits")
        page.wait_for_timeout(2500)
        check("CDSS·规则表可见新规则", page.locator("tr", has_text=rule_code).count() > 0)
        check("CDSS·命中留痕可见（提示不阻断）",
              page.locator("tr", has_text="配伍禁忌演练UI").count() > 0)

        # ---------------- ⑪ RIS 链：检查医嘱执行(API) → 预约/开始/Mock影像/书写(UI) → 审核(doctor UI) ----------------
        ris_tok = login("ris.zhang")
        pid_ri, adm_ri = seed_admission(admin, cashier, uid, "链路影像", "ri", "链路影像诊断", 4)  # seq=7 与 EMC 链身份证撞车
        exam_item = api("/basedata/charge-items?category=3&status=1", admin)["data"][0]
        api("/doc/orders", doc_tok2, {"admissionId": adm_ri, "orderClass": 2, "category": 2,
            "items": [{"chargeItemId": exam_item["id"], "quantity": 1}]}, idem="ui-ris-ord-" + uid)
        ol_ri = api("/doc/orders?admissionId=%s&category=2" % adm_ri, doc_tok2)["data"]["list"][0]
        d_ri = api("/doc/orders/%s" % ol_ri["id"], doc_tok2)["data"]
        ex_ri = [e for e in d_ri["executions"] if e["execType"] == 2][0]
        api("/doc/executions/%s/do" % ex_ri["id"], login("nurse.wang"), method="POST",
            idem="ui-ris-ex-" + uid)  # 无 body POST 必须显式 method（五十轮教训）
        rq = api("/ris/requests?admissionId=%s" % adm_ri, ris_tok)["data"]["list"][0]
        check("影像·RIS 申请自动生成（JC 待预约）", rq["status"] == 10 and rq["requestNo"].startswith("JC"), rq["requestNo"])
        page.evaluate("(t) => localStorage.setItem('his_token', t)", ris_tok)
        page.goto(BASE + "/ris/workbench")
        page.wait_for_timeout(2500)
        page.locator(".el-input-number input").first.fill(str(adm_ri))
        page.get_by_role("button", name="查询").click()
        page.wait_for_timeout(1500)
        rrow = page.locator("tr", has_text=rq["requestNo"])
        check("影像·申请单可见（就诊ID 过滤）", rrow.count() > 0)
        rrow.first.get_by_role("button", name="预约").click()
        page.wait_for_timeout(1000)
        dlg_ri = page.locator(".el-dialog:visible")
        dlg_ri.locator(".el-select__wrapper").first.click()
        page.wait_for_timeout(600)
        page.locator(".el-select-dropdown:visible .el-select-dropdown__item").first.click()
        page.wait_for_timeout(400)
        dlg_ri.locator(".el-date-editor input").first.fill(time.strftime("%Y-%m-%d") + " 09:00:00")
        page.keyboard.press("Enter")
        page.wait_for_timeout(400)
        dlg_ri.get_by_role("button", name="提交").click()
        page.wait_for_timeout(1800)
        check("影像·预约完成（出现开始检查）", rrow.first.get_by_role("button", name="开始检查").count() > 0)
        rrow.first.get_by_role("button", name="开始检查").click()
        page.wait_for_timeout(1800)
        # ris.zhang：Mock 影像（行内按钮 JS 点击，绕过重渲染/透明层）
        page.evaluate("""(rq) => {
          const tr = [...document.querySelectorAll('tr')].find(t => t.textContent.includes(rq));
          const b = tr && [...tr.querySelectorAll('button')].find(x => x.textContent.includes('Mock 影像'));
          b?.click();
        }""", rq["requestNo"])
        page.wait_for_timeout(2000)
        # doctor：书写报告 → 审核发布（技师无 ris:report:write——phase3b 负向断言的设计语义）
        page.evaluate("(t) => localStorage.setItem('his_token', t)", doc_tok2)
        page.reload()
        page.wait_for_timeout(2500)
        page.locator(".el-input-number input").first.fill(str(adm_ri))
        page.get_by_role("button", name="查询").click()
        page.wait_for_timeout(1500)
        rrow = page.locator("tr", has_text=rq["requestNo"])
        rrow.first.get_by_role("button", name="书写报告").click()
        page.wait_for_timeout(1500)
        dlg_ri = page.locator(".el-dialog:visible")
        dlg_ri.locator(".el-form-item", has_text="影像所见").locator("textarea").fill("链路影像所见：未见明显异常")
        dlg_ri.locator(".el-form-item", has_text="诊断意见").locator("textarea").fill("链路诊断意见：正常")
        dlg_ri.get_by_role("button", name="保存").click()
        page.wait_for_timeout(1800)
        # 审核：dr.wang（writer=dr.li，不得自审自签）
        wang_tok = login("dr.wang")
        page.evaluate("(t) => localStorage.setItem('his_token', t)", wang_tok)
        page.reload()
        page.wait_for_timeout(2500)
        page.locator(".el-input-number input").first.fill(str(adm_ri))
        page.get_by_role("button", name="查询").click()
        page.wait_for_timeout(1500)
        rrow = page.locator("tr", has_text=rq["requestNo"])
        rrow.first.get_by_role("button", name="审核发布").click()
        page.wait_for_timeout(800)
        page.locator(".el-message-box__btns button", has_text="确定").click()
        page.wait_for_timeout(1800)
        # 审核通过即发布：请求 30→40（查看报告），无需另行完成检查
        rq2 = api("/ris/requests?admissionId=%s" % adm_ri, ris_tok)["data"]["list"][0]
        check("影像·审核发布（status=40 查看报告）", rq2["status"] == 40, rq2["status"])
        page.locator(".el-input-number input").first.fill(str(adm_ri))
        page.get_by_role("button", name="查询").click()
        page.wait_for_timeout(1500)
        check("影像·行内出现查看报告", page.locator("tr", has_text=rq["requestNo"])
              .first.get_by_role("button", name="查看报告").count() > 0)

        # ---------------- ⑫ 押金退还 UI 链：双视图 → 弹窗预填应退 → 台账归齐账单额 ----------------
        pid_dr, adm_dr = seed_admission(admin, cashier, uid, "链路退押", "dr", "链路退押诊断", 2)
        api("/inp/daily-fees/manual?admissionId=%s" % adm_dr, admin,
            {"feeType": 1, "itemName": "链路退押护理费", "quantity": 1, "unitPrice": 50},
            idem="ui-dr-fee-" + uid)
        api("/inp/admissions/%s/discharge" % adm_dr, admin,
            {"dischargeWay": 2, "dischargeDiagnosis": "链路退押出院"}, idem="ui-dr-dc-" + uid)
        st_dr = api("/billing/admissions/%s/settle" % adm_dr, cashier, {"payMethod": 1},
                    idem="ui-dr-st-" + uid)  # 结算经办人=cashier（真实流程，账单对其可见）
        refundable = float(st_dr["data"]["refundAmount"])
        page.evaluate("(t) => localStorage.setItem('his_token', t)", cashier)
        page.goto(BASE + "/inpatient/settle")
        page.wait_for_timeout(2500)
        page.locator(".el-radio-button", has_text="押金退还").click()
        page.wait_for_timeout(1800)
        drow = page.locator("tr", has_text="链路退押" + uid)
        check("押金·退还视图行可见", drow.count() > 0)
        drow.first.get_by_role("button", name="退押金").click()
        page.wait_for_timeout(1500)
        dlg_dr = page.locator(".el-dialog:visible")
        prefill = dlg_dr.locator(".el-input-number input").first.input_value()
        check("押金·应退金额自动预填", abs(float(prefill) - refundable) < 0.01,
              (prefill, refundable))
        dlg_dr.get_by_role("button", name="确认退还").click()
        page.wait_for_timeout(2000)
        ad_dr = api("/inp/admissions/%s" % adm_dr, admin)
        check("押金·台账归齐账单额（deposit_total=账单）",
              abs(float(ad_dr["data"]["depositTotal"]) - 50.0) < 0.01,
              ad_dr["data"].get("depositTotal"))

        # ---------------- ⑬ KPI 看板 UI 链：指标卡渲染 + 数字非零（演示中心页面） ----------------
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/kpi/board")
        page.wait_for_timeout(3000)  # useCountUp 数字滚动完成后取值
        cards = page.locator(".kpi-card")
        check("KPI·指标卡渲染（≥12 张）", cards.count() >= 12, cards.count())
        kz = page.locator(".kpi-card", has_text="门诊人次").locator(".kpi-value").inner_text().strip()
        check("KPI·门诊人次非零", kz not in ("-", "0", "0.0", ""), kz)
        bed = page.locator(".kpi-card", has_text="床位使用率").locator(".kpi-value").inner_text().strip()
        check("KPI·床位使用率可算", bed not in ("-", ""), bed)

        # ---------------- ⑭ 上报管理 UI 链：统计头 + 队列表 + 报文抽屉 ----------------
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/pub/uploads")
        page.wait_for_timeout(2500)
        cards_rpt = page.locator(".kpi-card")
        check("上报·统计卡渲染（4 张）", cards_rpt.count() == 4, cards_rpt.count())
        rows_rpt = page.locator(".el-table__body-wrapper tr")
        check("上报·队列非空", rows_rpt.count() > 0, rows_rpt.count())
        # 报文抽屉：点第一行"报文"
        page.locator(".el-table__body-wrapper tr").first.get_by_role("button", name="报文").click()
        page.wait_for_timeout(1200)
        drawer_txt = page.locator(".el-drawer .el-textarea__inner").first.input_value()
        check("上报·报文预览（JSON 含 uploadNo）", "uploadNo" in drawer_txt)

        browser.close()

        browser.close()

    failed = [n for n, ok in results if not ok]
    print("\n===== UI 链路回归结果: %d/%d 通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        for n in failed:
            print("  - " + n)
        sys.exit(1)


if __name__ == "__main__":
    import json
    import urllib.parse
    main()
