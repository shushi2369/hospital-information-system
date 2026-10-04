#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
写并发压测（perf_concurrent.py）——真实业务链路混合负载（区别于只读基准）。
场景：N 个并发工作线程，各自执行 R 轮完整门诊写链路：
  建档 → 挂号 → 接诊 → 新增诊断 → 收费（资金事务）→ 待缴费列表读
输出：成功/失败数、p50/p95/max 延迟、吞吐量；失败即退出码 1。
用法：python perf_concurrent.py [baseUrl] [workers] [rounds]
"""
import json
import sys
import threading
import time
import urllib.parse
import urllib.request
import urllib.error
import itertools
import datetime

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost") + "/api/v1"
WORKERS = int(sys.argv[2]) if len(sys.argv) > 2 else 40
ROUNDS = int(sys.argv[3]) if len(sys.argv) > 3 else 3
PASSWORD = "His@2026"


def call(method, path, token=None, body=None, idem=None, timeout=30):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
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


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    return r["data"]["token"]


def main():
    cashier = login("cashier.li")
    admin = login("admin")
    latencies = []
    errors = []
    lock = threading.Lock()
    seq_counter = itertools.count(int(time.time()) % 10**8 * 100)  # 全局唯一纯数字序号（多轮连跑不撞号）
    # 每次运行随机出生日期段：idCard=6 位地区+8 位出生+4 位序号，跨运行不撞 B1001
    import random as _random
    _y, _m, _d = _random.randint(1970, 2005), _random.randint(1, 12), _random.randint(1, 28)
    run_birth_ymd = "%04d%02d%02d" % (_y, _m, _d)
    run_birth = "%04d-%02d-%02d" % (_y, _m, _d)

    # 动态容量感知：号源按 医生×日期×时段 限流，选剩余额度 ≥ ROUNDS+2 的桶；
    # 七十一轮：多日搜索（明日 → 7 天内），重复连跑耗尽近日号源后自动排更远
    st, dl = call("GET", "/basedata/doctors?pageNum=1&pageSize=50", admin)
    dl_rows = dl["data"] if isinstance(dl["data"], list) else dl["data"].get("list", [])
    quota = {d["id"]: d["dailyQuota"] for d in dl_rows if d.get("status") == 1 and d.get("dailyQuota")}
    buckets = []
    reg_date = None
    for day_offset in range(1, 8):
        day = (datetime.date.today() + datetime.timedelta(days=day_offset)).isoformat()
        day_buckets = []
        for did, q in quota.items():
            for period in (1, 2):
                st, rl = call("GET", "/registrations?regDate=%s&doctorId=%s&period=%d&pageNum=1&pageSize=1"
                              % (day, did, period), cashier)
                remain = q - rl["data"]["total"]
                if remain >= ROUNDS + 2:
                    day_buckets.append({"doctorId": did, "period": period})
        if day_buckets:
            buckets = day_buckets
            reg_date = day
            break
    if not buckets:
        print("FAIL 7 天内无剩余号源充足的挂号桶，请提高配额或清理历史号")
        sys.exit(1)
    print("挂号日期: %s 桶: %s" % (reg_date, [(_b['doctorId'], _b['period']) for _b in buckets]))

    def worker(wid):
        for rnd in range(ROUNDS):
            uid = "%010d" % next(seq_counter)
            bucket = buckets[(wid + rnd) % len(buckets)]
            t0 = time.time()
            try:
                st, r = call("POST", "/patients", cashier, {
                    "name": "压测患者" + uid, "gender": 1, "birthDate": run_birth,
                    "idCardNo": "340104" + run_birth_ymd + uid[-4:], "phone": "136" + uid[:8]}, idem="pf-pt-" + uid)
                if r["code"] != "OK":
                    raise RuntimeError("patient %s %s" % (r["code"], r.get("message", "")))
                st, pl = call("GET", "/patients?name=" + urllib.parse.quote("压测患者" + uid), cashier)
                pid = pl["data"]["list"][0]["id"]
                st, r = call("POST", "/registrations", cashier, {"patientId": pid, "doctorId": bucket["doctorId"],
                             "regDate": reg_date, "period": bucket["period"], "regType": 1}, idem="pf-reg-" + uid)
                if r["code"] != "OK":
                    raise RuntimeError("reg %s %s" % (r["code"], r.get("message", "")))
                st, rl = call("GET", "/registrations?patientId=%d&regDate=%s" % (pid, reg_date), cashier)
                reg_id = [x["id"] for x in rl["data"]["list"] if x["patientId"] == pid][-1]
                st, r = call("POST", "/clinic/visits/%d/start" % reg_id, admin, idem="pf-v-" + uid)
                visit_id = r.get("data")
                if not isinstance(visit_id, int):
                    raise RuntimeError("visit " + r["code"])
                st, r = call("POST", "/clinic/visits/%d/diagnoses" % visit_id, admin,
                             {"diagnosisCode": "J06.9", "diagnosisName": "压测上感", "diagnosisType": 1}, idem="pf-dg-" + uid)
                if r["code"] != "OK":
                    raise RuntimeError("diag " + r["code"])
                bill_body = {"visitId": visit_id, "payMethod": 1}
                st, r = call("POST", "/billing/bills", cashier, bill_body, idem="pf-ch-" + uid)
                if r["code"] != "OK":
                    raise RuntimeError("charge " + r["code"])
                st, r = call("GET", "/billing/visits/unpaid", cashier)
                if r["code"] != "OK":
                    raise RuntimeError("unpaid-list " + r["code"])
                with lock:
                    latencies.append(time.time() - t0)
            except Exception as e:  # noqa: BLE001 —— 压测要统计失败而非中断
                with lock:
                    errors.append("w%d r%d: %s" % (wid, rnd, e))

    threads = [threading.Thread(target=worker, args=(i,)) for i in range(WORKERS)]
    t_start = time.time()
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    elapsed = time.time() - t_start

    lat = sorted(latencies)
    n = len(lat)
    if n == 0:
        print("===== 全部失败 =====")
        for e in errors[:10]:
            print("  " + e)
        sys.exit(1)
    print("===== 写并发压测结果 =====")
    print("workers=%d rounds/worker=%d 总事务=%d 成功=%d 失败=%d" % (WORKERS, ROUNDS, n + len(errors), n, len(errors)))
    print("p50=%.2fs p95=%.2fs max=%.2fs 吞吐=%.1f 事务/s" % (
        lat[n // 2], lat[int(n * 0.95)], lat[-1], n / elapsed))
    if errors:
        print("失败样例:")
        for e in errors[:5]:
            print("  " + e)
    if len(errors) > n * 0.01:
        print("失败率超过 1%，判为不通过")
        sys.exit(1)


if __name__ == "__main__":
    main()
