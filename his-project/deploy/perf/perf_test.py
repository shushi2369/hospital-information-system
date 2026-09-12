#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HIS 一期性能验收脚本（指导文档 §7：≥100 并发用户；核心查询 95 分位 < 2s；提交类 95 分位 < 3s）
用法: python perf_test.py [BASE_URL]
流程:
  1) 管理员批量创建 100 个测试用户（分配四类角色）并逐一登录 → 100 个真实独立会话
  2) 查询阶段: 100 并发混合只读查询 3 轮/人，统计 p50/p95/p99 与错误率
  3) 提交阶段: 100 并发挂号（患者×医生×日期×时段 组合唯一），统计 p95 与成功率，随后并行退号清理
依赖: 仅 Python 3.8+ 标准库。
"""
import json
import sys
import time
import statistics
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/v1"
PASSWORD = "His@2026"
CONCURRENCY = 100
QUERY_ROUNDS = 3


def call(method, path, token=None, body=None, idem=None, timeout=30):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json;charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    if idem:
        req.add_header("X-Idempotency-Key", idem)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode("utf-8"))
        except Exception:
            return e.code, {"code": "HTTP" + str(e.code)}
    except Exception as e:
        return 0, {"code": "CONN_ERR", "message": str(e)}


def pct(values, p):
    values = sorted(values)
    if not values:
        return 0
    idx = min(len(values) - 1, max(0, round(p / 100 * len(values) + 0.5) - 1))
    return values[idx]


def report_phase(name, latencies, errors, limit_s):
    total = len(latencies) + len(errors)
    ok = len(latencies)
    p50 = pct(latencies, 50) * 1000
    p95 = pct(latencies, 95) * 1000
    p99 = pct(latencies, 99) * 1000
    err_rate = len(errors) / total * 100 if total else 0
    print(f"[{name}] 请求 {total} | 成功 {ok} | 错误 {len(errors)} ({err_rate:.2f}%) | "
          f"p50 {p50:.0f}ms | p95 {p95:.0f}ms | p99 {p99:.0f}ms | 阈值 p95<{limit_s * 1000:.0f}ms")
    return {"total": total, "errors": errors[:5], "p95": p95, "limit": limit_s * 1000}


def main():
    stamp = str(int(time.time()))[-7:]
    st, r = call("POST", "/auth/login", body={"username": "admin", "password": PASSWORD})
    admin = r["data"]["token"]
    st, roles = call("GET", "/system/roles", admin)
    role_map = {x["roleCode"]: x["id"] for x in roles["data"]}
    plan = (["CASHIER"] * 40) + (["DOCTOR"] * 30) + (["PHARMACIST"] * 15) + (["AUDITOR"] * 15)

    # ---- 1. 创建 100 用户并登录（100 个独立会话） ----
    t0 = time.time()
    def create_and_login(i):
        role = plan[i % len(plan)]
        uname = f"perf{stamp}{i:03d}"
        st, r = call("POST", "/system/users", admin,
                     {"username": uname, "realName": f"压测用户{i:03d}", "password": "Perf@2026x",
                      "roleIds": [role_map[role]]}, idem=f"perf-u-{stamp}-{i}")
        if r.get("code") != "OK":
            return None, None
        st, r = call("POST", "/auth/login", body={"username": uname, "password": "Perf@2026x"})
        return role, (r.get("data") or {}).get("token")
    with ThreadPoolExecutor(max_workers=20) as pool:
        sessions = [x for x in pool.map(create_and_login, range(CONCURRENCY)) if x[1]]
    print(f"[准备] {len(sessions)} 个真实用户会话创建完成，耗时 {time.time() - t0:.1f}s")

    # 收费员会话用于建档/挂号准备
    st, r = call("POST", "/auth/login", body={"username": "cashier.li", "password": PASSWORD})
    cashier = r["data"]["token"]

    # ---- 2. 查询阶段（100 并发 × 3 轮混合只读查询） ----
    today = time.strftime("%Y-%m-%d")
    week_later = time.strftime("%Y-%m-%d", time.localtime(time.time() + 7 * 86400))
    query_sets = {
        "CASHIER": ["/patients?pageSize=10", "/registrations?pageSize=10", "/billing/bills?pageSize=10",
                    "/billing/settlements?pageSize=10", "/basedata/doctors"],
        "DOCTOR": ["/basedata/doctors", "/basedata/drugs?pageSize=10", "/basedata/charge-items"],
        "PHARMACIST": ["/pharmacy/prescriptions", "/pharmacy/prescriptions/dispensable",
                       "/inventory/batches?pageSize=10", "/inventory/warnings"],
        "AUDITOR": ["/stats/registrations/daily?startDate=%s&endDate=%s" % (today, today),
                    "/stats/revenue/daily?startDate=%s&endDate=%s" % (today, today),
                    "/stats/revenue/detail?startDate=%s&endDate=%s" % (today, today),
                    "/logs/operations?pageSize=10"],
    }

    def query_worker(args):
        role, token, idx = args
        paths = query_sets[role]
        latencies, errors = [], []
        for k in range(QUERY_ROUNDS):
            path = paths[(idx + k) % len(paths)]
            t = time.time()
            st, r = call("GET", path, token)
            cost = time.time() - t
            if st == 200 and r.get("code") == "OK":
                latencies.append(cost)
            else:
                errors.append((path, st, r.get("code")))
        return latencies, errors

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        qres = list(pool.map(query_worker, [(role, token, i) for i, (role, token) in enumerate(sessions)]))
    q_lat = [x for lat, _ in qres for x in lat]
    q_err = [e for _, errs in qres for e in errs]
    print(f"[耗时] 查询阶段总时长 {time.time() - t0:.1f}s")
    q = report_phase("查询阶段(100并发)", q_lat, q_err, 2.0)

    # ---- 3. 提交阶段（100 并发挂号，组合唯一） ----
    n_patients = CONCURRENCY
    patient_ids = []
    t0 = time.time()
    for i in range(n_patients):
        id_card = "34010420000101" + f"{stamp[-4:]}{i:03d}"[-4:]
        st, r = call("POST", "/patients", cashier,
                     {"name": f"压测患者{stamp}{i:03d}", "gender": i % 2 + 1,
                      "idCardNo": id_card, "phone": "139" + f"{stamp}{i:03d}"[-8:]},
                     idem=f"perf-p-{stamp}-{i}")
        patient_no = None
        if r.get("code") == "OK":
            patient_no = r.get("data")
        elif r.get("code") == "B1001":  # 重跑幂等：从提示中取已有建档号
            msg = r.get("message", "")
            patient_no = msg.split("建档号 ")[1].split("，")[0] if "建档号" in msg else None
        if patient_no:
            st, pl = call("GET", "/patients?patientNo=" + patient_no, cashier)
            lst = (pl.get("data") or {}).get("list") or []
            patient_ids.append(lst[0]["id"] if lst else None)
        else:
            patient_ids.append(None)
    patient_ids = [p for p in patient_ids if p]
    print(f"[准备] {len(patient_ids)} 个压测患者建档完成，耗时 {time.time() - t0:.1f}s")
    assert len(patient_ids) >= CONCURRENCY * 0.98, "患者建档失败率过高"

    doctor_ids = [1, 2, 3]
    combos = []
    for i in range(CONCURRENCY):
        pid = patient_ids[i % len(patient_ids)]
        doc = doctor_ids[i % len(doctor_ids)]
        date = week_later if (i // 6) % 2 == 0 else today
        period = i % 2 + 1
        combos.append((pid, doc, date, period, i))

    def submit_worker(args):
        pid, doc, date, period, i = args
        role, token = sessions[i % len(sessions)]
        if role != "CASHIER":
            token = cashier  # 挂号权限仅收费员/管理员，统一用收费员会话压提交链路
        t = time.time()
        st, r = call("POST", "/registrations", token,
                     {"patientId": pid, "doctorId": doc, "regDate": date, "period": period, "regType": 1},
                     idem=f"perf-reg-{stamp}-{i}", timeout=60)
        cost = time.time() - t
        ok = st == 200 and r.get("code") == "OK"
        reg_id = None
        if ok:
            st, rl = call("GET", "/registrations?patientId=%d&regDate=%s" % (pid, date), token)
            for row in rl["data"]["list"]:
                if row["doctorId"] == doc and row["period"] == period and row["status"] == 10:
                    reg_id = row["id"]
                    break
        return cost, (r.get("code"), st), ok, reg_id

    t0 = time.time()
    with ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        sres = list(pool.map(submit_worker, combos))
    s_lat = [cost for cost, _, ok, _ in sres if ok]
    s_err = [(code, st) for _, code, ok, _ in sres if not ok]
    print(f"[耗时] 提交阶段总时长 {time.time() - t0:.1f}s")
    s = report_phase("提交阶段(100并发挂号)", s_lat, s_err, 3.0)
    submit_success = len([1 for _, _, ok, _ in sres if ok])

    # ---- 4. 清理：并行退号 ----
    reg_ids = [rid for _, _, _, rid in sres if rid]
    def cancel(rid):
        return call("POST", "/registrations/%d/cancel" % rid, cashier, idem=f"perf-c-{rid}")
    with ThreadPoolExecutor(max_workers=20) as pool:
        list(pool.map(cancel, reg_ids))

    # ---- 结论 ----
    print("\n========== 性能验收结论 ==========")
    print(f"并发用户数: {len(sessions)} (要求 >= 100)")
    ok1 = len(sessions) >= 100
    ok2 = q["p95"] < q["limit"] and q["total"] > 0 and len(q["errors"]) == 0
    ok3 = s["p95"] < s["limit"] and submit_success >= CONCURRENCY * 0.98
    print(f"核心查询 p95 {q['p95']:.0f}ms < 2000ms -> {'PASS' if ok2 else 'FAIL'} (错误 {len(q['errors'])})")
    print(f"提交类   p95 {s['p95']:.0f}ms < 3000ms -> {'PASS' if ok3 else 'FAIL'} (挂号成功 {submit_success}/{CONCURRENCY})")
    if q["errors"]:
        print("查询错误样例:", q["errors"][:5])
    if s_err:
        print("提交错误样例:", s_err[:5])
    sys.exit(0 if (ok1 and ok2 and ok3) else 1)


if __name__ == "__main__":
    main()
