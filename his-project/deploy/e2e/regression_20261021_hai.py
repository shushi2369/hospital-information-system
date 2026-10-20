#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-10-21 凌晨院感上报裸实体入参回归：必填校验 + 主键/确认字段白名单。"""
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
    name = "院感回归" + RUN
    st, r = call("POST", "/patients", admin, {
        "name": name, "gender": 1, "birthDate": "1990-01-01",
        "idCardNo": "34010519900101" + RUN[-4:], "phone": "139" + RUN[-8:]},
        idem="hai-pt-" + RUN)
    assert r.get("code") == "OK", r
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote(name), admin)
    pid = pl["data"]["list"][0]["id"]

    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    if not beds["data"]:
        call("POST", "/inp/beds", admin, {"wardId": 1, "bedNo": "HR-" + RUN, "chargeItemId": 10},
             idem="hai-bed-" + RUN)
        st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = beds["data"][0]
    st, r = call("POST", "/inp/admissions", admin, {
        "patientId": pid, "deptId": 1, "wardId": bed["wardId"], "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "院感回归",
        "depositAmount": 1, "payMethod": 1}, idem="hai-adm-" + RUN)
    assert r.get("code") == "OK", r
    adm = r["data"]["id"]

    base = {"admissionId": adm, "patientId": pid, "infectionType": 1, "infectionSite": "肺部"}

    st, r = call("POST", "/pub/hai", admin,
                 {"admissionId": adm, "patientId": pid, "infectionSite": "肺部"},
                 idem="hai-miss-type-" + RUN)
    check("H1. 缺感染类型被服务端校验拦截", st == 400 and r.get("code") == "A0001"
          and "感染类型" in (r.get("message") or ""), r)

    st, r = call("POST", "/pub/hai", admin,
                 {"admissionId": adm, "patientId": pid, "infectionType": 1, "infectionSite": "  "},
                 idem="hai-blank-site-" + RUN)
    check("H2. 空感染部位被拦截", st == 400 and r.get("code") == "A0001"
          and "感染部位" in (r.get("message") or ""), r)

    forged = dict(base)
    forged.update({"id": 987654323, "reporterId": 999,
                   "confirmNote": "已确认-伪造", "confirmTime": "2020-01-01T00:00:00",
                   "version": 9, "status": 30})
    st, r = call("POST", "/pub/hai", admin, forged, idem="hai-forge-" + RUN)
    check("H3. 合法上报成功", r.get("code") == "OK", r)

    st, lst = call("GET", "/pub/hai?status=10&pageSize=100", admin)
    row = next((x for x in lst["data"]["list"] if x.get("patientId") == pid), None)
    check("H4. 主键由服务端生成", row is not None and row.get("id") != 987654323, row)
    check("H5. 确认字段不可伪造", row is not None and row.get("confirmNote") is None
          and row.get("confirmTime") is None, row)
    check("H6. 报告人/状态/版本由服务端重置",
          row is not None and row.get("reporterId") != 999 and row.get("status") == 10
          and row.get("version") == 0, row)
    check("H7. 病例号保持 GR 前缀", row is not None and str(row.get("caseNo", "")).startswith("GR"), row)

    # ============ CDSS 规则裸实体入参 ============
    st, r = call("POST", "/cdss/rules", admin,
                 {"ruleCode": "CR" + RUN, "ruleType": 2, "refAId": 10},
                 idem="cdss-miss-msg-" + RUN)
    check("C1. 缺提示文案被服务端校验拦截", st == 400 and r.get("code") == "A0001"
          and "文案" in (r.get("message") or ""), r)

    code = "CR" + RUN
    forged_rule = {"id": 987654324, "ruleCode": code, "ruleType": 2, "refAId": 10,
                   "message": "回归提示", "status": 0, "createdBy": 999, "version": 7}
    st, r = call("POST", "/cdss/rules", admin, forged_rule, idem="cdss-forge-" + RUN)
    check("C2. 合法规则创建成功", r.get("code") == "OK", r)
    st, rl = call("GET", "/cdss/rules?ruleType=2&pageSize=100", admin)
    rule = next((x for x in rl["data"]["list"] if x.get("ruleCode") == code), None)
    check("C3. 规则主键/状态/审计列由服务端重置",
          rule is not None and rule.get("id") != 987654324 and rule.get("status") == 1
          and rule.get("version") == 0 and rule.get("createdBy") != 999, rule)

    # ============ 危急值列表医生数据范围（W-01 "待我确认"） ============
    chen = login("dr.chen")
    st, r = call("GET", "/alerts?pageNum=1&pageSize=200", chen)
    check("W1. 无管床/开单的医生看不到他人危急值",
          st == 200 and r.get("data") is not None and not r["data"]["list"], r)
    li = login("dr.li")
    st, r = call("GET", "/alerts?pageNum=1&pageSize=200", li)
    check("W2. 责任医生仍可看到本人危急值",
          st == 200 and len((r.get("data") or {}).get("list") or []) > 0, r)

    failed = [x for x in RESULTS if not x[1]]
    print("\nhai audit regression: %d/%d passed, %d failed"
          % (len(RESULTS) - len(failed), len(RESULTS), len(failed)))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
