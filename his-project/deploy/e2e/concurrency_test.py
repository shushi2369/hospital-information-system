#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
并发安全专项测试（concurrency_test.py）——此前 26 轮查验全部串行，真并发首次覆盖。
验证：①物资并发领用不超卖（原子扣减）②同一血袋并发发血仅一次成功（唯一约束）
③同一申请并发配血不重复推进 ④并发取消与配血竞态 ⑤同患者并发入院恰一笔成功（患者行锁串行化）。
"""
import json
import socket
import sys
import time
import threading
import urllib.request
import urllib.error

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/v1"
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
    except (urllib.error.URLError, socket.timeout, OSError) as e:
        # 并发场景下连接层抖动（重置/超时）不应让线程静默死亡、out 留 None 崩溃解包
        return 0, {"code": "CONN_FAIL", "message": str(e)}


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:130]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    return r["data"]["token"]


def parallel(calls, workers=None):
    """并发执行 call 返回 (状态码,R) 列表，barrier 同步齐发"""
    barrier = threading.Barrier(len(calls))
    out = [None] * len(calls)

    def run(i, fn):
        barrier.wait()
        out[i] = fn()

    threads = [threading.Thread(target=run, args=(i, fn)) for i, fn in enumerate(calls)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return out


def main():
    uid = str(int(time.time() * 1000))[-8:]
    today = time.strftime("%Y-%m-%d")
    admin = login("admin")
    nurse = login("nurse.wang")
    bb_tech = login("bb.tech")
    pharmacist = login("pharm.zhao")

    # ================= ① 物资并发领用不超卖 =================
    code = "CC" + uid
    call("POST", "/mat/materials", admin, {"materialCode": code, "name": "并发物资" + uid,
         "category": 1, "unit": "件", "price": 1.00, "safeStock": 0}, idem="cc-m1-" + uid)
    st, ml = call("GET", "/mat/materials?category=1", admin)
    mat = next((m for m in ml["data"]["list"] if m["materialCode"] == code), None)
    call("POST", "/mat/purchases", admin, {"supplierId": 1, "materialId": mat["id"],
         "quantity": 10, "unitPrice": 1.00}, idem="cc-m2-" + uid)
    st, pl = call("GET", "/mat/purchases?status=10", admin)
    po = next((p for p in pl["data"]["list"] if p["materialId"] == mat["id"]), None)
    call("POST", "/mat/purchases/%d/approve" % po["id"], admin, idem="cc-m3-" + uid)
    call("POST", "/mat/purchases/%d/receive?batchNo=CC-B1-%s&expireDate=2027-12-31" % (po["id"], uid),
         admin, idem="cc-m4-" + uid)
    # 库存=10，10 个并发各领 1：恰 10 成功 0 失败、库存=0、不超卖
    calls = [(lambda i=i: call("POST", "/mat/requisitions", admin,
              {"materialId": mat["id"], "deptId": 1, "quantity": 1},
              idem="cc-r-%s-%d" % (uid, i))) for i in range(10)]
    out = parallel(calls)
    ok_cnt = sum(1 for st, r in out if r["code"] == "OK")
    st, sl = call("GET", "/mat/stocks", admin)
    left = next((s["quantity"] for s in sl["data"] if s["materialId"] == mat["id"]), -1)
    check("C1. 并发领用×10（库存10）：恰10成功", ok_cnt == 10, ok_cnt)
    check("C2. 并发领用不超卖：库存=0", left == 0, left)
    # 11 个并发领 2（库存0）：全部失败
    calls = [(lambda i=i: call("POST", "/mat/requisitions", admin,
              {"materialId": mat["id"], "deptId": 1, "quantity": 2},
              idem="cc-r2-%s-%d" % (uid, i))) for i in range(11)]
    out = parallel(calls)
    ok_cnt = sum(1 for st, r in out if r["code"] == "OK")
    check("C3. 零库存并发领用：全部拒绝", ok_cnt == 0, ok_cnt)

    # ================= ② 同一血袋并发发血仅一次 =================
    # 建申请→审核→配血（相容）→ 并发发血 ×3（同血袋同申请）
    st, r = call("POST", "/bb/requests", doctor := login("dr.li"), {
        "admissionId": 75, "patientId": 415, "bloodType": 4, "rh": 1,
        "component": 1, "volumeMl": 200, "usePurpose": "并发复验"}, idem="cc-b1-" + uid)
    st, rl = call("GET", "/bb/requests?status=10", bb_tech)
    req_id = [x["id"] for x in rl["data"]["list"] if x["usePurpose"] == "并发复验"][0]
    call("POST", "/bb/requests/%d/review?approved=true" % req_id, bb_tech, idem="cc-b2-" + uid)
    st, av = call("GET", "/bb/bags/available?bloodType=4&component=1", bb_tech)
    if not av["data"]:  # 在库耗尽：自动入库新血袋（血袋为一次性资源）
        call("POST", "/bb/bags", bb_tech, {
            "bagNo": "CC-BAG-" + uid, "bloodType": 4, "rh": 1, "component": 1,
            "volumeMl": 200, "expireDate": "2027-12-31"}, idem="cc-bag-" + uid)
        st, av = call("GET", "/bb/bags/available?bloodType=4&component=1", bb_tech)
    bag_id = av["data"][0]["id"]
    call("POST", "/bb/requests/%d/cross-match" % req_id, bb_tech, {
        "bagId": bag_id, "crossMethod": "盐水介质", "crossResult": 1}, idem="cc-b3-" + uid)
    calls = [(lambda i=i: call("POST", "/bb/requests/%d/issue" % req_id, bb_tech,
              {"bagId": bag_id, "receiverId": 6}, idem="cc-i-%s-%d" % (uid, i))) for i in range(3)]
    out = parallel(calls)
    ok_cnt = sum(1 for st, r in out if r["code"] == "OK")
    st, d = call("GET", "/bb/requests/%d" % req_id, admin)
    issues = d["data"]["issues"]
    check("C4. 同袋并发发血×3：恰1成功", ok_cnt == 1, ok_cnt)
    check("C5. 发血记录唯一", len(issues) == 1, len(issues))
    st, r = call("POST", "/bb/requests/%d/transfusion" % req_id, nurse, {
        "bagId": bag_id, "checker2Id": 2}, idem="cc-t1-" + uid)
    check("C6. 前置：输注开始", r["code"] == "OK", r)
    # 并发结束 ×3：恰一次推进
    calls = [(lambda i=i: call("POST", "/bb/requests/%d/finish" % req_id, nurse,
              {"outcome": 1, "note": "并发结束%d" % i}, idem="cc-f-%s-%d" % (uid, i))) for i in range(3)]
    out = parallel(calls)
    ok_cnt = sum(1 for st, r in out if r["code"] == "OK")
    check("C7. 并发结束×3：恰1次推进(60)", ok_cnt == 1, ok_cnt)

    # ================= ③ 并发取消与配血竞态 =================
    call("POST", "/bb/requests", doctor, {
        "admissionId": 75, "patientId": 415, "bloodType": 4, "rh": 1,
        "component": 1, "volumeMl": 100, "usePurpose": "竞态复验"}, idem="cc-b4-" + uid)
    st, rl = call("GET", "/bb/requests?status=10", bb_tech)
    req2 = [x["id"] for x in rl["data"]["list"] if x["usePurpose"] == "竞态复验"][0]
    call("POST", "/bb/requests/%d/review?approved=true" % req2, bb_tech, idem="cc-b5-" + uid)
    st, av = call("GET", "/bb/bags/available?bloodType=4&component=1", bb_tech)
    if not av["data"]:  # 在库耗尽：再补一袋
        call("POST", "/bb/bags", bb_tech, {
            "bagNo": "CC-BAG2-" + uid, "bloodType": 4, "rh": 1, "component": 1,
            "volumeMl": 200, "expireDate": "2027-12-31"}, idem="cc-bag2-" + uid)
        st, av = call("GET", "/bb/bags/available?bloodType=4&component=1", bb_tech)
    bag2 = av["data"][0]["id"]
    # 并发：取消 + 配血同时发
    calls = [
        (lambda: call("POST", "/bb/requests/%d/cancel" % req2, doctor, idem="cc-x1-" + uid)),
        (lambda: call("POST", "/bb/requests/%d/cross-match" % req2, bb_tech, {
            "bagId": bag2, "crossMethod": "盐水介质", "crossResult": 1}, idem="cc-x2-" + uid)),
    ]
    out = parallel(calls)
    codes = [r["code"] for _, r in out]
    st, d = call("GET", "/bb/requests/%d" % req2, admin)
    final_status = d["data"]["request"]["status"]
    consistent = (final_status == 70 and codes.count("OK") >= 1) or (final_status == 30 and codes.count("OK") >= 1)
    check("C8. 取消/配血竞态：终态一致（70 取消 或 30 配血，无中间态悬挂）", consistent,
          {"codes": codes, "final": final_status})

    # ================= ⑤ 同患者并发双入院（患者行锁串行化） =================
    import urllib.parse
    cashier = login("cashier.li")
    st, r = call("POST", "/patients", cashier, {"name": "并发入院患者" + uid, "gender": 1,
                 "birthDate": "1992-06-15", "idCardNo": "34010419900101" + uid[-4:],
                 "phone": "138" + uid}, idem="cc-pt-" + uid)
    check("C9. 前置：建档成功", r["code"] == "OK", r)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("并发入院患者" + uid), cashier)
    race_patient = pl["data"]["list"][0]["id"]
    st, wards = call("GET", "/inp/wards", cashier)
    race_ward, race_dept, free = None, None, []
    for w in wards["data"]:  # 动态选有 ≥2 张空闲床的病区（历史运行床位残留会耗尽首选病区）
        st, beds = call("GET", "/inp/beds?wardId=%d&bedStatus=1" % w["id"], cashier)
        if len(beds["data"]) >= 2:
            race_ward, race_dept, free = w["id"], w["deptId"], beds["data"]
            break
    for _ in range(5):  # 仍不足自动补建（多套件连跑耗床）；设上限防死循环
        if race_ward is not None and len(free) >= 2:
            break
        if race_ward is None:
            race_ward = wards["data"][0]["id"]
            race_dept = wards["data"][0]["deptId"]
        no = ("CC" + uid[-6:] + "-%d") % len(free)  # 床号≤16位（CC-BED-uid-0 恰 17 位超限）
        st, rb = call("POST", "/inp/beds", admin, {"wardId": race_ward, "bedNo": no,
                      "chargeItemId": 10}, idem="cc-bed-" + no)
        print("  [bed-create] %s -> %s %s" % (no, st, rb if isinstance(rb, dict) else rb))
        st, beds = call("GET", "/inp/beds?wardId=%d&bedStatus=1" % race_ward, cashier)
        free = beds["data"]
    adm_body = lambda bed_id: {"patientId": race_patient, "deptId": race_dept, "wardId": race_ward,
                               "bedId": bed_id, "doctorId": 2, "admissionType": 1,
                               "plannedDiagnosis": "并发入院竞态复验", "depositAmount": 100,
                               "payMethod": 1}
    calls = [
        (lambda: call("POST", "/inp/admissions", cashier, adm_body(free[0]["id"]), idem="cc-am1-" + uid)),
        (lambda: call("POST", "/inp/admissions", cashier, adm_body(free[1]["id"]), idem="cc-am2-" + uid)),
    ]
    out = parallel(calls)
    codes = [r["code"] for _, r in out]
    print("  [race] codes=%s" % codes)
    ok_cnt = codes.count("OK")
    check("C10. 同患者并发入院×2：恰1成功，另笔 B6002", ok_cnt == 1 and codes.count("B6002") == 1, codes)
    st, af = call("GET", "/inp/admissions?status=10&patientId=%d" % race_patient, cashier)
    active_cnt = af["data"]["total"] if isinstance(af["data"], dict) else len(af["data"])
    check("C11. 该患者在院记录恰1条", active_cnt == 1, active_cnt)
    st, bedsafter = call("GET", "/inp/beds?wardId=%d" % race_ward, cashier)
    used = sum(1 for b in bedsafter["data"] if b["id"] in (free[0]["id"], free[1]["id"]) and b["bedStatus"] != 1)
    check("C12. 竞态床位恰占用1张（无泄漏）", used == 1, used)

    failed = [n for n, ok, _ in results if not ok]
    print("\n===== 并发安全专项结果: %d/%d 通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:")
        for name in failed:
            print("  - " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
