#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
四期三模块 e2e（e2e_phase4b.py）——pub/cnt/ae 此前仅有单测、无 API 级覆盖（三十二轮补齐）。
覆盖：①传染病卡自动报卡（诊断触发）→上报→审核→回执全链路 + 顺序类守卫（三十二轮教训）
     ②会诊申请→接受→完成 + 终态守卫 ③不良事件上报→派单→整改→关闭 + 枚举校验 + 终态守卫
     ④权限收敛（体检角色访问公卫/会诊/不良事件被拒）。
"""
import json
import sys
import time
import urllib.parse
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


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:130]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    return r["data"]["token"]


def main():
    uid = str(int(time.time() * 1000))[-8:]
    today = time.strftime("%Y-%m-%d")
    admin = login("admin")
    cashier = login("cashier.li")
    doctor = login("dr.li")
    pe_nurse = login("pe.nurse")

    # ================= A. 传染病卡全链路（诊断触发自动报卡） =================
    st, r = call("POST", "/patients", cashier, {"name": "公卫链路患者" + uid, "gender": 1,
                 "birthDate": "1991-03-04", "idCardNo": "34010419910304" + uid[-4:],
                 "phone": "137" + uid}, idem="p4b-pt-" + uid)
    check("1. 建档成功", r["code"] == "OK", r)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("公卫链路患者" + uid), cashier)
    patient_id = pl["data"]["list"][0]["id"]
    st, r = call("POST", "/registrations", cashier, {"patientId": patient_id, "doctorId": 2,
                 "regDate": today, "period": 2, "regType": 1}, idem="p4b-reg-" + uid)
    check("2. 挂号成功", r["code"] == "OK", r)
    st, rl = call("GET", "/registrations?patientId=%d&regDate=%s" % (patient_id, today), cashier)
    reg_id = rl["data"]["list"][0]["id"]
    st, r = call("POST", "/clinic/visits/%d/start" % reg_id, doctor, idem="p4b-v-" + uid)
    visit_id = r.get("data")
    check("3. 接诊成功", r["code"] == "OK" and isinstance(visit_id, int), r)
    st, r = call("POST", "/clinic/visits/%d/diagnoses" % visit_id, doctor,
                 {"diagnosisCode": "A15.0", "diagnosisName": "继发性肺结核", "diagnosisType": 1},
                 idem="p4b-dg-" + uid)
    check("4. 新增诊断（含字典病名）", r["code"] == "OK", r)
    st, cl = call("GET", "/pub/cards?status=10&pageNum=1&pageSize=50", admin)
    card = next((c for c in cl["data"]["list"]
                 if c["patientId"] == patient_id and c["diseaseName"] == "肺结核"), None)
    check("5. 诊断触发自动报卡（status=10）", card is not None, cl["data"].get("total"))
    st, r = call("POST", "/pub/cards/%d/approve" % card["id"], admin, idem="p4b-ap0-" + uid)
    check("6. 守卫：未上报不能审核（三十二轮顺序教训）", r["code"] != "OK" and "未上报" in str(r.get("message", "")), r)
    st, r = call("POST", "/pub/cards/%d/report" % card["id"], admin, idem="p4b-rp-" + uid)
    check("7. 上报登记（10→20）", r["code"] == "OK", r)
    st, r = call("POST", "/pub/cards/%d/approve" % card["id"], admin, idem="p4b-ap-" + uid)
    check("8. 审核（20→30）", r["code"] == "OK", r)
    st, r = call("POST", "/pub/cards/%d/receipt" % card["id"], admin,
                 {"receiptNo": "JK-P4B-" + uid}, idem="p4b-rc-" + uid)
    check("9. 回执登记（30→40）", r["code"] == "OK", r)
    st, cl = call("GET", "/pub/cards?status=40&pageNum=1&pageSize=50", admin)
    done = next((c for c in cl["data"]["list"] if c["id"] == card["id"]), None)
    check("10. 终态 40 + 回执号落显", done is not None and done.get("receiptNo") == "JK-P4B-" + uid, done)
    st, r = call("POST", "/pub/cards/%d/report" % card["id"], admin, idem="p4b-rp2-" + uid)
    check("11. 守卫：终态不可再上报", r["code"] != "OK", r)

    # ================= B. 会诊 =================
    st, al = call("GET", "/inp/admissions?status=10&pageNum=1&pageSize=5", admin)
    adm = al["data"]["list"][0]
    st, r = call("POST", "/cnt/requests", admin, {"admissionId": adm["id"],
                 "patientId": adm["patientId"], "deptId": 1, "consultDoctorId": 2,
                 "urgent": 1, "reason": "p4b 会诊链路"}, idem="p4b-c1-" + uid)
    check("12. 会诊申请（10）", r["code"] == "OK" and str(r.get("data", "")).startswith("HZ"), r)
    st, cpage = call("GET", "/cnt/requests?status=10&pageNum=1&pageSize=50", admin)
    creq = next((x for x in cpage["data"]["list"] if x["reason"] == "p4b 会诊链路"), None)
    check("13. 列表可见（待接受）", creq is not None, cpage["data"].get("total"))
    st, r = call("POST", "/cnt/requests/%d/accept" % creq["id"], admin, idem="p4b-c2-" + uid)
    check("14. 接受（10→20）", r["code"] == "OK", r)
    st, r = call("POST", "/cnt/requests/%d/complete?opinion=%s"
                 % (creq["id"], urllib.parse.quote("p4b 会诊意见")), admin, idem="p4b-c3-" + uid)
    check("15. 完成（20→30）", r["code"] == "OK", r)
    st, cpage2 = call("GET", "/cnt/requests?status=30&pageNum=1&pageSize=50", admin)
    cdone = next((x for x in cpage2["data"]["list"] if x["id"] == creq["id"]), None)
    check("16. 终态 30 + 意见落显", cdone is not None and cdone.get("opinion"), cdone)
    st, r = call("POST", "/cnt/requests/%d/accept" % creq["id"], admin, idem="p4b-c4-" + uid)
    check("17. 守卫：已完成不可再接受", r["code"] != "OK", r)

    # ================= C. 不良事件 =================
    st, r = call("POST", "/ae", admin, {"eventType": 9, "severity": 2, "departmentId": 1,
                 "eventTime": today + "T10:00:00", "description": "p4b 枚举越界"}, idem="p4b-a0-" + uid)
    check("18. 守卫：eventType 取值 1~7", r["code"] != "OK" and "1~7" in str(r.get("message", "")), r)
    st, r = call("POST", "/ae", admin, {"eventType": 1, "severity": 2, "departmentId": 1,
                 "eventTime": today + "T10:00:00", "description": "p4b 不良事件链路"}, idem="p4b-a1-" + uid)
    check("19. 上报（10）", r["code"] == "OK" and str(r.get("data", "")).startswith("AE"), r)
    st, apage = call("GET", "/ae?status=10&pageNum=1&pageSize=50", admin)
    aevt = next((x for x in apage["data"]["list"] if x["description"] == "p4b 不良事件链路"), None)
    check("20. 列表可见（待派单）", aevt is not None, apage["data"].get("total"))
    st, r = call("POST", "/ae/%d/assign" % aevt["id"], admin, idem="p4b-a2-" + uid)
    check("21. 派单（10→20）", r["code"] == "OK", r)
    st, r = call("POST", "/ae/%d/rectify?handlerNote=%s"
                 % (aevt["id"], urllib.parse.quote("p4b 整改措施")), admin, idem="p4b-a3-" + uid)
    check("22. 整改（20→30）", r["code"] == "OK", r)
    st, r = call("POST", "/ae/%d/close" % aevt["id"], admin, idem="p4b-a4-" + uid)
    check("23. 关闭（30→40）", r["code"] == "OK", r)
    st, r = call("POST", "/ae/%d/assign" % aevt["id"], admin, idem="p4b-a5-" + uid)
    check("24. 守卫：已关闭不可再派单", r["code"] != "OK", r)

    # ================= D. 权限收敛 =================
    st, r = call("GET", "/pub/cards", pe_nurse)
    check("25. 体检角色查公卫卡被拒", r["code"] != "OK", r)
    st, r = call("GET", "/cnt/requests", pe_nurse)
    check("26. 体检角色查会诊被拒", r["code"] != "OK", r)
    st, r = call("GET", "/ae", pe_nurse)
    check("27. 体检角色查不良事件被拒", r["code"] != "OK", r)

    failed = [n for n, ok, _ in results if not ok]
    print("\n===== 四期三模块 e2e 结果: %d/%d 通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:")
        for name in failed:
            print("  - " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
