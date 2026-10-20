#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
2026-10-21 凌晨加严审计回归：
  A. 不良事件上报：必填校验前置（不再落 DB 非空约束）、服务端字段/主键白名单、
     整改说明空值与长度守卫
  B. 医保专员账单详情：与 billPage 数据范围对齐（可列即可看）
"""
import json
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

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
    print(("PASS " if cond else "FAIL ") + name
          + (("  | " + str(detail)[:240]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败 %s: %s" % (username, r)
    return r["data"]["token"]


def main():
    admin = login("admin")

    # ================= A. 不良事件上报 =================
    st, r = call("POST", "/ae", admin, {"eventType": 1, "severity": 1},
                 idem="late-ae-missing-" + RUN)
    check("A1. 缺必填字段被参数校验拦截", st == 400 and r.get("code") == "A0001"
          and "科室" in (r.get("message") or ""), r)

    st, r = call("POST", "/ae", admin, {
        "id": 987654321, "eventType": 1, "severity": 2, "departmentId": 1,
        "eventTime": "2026-10-20T10:00:00", "description": "late audit mass assignment",
        "qcId": 999, "handlerNote": "forged", "handlerId": 999,
        "closedTime": "2026-10-20T11:00:00", "version": 7},
        idem="late-ae-mass-" + RUN)
    check("A2. 上报成功", r.get("code") == "OK", r)
    event_no = r.get("data")
    st, p = call("GET", "/ae?status=10&pageSize=100", admin)
    row = next((x for x in (p.get("data") or {}).get("list", [])
                if x.get("eventNo") == event_no), None)
    check("A3. 主键由服务端生成", row is not None and row.get("id") != 987654321, row)
    check("A4. 质控/整改/闭环字段不可伪造", row is not None and row.get("qcId") is None
          and row.get("handlerId") is None and row.get("handlerNote") is None
          and row.get("closedTime") is None, row)
    check("A5. 版本从 0 起算", row is not None and row.get("version") == 0, row)

    st, r = call("POST", "/ae/%d/assign" % row["id"], admin, idem="late-ae-as-" + RUN)
    check("A6. 质控分派成功", r.get("code") == "OK", r)
    st, r = call("POST", "/ae/%d/rectify?handlerNote=" % row["id"], admin,
                 idem="late-ae-rt0-" + RUN)
    check("A7. 空整改说明被拒", st == 400 and r.get("code") == "A0001", r)
    st, r = call("POST", "/ae/%d/rectify?handlerNote=%s" % (row["id"], "x" * 600), admin,
                 idem="late-ae-rt1-" + RUN)
    check("A8. 超长整改说明被拒", st == 400 and r.get("code") == "A0001", r)
    st, r = call("POST", "/ae/%d/rectify?handlerNote=%s"
                 % (row["id"], urllib.parse.quote("已整改")), admin,
                 idem="late-ae-rt2-" + RUN)
    check("A9. 正常整改仍可提交", r.get("code") == "OK", r)

    # ================= C. 会诊申请入参 =================
    st, a = call("GET", "/inp/admissions?pageSize=1&status=10", admin)
    admissions = (a.get("data") or {}).get("list") or []
    if admissions:
        link = {"admissionId": admissions[0]["id"]}
    else:
        st, v = call("GET", "/clinic/visits?pageSize=1", admin)
        link = {"visitId": ((v.get("data") or {}).get("list") or [{}])[0].get("id")}
    st, r = call("POST", "/cnt/requests", admin, dict(link, reason="late probe"),
                 idem="late-cnt-miss-" + RUN)
    check("C1. 缺受邀科室被参数校验拦截", st == 400 and r.get("code") == "A0001"
          and "科室" in (r.get("message") or ""), r)
    st, r = call("POST", "/cnt/requests", admin, dict(
        link, deptId=1, reason="late consult", id=987654322, consultDoctorId=999,
        acceptTime="2026-10-20T09:00:00", opinion="forged opinion",
        opinionTime="2026-10-20T09:30:00", version=5),
        idem="late-cnt-mass-" + RUN)
    check("C2. 会诊申请成功", r.get("code") == "OK", r)
    req_no = r.get("data")
    st, p = call("GET", "/cnt/requests?pageSize=100", admin)
    row2 = next((x for x in (p.get("data") or {}).get("list", [])
                 if x.get("reqNo") == req_no), None)
    check("C3. 会诊主键/接受与意见字段不可伪造", row2 is not None
          and row2.get("id") != 987654322 and row2.get("consultDoctorId") is None
          and row2.get("acceptTime") is None and row2.get("opinion") is None
          and row2.get("opinionTime") is None and row2.get("version") == 0, row2)

    st, r = call("POST", "/cnt/requests/%d/accept" % row2["id"], admin,
                 idem="late-cnt-ac-" + RUN)
    check("C4. 会诊接受成功", r.get("code") == "OK", r)
    st, r = call("POST", "/cnt/requests/%d/complete?opinion=%s"
                 % (row2["id"], "x" * 1100), admin, idem="late-cnt-cp1-" + RUN)
    check("C5. 超长会诊意见被拒", st == 400 and r.get("code") == "A0001", r)
    st, r = call("POST", "/cnt/requests/%d/complete?opinion=%s"
                 % (row2["id"], urllib.parse.quote("同意会诊")), admin,
                 idem="late-cnt-cp2-" + RUN)
    check("C6. 正常会诊意见仍可提交", r.get("code") == "OK", r)

    # ================= D. 医生姓名回填（bas_doctor.id ≠ sys_user.id） =================
    st, docs = call("GET", "/basedata/doctors?status=1", admin)
    dmap = {d["id"]: d.get("doctorName") for d in (docs.get("data") or [])}
    st, r = call("GET", "/doc/orders?pageSize=50", admin)
    orders = (r.get("data") or {}).get("list") or []
    bad_doc = [o["id"] for o in orders
               if o.get("doctorId") in dmap and o.get("doctorName") != dmap[o["doctorId"]]]
    check("D1. 住院医嘱开单医生姓名正确", bool(orders) and not bad_doc, bad_doc)
    st, r = call("GET", "/lis/requests?pageSize=50", admin)
    lis_rows = (r.get("data") or {}).get("list") or []
    bad_lis = [x["id"] for x in lis_rows
               if x.get("doctorId") in dmap and x.get("doctorName") != dmap[x["doctorId"]]]
    check("D2. 检验申请开单医生姓名正确", bool(lis_rows) and not bad_lis, bad_lis)

    # ================= B. 医保专员账单详情 =================
    st, b = call("GET", "/billing/bills?pageSize=5", admin)
    bills = (b.get("data") or {}).get("list") or []
    check("B0. 存在可测账单", bool(bills), b)
    st, roles = call("GET", "/system/roles", admin)
    role_id = next(x["id"] for x in roles["data"] if x["roleCode"] == "MEDCLERK")
    username = "late" + RUN
    st, r = call("POST", "/system/users", admin, {
        "username": username, "realName": "Late MedClerk", "password": PASSWORD,
        "roleIds": [role_id]}, idem="late-med-user-" + RUN)
    assert r.get("code") == "OK", r
    med = login(username)
    st, r = call("GET", "/billing/bills?pageSize=5", med)
    check("B1. 医保专员可列全部账单", r.get("code") == "OK"
          and bool((r.get("data") or {}).get("list")), r)
    st, r = call("GET", "/billing/bills/%d" % bills[0]["id"], med)
    check("B2. 医保专员可查看账单详情", r.get("code") == "OK", r)
    st, r = call("GET", "/billing/bills/%d" % bills[0]["id"], admin)
    check("B3. 管理员账单详情不受影响", r.get("code") == "OK", r)

    failed = [x for x in RESULTS if not x[1]]
    print("\n===== late audit regression: %d/%d passed, %d failed =====" %
          (len(RESULTS) - len(failed), len(RESULTS), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
