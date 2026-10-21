#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
2026-10-21 CDSS/KPI/体检回归：
  A. CDSS 规则局部更新合并后校验（自配对死规则、空文案、超长文案、年龄区间反转）
  B. KPI 时间过滤上界生效 + 区间反转拒绝（含 CSV 导出）
  C. 体检套餐嵌套项目校验（负价、空收费项目编码）
前置：后端已部署本次修复版本（localhost:8080）。
"""
import json
import random
import sys
import time
import urllib.error
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
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:240]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败 %s: %s" % (username, r)
    return r["data"]["token"]


def find_rule(token, code):
    st, r = call("GET", "/cdss/rules?pageNum=1&pageSize=100", token)
    return next((x for x in r["data"]["list"] if x["ruleCode"] == code), None)


def find_package(token, name):
    st, r = call("GET", "/pe/packages?pageNum=1&pageSize=100", token)
    return next((x for x in r["data"]["list"] if x["name"] == name), None)


def main():
    admin = login("admin")

    # ===== A. CDSS 规则更新合并后校验 =====
    code = "RCCDSS" + RUN
    st, r = call("POST", "/cdss/rules", admin, {
        "ruleCode": code, "ruleType": 1, "refAId": 1, "refBId": 2,
        "level": 1, "message": "配伍回归", "status": 1}, idem="rc-cdss-c-" + RUN)
    rule = find_rule(admin, code)
    check("A1. 临时配伍规则创建", r.get("code") == "OK" and rule is not None, r)
    if rule:
        rid = rule["id"]
        st, r = call("PUT", "/cdss/rules/%d" % rid, admin, {"refAId": 2},
                     idem="rc-cdss-u1-" + RUN)
        after = find_rule(admin, code)
        check("A2. 局部更新不得把配伍规则改为自配对死规则",
              r.get("code") != "OK" and after["refAId"] == 1 and after["refBId"] == 2, (r, after))

        st, r = call("PUT", "/cdss/rules/%d" % rid, admin, {"message": "   "},
                     idem="rc-cdss-u2-" + RUN)
        check("A3. 更新不得写入空白提示文案", r.get("code") != "OK", r)

        st, r = call("PUT", "/cdss/rules/%d" % rid, admin, {"message": "x" * 300},
                     idem="rc-cdss-u3-" + RUN)
        check("A4. 更新超长文案返回明确业务校验错误",
              r.get("code") == "A0001", r)

        st, r = call("PUT", "/cdss/rules/%d" % rid, admin, {"message": "配伍回归-改"},
                     idem="rc-cdss-u4-" + RUN)
        after = find_rule(admin, code)
        check("A5. 合法局部更新仍生效（null 字段跳过语义不变）",
              r.get("code") == "OK" and after["message"] == "配伍回归-改"
              and after["refAId"] == 1, (r, after))

    code_age = "RCAGE" + RUN
    st, r = call("POST", "/cdss/rules", admin, {
        "ruleCode": code_age, "ruleType": 3, "refAId": 1, "refBId": 5,
        "ageMin": 10, "ageMax": 5, "level": 1, "message": "年龄区间回归", "status": 1},
        idem="rc-cdss-age-" + RUN)
    check("A6. 年龄下限大于上限的规则被拒", r.get("code") != "OK", r)

    st, r = call("POST", "/cdss/rules", admin, {
        "ruleCode": "RCALG" + RUN, "ruleType": 4, "refAId": 1,
        "level": 1, "message": "过敏回归", "status": 1}, idem="rc-cdss-alg-" + RUN)
    check("A7. 过敏规则缺关键词被拒（原有校验不回归）", r.get("code") != "OK", r)

    if rule:
        call("PUT", "/cdss/rules/%d" % rule["id"], admin, {"status": 0},
             idem="rc-cdss-off-" + RUN)

    # ===== B. KPI 时间过滤 =====
    st, r = call("GET", "/report/kpi/workload?to=2000-01-01T00:00:00", admin)
    check("B1. 仅传 to 时手术台次上界生效",
          r.get("code") == "OK" and r["data"]["surgeriesCompleted"] == 0, r)
    st, r = call("GET", "/report/kpi/workload?from=2000-01-01T00:00:00&to=2000-01-02T00:00:00", admin)
    check("B2. 历史空区间手术台次为 0",
          r.get("code") == "OK" and r["data"]["surgeriesCompleted"] == 0, r)
    st, r1 = call("GET", "/report/kpi/workload", admin)
    st, r2 = call("GET", "/report/kpi/workload?to=2099-01-01T00:00:00", admin)
    check("B3. 仅上限且覆盖今天时与全量一致",
          r1.get("code") == "OK" and r1["data"] == r2["data"], (r1, r2))

    st, r = call("GET", "/report/kpi/workload?from=2027-01-01T00:00:00&to=2026-01-01T00:00:00", admin)
    check("B4. workload 区间反转被拒", r.get("code") == "A0001", r)
    st, r = call("GET", "/report/kpi/efficiency?from=2027-01-01T00:00:00&to=2026-01-01T00:00:00", admin)
    check("B5. efficiency 区间反转被拒", r.get("code") == "A0001", r)
    st, r = call("GET", "/report/kpi/safety?from=2027-01-01T00:00:00&to=2026-01-01T00:00:00", admin)
    check("B6. safety 区间反转被拒", r.get("code") == "A0001", r)
    st, r = call("GET", "/report/kpi/export?from=2027-01-01T00:00:00&to=2026-01-01T00:00:00", admin)
    check("B7. KPI 导出区间反转被拒", r.get("code") == "A0001", r)

    # ===== C. 体检套餐嵌套项目校验 =====
    name = "回归体检套餐" + RUN
    st, r = call("POST", "/pe/packages", admin, {
        "name": name, "price": 100,
        "items": [{"chargeItemId": 1, "itemName": "负价项", "price": -5}]},
        idem="rc-pe-neg-" + RUN)
    check("C1. 套餐项目负价被拒", r.get("code") != "OK", r)
    st, r = call("POST", "/pe/packages", admin, {
        "name": name, "price": 100,
        "items": [{"chargeItemId": None, "itemName": "无编码项", "price": 10}]},
        idem="rc-pe-null-" + RUN)
    check("C2. 套餐项目缺收费项目编码被拒", r.get("code") != "OK", r)

    st, r = call("POST", "/pe/packages", admin, {
        "name": name, "price": 100,
        "items": [{"chargeItemId": 1, "itemName": "普通门诊诊查费", "price": 10}]},
        idem="rc-pe-ok-" + RUN)
    pkg = find_package(admin, name)
    check("C3. 合法套餐创建", r.get("code") == "OK" and pkg is not None, r)
    if pkg:
        st, r = call("PUT", "/pe/packages/%d" % pkg["id"], admin, {
            "name": name, "price": 100,
            "items": [{"chargeItemId": 1, "itemName": "普通门诊诊查费", "price": -1}]},
            idem="rc-pe-updneg-" + RUN)
        check("C4. 套餐更新项目负价被拒", r.get("code") != "OK", r)
        st, r = call("PUT", "/pe/packages/%d" % pkg["id"], admin, {
            "name": name + "改", "price": 120,
            "items": [{"chargeItemId": 1, "itemName": "普通门诊诊查费", "price": 20}]},
            idem="rc-pe-updok-" + RUN)
        check("C5. 合法套餐更新", r.get("code") == "OK", r)

    failed = [x for x in RESULTS if not x[1]]
    print("\n===== cdss/kpi/pe regression: %d/%d passed, %d failed =====" %
          (len(RESULTS) - len(failed), len(RESULTS), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
