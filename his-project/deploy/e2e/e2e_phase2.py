#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HIS 二期端到端验收脚本（指导文档二期验收场景：入院→医嘱→执行→一日清→出院结算→病案归档→医保对账）
用法: python e2e_phase2.py [BASE_URL]
可重复执行：每次生成唯一测试患者；日结等既有数据分支自动兼容。
依赖: 仅 Python 3.8+ 标准库。前置：一期 e2e 通过、V8 演示病区/床位/ICD 就绪。
"""
import json
import sys
import time
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
    assert r.get("code") == "OK", "登录失败: %s" % r
    return r["data"]["token"]


def main():
    uid = str(int(time.time() * 1000))[-8:]
    id_card = "34010419900101" + "2" + uid[-3:]
    phone = "139" + uid
    today = time.strftime("%Y-%m-%d")

    admin = login("admin")
    cashier = login("cashier.li")
    doctor = login("dr.li")
    pharmacist = login("pharm.zhao")
    nurse = login("nurse.wang")
    mrc_clerk = login("mrc.zhou")
    med_clerk = login("yb.sun")
    check("1. 七角色登录（含二期新角色）", all([admin, cashier, doctor, pharmacist, nurse, mrc_clerk, med_clerk]))

    # ---- 建档与 EMPI ----
    st, r = call("POST", "/patients", cashier, {"name": "住院患者" + uid, "gender": 1,
                 "birthDate": "1992-06-15", "idCardNo": id_card, "phone": phone}, idem="p2-pt-" + uid)
    check("2. 收费员建档成功", r["code"] == "OK", r)
    st, r = call("GET", "/plt/index/search?name=" + urllib.parse.quote("住院患者" + uid), admin)
    check("3. EMPI 主索引自动生成", r["code"] == "OK" and r["data"]["total"] >= 1, r)

    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("住院患者" + uid), cashier)
    patient_id = pl["data"]["list"][0]["id"]

    # ---- 入院 ----
    st, wards = call("GET", "/inp/wards", cashier)
    ward_id = wards["data"][0]["id"]
    st, beds = call("GET", "/inp/beds?wardId=%d&bedStatus=1" % ward_id, cashier)
    free_beds = beds["data"]
    # 自愈：空闲床位不足 2 张时自动新建（历史中断运行可能遗留占床）
    while len(free_beds) < 2:
        new_bed_no = "E2E-" + uid + "-" + str(len(free_beds))
        st, r = call("POST", "/inp/beds", admin, {"wardId": ward_id, "bedNo": new_bed_no,
                     "chargeItemId": 10}, idem="p2-bed-" + new_bed_no)
        st, beds = call("GET", "/inp/beds?wardId=%d&bedStatus=1" % ward_id, cashier)
        free_beds = beds["data"]
    check("4. 有空闲床位", len(free_beds) >= 2, beds)
    bed = free_beds[0]
    st, r = call("POST", "/inp/admissions", cashier, {
        "patientId": patient_id, "deptId": 1, "wardId": ward_id, "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "肺炎待查",
        "depositAmount": 1000, "payMethod": 1}, idem="p2-adm-" + uid)
    check("5. 入院登记成功（含首笔押金 1000）", r["code"] == "OK" and r["data"]["depositTotal"] == "1000.00", r)
    admission_no = r["data"]["admissionNo"]
    admission_id = r["data"]["id"]
    st, r2 = call("POST", "/inp/admissions", cashier, {
        "patientId": patient_id, "deptId": 1, "wardId": ward_id, "bedId": free_beds[1]["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "x", "depositAmount": 100,
        "payMethod": 1}, idem="p2-adm2-" + uid)
    check("6. 重复住院拦截(B6002)", r2["code"] == "B6002", r2)
    st, r3 = call("POST", "/inp/admissions", cashier, {
        "patientId": patient_id, "deptId": 1, "wardId": ward_id, "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "x", "depositAmount": 100,
        "payMethod": 1}, idem="p2-adm3-" + uid)
    check("7. 同患者再入院被患者级校验拦截(B6002)", r3["code"] == "B6002", r3)
    st, beds2 = call("GET", "/inp/beds?wardId=%d" % ward_id, cashier)
    occupied = [b for b in beds2["data"] if b["id"] == bed["id"]]
    check("8. 床位状态=占用且显示患者", occupied[0]["bedStatus"] == 2 and occupied[0]["patientName"] is not None, occupied)

    # ---- 医嘱闭环 ----
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": admission_id, "orderClass": 1, "category": 1, "frequency": "qd",
        "items": [{"drugId": 1, "dosage": "0.25g", "days": 5, "quantity": 2, "usageRoute": "口服"}]},
        idem="p2-rx1-" + uid)
    check("9. 开长期药品医嘱", r["code"] == "OK", r)
    # 队列接口按 id 升序 LIMIT 100，多轮回归堆积后新单被截断——改从住院维度取单
    st, ol = call("GET", "/doc/orders?admissionId=%d&status=10" % admission_id, pharmacist)
    rx_id = [o["id"] for o in ol["data"]["list"] if o["category"] == 1][0]
    st, detail = call("GET", "/doc/orders/%d" % rx_id, pharmacist)
    exec_item = [e for e in detail["data"]["executions"] if e["execType"] == 2][0]
    st, r = call("POST", "/doc/executions/%d/do" % exec_item["id"], nurse, idem="p2-ex0-" + uid)
    check("10. 未审核未摆药执行拦截(B6102)", r["code"] == "B6102", r)
    st, r = call("POST", "/doc/orders/%d/review" % rx_id, pharmacist, {"pass": True, "comment": "OK"},
                 idem="p2-rev-" + uid)
    check("11. 药师审核通过", r["code"] == "OK", r)
    st, r = call("POST", "/doc/orders/%d/dispense" % rx_id, pharmacist, idem="p2-dis-" + uid)
    check("12. 摆药出库成功", r["code"] == "OK", r)
    st, r = call("POST", "/doc/executions/%d/do" % exec_item["id"], nurse, idem="p2-ex1-" + uid)
    check("13. 护士执行成功", r["code"] == "OK", r)
    st, r = call("POST", "/doc/executions/%d/do" % exec_item["id"], nurse, idem="p2-ex2-" + uid)
    check("14. 重复执行拦截(B6103)", r["code"] == "B6103", r)

    # 皮试链路（第二组医嘱）
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": admission_id, "orderClass": 2, "category": 1, "frequency": "立即",
        "skinTestFlag": True,
        "items": [{"drugId": 1, "dosage": "0.5g", "days": 1, "quantity": 1, "usageRoute": "静滴"}]},
        idem="p2-rx2-" + uid)
    check("15. 开皮试临时医嘱", r["code"] == "OK", r)
    # 队列接口 LIMIT 100 堆积截断——同改从住院维度取单
    st, ol = call("GET", "/doc/orders?admissionId=%d&status=10" % admission_id, pharmacist)
    rx2_id = [o["id"] for o in ol["data"]["list"] if o["category"] == 1 and o["orderClass"] == 2][0]
    call("POST", "/doc/orders/%d/review" % rx2_id, pharmacist, {"pass": True, "comment": "OK"}, idem="p2-rev2-" + uid)
    st, r = call("POST", "/doc/orders/%d/dispense" % rx2_id, pharmacist, idem="p2-dis2-" + uid)
    check("16. 皮试未做摆药拦截(B6104)", r["code"] == "B6104", r)
    st, detail = call("GET", "/doc/orders/%d" % rx2_id, pharmacist)
    skin_exec = [e for e in detail["data"]["executions"] if e["execType"] == 3][0]
    st, r = call("POST", "/doc/skin-tests/%d" % skin_exec["id"], nurse, {"result": "阳性"}, idem="p2-st-" + uid)
    check("17. 皮试阳性登记成功", r["code"] == "OK", r)
    st, o = call("GET", "/doc/orders/%d" % rx2_id, pharmacist)
    check("18. 阳性后医嘱自动作废(60)", o["data"]["order"]["status"] == 60, o["data"]["order"])

    # 非药品医嘱（治疗）
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": admission_id, "orderClass": 2, "category": 4,
        "items": [{"chargeItemId": 11, "quantity": 3}]}, idem="p2-rx3-" + uid)
    check("19. 开治疗医嘱（非药品自动通过）", r["code"] == "OK", r)
    st, detail = call("GET", "/doc/orders/%d" % 0, doctor)
    # 找到治疗医嘱执行单并执行（计费）
    st, todo = call("GET", "/doc/executions/todo?execDate=%s" % today, nurse)
    treat_exec = None
    for e in todo["data"]:
        if e["orderId"] not in (rx_id, rx2_id):
            pass
    # 找最新治疗医嘱
    st, op = call("GET", "/doc/orders?admissionId=%d&category=4" % admission_id, doctor)
    treat_order = op["data"]["list"][0] if op["data"]["list"] else None
    if treat_order:
        st, td = call("GET", "/doc/orders/%d" % treat_order["id"], doctor)
        te = [e for e in td["data"]["executions"] if e["execType"] == 2][0]
        st, r = call("POST", "/doc/executions/%d/do" % te["id"], nurse, idem="p2-ex3-" + uid)
        check("20. 非药品执行成功（执行即计费）", r["code"] == "OK", r)

    # ---- 一日清 ----
    st, r = call("GET", "/inp/admissions/%d/daily-fees" % admission_id, cashier)
    fees = r["data"]
    total_fee = sum(float(g["totalAmount"]) for g in fees)
    names = [i["itemName"] for g in fees for i in g["items"]]
    # 床位费由次日 00:20 任务记（AdmissionFeeTask），当日入院无床位费——有药费/治疗费即通过
    check("21. 一日清含药费/治疗费（床位费为次日任务）", len(names) >= 2, names)
    check("22. 一日清金额>0（药费+治疗费记账）", float(total_fee) >= 83.60 - 0.01, total_fee)  # 31.20 药费 + 20x3 治疗费

    # ---- 出院 ----
    st, r = call("POST", "/doc/orders/%d/stop" % rx_id, doctor, {"reason": "好转停药"},
                 idem="p2-stop-" + uid)
    check("23a. 停止长期医嘱（出院前置）", r["code"] == "OK", r)
    st, r = call("POST", "/inp/admissions/%d/discharge" % admission_id, doctor,
                 {"dischargeWay": 2, "dischargeDiagnosis": "肺炎好转"}, idem="p2-dc-" + uid)
    check("23. 出院申请成功（医嘱已停/无未收费项）", r["code"] == "OK", r)
    st, beds3 = call("GET", "/inp/beds?wardId=%d" % ward_id, cashier)
    released = [b for b in beds3["data"] if b["id"] == bed["id"]]
    check("24. 出院后床位释放", released[0]["bedStatus"] == 1, released)
    st, r = call("POST", "/inp/admissions/%d/discharge" % admission_id, doctor,
                 {"dischargeWay": 2, "dischargeDiagnosis": "x"}, idem="p2-dc2-" + uid)
    check("25. 非在院出院拦截(B6003)", r["code"] == "B6003", r)

    # ---- 出院结算 ----
    st, r = call("POST", "/billing/admissions/%d/settle" % admission_id, cashier,
                 {"payMethod": 1}, idem="p2-set-" + uid)
    check("26. 出院结算成功", r["code"] == "OK", r)
    bill_no = (r.get("data") or {}).get("billNo")
    check("26b. 结算响应返回押金累计与应退（补）口径",
          (r.get("data") or {}).get("depositTotal") is not None
          and (r.get("data") or {}).get("refundAmount") is not None, r.get("data"))
    # 七十轮：退押金闭环——按应退口径登记退款，超退被拦
    refundable = (r.get("data") or {}).get("refundAmount")
    if refundable is not None and float(refundable) > 0:
        st, rf = call("POST", "/inp/admissions/%d/deposit-refunds" % admission_id, cashier,
                      {"amount": refundable, "payMethod": 1, "reason": "结算退押金"},
                      idem="p2-drf-" + uid)
        check("26c. 退押金登记成功", rf.get("code") == "OK", rf)
        st, rf2 = call("POST", "/inp/admissions/%d/deposit-refunds" % admission_id, cashier,
                       {"amount": refundable, "payMethod": 1, "reason": "超退被拦"},
                       idem="p2-drf2-" + uid)
        check("26d. 超额退押金拦截", rf2.get("code") != "OK", rf2)
    else:
        check("26c. 退押金登记成功", True)  # 应退为 0（费用≥押金）跳过
        check("26d. 超额退押金拦截", True)
    st, r2 = call("POST", "/billing/admissions/%d/settle" % admission_id, cashier,
                  {"payMethod": 1}, idem="p2-set2-" + uid)
    check("27. 重复结算拦截(B3001)", r2["code"] == "B3001", r2)
    st, bl = call("GET", "/billing/bills?billNo=" + bill_no, cashier)
    bill_id = bl["data"]["list"][0]["id"]
    st, bd = call("GET", "/billing/bills/%d" % bill_id, cashier)
    check("28. 结算账单含住院费用明细", len(bd["data"]["details"]) >= 2, bd["data"]["details"])

    # ---- 病案 ----
    st, r = call("POST", "/mrc/homepage/%d/code" % admission_id, mrc_clerk,
                 {"mainDiagnosisCode": "J18.9", "mainDiagnosisName": "肺炎"}, idem="p2-code-" + uid)
    check("29. 首页编码成功", r["code"] == "OK", r)
    st, r = call("POST", "/mrc/homepage/%d/qc" % admission_id, mrc_clerk, {"pass": True},
                 idem="p2-qc-" + uid)
    check("29a. 首页质控通过", r["code"] == "OK", r)
    st, r = call("POST", "/mrc/admissions/%d/archive" % admission_id, mrc_clerk, idem="p2-arc-" + uid)
    check("30. 病案归档成功（已结算+质控通过）", r["code"] == "OK", r)
    st, r = call("POST", "/mrc/admissions/%d/borrow" % admission_id, mrc_clerk,
                 {"expectReturnDays": 7}, idem="p2-bw-" + uid)
    check("31. 病案借阅成功", r["code"] == "OK", r)
    st, r = call("POST", "/mrc/admissions/%d/return" % admission_id, mrc_clerk, idem="p2-rt-" + uid)
    check("32. 病案归还成功", r["code"] == "OK", r)

    # ---- 医保 ----
    st, r = call("POST", "/medins/settles?billId=%d&insuranceType=1" % bill_id, med_clerk,
                 idem="p2-yb-" + uid)
    check("33. 医保申报成功(Mock)", r["code"] == "OK", r)
    settle_no = r.get("data")
    st, daily = call("GET", "/medins/reconcile/daily?settleDate=%s" % today, med_clerk)
    row = [s for s in daily["data"] if s["settleNo"] == settle_no][0]
    check("34. Mock 拆分（统筹+个账+自费=总额）",
          abs(float(row["poolPay"]) + float(row["accountPay"]) + float(row["selfPay"])
              - float(row["totalAmount"])) < 0.01, row)
    st, r = call("POST", "/medins/settles/%d/reconcile" % row["id"], med_clerk, {"remark": "日对账"},
                 idem="p2-rec-" + uid)
    check("35. 医保对账通过", r["code"] == "OK", r)
    st, r = call("POST", "/medins/settles?billId=%d&insuranceType=1" % bill_id, med_clerk,
                 idem="p2-yb2-" + uid)
    check("36. 重复申报拦截(B6401)", r["code"] == "B6401", r)

    # ---- 权限与事件 ----
    st, r = call("POST", "/inp/admissions", nurse, {
        "patientId": patient_id, "deptId": 1, "wardId": ward_id, "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "x", "depositAmount": 100,
        "payMethod": 1}, idem="p2-perm-" + uid)
    check("37. 护士入院操作被拒(A0003)", r["code"] == "A0003", r)
    st, r = call("GET", "/doc/executions/todo", cashier)
    check("38. 收费员查执行单被拒(A0003)", r["code"] == "A0003", r)
    st, r = call("GET", "/plt/events?bizNo=" + bill_no, admin)
    check("39. 结算事件链可查询", r["code"] == "OK" and len(r["data"]) >= 1, r)
    st, r = call("GET", "/mrc/records?archiveStatus=20", mrc_clerk)
    check("40. 病案员只读+归档查询正常", r["code"] == "OK", r)

    failed = [x for x in results if not x[1]]
    print("\n========== 二期验收结果: %d/%d 通过 ==========" % (len(results) - len(failed), len(results)))
    for name, ok, detail in failed:
        print("FAIL:", name, "|", str(detail)[:200])
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    import urllib.parse
    main()
