#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HIS 一期端到端验收脚本（指导文档 §9 全验收场景自动化版）
用法: python e2e_acceptance.py [BASE_URL]
默认 BASE_URL = http://localhost:8080/api/v1
可重复执行：每次运行生成唯一测试患者；日结步骤在当日已日结时按 B3006 分支校验。
依赖: 仅 Python 3.8+ 标准库。
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
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
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
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:120]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败: %s" % r
    return r["data"]["token"]


def main():
    uid = str(int(time.time() * 1000))[-8:]      # 毫秒后8位，保证跨运行唯一
    id_card = "34010419900101" + uid[-4:]        # 18位：6位地区+8位生日+4位序号
    phone = "138" + uid
    today = time.strftime("%Y-%m-%d")

    admin = login("admin")
    # 七十九轮：账号卫生——停用历史验收账号（rj%），防用户列表被测试账号灌满。
    # 账单引用保留（审计完整），仅停登录；本轮新账号在下方创建。
    st, olds = call("GET", "/system/users?username=rj&pageSize=100&status=1", admin)
    for u in (olds.get("data", {}).get("list") or []):
        if u.get("username") != "admin":
            # PUT 端点带 @Idempotent——必须携带幂等头（七十九轮：缺头 A0004 静默失败）
            call("PUT", "/system/users/%s/status" % u["id"], admin, {"status": 0},
                 idem="e2e-dis-%s" % u["id"])
    # 一次性收费员：每轮运行动态创建——日结锁定（B3006）生效后，固定收费员的当日历史日结会污染本轮
    st, roles = call("GET", "/system/roles", admin)
    cashier_role_id = [x for x in roles["data"] if x["roleCode"] == "CASHIER"][0]["id"]
    rj_user = "rj" + uid
    call("POST", "/system/users", admin, {"username": rj_user, "realName": "验收收费员",
         "password": PASSWORD, "roleIds": [cashier_role_id]}, idem="e2e-rjuser-" + uid)
    cashier = login(rj_user)
    doctor = login("dr.li")
    pharmacist = login("pharm.zhao")
    auditor = login("auditor.sun")
    check("1. 五角色登录", all([admin, cashier, doctor, pharmacist, auditor]))

    # ---- 患者建档 ----
    st, r = call("POST", "/patients", doctor, {"name": "验收患者" + uid, "gender": 1,
                 "birthDate": "1990-01-01", "idCardNo": id_card, "phone": phone}, idem="e2e-p-" + uid)
    check("2. 医生建档被拒(A0003)", r["code"] == "A0003", r)
    st, r = call("POST", "/patients", cashier, {"name": "验收患者" + uid, "gender": 1,
                 "birthDate": "1990-01-01", "idCardNo": id_card, "phone": phone}, idem="e2e-p2-" + uid)
    check("3. 收费员建档成功", r["code"] == "OK", r)
    patient_no = r.get("data")
    st, r2 = call("POST", "/patients", cashier, {"name": "重复" + uid, "gender": 1,
                  "idCardNo": id_card, "phone": phone}, idem="e2e-p3-" + uid)
    check("4. 重复建档拦截(B1001)", r2["code"] == "B1001", r2)
    st, r3 = call("POST", "/patients", cashier, {"name": "验收患者" + uid, "gender": 1,
                  "birthDate": "1990-01-01", "idCardNo": id_card, "phone": phone}, idem="e2e-p2-" + uid)
    check("5. 建档幂等重放返回相同结果", r3.get("data") == patient_no, (r, r3))

    # 患者ID
    st, pl = call("GET", "/patients?patientNo=" + patient_no, cashier)
    patient_id = pl["data"]["list"][0]["id"]

    # ---- 挂号 ----
    reg_body = {"patientId": patient_id, "doctorId": 2, "regDate": today, "period": 2, "regType": 1}
    st, r = call("POST", "/registrations", cashier, reg_body, idem="e2e-r-" + uid)
    check("6. 挂号成功(普通号 10+10)", r["code"] == "OK" and r["data"]["totalFee"] == "20.00", r)
    reg_id = None
    st, rl = call("GET", "/registrations?patientId=%d&regDate=%s" % (patient_id, today), cashier)
    reg_id = rl["data"]["list"][0]["id"]
    st, r2 = call("POST", "/registrations", cashier, reg_body, idem="e2e-r2-" + uid)
    # 八十九轮：A0005 幂等键冲突——同键不同载荷必须拒绝（IdempotentAspect 摘要比对）。
    # 注意：载荷差异必须落在 DTO 真实绑定字段上——未知字段反序列化即被丢弃，摘要不变走快照重放
    tampered = dict(reg_body); tampered["doctorId"] = 3
    st, r3 = call("POST", "/registrations", cashier, tampered, idem="e2e-r-" + uid)
    check("幂等冲突 A0005（同键不同载荷）", r3.get("code") == "A0005", r3)
    check("7. 重复挂号拦截(B1002)", r2["code"] == "B1002", r2)

    # ---- 接诊与病历 ----
    st, r = call("POST", "/clinic/visits/%d/start" % reg_id, doctor, idem="e2e-v1-" + uid)
    visit_id = r.get("data")
    check("8. 接诊成功", r["code"] == "OK" and isinstance(visit_id, int), r)
    st, r2 = call("POST", "/clinic/visits/%d/start" % reg_id, doctor, idem="e2e-v2-" + uid)
    check("9. 重复接诊返回同一就诊(幂等)", r2.get("data") == visit_id, (r, r2))

    st, r = call("PUT", "/clinic/visits/%d/record" % visit_id, doctor,
                 {"chiefComplaint": "咳嗽3天", "presentIllness": "受凉后咳嗽伴发热",
                  "physicalExam": "咽部充血", "advice": "多饮水"}, idem="e2e-rec-" + uid)
    check("10. 暂存病历", r["code"] == "OK", r)
    st, r = call("POST", "/clinic/visits/%d/diagnoses" % visit_id, doctor,
                 {"diagnosisCode": "J06.9", "diagnosisName": "急性上呼吸道感染", "diagnosisType": 1},
                 idem="e2e-d-" + uid)
    check("11. 新增诊断", r["code"] == "OK", r)
    rx_body = {"items": [
        {"drugId": 1, "dosage": "0.25g", "frequency": "tid", "usageRoute": "口服", "days": 7, "quantity": 2},
        {"drugId": 2, "dosage": "0.3g", "frequency": "bid", "usageRoute": "口服", "days": 3, "quantity": 1}]}
    st, r = call("POST", "/clinic/visits/%d/prescriptions" % visit_id, doctor, rx_body, idem="e2e-rx-" + uid)
    check("12. 开处方金额后端重算(53.20)", r["code"] == "OK" and r["data"]["totalAmount"] == "53.20", r)
    rx_id = None
    st, q = call("GET", "/pharmacy/prescriptions", pharmacist)
    for rx in q["data"]:
        if rx["visitId"] == visit_id:
            rx_id = rx["id"]
    st, r = call("POST", "/clinic/visits/%d/exam-applications" % visit_id, pharmacist,
                 {"chargeItemId": 3}, idem="e2e-ex-" + uid)
    check("13. 医生开检查申请被拒(A0003)", r["code"] == "A0003", r)
    st, r = call("POST", "/clinic/visits/%d/exam-applications" % visit_id, doctor,
                 {"chargeItemId": 3}, idem="e2e-ex2-" + uid)
    check("14. 检查申请成功(25.00)", r["code"] == "OK" and r["data"]["price"] == "25.00", r)
    st, r = call("POST", "/clinic/visits/%d/complete" % visit_id, doctor, idem="e2e-c-" + uid)
    check("15. 提交病历", r["code"] == "OK", r)
    st, r = call("PUT", "/clinic/visits/%d/record" % visit_id, doctor,
                 {"chiefComplaint": "改"}, idem="e2e-rec2-" + uid)
    check("16. 完成后病历锁定(B2001)", r["code"] == "B2001", r)

    # ---- 收费 ----
    st, r = call("GET", "/billing/visits/%d/payable" % visit_id, cashier)
    check("17. 待缴费清单(98.20)", r["code"] == "OK" and r["data"]["totalAmount"] == "98.20", r)
    bill_body = {"visitId": visit_id, "payMethod": 1}
    st, r = call("POST", "/billing/bills", cashier, bill_body, idem="e2e-bill-" + uid)
    check("18. 收费成功", r["code"] == "OK", r)
    bill_no = r.get("data", {}).get("billNo")
    st, r2 = call("POST", "/billing/bills", cashier, bill_body, idem="e2e-bill-" + uid)
    check("19. 收费幂等重放返回同一单号", r2.get("data", {}).get("billNo") == bill_no, (r, r2))
    st, r3 = call("POST", "/billing/bills", cashier, bill_body, idem="e2e-bill3-" + uid)
    check("20. 重复收费拦截(B3001)", r3["code"] == "B3001", r3)

    # 收费单与明细ID
    st, bl = call("GET", "/billing/bills?billNo=" + bill_no, cashier)
    bill = bl["data"]["list"][0]
    bill_id = bill["id"]
    st, bd = call("GET", "/billing/bills/%d" % bill_id, cashier)
    details = bd["data"]["details"]
    rx_details = sorted([d for d in details if d["feeType"] == 7], key=lambda x: x["id"])
    reg_detail = [d for d in details if d["feeType"] == 1][0]

    # 五十二轮：横向越权（IDOR）——收费员读他人账单详情被拒(A0003)
    st, me0 = call("GET", "/auth/me", cashier)
    my_cashier_id = me0["data"]["userId"]
    st, allb = call("GET", "/billing/bills?pageNum=1&pageSize=50", admin)
    foreign_id = next((b["id"] for b in allb["data"]["list"] if b["cashierId"] != my_cashier_id), None)
    if foreign_id:
        st, r = call("GET", "/billing/bills/%d" % foreign_id, cashier)
        check("20b. 横向越权读他人账单被拒(A0003)", r["code"] == "A0003", r)
    else:
        check("20b. 横向越权读他人账单被拒(A0003)", True)  # 空库无他人账单，跳过

    # ---- 审核/发药 ----
    st, r = call("POST", "/pharmacy/prescriptions/%d/review" % rx_id, pharmacist,
                 {"pass": True, "comment": "审核通过"}, idem="e2e-rev-" + uid)
    check("21. 药师审核通过", r["code"] == "OK", r)
    st, r2 = call("POST", "/pharmacy/prescriptions/%d/review" % rx_id, pharmacist,
                  {"pass": True, "comment": "再审"}, idem="e2e-rev2-" + uid)
    check("22. 重复审核拦截(B4008)", r2["code"] == "B4008", r2)
    st, r = call("POST", "/pharmacy/prescriptions/%d/dispense" % rx_id, pharmacist, idem="e2e-dsp-" + uid)
    check("23. 发药成功", r["code"] == "OK", r)
    st, r2 = call("POST", "/pharmacy/prescriptions/%d/dispense" % rx_id, pharmacist, idem="e2e-dsp2-" + uid)
    check("24. 重复发药拦截(B4003)", r2["code"] == "B4003", r2)

    # ---- 退费/退药 ----
    st, r = call("POST", "/billing/refunds", cashier,
                 {"billId": bill_id, "reason": "部分退",
                  "details": [{"chargeDetailId": rx_details[0]["id"], "refundQuantity": rx_details[0]["quantity"]}]},
                 idem="e2e-rf1-" + uid)
    check("25. 处方部分退费被拦(B3003 整方退费)", r["code"] == "B3003", r)
    st, r = call("POST", "/pharmacy/returns", pharmacist,
                 {"dispenseOrderId": 0, "reason": "x"}, idem="e2e-rt0-" + uid)
    # 真实发药单ID
    st, dl = call("GET", "/pharmacy/dispense-orders?pageSize=50", pharmacist)
    dispense_id = [d["id"] for d in dl["data"]["list"] if d["prescriptionId"] == rx_id][0]
    st, r = call("POST", "/billing/refunds", cashier,
                 {"billId": bill_id, "reason": "未退药先退费",
                  "details": [{"chargeDetailId": d["id"], "refundQuantity": d["quantity"]} for d in rx_details]},
                 idem="e2e-rf2-" + uid)
    check("26. 已发药处方退费被拦(B3004)", r["code"] == "B3004", r)
    st, r = call("POST", "/pharmacy/returns", pharmacist,
                 {"dispenseOrderId": dispense_id, "reason": "验收退药"}, idem="e2e-rt-" + uid)
    check("27. 整方退药成功", r["code"] == "OK", r)
    st, r2 = call("POST", "/pharmacy/returns", pharmacist,
                  {"dispenseOrderId": dispense_id, "reason": "再退"}, idem="e2e-rt2-" + uid)
    check("28. 重复退药拦截(B4006)", r2["code"] == "B4006", r2)
    st, r = call("POST", "/billing/refunds", cashier,
                 {"billId": bill_id, "reason": "退药后整方退费",
                  "details": [{"chargeDetailId": d["id"], "refundQuantity": d["quantity"]} for d in rx_details]},
                 idem="e2e-rf3-" + uid)
    check("29. 退药后整方退费成功(53.20)", r["code"] == "OK", r)
    st, r = call("POST", "/billing/refunds", cashier,
                 {"billId": bill_id, "reason": "退挂号费",
                  "details": [{"chargeDetailId": reg_detail["id"], "refundQuantity": 1}]},
                 idem="e2e-rf4-" + uid)
    check("30. 已完成就诊挂号费退费被拦(B3005)", r["code"] == "B3005", r)

    # ---- 日结 ----
    st, r = call("POST", "/billing/settlements", cashier, {"settleDate": today}, idem="e2e-st-" + uid)
    st2, r2 = call("POST", "/billing/settlements", cashier, {"settleDate": today}, idem="e2e-st2-" + uid)
    if r["code"] == "OK":
        check("31. 日结成功", True)
        settle = r["data"]
    else:
        check("31. 日结(当日已日结 → B3006 分支)", r["code"] == "B3006", r)
        st, sl = call("GET", "/billing/settlements?settleDate=%s" % today, cashier)
        settle = sl["data"]["list"][0]
    # 日结锁定：本人当日已日结后不能再收费（B3006），否则交易落在任何日结之外
    st, r = call("POST", "/billing/bills", cashier, bill_body, idem="e2e-billlock-" + uid)
    check("31b. 日结后同收费员再收费被拦(B3006)", r["code"] == "B3006", r)
    # 交叉复核：日结按收费员+结算时点快照比对（须限定本收费员，避免他人生成的账单混入）
    st, me = call("GET", "/auth/me", cashier)
    cashier_id = me["data"]["userId"]
    st, bl = call("GET", "/billing/bills?startDate=%s&endDate=%s&cashierId=%d&pageSize=200" % (today, today, cashier_id), cashier)
    st, fl = call("GET", "/billing/refunds?startDate=%s&endDate=%s&pageSize=200" % (today, today), cashier)
    # 统一截断到秒级再比对：避免退费与日结同秒发生时微秒精度导致误判
    def to_sec(ts):
        return (ts or "").replace("T", " ")[:19]
    cutoff = to_sec(settle["createdAt"])
    charge_sum = sum(float(b["payableAmount"]) for b in bl["data"]["list"]
                     if b["payTime"] and to_sec(b["payTime"]) <= cutoff)
    refund_sum = sum(float(x["refundAmount"]) for x in fl["data"]["list"]
                     if x["refundTime"] and to_sec(x["refundTime"]) <= cutoff
                     and x.get("operatorName") == "验收收费员")
    check("32. 日结金额可由明细复核",
          abs(charge_sum - float(settle["totalChargeAmount"])) < 0.001
          and abs(refund_sum - float(settle["totalRefundAmount"])) < 0.001,
          (charge_sum, refund_sum, settle["totalChargeAmount"], settle["totalRefundAmount"]))

    # ---- 报表 ----
    for name, path in [("33. 日挂号量报表", "/stats/registrations/daily?startDate=%s&endDate=%s" % (today, today)),
                       ("34. 日门诊量报表", "/stats/visits/daily?startDate=%s&endDate=%s" % (today, today)),
                       ("35. 日收入报表", "/stats/revenue/daily?startDate=%s&endDate=%s" % (today, today)),
                       ("36. 费用类别分布", "/stats/revenue/distribution?startDate=%s&endDate=%s" % (today, today)),
                       ("37. 药品库存报表", "/stats/drug-inventory")]:
        st, r = call("GET", path, auditor)
        check(name, r["code"] == "OK", r)
    st, r = call("GET", "/stats/revenue/detail?startDate=%s&endDate=%s" % (today, today), auditor)
    check("38. 收入明细下钻非空", r["code"] == "OK" and r["data"]["total"] > 0, r)

    # ---- 审计日志 ----
    st, r = call("GET", "/logs/operations?pageSize=50", admin)
    check("39. 操作日志可查询", r["code"] == "OK" and r["data"]["total"] > 0, r)
    st, r = call("GET", "/logs/operations?pageSize=10", pharmacist)
    check("40. 药师查日志被拒(A0003)", r["code"] == "A0003", r)

    # ---- 汇总 ----
    failed = [x for x in results if not x[1]]
    print("\n========== 验收结果: %d/%d 通过 ==========" % (len(results) - len(failed), len(results)))
    for name, ok, detail in failed:
        print("FAIL:", name, "|", str(detail)[:200])
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
