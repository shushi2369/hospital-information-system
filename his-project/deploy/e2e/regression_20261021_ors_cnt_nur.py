#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
2026-10-21 会诊/护理/手术/急诊回归：
  A. 会诊接受限受邀科室（跨科室抢单拒绝，管理员代接受保留）
  B. 体征收缩压>舒张压、记录时间不得晚于当前
  C. 手术取消在 10/20/30 均可用且条件更新不误伤
  D. 急诊关档同步分诊单状态 2
前置：后端已部署本次修复版本（localhost:8080）。
"""
import json
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080/api/v1"
PASSWORD = "His@2026"
RUN = str(int(time.time() * 1000))[-8:] + str(random.randint(10, 99))
RESULTS = []


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


def check(name, cond, detail=""):
    RESULTS.append((name, bool(cond), detail))
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:240]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败 %s: %s" % (username, r)
    return r["data"]["token"]


def create_patient(token, tag, suffix):
    for i in range(8):
        seq = random.randint(0, 9999)
        body = {"name": tag + suffix, "gender": 1, "birthDate": "1990-01-01",
                "idCardNo": "34010619900102%04d" % seq,
                "phone": "15" + "%09d" % random.randint(0, 999999999)}
        st, r = call("POST", "/patients", token, body, idem="rc-p-%s-%d-%d" % (suffix, seq, i))
        if r.get("code") == "OK":
            st2, pl = call("GET", "/patients?patientNo=" + r["data"], token)
            assert st2 == 200 and pl["data"]["list"], pl
            return pl["data"]["list"][0]
        if r.get("code") != "B1001":
            break
    raise AssertionError("患者建档失败: %s" % r)


def ensure_admission(admin):
    st, r = call("GET", "/inp/admissions?pageNum=1&pageSize=1&status=10", admin)
    rows = (r.get("data") or {}).get("list") or []
    if rows:
        return rows[0]["id"], rows[0]["patientId"]
    patient = create_patient(admin, "RCADM", RUN)
    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    n = 0
    while not beds["data"]:
        n += 1
        call("POST", "/inp/beds", admin, {
            "wardId": 1, "bedNo": "RC-" + RUN + "-" + str(n), "chargeItemId": 10},
            idem="rc-bed-" + RUN + str(n))
        st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = beds["data"][0]
    st, r = call("POST", "/inp/admissions", admin, {
        "patientId": patient["id"], "deptId": 1, "wardId": bed["wardId"], "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "回归",
        "depositAmount": 1000, "payMethod": 1}, idem="rc-adm-" + RUN)
    assert r.get("code") == "OK", r
    return r["data"]["id"], patient["id"]


def main():
    admin = login("admin")
    doctor = login("dr.li")
    nurse = login("nurse.wang")
    emc = login("emc.li")
    admission_id, patient_id = ensure_admission(admin)
    today = datetime.now().strftime("%Y-%m-%d")

    # ===== A. 会诊接受限受邀科室 =====
    reason = "cnt-scope-" + RUN
    st, r = call("POST", "/cnt/requests", doctor, {
        "admissionId": admission_id, "patientId": patient_id, "deptId": 3,
        "reason": reason, "urgent": 0}, idem="rc-cnt-c-" + RUN)
    check("A1. 会诊申请创建", r.get("code") == "OK", r)
    st, page = call("GET", "/cnt/requests?status=10&pageNum=1&pageSize=100", admin)
    row = next((x for x in page["data"]["list"] if x.get("reason") == reason), None)
    check("A2. 待接受会诊可检索", row is not None, page)
    if row:
        st, r = call("POST", "/cnt/requests/%d/accept" % row["id"], doctor, idem="rc-cnt-a1-" + RUN)
        check("A3. 非受邀科室医生接受被拒(A0003)", r.get("code") == "A0003", r)
        st, r = call("POST", "/cnt/requests/%d/accept" % row["id"], admin, idem="rc-cnt-a2-" + RUN)
        check("A4. 管理员代接受保留", r.get("code") == "OK", r)
        st, r = call("POST", "/cnt/requests/%d/complete?opinion=ok" % row["id"], admin,
                     idem="rc-cnt-cp-" + RUN)
        check("A5. 管理员代完成意见", r.get("code") == "OK", r)

    # ===== B. 体征边界 =====
    base = {"admissionId": admission_id, "temperature": 36.5, "pulse": 80, "respiration": 18}
    st, r = call("POST", "/nur/vital-signs", nurse,
                 dict(base, bpHigh=60, bpLow=170), idem="rc-nur-1-" + RUN)
    check("B1. 收缩压小于舒张压被拒", r.get("code") == "A0001", r)
    st, r = call("POST", "/nur/vital-signs", nurse,
                 dict(base, bpHigh=120, bpLow=80,
                      recordTime=(datetime.now() + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%S")),
                 idem="rc-nur-2-" + RUN)
    check("B2. 未来记录时间被拒", r.get("code") == "A0001", r)
    st, r = call("POST", "/nur/vital-signs", nurse,
                 dict(base, bpHigh=120, bpLow=80), idem="rc-nur-3-" + RUN)
    check("B3. 合法体征可录入", r.get("code") == "OK", r)

    # ===== C. 手术取消 =====
    def create_ors(tag):
        name = "回归取消" + tag + RUN
        st, r = call("POST", "/ors/requests", doctor, {
            "admissionId": admission_id, "patientId": patient_id, "surgeryName": name,
            "diagnosis": "回归", "plannedDate": today, "anesthesiaMethod": 4},
            idem="rc-ors-c-" + tag + RUN)
        assert r.get("code") == "OK", r
        st, ol = call("GET", "/ors/requests?admissionId=%d&pageNum=1&pageSize=100" % admission_id, doctor)
        return next(x for x in ol["data"]["list"] if x["surgeryName"] == name)

    req = create_ors("A")
    st, r = call("POST", "/ors/requests/%d/cancel" % req["id"], doctor, idem="rc-ors-x1-" + RUN)
    st, d = call("GET", "/ors/requests/%d" % req["id"], doctor)
    check("C1. 待审核申请可取消且状态=70",
          r.get("code") == "OK" and d["data"]["request"]["status"] == 70, (r, d))

    req2 = create_ors("B")
    call("POST", "/ors/requests/%d/review" % req2["id"], doctor, {"approved": True},
         idem="rc-ors-rv-" + RUN)
    st, r = call("POST", "/ors/requests/%d/cancel" % req2["id"], doctor, idem="rc-ors-x2-" + RUN)
    st, d = call("GET", "/ors/requests/%d" % req2["id"], doctor)
    check("C2. 已审核未排台申请可取消且状态=70",
          r.get("code") == "OK" and d["data"]["request"]["status"] == 70, (r, d))
    st, r = call("POST", "/ors/requests/%d/cancel" % req2["id"], doctor, idem="rc-ors-x3-" + RUN)
    check("C3. 重复取消被拒（条件更新生效）", r.get("code") != "OK", r)

    # ===== D. 急诊关档同步分诊 =====
    complaint = "emc-sync-" + RUN
    st, r = call("POST", "/emc/triage", emc, {
        "patientId": patient_id, "chiefComplaint": complaint,
        "triageLevel": 3, "centerType": 1}, idem="rc-emc-t-" + RUN)
    st, tp = call("GET", "/emc/triage?pageNum=1&pageSize=100", emc)
    tri = next((x for x in tp["data"]["list"] if x.get("chiefComplaint") == complaint), None)
    check("D1. 分诊登记", tri is not None, tp)
    if tri:
        st, r = call("POST", "/emc/visits", emc, {"triageId": tri["id"], "centerType": 1},
                     idem="rc-emc-v-" + RUN)
        st, vp = call("GET", "/emc/visits?pageNum=1&pageSize=100&centerType=1", emc)
        vis = next(x for x in vp["data"]["list"] if x.get("triageId") == tri["id"])
        st, r = call("POST", "/emc/visits/%d/close" % vis["id"], emc, {"outcome": 1},
                     idem="rc-emc-cl-" + RUN)
        check("D2. 急诊病例关档", r.get("code") == "OK", r)
        st, closed = call("GET", "/emc/triage?status=2&pageNum=1&pageSize=100", emc)
        check("D3. 关档后分诊单出现在已关档筛选",
              any(x["id"] == tri["id"] for x in closed["data"]["list"]), closed)

    failed = [x for x in RESULTS if not x[1]]
    print("\n===== ors/cnt/nur/emc regression: %d/%d passed, %d failed =====" %
          (len(RESULTS) - len(failed), len(RESULTS), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
