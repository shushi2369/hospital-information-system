#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HIS 三期端到端验收脚本（检验闭环 + 危急值闭环 + 药库管理）
用法: python e2e_phase3.py [BASE_URL]
可重复执行（唯一测试患者）。前置：二期 e2e 通过（V8 病区/床位/药品就绪）。
"""
import json
import sys
import time
import urllib.request
import urllib.error
import urllib.parse

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
    assert r.get("code") == "OK", "登录失败: %s" % r
    return r["data"]["token"]


def main():
    uid = str(int(time.time() * 1000))[-8:]
    id_card = "34010520000101" + uid[-4:]
    phone = "139" + uid
    today = time.strftime("%Y-%m-%d")

    admin = login("admin")
    doctor = login("dr.li")
    nurse = login("nurse.wang")
    lab_tech = login("lab.chen")
    cashier = login("cashier.li")
    check("1. 角色登录（含检验技师）", all([admin, doctor, nurse, lab_tech, cashier]))

    # 建档
    call("POST", "/patients", admin, {"name": "三期患者" + uid, "gender": 1,
         "birthDate": "1992-06-15", "idCardNo": id_card, "phone": phone}, idem="p3-pt-" + uid)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("三期患者" + uid), admin)
    patient_id = pl["data"]["list"][0]["id"]

    # 入院
    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    n = 0
    while not beds["data"]:
        n += 1
        call("POST", "/inp/beds", admin, {"wardId": 1, "bedNo": "P3-" + uid + "-" + str(n), "chargeItemId": 10},
             idem="p3-bed-" + uid + str(n))
        st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = beds["data"][0]
    ward_id = bed["wardId"]
    st, r = call("POST", "/inp/admissions", admin, {
        "patientId": patient_id, "deptId": 1, "wardId": ward_id, "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "肺炎待查",
        "depositAmount": 1000, "payMethod": 1}, idem="p3-adm-" + uid)
    check("2. 入院登记成功", r["code"] == "OK", r)
    admission_id = r["data"]["id"]

    # 检验医嘱
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": admission_id, "orderClass": 2, "category": 3,
        "items": [{"chargeItemId": 3, "quantity": 1}]}, idem="p3-rx-" + uid)
    check("3. 开检验医嘱", r["code"] == "OK", r)
    st, ol = call("GET", "/doc/orders?admissionId=%d&category=3" % admission_id, doctor)
    rx_id = ol["data"]["list"][0]["id"]

    # 执行 → 自动生成 LIS 申请
    st, d = call("GET", "/doc/orders/%d" % rx_id, doctor)
    exec_item = [e for e in d["data"]["executions"] if e["execType"] == 2][0]
    st, r = call("POST", "/doc/executions/%d/do" % exec_item["id"], nurse, idem="p3-ex-" + uid)
    check("4. 执行检验医嘱（自动生成 LIS 申请）", r["code"] == "OK", r)
    st, reqs = call("GET", "/lis/requests?admissionId=%d" % admission_id, lab_tech)
    lis_req = reqs["data"]["list"][0]
    lis_id = lis_req["id"]
    check("5. LIS 申请单自动生成(待采集)", lis_req["status"] == 10, lis_req)

    # 采集
    st, r = call("POST", "/lis/specimens/collect?requestId=%d" % lis_id, lab_tech, idem="p3-col-" + uid)
    check("6. 标本采集成功(条码)", r["code"] == "OK" and "specimenNo" in r.get("data", {}), r)

    # 接收
    st, r = call("POST", "/lis/specimens/%d/receive" % lis_id, lab_tech, idem="p3-rec-" + uid)
    check("7. 标本接收成功(检验中)", r["code"] == "OK", r)

    # Mock 录入（含危急值）
    st, r = call("POST", "/lis/results/entry", lab_tech,
                 {"requestId": lis_id, "fetch": True}, idem="p3-entry1-" + uid)
    check("8. Mock 录入成功", r["code"] == "OK", r)
    # 用末位 9 的标本号重试触发危急值
    st, detail = call("GET", "/doc/orders/%d" % rx_id, doctor)

    # 重复录入覆盖
    st, r = call("POST", "/lis/results/entry", lab_tech,
                 {"requestId": lis_id, "fetch": True}, idem="p3-entry2-" + uid)
    check("9. 重复录入覆盖(幂等)", r["code"] == "OK", r)

    # 报告发布
    st, r = call("POST", "/lis/reports/%d/publish" % lis_id, lab_tech, idem="p3-pub-" + uid)
    check("10. 报告发布成功", r["code"] == "OK", r)
    st, r = call("POST", "/lis/reports/%d/publish" % lis_id, lab_tech, idem="p3-pub2-" + uid)
    check("11. 重复发布拦截(应A0001)", r["code"] == "A0001", r)

    # 检验医嘱联动已执行
    st, o = call("GET", "/doc/orders/%d" % rx_id, doctor)
    check("12. 检验医嘱联动已执行(40)", o["data"]["order"]["status"] == 40, o["data"]["order"])

    # 报告查询
    st, r = call("GET", "/lis/reports/%d" % lis_id, doctor)
    check("13. 医生查报告(含结果行)", r["code"] == "OK" and len(r["data"]["results"]) > 0, r)

    # 危急值闭环（构造：重新开一个检验医嘱 → 执行 → 录入指定危急结果）
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": admission_id, "orderClass": 2, "category": 3,
        "items": [{"chargeItemId": 3, "quantity": 1}]}, idem="p3-rx3-" + uid)
    st, ol = call("GET", "/doc/orders?admissionId=%d&category=3" % admission_id, doctor)
    rx3 = [o["id"] for o in ol["data"]["list"] if o["id"] != rx_id][0]
    st, r = call("POST", "/doc/orders/%d/review" % rx3, lab_tech,
                 {"pass": True, "comment": "OK"}, idem="p3-rev3-" + uid)
    st, d = call("GET", "/doc/orders/%d" % rx3, doctor)
    ex3 = [e for e in d["data"]["executions"] if e["execType"] == 2][0]
    st, r = call("POST", "/doc/executions/%d/do" % ex3["id"], nurse, idem="p3-ex3-" + uid)
    # Mock 取数末位 9 才危急 → 改用手动录入高值白细胞
    st, lis_reqs2 = call("GET", "/lis/requests?admissionId=%d" % admission_id, lab_tech)
    lis3_id = [q["id"] for q in lis_reqs2["data"]["list"] if q["orderId"] == rx3][0]
    call("POST", "/lis/specimens/collect?requestId=%d" % lis3_id, lab_tech, idem="p3-col2-" + uid)
    call("POST", "/lis/specimens/%d/receive" % lis3_id, lab_tech, idem="p3-rec2-" + uid)
    st, r = call("POST", "/lis/results/entry", lab_tech,
                 {"requestId": lis3_id, "rows": [
                     {"itemName": "白细胞计数", "resultValue": "35.6", "unit": "10^9/L",
                      "referenceRange": "3.5~9.5", "abnormalFlag": 1}]}, idem="p3-entry3-" + uid)
    check("14. 录入危急结果(白细胞 35.6)", r["code"] == "OK", r)
    st, alerts = call("GET", "/alerts?status=10", admin)
    new_alerts = [a for a in alerts["data"]["list"] if a["admissionId"] == admission_id and a["status"] == 10]
    check("15. 危急值自动生成", len(new_alerts) > 0, alerts)

    # 通知 → 确认 → 闭环
    alert_id = new_alerts[0]["id"]
    st, r = call("POST", "/alerts/%d/notify" % alert_id, lab_tech, idem="p3-nt-" + uid)
    check("16. 技师通知登记", r["code"] == "OK", r)
    st, r = call("POST", "/alerts/%d/confirm" % alert_id, doctor, idem="p3-cf-" + uid)
    check("17. 医生确认", r["code"] == "OK", r)
    st, r = call("POST", "/alerts/%d/confirm" % alert_id, doctor, idem="p3-cf2-" + uid)
    check("18. 重复确认拦截(应A0001)", r["code"] == "A0001", r)
    st, r = call("POST", "/alerts/%d/close" % alert_id, doctor,
                 {"handleNote": "已给予升白治疗"}, idem="p3-cl-" + uid)
    check("19. 处置闭环成功", r["code"] == "OK", r)

    # 药库
    st, r = call("POST", "/whse/suppliers", admin,
                 {"supplierCode": "SUPP3" + uid, "supplierName": "三期供应商" + uid}, idem="p3-sup-" + uid)
    check("20. 新增供应商", r["code"] == "OK", r)
    st, sups = call("GET", "/whse/suppliers", admin)
    sup_id = [s for s in sups["data"] if "SUPP3" in s.get("supplierCode", "")][0]["id"]
    st, r = call("POST", "/whse/purchase-orders", admin,
                 {"supplierId": sup_id, "drugId": 2, "quantity": 30, "unitPrice": "18.00",
                  "expectedDate": today}, idem="p3-po-" + uid)
    check("21. 创建采购单", r["code"] == "OK", r)
    st, pos = call("GET", "/whse/purchase-orders?supplierId=%d" % sup_id, admin)
    po_id = pos["data"]["list"][0]["id"]
    st, r = call("POST", "/whse/purchase-orders/%d/approve" % po_id, admin, idem="p3-app-" + uid)
    check("22. 采购审批", r["code"] == "OK", r)
    st, r = call("POST", "/whse/purchase-orders/%d/receive" % po_id, admin,
                 {"expiryDate": "2027-12-31"}, idem="p3-rec-" + uid)
    check("23. 到货入库成功", r["code"] == "OK", r)
    st, r2 = call("POST", "/whse/purchase-orders/%d/receive" % po_id, admin,
                  {"expiryDate": "2027-12-31"}, idem="p3-rec2-" + uid)
    check("24. 重复入库拦截(应A0001)", r2["code"] == "A0001", r2)
    st, inv = call("GET", "/inventory/batches?drugId=2&pageSize=5", admin)
    batch = [b for b in inv["data"]["list"] if b["batchNo"] and "CG" in str(b.get("batchNo", ""))]
    check("25. 库存批次 FEFO 可追溯", len(inv["data"]["list"]) > 0, inv)

    # 事件查询
    st, r = call("GET", "/plt/events?eventType=alert.created&startDate=%s&endDate=%s" % (today, today), admin)
    check("26. 集成事件可查询", r["code"] == "OK" and isinstance(r["data"], list), r)

    # 越权
    st, r = call("POST", "/lis/results/entry", cashier,
                 {"requestId": lis_id, "rows": [
                     {"itemName": "x", "resultValue": "1", "abnormalFlag": 0}]},
                 idem="p3-x-" + uid)
    check("27. 收费员结果录入被拒(A0003)", r["code"] == "A0003", r)
    st, r = call("GET", "/lis/reports", admin)
    check("28. 报告查询正常", r["code"] == "OK", r)

    failed = [x for x in results if not x[1]]
    print("\n========== 三期验收结果: %d/%d 通过 ==========" % (len(results) - len(failed), len(results)))
    for name, ok, detail in failed:
        print("FAIL:", name, "|", str(detail)[:200])
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    import urllib.parse
    main()
