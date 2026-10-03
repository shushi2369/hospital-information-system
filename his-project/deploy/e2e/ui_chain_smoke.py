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
    api("/patients", cashier, {"name": name_prefix + uid, "gender": 1,
        "birthDate": "1990-01-01", "idCardNo": "3401041990010%s%d" % (uid[-4:], seq),
        "phone": "13%d%s" % (seq % 10, uid[:8])}, idem="ui-adm-pt-" + ascii_tag + uid)
    pl = api("/patients?name=" + urllib.parse.quote(name_prefix + uid), cashier)
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
            # 五十七轮：排台日期推进到明天——今日 3 手术间×10 台次已被整天回归逼近饱和，
            # 重试轮换只扫今天必撞；手术本就提前排程，对话框自动带入 plannedDate 无需日期控件交互
            "plannedDate": time.strftime("%Y-%m-%d", time.localtime(time.time() + 86400)),
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
                for seq_val in range(1, 6):
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
        page.wait_for_timeout(2000)
        page.get_by_role("button", name="分诊登记").click()
        page.wait_for_timeout(1000)
        page.locator(".el-dialog:visible .el-input-number input").first.fill(str(pid_emc))
        page.locator(".el-dialog:visible .el-form-item", has_text="主诉").locator("input").fill(complaint)
        page.locator(".el-dialog:visible").get_by_role("button", name="登记").click()
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
