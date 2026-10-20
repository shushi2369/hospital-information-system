#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一百二十一~一百二十二轮全模块审计回归（2026-10-21）：
  A. 门诊检查申请列表数据范围（医生越权枚举他人就诊）
  B. 同一就诊重复诊断不重复生成传染病报告卡（V60 生成列唯一键）
  C. LIS 结果行空白校验 + 报告分页 patientId/admissionId/status 过滤
  D. 门诊 RIS 危急值闭环（V59 允许 admission_id 为空；开单医生确认/闭环，他人拒绝）
  E. RIS 无影像禁止写报告
  F. 退押金双重守卫（超过应退金额拒绝）+ 住院结算账单禁止走门诊退费
  G. 冲正最后一笔手工费后出院未结(20)自动结算(30)
  H. 停嘱恢复回到正确状态（未摆药 20 / 已摆药 30）
  I. 配血完成(30)可用血申请可取消
  J. 门诊会诊跨患者拦截与 patientId 回填
  K. 采购单供应商存在性校验
  L. 手术取消后不可再排台
前置：后端已部署本次修复版本（localhost:8080），演示账号可用。
"""
import datetime
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
TOKENS = {}
DOCTOR_TOKENS = {2: "dr.li", 3: "dr.chen", 4: "dr.wang"}


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
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:300]) if detail and not cond else ""))


def login(username):
    if username in TOKENS:
        return TOKENS[username]
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败 %s: %s" % (username, r)
    TOKENS[username] = r["data"]["token"]
    return TOKENS[username]


def create_patient(token, tag, suffix):
    for _ in range(8):
        seq = random.randint(0, 9999)
        body = {"name": tag + suffix, "gender": 1, "birthDate": "1990-01-01",
                "idCardNo": "34010619900101%04d" % seq,
                "phone": "15" + "%09d" % random.randint(0, 999999999)}
        st, r = call("POST", "/patients", token, body, idem="au-p-%s-%d-%d" % (suffix, seq, _))
        if r.get("code") == "OK":
            st2, pl = call("GET", "/patients?patientNo=" + r["data"], token)
            assert st2 == 200 and pl["data"]["list"], pl
            return pl["data"]["list"][0]
        if r.get("code") != "B1001":
            break
    raise AssertionError("患者建档失败: %s" % r)


def start_visit(cashier, tag, suffix):
    patient = create_patient(cashier, tag, suffix)
    reg = None
    doc_id = None
    for off in range(7):
        for candidate in (2, 3, 4):
            d = time.strftime("%Y-%m-%d", time.localtime(time.time() + off * 86400))
            st, r = call("POST", "/registrations", cashier, {
                "patientId": patient["id"], "doctorId": candidate, "regDate": d,
                "period": 2, "regType": 1},
                idem="au-reg-%s-%d-%d" % (suffix, off, candidate))
            if r.get("code") == "OK":
                reg = r
                doc_id = candidate
                break
        if reg:
            break
    assert reg, "号源不可用: %s" % reg
    st, regs = call("GET", "/registrations?patientId=%d" % patient["id"], cashier)
    reg_row = next(x for x in regs["data"]["list"] if x.get("doctorId") == doc_id)
    doctor_name = DOCTOR_TOKENS[doc_id]
    doctor = login(doctor_name)
    st, r = call("POST", "/clinic/visits/%d/start" % reg_row["id"], doctor, idem="au-v-" + suffix)
    assert r.get("code") == "OK", r
    visit_id = r["data"]
    st, r = call("PUT", "/clinic/visits/%d/record" % visit_id, doctor, {
        "chiefComplaint": "cough", "presentIllness": "cold",
        "physicalExam": "ok", "advice": "water"}, idem="au-rec-" + suffix)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/clinic/visits/%d/diagnoses" % visit_id, doctor, {
        "diagnosisCode": "J06.9", "diagnosisName": "URI", "diagnosisType": 1},
        idem="au-dx-" + suffix)
    assert r.get("code") == "OK", r
    return {"patient": patient, "visitId": visit_id, "doctorId": doc_id,
            "doctorName": doctor_name, "doctor": doctor}


def complete_and_bill(cashier, doctor, visit_id, suffix):
    st, r = call("POST", "/clinic/visits/%d/complete" % visit_id, doctor, idem="au-c-" + suffix)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/billing/bills", cashier, {"visitId": visit_id, "payMethod": 1},
                 idem="au-bill-" + suffix)
    assert r.get("code") == "OK", r
    bill_no = r["data"]["billNo"]
    st, bl = call("GET", "/billing/bills?billNo=" + bill_no, cashier)
    bill_id = bl["data"]["list"][0]["id"]
    st, bd = call("GET", "/billing/bills/%d" % bill_id, cashier)
    return bill_id, bd["data"]["details"]


def create_admission(admin, tag, suffix, deposit=300):
    patient = create_patient(admin, tag, suffix)
    bed_no = ("AU" + suffix)[-16:]
    st, r = call("POST", "/inp/beds", admin, {
        "wardId": 1, "bedNo": bed_no, "chargeItemId": 10}, idem="au-bed-" + suffix)
    assert r.get("code") == "OK", r
    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = next(b for b in beds["data"] if b.get("bedNo") == bed_no)
    st, r = call("POST", "/inp/admissions", admin, {
        "patientId": patient["id"], "deptId": 1, "wardId": 1, "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "审计回归",
        "depositAmount": deposit, "payMethod": 1}, idem="au-adm-" + suffix)
    assert r.get("code") == "OK", r
    admission_id = r["data"]["id"]
    st, d = call("GET", "/inp/admissions/%d" % admission_id, admin)
    data = d["data"]
    adm = data.get("admission", data)
    return {"id": admission_id, "patientId": patient["id"], "patient": patient,
            "status": adm.get("status"), "depositTotal": adm.get("depositTotal")}


def admission_detail(admin, admission_id):
    st, d = call("GET", "/inp/admissions/%d" % admission_id, admin)
    data = d["data"]
    return data.get("admission", data)


def add_manual_fee(admin, admission_id, suffix, amount):
    st, r = call("POST", "/inp/daily-fees/manual?admissionId=%d" % admission_id, admin, {
        "feeType": 1, "itemName": "审计手工费" + suffix, "quantity": 1,
        "unitPrice": amount}, idem="au-fee-%s-%s" % (RUN, suffix))
    assert r.get("code") == "OK", r
    return r["data"]


def discharge(admin, admission_id, suffix):
    return call("POST", "/inp/admissions/%d/discharge" % admission_id, admin, {
        "dischargeWay": 2, "dischargeDiagnosis": "审计出院诊断"},
        idem="au-dc-%s-%s" % (RUN, suffix))


def find_ris(ris_token, patient_id, visit_id):
    st, r = call("GET", "/ris/requests?patientId=%d&pageSize=50" % patient_id, ris_token)
    rows = (r.get("data") or {}).get("list") or []
    return next((x for x in rows if x.get("visitId") == visit_id), None)


def find_lis(lab_token, admission_id):
    st, r = call("GET", "/lis/requests?admissionId=%d&pageSize=50" % admission_id, lab_token)
    rows = (r.get("data") or {}).get("list") or []
    return rows[0] if rows else None


def main():
    admin = login("admin")
    cashier = login("cashier.li")
    doctor = login("dr.li")
    doctor2 = login("dr.wang")
    doctor3 = login("dr.chen")
    pharmacist = login("pharm.zhao")
    ris = login("ris.zhang")
    lab = login("lab.chen")
    nurse = login("nurse.wang")
    bb = login("bb.tech")
    today = time.strftime("%Y-%m-%d")
    tomorrow = (datetime.datetime.strptime(today, "%Y-%m-%d") + datetime.timedelta(days=1)).strftime("%Y-%m-%d")
    future = "2028-12-31"

    st, items = call("GET", "/basedata/charge-items?category=3&status=1", admin)
    ris_item = items["data"][0]
    st, items = call("GET", "/basedata/charge-items?category=4&status=1", admin)
    lis_item = items["data"][0]

    # ================= A. 门诊检查申请列表数据范围 =================
    v = start_visit(cashier, "审计越权", RUN + "A")
    st, r = call("POST", "/clinic/visits/%d/exam-applications" % v["visitId"], v["doctor"],
                 {"chargeItemId": ris_item["id"]}, idem="au-exa-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("GET", "/clinic/visits/%d/exam-applications" % v["visitId"], v["doctor"])
    check("A1. 接诊医生可查看本人就诊检查申请", r.get("code") == "OK" and len(r["data"]) >= 1, r)
    other_doctor_name = "dr.wang" if v["doctorName"] != "dr.wang" else "dr.chen"
    st, r = call("GET", "/clinic/visits/%d/exam-applications" % v["visitId"], login(other_doctor_name))
    check("A2. 其他医生越权查看检查申请被拒(A0003)", r.get("code") == "A0003", r)

    # ================= B. 重复诊断传染病卡去重 =================
    v = start_visit(cashier, "审计报卡", RUN + "B")
    for idx in (1, 2):
        st, r = call("POST", "/clinic/visits/%d/diagnoses" % v["visitId"], v["doctor"], {
            "diagnosisCode": "A15.0", "diagnosisName": "继发性肺结核", "diagnosisType": idx},
            idem="au-tb-%s-%d" % (RUN, idx))
        assert r.get("code") == "OK", r
    st, cl = call("GET", "/pub/cards?status=10&pageNum=1&pageSize=200", admin)
    hit = [c for c in cl["data"]["list"]
           if c.get("patientId") == v["patient"]["id"] and c.get("diseaseName") == "肺结核"]
    check("B1. 同就诊重复诊断只生成一张报告卡", len(hit) == 1, [c["id"] for c in hit])
    st, vd = call("GET", "/clinic/visits/%d" % v["visitId"], v["doctor"])
    check("B2. 两条肺结核诊断均已落库（阳性对照）",
          len([d for d in vd["data"]["diagnoses"] if d.get("diagnosisCode") == "A15.0"]) == 2,
          vd["data"]["diagnoses"])

    # ================= C. LIS 空白结果校验与报告过滤 =================
    # 门诊检验无 billing→LIS 联动，改走住院检验医嘱执行（doc→lis 单向）
    adm = create_admission(admin, "审计检验", RUN + "C", 200)
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": adm["id"], "orderClass": 2, "category": 3,
        "items": [{"chargeItemId": lis_item["id"], "quantity": 1}]}, idem="au-lord-" + RUN)
    assert r.get("code") == "OK", r
    st, ol = call("GET", "/doc/orders?admissionId=%d&pageSize=50" % adm["id"], doctor)
    lorder = next(x for x in ol["data"]["list"] if x.get("category") == 3)
    st, todo = call("GET", "/doc/executions/todo?execDate=%s" % today, nurse)
    execs = [x for x in todo["data"] if x.get("orderId") == lorder["id"]
             and x.get("execType") == 2 and x.get("status") == 1]
    check("C0. 检验医嘱生成待执行单", len(execs) >= 1, todo.get("data"))
    assert execs, "无检验执行单"
    st, r = call("POST", "/doc/executions/%d/do" % execs[0]["id"], nurse, idem="au-exec-" + RUN)
    assert r.get("code") == "OK", r
    lis_req = find_lis(lab, adm["id"])
    check("C1. 执行检验医嘱生成 LIS 申请单", lis_req is not None, lis_req)
    assert lis_req, "LIS 申请未生成"
    lis_id = lis_req["id"]
    st, r = call("POST", "/lis/specimens/collect?requestId=%d" % lis_id, lab, idem="au-col-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/lis/specimens/%d/receive" % lis_id, lab, idem="au-rcv-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/lis/results/entry", lab, {
        "requestId": lis_id, "fetch": False,
        "rows": [{"itemName": "WBC", "resultValue": ""}]}, idem="au-lisblank-" + RUN)
    check("C2. 空白检验结果被拒(400)",
          st == 400 and r.get("code") == "A0001" and "检验结果" in str(r.get("message", "")), r)
    st, r = call("POST", "/lis/results/entry", lab, {
        "requestId": lis_id, "fetch": False,
        "rows": [{"itemName": "WBC", "resultValue": "6.5", "unit": "10^9/L",
                  "referenceRange": "4.0-10.0"}]}, idem="au-lis-" + RUN)
    check("C3. 合法结果录入成功", r.get("code") == "OK", r)
    st, r = call("POST", "/lis/reports/%d/publish" % lis_id, lab, idem="au-pub-" + RUN)
    check("C4. 检验报告发布成功", r.get("code") == "OK", r)
    st, r = call("GET", "/lis/reports?patientId=%d&status=20&pageSize=100" % adm["patientId"], doctor)
    rows = (r.get("data") or {}).get("list") or []
    check("C5. 报告按 patientId+status 正确过滤", any(x.get("requestId") == lis_id for x in rows),
          [x.get("requestId") for x in rows])
    st, r = call("GET", "/lis/reports?patientId=999999999&pageSize=100", doctor)
    rows = (r.get("data") or {}).get("list") or []
    check("C6. 不存在的 patientId 过滤为空", r.get("code") == "OK" and len(rows) == 0, rows)
    st, r = call("GET", "/lis/reports?status=40&pageSize=100", doctor)
    rows = (r.get("data") or {}).get("list") or []
    check("C7. 不存在的报告状态过滤为空", r.get("code") == "OK" and len(rows) == 0, rows[:3])

    # ================= D. RIS 无影像写报告 + 门诊危急值闭环 =================
    v = start_visit(cashier, "审计危急", RUN + "D")
    st, r = call("POST", "/clinic/visits/%d/exam-applications" % v["visitId"], v["doctor"],
                 {"chargeItemId": ris_item["id"]}, idem="au-exr-" + RUN)
    assert r.get("code") == "OK", r
    complete_and_bill(cashier, v["doctor"], v["visitId"], RUN + "D")
    ris_req = find_ris(ris, v["patient"]["id"], v["visitId"])
    check("D1. 收费后生成 RIS 申请单", ris_req is not None, ris_req)
    assert ris_req, "RIS 申请未生成"
    rid = ris_req["id"]
    device = {1: 1, 2: 2, 3: 3}.get(ris_req.get("modality"), 1)
    st, r = call("POST", "/ris/requests/%d/appoint" % rid, ris, {
        "deviceId": device, "apptTime": today + "T20:00:00"}, idem="au-ap-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/ris/requests/%d/start" % rid, ris, idem="au-st-" + RUN)
    assert r.get("code") == "OK", r
    sign = "审计危急值" + RUN[-6:]
    st, r = call("POST", "/ris/reports", v["doctor"], {
        "requestId": rid, "finding": "影像所见", "conclusion": "建议急诊处理",
        "criticalSign": sign}, idem="au-rw0-" + RUN)
    check("D2. 未归档影像写报告被拒", r.get("code") == "A0001"
          and "影像未归档" in str(r.get("message", "")), r)
    st, r = call("POST", "/ris/requests/%d/images" % rid, ris, {"fetch": True}, idem="au-im-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/ris/requests/%d/finish" % rid, ris, idem="au-fi-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/ris/reports", v["doctor"], {
        "requestId": rid, "finding": "影像所见", "conclusion": "建议急诊处理",
        "criticalSign": sign}, idem="au-rw-" + RUN)
    check("D3. 影像归档后可写报告", r.get("code") == "OK", r)
    st, rl = call("GET", "/ris/reports?status=10&pageSize=100", v["doctor"])
    rep = next((x for x in rl["data"]["list"] if x.get("requestId") == rid), None)
    check("D4. 报告待审核列表可查", rep is not None, rep)
    assert rep, "RIS 报告不存在"
    reviewer_name = "dr.wang" if v["doctorName"] != "dr.wang" else "dr.chen"
    st, r = call("POST", "/ris/reports/%d/review" % rep["id"], login(reviewer_name),
                 {"approved": True}, idem="au-rv-" + RUN)
    check("D5. 报告审核发布成功", r.get("code") == "OK", r)
    st, al = call("GET", "/alerts?status=10&pageNum=1&pageSize=200", v["doctor"])
    hit = [a for a in al["data"]["list"] if a.get("criticalValue") == sign]
    check("D6. 门诊危急值已生成且住院ID为空(V59)", len(hit) == 1 and hit[0].get("admissionId") is None,
          hit)
    assert hit, "门诊危急值未生成"
    aid = hit[0]["id"]
    st, r = call("POST", "/alerts/%d/notify" % aid, ris, idem="au-nt-" + RUN)
    check("D7. 技师通知登记(10→20)", r.get("code") == "OK", r)
    st, r = call("POST", "/alerts/%d/confirm" % aid, login(reviewer_name), idem="au-cfw-" + RUN)
    check("D8. 非开单医生确认被拒(A0003)", r.get("code") == "A0003", r)
    st, r = call("POST", "/alerts/%d/confirm" % aid, v["doctor"], idem="au-cf-" + RUN)
    check("D9. 开单医生确认(20→30)", r.get("code") == "OK", r)
    st, r = call("POST", "/alerts/%d/close" % aid, v["doctor"],
                 {"handleNote": "已电话通知开单医生并处置"}, idem="au-cl-" + RUN)
    check("D10. 开单医生闭环(30→40)", r.get("code") == "OK", r)
    st, al = call("GET", "/alerts?status=40&pageNum=1&pageSize=200", v["doctor"])
    check("D11. 闭环后状态为已闭环", any(a["id"] == aid for a in al["data"]["list"]), aid)

    # ================= E/F. 押金退款守卫与住院账单退费拦截 =================
    adm = create_admission(admin, "审计押金", RUN + "E", 1000)
    add_manual_fee(admin, adm["id"], "E", 300)
    st, r = discharge(admin, adm["id"], "E")
    check("E1. 出院后状态为未结算(20)", r.get("code") == "OK"
          and admission_detail(admin, adm["id"]).get("status") == 20,
          admission_detail(admin, adm["id"]))
    st, r = call("POST", "/billing/admissions/%d/settle" % adm["id"], admin,
                 {"payMethod": 1}, idem="au-set-" + RUN)
    check("E2. 出院结算成功", r.get("code") == "OK", r)
    settle_bill_id = r["data"]["id"] if r.get("data") else None
    check("E3. 结算后状态为已结算(30)", admission_detail(admin, adm["id"]).get("status") == 30,
          admission_detail(admin, adm["id"]))
    st, r = call("POST", "/inp/admissions/%d/deposit-refunds" % adm["id"], admin, {
        "amount": 500, "payMethod": 1, "reason": "审计退押金1"}, idem="au-dr1-" + RUN)
    check("E4. 应退金额内退押金成功", r.get("code") == "OK", r)
    st, r = call("POST", "/inp/admissions/%d/deposit-refunds" % adm["id"], admin, {
        "amount": 500, "payMethod": 1, "reason": "审计退押金2"}, idem="au-dr2-" + RUN)
    check("E5. 超过应退金额退押金被拒(B6007)", r.get("code") == "B6007"
          and "超过应退金额" in str(r.get("message", "")), r)
    check("E6. 超额退款未扣减押金余额",
          float(admission_detail(admin, adm["id"]).get("depositTotal")) == 500.0,
          admission_detail(admin, adm["id"]).get("depositTotal"))
    st, r = call("POST", "/inp/admissions/%d/deposit-refunds" % adm["id"], admin, {
        "amount": 200, "payMethod": 1, "reason": "审计退押金3"}, idem="au-dr3-" + RUN)
    check("E7. 退至账单额为止成功", r.get("code") == "OK", r)
    check("E8. 押金余额等于账单净额",
          float(admission_detail(admin, adm["id"]).get("depositTotal")) == 300.0,
          admission_detail(admin, adm["id"]).get("depositTotal"))
    st, bd = call("GET", "/billing/bills/%d" % settle_bill_id, admin)
    details = bd["data"]["details"]
    st, r = call("POST", "/billing/refunds", admin, {
        "billId": settle_bill_id, "reason": "住院账单退费",
        "details": [{"chargeDetailId": d["id"], "refundQuantity": d["quantity"]}
                    for d in details]}, idem="au-brf-" + RUN)
    check("F1. 住院结算账单禁止直接退费(B3003)", r.get("code") == "B3003"
          and "住院结算账单" in str(r.get("message", "")), r)

    # ================= G. 冲正最后一笔手工费自动结算 =================
    adm = create_admission(admin, "审计冲正", RUN + "G", 500)
    fee_id = add_manual_fee(admin, adm["id"], "G", 120)
    st, r = discharge(admin, adm["id"], "G")
    check("G1. 有未结费用出院状态为20", r.get("code") == "OK"
          and admission_detail(admin, adm["id"]).get("status") == 20,
          admission_detail(admin, adm["id"]))
    st, r = call("POST", "/inp/daily-fees/%d/void" % fee_id, admin, idem="au-void-" + RUN)
    check("G2. 手工费冲正成功", r.get("code") == "OK", r)
    check("G3. 末笔未结费用冲正后自动结算(30)",
          admission_detail(admin, adm["id"]).get("status") == 30,
          admission_detail(admin, adm["id"]))
    st, r = call("POST", "/inp/admissions/%d/deposit-refunds" % adm["id"], admin, {
        "amount": 500, "payMethod": 1, "reason": "无费用退押金"}, idem="au-grf-" + RUN)
    check("G4. 无账单住院可全额退押金", r.get("code") == "OK", r)

    # ================= H. 停嘱恢复状态 =================
    adm = create_admission(admin, "审计医嘱", RUN + "H", 300)
    st, dl = call("GET", "/basedata/drugs?status=1", admin)
    drugs = (dl["data"]["list"] if isinstance(dl["data"], dict) else dl["data"]) or []
    drug = drugs[0]
    st, r = call("POST", "/inventory/inbound", admin, {
        "drugId": drug["id"], "batchNo": ("AUD" + RUN)[-32:], "expiryDate": future,
        "quantity": 50, "unitPrice": 1}, idem="au-inb-" + RUN)
    check("H1. 药品入库准备库存", r.get("code") == "OK", r)
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": adm["id"], "orderClass": 1, "category": 1, "frequency": "qd",
        "items": [{"drugId": drug["id"], "quantity": 2}]}, idem="au-ord-" + RUN)
    assert r.get("code") == "OK", r
    st, ol = call("GET", "/doc/orders?admissionId=%d&pageSize=50" % adm["id"], doctor)
    order_row = next(x for x in ol["data"]["list"] if x.get("category") == 1 and x.get("status") == 10)
    oid = order_row["id"]
    st, r = call("POST", "/doc/orders/%d/review" % oid, pharmacist, {"pass": True, "comment": "ok"},
                 idem="au-orv-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/doc/orders/%d/stop" % oid, doctor, {"reason": "审计停嘱"},
                 idem="au-ost-" + RUN)
    check("H2. 已审核未摆药医嘱可停", r.get("code") == "OK", r)
    st, r = call("POST", "/doc/orders/%d/resume" % oid, doctor, idem="au-ors1-" + RUN)
    st, d = call("GET", "/doc/orders/%d" % oid, doctor)
    check("H3. 未摆药恢复回到待摆药(20)", r.get("code") == "OK" and d["data"]["order"]["status"] == 20,
          d["data"]["order"])
    st, r = call("POST", "/doc/orders/%d/dispense" % oid, pharmacist, idem="au-odp-" + RUN)
    st, d = call("GET", "/doc/orders/%d" % oid, doctor)
    check("H4. 摆药后医嘱进入执行中(30)", r.get("code") == "OK" and d["data"]["order"]["status"] == 30,
          {"resp": r, "order": d["data"]["order"]})
    st, r = call("POST", "/doc/orders/%d/stop" % oid, doctor, {"reason": "审计再停"},
                 idem="au-ost2-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/doc/orders/%d/resume" % oid, doctor, idem="au-ors2-" + RUN)
    st, d = call("GET", "/doc/orders/%d" % oid, doctor)
    check("H5. 已摆药恢复保持执行中(30)", r.get("code") == "OK" and d["data"]["order"]["status"] == 30,
          d["data"]["order"])

    # ================= I. 配血完成可取消 =================
    adm = create_admission(admin, "审计用血", RUN + "I", 100)
    bag_no = "AU-BAG-" + RUN
    st, r = call("POST", "/bb/bags", bb, {
        "bagNo": bag_no, "bloodType": 4, "rh": 1, "component": 1,
        "volumeMl": 200, "expireDate": tomorrow}, idem="au-bag-" + RUN)
    check("I1. 血袋入库", r.get("code") == "OK", r)
    st, bl = call("GET", "/bb/bags?pageNum=1&pageSize=20&bagNo=" + bag_no, bb)
    bag = next((b for b in bl["data"]["list"] if b["bagNo"] == bag_no), None)
    assert bag, "血袋未入库"
    st, r = call("POST", "/bb/requests", doctor, {
        "admissionId": adm["id"], "patientId": adm["patientId"], "bloodType": 4, "rh": 1,
        "component": 1, "volumeMl": 200, "usePurpose": "审计用血"}, idem="au-br-" + RUN)
    check("I2. 用血申请创建", r.get("code") == "OK", r)
    st, rl = call("GET", "/bb/requests?admissionId=%d&status=10&pageSize=50" % adm["id"], bb)
    req_row = next((x for x in rl["data"]["list"] if x.get("patientId") == adm["patientId"]), None)
    assert req_row, "用血申请未生成"
    brid = req_row["id"]
    st, r = call("POST", "/bb/requests/%d/review?approved=true" % brid, bb, idem="au-brv-" + RUN)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/bb/requests/%d/cross-match" % brid, bb, {
        "bagId": bag["id"], "crossMethod": "抗人球", "crossResult": 1}, idem="au-bcm-" + RUN)
    check("I3. 相容配血完成(20→30)", r.get("code") == "OK", r)
    st, rl = call("GET", "/bb/requests?admissionId=%d&status=30&pageSize=50" % adm["id"], bb)
    check("I4. 申请处于配血完成(30)", any(x["id"] == brid for x in rl["data"]["list"]), "")
    st, r = call("POST", "/bb/requests/%d/cancel" % brid, doctor, idem="au-bcn-" + RUN)
    check("I5. 配血完成未发血可取消", r.get("code") == "OK", r)
    st, rl = call("GET", "/bb/requests?admissionId=%d&status=70&pageSize=50" % adm["id"], bb)
    check("I6. 取消后状态为70", any(x["id"] == brid for x in rl["data"]["list"]), "")

    # ================= J. 门诊会诊跨患者拦截/回填 =================
    v = start_visit(cashier, "审计会诊", RUN + "J")
    patient_b = create_patient(cashier, "审计会诊B", RUN + "JB")
    consult = {"visitId": v["visitId"], "patientId": patient_b["id"], "consultDoctorId": 3,
               "deptId": 1, "urgent": 1, "reason": "审计会诊"}
    st, r = call("POST", "/cnt/requests", v["doctor"], consult, idem="au-cntx-" + RUN)
    check("J1. 跨患者门诊会诊被拒(A0001)", r.get("code") == "A0001"
          and "不匹配" in str(r.get("message", "")), r)
    consult.pop("patientId")
    st, r = call("POST", "/cnt/requests", v["doctor"], consult, idem="au-cnt-" + RUN)
    check("J2. 门诊会诊可正常申请", r.get("code") == "OK", r)
    req_no = r.get("data")
    st, rl = call("GET", "/cnt/requests?patientId=%d&pageSize=100" % v["patient"]["id"], v["doctor"])
    created = next((x for x in rl["data"]["list"] if x.get("reqNo") == req_no), None)
    check("J3. 会诊 patientId 由就诊回填", created is not None
          and created.get("patientId") == v["patient"]["id"], created)

    # ================= K. 采购供应商校验 =================
    st, r = call("POST", "/whse/purchase-orders", admin, {
        "supplierId": 999999999, "drugId": drug["id"], "quantity": 10, "unitPrice": 1},
        idem="au-po-" + RUN)
    check("K1. 不存在供应商采购被拒", r.get("code") != "OK"
          and "供应商" in str(r.get("message", "")), r)

    # ================= L. 手术取消后不可排台 =================
    adm = create_admission(admin, "审计手术", RUN + "L", 200)
    st, r = call("POST", "/ors/requests", doctor, {
        "admissionId": adm["id"], "patientId": adm["patientId"], "surgeryName": "审计手术",
        "diagnosis": "审计诊断", "plannedDate": tomorrow, "anesthesiaMethod": 1},
        idem="au-or-" + RUN)
    check("L1. 手术申请创建", r.get("code") == "OK", r)
    st, ol = call("GET", "/ors/requests?admissionId=%d&pageSize=50" % adm["id"], admin)
    orow = next(x for x in ol["data"]["list"] if x.get("surgeryName") == "审计手术")
    oid = orow["id"]
    st, r = call("POST", "/ors/requests/%d/review" % oid, admin, {"approved": True},
                 idem="au-orv-" + RUN)
    check("L2. 手术审核通过(10→20)", r.get("code") == "OK", r)
    st, r = call("POST", "/ors/requests/%d/cancel?reason=%s" % (oid, urllib.parse.quote("审计取消")),
                 admin, idem="au-ocn-" + RUN)
    check("L3. 已审核手术可取消(20→70)", r.get("code") == "OK", r)
    st, rooms = call("GET", "/ors/rooms", admin)
    room = next((x for x in rooms["data"] if x.get("status") == 1), None)
    assert room, "无可用手术间"
    st, r = call("POST", "/ors/requests/%d/schedule" % oid, admin, {
        "roomId": room["id"], "surgeryDate": tomorrow, "seqNo": 1, "surgeonId": 2},
        idem="au-osch-" + RUN)
    check("L4. 已取消手术禁止排台", r.get("code") != "OK"
          and "未审核" in str(r.get("message", "")), r)
    st, r = call("POST", "/ors/requests/%d/start" % oid, admin, idem="au-ost-" + RUN)
    check("L5. 已取消手术禁止开始", r.get("code") != "OK", r)

    failed = [x for x in RESULTS if not x[1]]
    print("\n===== audit regression: %d/%d passed, %d failed =====" %
          (len(RESULTS) - len(failed), len(RESULTS), len(failed)))
    if failed:
        for name, _, detail in failed:
            print("FAILED:", name, "|", str(detail)[:400])
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
