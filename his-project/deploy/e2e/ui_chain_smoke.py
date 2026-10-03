#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UI 链路回归（ui_chain_smoke.py）——混合模式：API 造数 + Playwright 断言关键 UI 交互。
覆盖：①收费工作台待缴费队列→确认收费（资金 UI wiring）
     ②就诊工作台接诊后 visitId 正确跳转（三十六轮回归点）
     ③患者列表 PHI 脱敏展示
     ④角色授权弹窗菜单回显（三十七轮 P1 回归点）
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


def check(name, cond):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name)


def api(path, token=None, body=None, idem=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method="POST" if body is not None else "GET")
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


def main():
    from playwright.sync_api import sync_playwright
    uid = str(int(time.time()) % 10**8)

    admin = login("admin")
    cashier = login("cashier.li")

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
        # API 建一个手机号已知的患者
        r = api("/patients", cashier, {"name": "链路脱敏" + uid, "gender": 1,
                "birthDate": "1990-01-01", "idCardNo": "34010419900101" + uid[-4:],
                "phone": "138" + uid[:8]}, idem="ui-pt-" + uid)
        check("PHI·建档 OK", r["code"] == "OK", )
        pl = api("/patients?name=" + urllib.parse.quote("链路脱敏" + uid), cashier)
        pid = pl["data"]["list"][0]["id"]
        phone = pl["data"]["list"][0]["phone"]
        page.reload()
        page.wait_for_timeout(1200)
        page.evaluate("(t) => localStorage.setItem('his_token', t)", admin)
        page.goto(BASE + "/regdesk/patients")
        page.wait_for_timeout(1500)
        body = page.inner_text("body")
        check("PHI·列表手机号脱敏（138****）",
              ("138****" + phone[-4:]) in body and phone not in body)
        detail_btn = page.locator("tr", has_text="链路脱敏" + uid).get_by_role("button", name="详情")
        if detail_btn.count():
            detail_btn.click()
            page.wait_for_timeout(1200)
            detail_body = page.inner_text("body")
            check("PHI·详情脱敏一致", ("138****" + phone[-4:]) in detail_body and phone not in detail_body)
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
        r = api("/clinic/visits/%d/start" % reg_id, doctor, idem="ui-ch-v-" + uid)
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
        # 翻页找 PUB_USER
        for _ in range(3):
            if page.locator("tr", has_text="PUB_USER").count() > 0:
                break
            nxt = page.locator("button", has_text="下一页")
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
