#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
五期-lite 区域平台上报 e2e（e2e_phase5.py）——Mock 网关版。
覆盖：诊断触发传染病报卡 → 闭环回执 → 上报受理（QY）→ 压测失败标记确定性拒绝 →
     重试耗尽 → 手动重报仍失败 → 已成功单重报被拦 → 住院结算触发 → 统计口径。
"""
import json
import sys
import time
import urllib.parse
import urllib.request
import urllib.error

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost") + "/api/v1"
PASSWORD = "His@2026"
results = []


def call(method, path, token=None, body=None, idem=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json;charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    if idem:
        req.add_header("X-Idempotency-Key", idem)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {"code": "HTTP" + str(e.code)}


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败: %s" % r
    return r["data"]["token"]


def check(name, cond, detail=None):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:120]) if detail and not cond else ""))


def make_infectious_visit(doctor, cashier, admin, name, uid, suffix):
    sn = {"A": "1", "B": "2", "C": "3"}.get(suffix, "9")  # 后缀数字化且互不相同
    """门诊接诊 + 诊断含'霍乱' → 自动生成传染病待报卡，返回 (patientId, cardId)"""
    st, cr = call("POST", "/patients", cashier, {"name": name, "gender": 1,
         "birthDate": "1998-01-02", "idCardNo": "3401051998010%s%s" % (uid[-4:], sn),
         "phone": "13%s%s" % (sn, uid[:8])}, idem="p5-pt-%s-%s" % (suffix, uid))
    if cr.get("code") != "OK":
        print("[seed-debug] create:", str(cr)[:160])
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote(name), cashier)
    if not pl.get("data", {}).get("list"):
        print("[seed-debug] name=%s 查询为空" % name)
    pid = pl["data"]["list"][0]["id"]
    call("POST", "/registrations", cashier, {"patientId": pid, "doctorId": 2,
         "regDate": time.strftime("%Y-%m-%d"), "period": 2, "regType": 1},
         idem="p5-reg-%s-%s" % (suffix, uid))
    st, rl = call("GET", "/registrations?patientId=%s" % pid, cashier)
    reg = [x["id"] for x in rl["data"]["list"] if x["patientId"] == pid][-1]
    st, r = call("POST", "/clinic/visits/%s/start" % reg, doctor, idem="p5-v-%s-%s" % (suffix, uid))
    visit_id = r.get("data")
    call("POST", "/clinic/visits/%s/diagnoses" % visit_id, doctor,
         {"diagnosisCode": "A00.9", "diagnosisName": "霍乱", "diagnosisType": 1},
         idem="p5-dx-%s-%s" % (suffix, uid))
    st, cl = call("GET", "/pub/cards?status=10&pageSize=50", admin)
    card_id = next((c["id"] for c in cl["data"]["list"] if c.get("patientId") == pid), None)
    return pid, card_id


def close_card(admin, card_id, suffix, receipt_no, uid):
    call("POST", "/pub/cards/%s/report" % card_id, admin, idem="p5-rp-%s-%s" % (suffix, uid))
    call("POST", "/pub/cards/%s/approve" % card_id, admin, idem="p5-ap-%s-%s" % (suffix, uid))
    call("POST", "/pub/cards/%s/receipt" % card_id, admin,
         {"receiptNo": receipt_no}, idem="p5-rc-%s-%s" % (suffix, uid))


def deliver(admin, limit=100):
    st, r = call("POST", "/rpt/uploads/deliver?limit=%d" % limit, admin)
    return r.get("code") == "OK"


def main():
    uid = str(int(time.time()) % 10**8)
    admin = login("admin")
    doctor = login("dr.li")
    cashier = login("cashier.li")

    # ---- 1. 传染病闭环触发（正常姓名 → Mock 受理 QY） ----
    pid1, card1 = make_infectious_visit(doctor, cashier, admin, "上报患者" + uid, uid, "A")
    check("1. 诊断霍乱自动报卡", card1 is not None, card1)
    close_card(admin, card1, "A", "QS-A-" + uid, uid)
    deliver(admin, 100)
    st, ul = call("GET", "/rpt/uploads?bizType=1&pageSize=50", admin)
    mine = next((u for u in ul["data"]["list"] if u["bizId"] == card1 and u["status"] == 20), None)
    check("2. 闭环触发上报（Mock 受理）", mine is not None, len(ul["data"]["list"]))
    if mine:
        check("3. 受理号为 QY 前缀", str(mine["receiptNo"]).startswith("QY"), mine["receiptNo"])

    # ---- 2. 压测失败标记 → 确定性失败 → 重试耗尽 → 30 ----
    pid2, card2 = make_infectious_visit(doctor, cashier, admin, "压测失败" + uid, uid, "B")
    close_card(admin, card2, "B", "QS-B-" + uid, uid)
    for _ in range(6):
        deliver(admin, 100)
    st, ul = call("GET", "/rpt/uploads?bizType=1&pageSize=50", admin)
    fail_row = next((u for u in ul["data"]["list"] if u["bizId"] == card2 and u["status"] == 30), None)
    check("4. 失败标记卡投递确定性拒绝", fail_row is not None, fail_row)
    if fail_row:
        check("5. 重试耗尽（retryCount>=4 且有 last_error）",
              fail_row["retryCount"] >= 4 and fail_row.get("lastError"), fail_row)

    # ---- 3. 手动重报：仍失败（确定性行为） ----
    if fail_row:
        st, r = call("POST", "/rpt/uploads/%s/retry" % fail_row["id"], admin)
        # 语义：重报触发一次投递，失败标记仍在 → 状态不得变成 20（未误报成功）
        check("6. 手动重报执行（失败单未误报成功）",
              r.get("data", {}).get("status") != 20, r.get("data"))

    # ---- 4. 已成功单重报被拦 ----
    if mine:
        st, r = call("POST", "/rpt/uploads/%s/retry" % mine["id"], admin)
        check("7. 已成功上报重报被拦", r.get("code") != "OK", r)

    # ---- 5. 出院结算触发（bizType=3） ----
    call("POST", "/patients", cashier, {"name": "结算上报" + uid, "gender": 1,
         "birthDate": "1991-02-02", "idCardNo": "34010419910202" + uid[-4:],
         "phone": "135" + uid[:8]}, idem="p5-pt3-" + uid)
    st, pl3 = call("GET", "/patients?name=" + urllib.parse.quote("结算上报" + uid), cashier)
    pid3 = pl3["data"]["list"][0]["id"]
    call("POST", "/registrations", cashier, {"patientId": pid3, "doctorId": 2,
         "regDate": time.strftime("%Y-%m-%d"), "period": 2, "regType": 1}, idem="p5-reg3-" + uid)
    st, rl = call("GET", "/registrations?patientId=%s" % pid3, cashier)
    reg3 = [x["id"] for x in rl["data"]["list"] if x["patientId"] == pid3][-1]
    doctor = login("dr.li")
    st, r = call("POST", "/clinic/visits/%s/start" % reg3, doctor, idem="p5-v3-" + uid)
    visit3 = r.get("data")
    call("POST", "/clinic/visits/%s/complete" % reg3, doctor, idem="p5-v4-" + uid)
    call("POST", "/billing/bills", cashier, {"visitId": visit3, "payMethod": 1}, idem="p5-bill-" + uid)

    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = None
    n = 0
    while not beds["data"]:
        n += 1
        call("POST", "/inp/beds", admin, {"wardId": 1, "bedNo": "P5-%s-%d" % (uid[-4:], n),
             "chargeItemId": 10}, idem="p5-bed-%s-%d" % (uid, n))
        st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = beds["data"][0]
    st, adm = call("POST", "/inp/admissions", admin, {
        "patientId": pid3, "deptId": 1, "wardId": bed["wardId"], "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "结算上报观察",
        "depositAmount": 200, "payMethod": 1}, idem="p5-adm-" + uid)
    adm_id = adm["data"].get("id") if isinstance(adm["data"], dict) else adm["data"]
    call("POST", "/inp/daily-fees/manual?admissionId=%s" % adm_id, admin,
         {"feeType": 1, "itemName": "上报观察费", "quantity": 1, "unitPrice": 80},
         idem="p5-fee-" + uid)
    call("POST", "/inp/admissions/%s/discharge" % adm_id, admin,
         {"dischargeWay": 2, "dischargeDiagnosis": "上报观察出院"}, idem="p5-dc-" + uid)
    call("POST", "/billing/admissions/%s/settle" % adm_id, cashier,
         {"payMethod": 1}, idem="p5-set-" + uid)

    deliver(admin, 100)
    st, ul = call("GET", "/rpt/uploads?bizType=3&pageSize=50", admin)
    settle_row = next((u for u in ul["data"]["list"] if u["status"] == 20
                       and str(adm_id) in (u.get("payload") or "")), None)
    check("9. 出院结算触发上报（Mock 受理）", settle_row is not None, len(ul["data"]["list"]))

    # ---- 6. 统计口径 ----
    st, r = call("GET", "/rpt/uploads/stats", admin)
    s = r.get("data", {})
    check("10. 统计口径（total=uploaded+pending+failed）",
          s.get("total") == s.get("uploaded", 0) + s.get("pending", 0) + s.get("failed", 0), s)

    failed = [n for n, ok2 in results if not ok2]
    print("\n===== 五期-lite 上报 e2e 结果: %d/%d 通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        for n in failed:
            print("  - " + n)
        sys.exit(1)


if __name__ == "__main__":
    main()
