#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
一百二十轮修复回归（2026-10-21）：
  A. 负数量/零数量处方与住院医嘱拦截（DTO 嵌套校验 + 服务层兜底）
  B. 退费 vs 发药并发互斥（处方行锁 + 联动作废失败回滚）
  C. 检查费退费门禁（已预约/已开始不可退；未预约退费后 RIS 同步作废）
前置：后端已部署本次修复版本（localhost:8080），演示账号可用。
"""
import json
import random
import sys
import threading
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


def create_patient(token, tag, suffix):
    """随机 18 位身份证；撞号时换号重试（B1001）。返回患者对象。"""
    for _ in range(8):
        seq = random.randint(0, 9999)
        body = {"name": tag + suffix, "gender": 1, "birthDate": "1990-01-01",
                "idCardNo": "34010619900101%04d" % seq,
                "phone": "15" + "%09d" % random.randint(0, 999999999)}
        st, r = call("POST", "/patients", token, body, idem="rg-p-%s-%d-%d" % (suffix, seq, _))
        if r.get("code") == "OK":
            st2, pl = call("GET", "/patients?patientNo=" + r["data"], token)
            assert st2 == 200 and pl["data"]["list"], pl
            return pl["data"]["list"][0]
        if r.get("code") != "B1001":
            break
    raise AssertionError("患者建档失败: %s" % r)


def start_visit(cashier, doctor, tag, suffix):
    patient = create_patient(cashier, tag, suffix)
    today = time.strftime("%Y-%m-%d")
    st, r = call("POST", "/registrations", cashier, {
        "patientId": patient["id"], "doctorId": 2, "regDate": today,
        "period": 2, "regType": 1}, idem="rg-reg-" + suffix)
    assert r.get("code") == "OK", r
    st, regs = call("GET", "/registrations?patientId=%d&regDate=%s" % (patient["id"], today), cashier)
    reg = next(x for x in regs["data"]["list"] if x["patientId"] == patient["id"])
    st, r = call("POST", "/clinic/visits/%d/start" % reg["id"], doctor, idem="rg-v-" + suffix)
    assert r.get("code") == "OK", r
    visit_id = r["data"]
    st, r = call("PUT", "/clinic/visits/%d/record" % visit_id, doctor, {
        "chiefComplaint": "cough", "presentIllness": "cold",
        "physicalExam": "ok", "advice": "water"}, idem="rg-rec-" + suffix)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/clinic/visits/%d/diagnoses" % visit_id, doctor, {
        "diagnosisCode": "J06.9", "diagnosisName": "URI", "diagnosisType": 1},
        idem="rg-dx-" + suffix)
    assert r.get("code") == "OK", r
    return patient, visit_id


def complete_and_bill(cashier, doctor, visit_id, suffix):
    st, r = call("POST", "/clinic/visits/%d/complete" % visit_id, doctor, idem="rg-c-" + suffix)
    assert r.get("code") == "OK", r
    st, r = call("POST", "/billing/bills", cashier, {"visitId": visit_id, "payMethod": 1},
                 idem="rg-bill-" + suffix)
    assert r.get("code") == "OK", r
    bill_no = r["data"]["billNo"]
    st, bl = call("GET", "/billing/bills?billNo=" + bill_no, cashier)
    bill_id = bl["data"]["list"][0]["id"]
    st, bd = call("GET", "/billing/bills/%d" % bill_id, cashier)
    return bill_id, bd["data"]["details"]


def visit_detail(doctor, visit_id):
    st, r = call("GET", "/clinic/visits/%d" % visit_id, doctor)
    assert r.get("code") == "OK", r
    return r["data"]


def find_ris(ris_token, patient_id, visit_id):
    st, r = call("GET", "/ris/requests?patientId=%d&pageSize=50" % patient_id, ris_token)
    rows = (r.get("data") or {}).get("list") or []
    return next((x for x in rows if x.get("visitId") == visit_id), None)


def ensure_admission(admin):
    st, r = call("GET", "/inp/admissions?pageNum=1&pageSize=1&status=10", admin)
    rows = (r.get("data") or {}).get("list") or []
    if rows:
        return rows[0]["id"]
    patient = create_patient(admin, "RGADM", RUN + "A")
    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    n = 0
    while not beds["data"]:
        n += 1
        call("POST", "/inp/beds", admin, {
            "wardId": 1, "bedNo": "RG-" + RUN + "-" + str(n), "chargeItemId": 10},
            idem="rg-bed-" + RUN + str(n))
        st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = beds["data"][0]
    st, r = call("POST", "/inp/admissions", admin, {
        "patientId": patient["id"], "deptId": 1, "wardId": bed["wardId"], "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "回归",
        "depositAmount": 1000, "payMethod": 1}, idem="rg-adm-" + RUN)
    assert r.get("code") == "OK", r
    return r["data"]["id"]


def main():
    admin = login("admin")
    doctor = login("dr.li")
    pharmacist = login("pharm.zhao")
    ris = login("ris.zhang")
    st, roles = call("GET", "/system/roles", admin)
    role_id = next(x for x in roles["data"] if x["roleCode"] == "CASHIER")["id"]
    cashier_name = "rg" + RUN
    st, r = call("POST", "/system/users", admin, {
        "username": cashier_name, "realName": "RegCashier", "password": PASSWORD,
        "roleIds": [role_id]}, idem="rg-user-" + RUN)
    assert r.get("code") == "OK", r
    cashier = login(cashier_name)

    # ================= A. 负数量/零数量守卫 =================
    _, vid = start_visit(cashier, doctor, "RGNEG", RUN + "N")
    neg = {"items": [
        {"drugId": 1, "dosage": "0.25g", "frequency": "tid", "usageRoute": "oral",
         "days": 7, "quantity": 1},
        {"drugId": 2, "dosage": "0.25g", "frequency": "tid", "usageRoute": "oral",
         "days": 7, "quantity": -9}]}
    st, r = call("POST", "/clinic/visits/%d/prescriptions" % vid, doctor, neg, idem="rg-neg-" + RUN)
    check("A1. 负数量处方被拒", st == 400 and r.get("code") == "A0001"
          and "quantity" in json.dumps(r.get("data"), ensure_ascii=False), r)
    zero = {"items": [{"drugId": 1, "dosage": "0.25g", "frequency": "tid",
                       "usageRoute": "oral", "days": 7, "quantity": 0}]}
    st, r = call("POST", "/clinic/visits/%d/prescriptions" % vid, doctor, zero, idem="rg-zero-" + RUN)
    check("A2. 零数量处方被拒", st == 400 and r.get("code") == "A0001", r)
    huge = {"items": [{"drugId": 1, "dosage": "0.25g", "frequency": "tid",
                       "usageRoute": "oral", "days": 7, "quantity": 100000000000}]}
    st, r = call("POST", "/clinic/visits/%d/prescriptions" % vid, doctor, huge, idem="rg-huge-" + RUN)
    check("A3. 超精度数量处方被拒", st == 400 and r.get("code") == "A0001", r)
    before = visit_detail(doctor, vid)["prescriptions"]
    check("A4. 非法处方未落库", not before, before)
    ok = {"items": [{"drugId": 1, "dosage": "0.25g", "frequency": "tid",
                     "usageRoute": "oral", "days": 7, "quantity": 1}]}
    st, r = call("POST", "/clinic/visits/%d/prescriptions" % vid, doctor, ok, idem="rg-pos-" + RUN)
    check("A5. 正常处方仍可开（阳性对照）",
          r.get("code") == "OK" and float(r["data"]["totalAmount"]) > 0, r)

    adm = ensure_admission(admin)
    st, dl = call("GET", "/basedata/drugs?status=1", admin)
    drugs = (dl["data"]["list"] if isinstance(dl["data"], dict) else dl["data"]) or []
    drug_id = drugs[0]["id"]
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": adm, "orderClass": 2, "category": 1, "frequency": "qd",
        "items": [{"drugId": drug_id, "quantity": -3}]}, idem="rg-ioneg-" + RUN)
    check("A6. 负数量住院医嘱被拒", st == 400 and r.get("code") == "A0001", r)
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": adm, "orderClass": 2, "category": 1, "frequency": "qd",
        "items": [{"drugId": drug_id, "quantity": 0}]}, idem="rg-iozero-" + RUN)
    check("A7. 零数量住院医嘱被拒", st == 400 and r.get("code") == "A0001", r)
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": adm, "orderClass": 2, "category": 1, "frequency": "qd",
        "items": [{"drugId": drug_id, "quantity": 1}]}, idem="rg-iopos-" + RUN)
    check("A8. 正常住院医嘱仍可开（阳性对照）", r.get("code") == "OK", r)

    # ================= B. 退费 vs 发药并发 =================
    race_both = 0
    race_bad = []
    for i in range(4):
        suffix = RUN + "R%d" % i
        _, vid = start_visit(cashier, doctor, "RGRACE", suffix)
        st, r = call("POST", "/clinic/visits/%d/prescriptions" % vid, doctor, {
            "items": [{"drugId": 1, "dosage": "0.25g", "frequency": "tid",
                       "usageRoute": "oral", "days": 7, "quantity": 2}]},
            idem="rg-rx-" + suffix)
        assert r.get("code") == "OK", r
        rx_id = r["data"]["prescriptionId"]
        bill_id, details = complete_and_bill(cashier, doctor, vid, suffix)
        rx_details = [d for d in details if d.get("feeType") == 7]
        assert rx_details, details
        st, r = call("POST", "/pharmacy/prescriptions/%d/review" % rx_id, pharmacist,
                     {"pass": True, "comment": "ok"}, idem="rg-rev-" + suffix)
        assert r.get("code") == "OK", r
        payload = {"billId": bill_id, "reason": "race refund",
                   "details": [{"chargeDetailId": d["id"], "refundQuantity": d["quantity"]}
                               for d in rx_details]}
        barrier = threading.Barrier(2)
        out = {}

        def do_refund():
            barrier.wait()
            out["refund"] = call("POST", "/billing/refunds", cashier, payload,
                                 idem="rg-rf-" + suffix)

        def do_dispense():
            barrier.wait()
            out["dispense"] = call("POST", "/pharmacy/prescriptions/%d/dispense" % rx_id,
                                   pharmacist, idem="rg-dsp-" + suffix)

        t1 = threading.Thread(target=do_refund)
        t2 = threading.Thread(target=do_dispense)
        t1.start()
        t2.start()
        t1.join()
        t2.join()
        rc = out["refund"][1].get("code")
        dc = out["dispense"][1].get("code")
        if rc == "OK" and dc == "OK":
            race_both += 1
        rx = next(x for x in visit_detail(doctor, vid)["prescriptions"] if x["id"] == rx_id)
        st, orders = call("GET", "/pharmacy/dispense-orders?pageSize=200", pharmacist)
        has_order = any(o.get("prescriptionId") == rx_id for o in orders["data"]["list"])
        if rc == "OK":
            good = dc != "OK" and rx["status"] == 50 and rx.get("voidReason") == "退费" and not has_order
            if not good:
                race_bad.append({"iter": i, "rc": rc, "dc": dc, "rx": rx, "order": has_order})
        else:
            good = dc == "OK" and rc == "B3004" and rx["status"] == 30 and has_order
            if not good:
                race_bad.append({"iter": i, "rc": rc, "dc": dc, "rx": rx, "order": has_order})
        print("iter=%d refund=%s dispense=%s rxStatus=%s order=%s" % (i, rc, dc, rx["status"], has_order))
    check("B1. 退费与发药不可同时成功", race_both == 0, race_both)
    check("B2. 结果与处方/发药单一致", not race_bad, race_bad)

    # ================= C. 检查费退费门禁 =================
    # C1: 未预约 → 退费成功并作废 RIS，之后不可预约
    _, vid = start_visit(cashier, doctor, "RGEXA", RUN + "E1")
    st, r = call("POST", "/clinic/visits/%d/exam-applications" % vid, doctor,
                 {"chargeItemId": 5}, idem="rg-ex-" + RUN + "E1")
    assert r.get("code") == "OK", r
    bill_id, details = complete_and_bill(cashier, doctor, vid, RUN + "E1")
    exam_details = [d for d in details if d.get("sourceType") == 3]
    assert exam_details, details
    patient_id = visit_detail(doctor, vid)["patient"]["id"]
    req = find_ris(ris, patient_id, vid)
    assert req and req["status"] == 10, req
    st, r = call("POST", "/billing/refunds", cashier, {
        "billId": bill_id, "reason": "unbooked exam refund",
        "details": [{"chargeDetailId": d["id"], "refundQuantity": d["quantity"]}
                    for d in exam_details]}, idem="rg-exrf-" + RUN + "E1")
    check("C1. 未预约检查费可退", r.get("code") == "OK", r)
    exam_rows = call("GET", "/clinic/visits/%d/exam-applications" % vid, doctor)[1]["data"]
    ris_after = find_ris(ris, patient_id, vid)
    check("C2. 退费后检查申请已作废", exam_rows and exam_rows[0]["status"] == 50, exam_rows)
    check("C3. 退费后 RIS 单同步作废(50)", ris_after and ris_after["status"] == 50, ris_after)
    st, r = call("POST", "/ris/requests/%d/appoint" % req["id"], ris,
                 {"deviceId": 1, "apptTime": time.strftime("%Y-%m-%d") + "T14:00:00"},
                 idem="rg-exap-" + RUN + "E1")
    check("C4. 已退费 RIS 不能再预约", r.get("code") != "OK", r)

    # C2: 已预约 → 退费被拒，检查保持可执行
    _, vid = start_visit(cashier, doctor, "RGEXB", RUN + "E2")
    st, r = call("POST", "/clinic/visits/%d/exam-applications" % vid, doctor,
                 {"chargeItemId": 5}, idem="rg-ex-" + RUN + "E2")
    assert r.get("code") == "OK", r
    bill_id, details = complete_and_bill(cashier, doctor, vid, RUN + "E2")
    exam_details = [d for d in details if d.get("sourceType") == 3]
    patient_id = visit_detail(doctor, vid)["patient"]["id"]
    req = find_ris(ris, patient_id, vid)
    assert req, "RIS 单未生成"
    st, r = call("POST", "/ris/requests/%d/appoint" % req["id"], ris,
                 {"deviceId": 1, "apptTime": time.strftime("%Y-%m-%d") + "T15:00:00"},
                 idem="rg-exap-" + RUN + "E2")
    assert r.get("code") == "OK", r
    st, r = call("POST", "/billing/refunds", cashier, {
        "billId": bill_id, "reason": "booked exam refund",
        "details": [{"chargeDetailId": d["id"], "refundQuantity": d["quantity"]}
                    for d in exam_details]}, idem="rg-exrf-" + RUN + "E2")
    check("C5. 已预约检查费退费被拒(B3003)", r.get("code") == "B3003"
          and "已预约" in (r.get("message") or ""), r)
    exam_rows = call("GET", "/clinic/visits/%d/exam-applications" % vid, doctor)[1]["data"]
    ris_after = find_ris(ris, patient_id, vid)
    check("C6. 拒绝退费后检查申请保持已收费(20)", exam_rows and exam_rows[0]["status"] == 20, exam_rows)
    check("C7. 拒绝退费后 RIS 保持已预约(20)", ris_after and ris_after["status"] == 20, ris_after)
    st, r = call("POST", "/ris/requests/%d/start" % req["id"], ris, idem="rg-exst-" + RUN + "E2")
    check("C8. 拒绝退费后检查仍可正常开始", r.get("code") == "OK", r)

    # C3: 已开始 → 退费被拒
    _, vid = start_visit(cashier, doctor, "RGEXC", RUN + "E3")
    st, r = call("POST", "/clinic/visits/%d/exam-applications" % vid, doctor,
                 {"chargeItemId": 5}, idem="rg-ex-" + RUN + "E3")
    assert r.get("code") == "OK", r
    bill_id, details = complete_and_bill(cashier, doctor, vid, RUN + "E3")
    exam_details = [d for d in details if d.get("sourceType") == 3]
    patient_id = visit_detail(doctor, vid)["patient"]["id"]
    req = find_ris(ris, patient_id, vid)
    st, r = call("POST", "/ris/requests/%d/appoint" % req["id"], ris,
                 {"deviceId": 1, "apptTime": time.strftime("%Y-%m-%d") + "T16:00:00"},
                 idem="rg-exap-" + RUN + "E3")
    assert r.get("code") == "OK", r
    st, r = call("POST", "/ris/requests/%d/start" % req["id"], ris, idem="rg-exst-" + RUN + "E3")
    assert r.get("code") == "OK", r
    st, r = call("POST", "/billing/refunds", cashier, {
        "billId": bill_id, "reason": "started exam refund",
        "details": [{"chargeDetailId": d["id"], "refundQuantity": d["quantity"]}
                    for d in exam_details]}, idem="rg-exrf-" + RUN + "E3")
    check("C9. 已开始检查退费被拒(B3003)", r.get("code") == "B3003"
          and "已开始" in (r.get("message") or ""), r)
    ris_after = find_ris(ris, patient_id, vid)
    exam_rows = call("GET", "/clinic/visits/%d/exam-applications" % vid, doctor)[1]["data"]
    check("C10. 拒绝后 RIS 仍为检查中(30)", ris_after and ris_after["status"] == 30, ris_after)
    check("C11. 拒绝后检查申请未被作废", exam_rows and exam_rows[0]["status"] == 20, exam_rows)

    # C4: 退费 vs 预约并发（行锁互斥，3 轮）
    c_both = 0
    c_bad = []
    for i in range(3):
        suffix = RUN + "E4%d" % i
        _, vid = start_visit(cashier, doctor, "RGEXR", suffix)
        st, r = call("POST", "/clinic/visits/%d/exam-applications" % vid, doctor,
                     {"chargeItemId": 5}, idem="rg-ex-" + suffix)
        assert r.get("code") == "OK", r
        bill_id, details = complete_and_bill(cashier, doctor, vid, suffix)
        exam_details = [d for d in details if d.get("sourceType") == 3]
        patient_id = visit_detail(doctor, vid)["patient"]["id"]
        req = find_ris(ris, patient_id, vid)
        assert req and req["status"] == 10, req
        payload = {"billId": bill_id, "reason": "race exam refund",
                   "details": [{"chargeDetailId": d["id"], "refundQuantity": d["quantity"]}
                               for d in exam_details]}
        barrier = threading.Barrier(2)
        out = {}

        def do_refund():
            barrier.wait()
            out["refund"] = call("POST", "/billing/refunds", cashier, payload,
                                 idem="rg-exrf-" + suffix)

        def do_appoint():
            barrier.wait()
            out["appoint"] = call("POST", "/ris/requests/%d/appoint" % req["id"], ris,
                                  {"deviceId": 1,
                                   "apptTime": time.strftime("%Y-%m-%d") + "T17:00:00"},
                                  idem="rg-exap-" + suffix)

        t1 = threading.Thread(target=do_refund)
        t2 = threading.Thread(target=do_appoint)
        t1.start()
        t2.start()
        t1.join()
        t2.join()
        rc = out["refund"][1].get("code")
        ac = out["appoint"][1].get("code")
        after = find_ris(ris, patient_id, vid)
        if rc == "OK" and ac == "OK":
            c_both += 1
        if rc == "OK":
            good = ac != "OK" and after and after["status"] == 50
        else:
            good = ac == "OK" and rc == "B3003" and after and after["status"] == 20
        if not good:
            c_bad.append({"iter": i, "refund": rc, "appoint": ac, "ris": after})
        print("exam-race iter=%d refund=%s appoint=%s risStatus=%s"
              % (i, rc, ac, after and after["status"]))
    check("C12. 退费与预约不可同时成功", c_both == 0, c_both)
    check("C13. 并发结果与 RIS 状态一致", not c_bad, c_bad)

    failed = [x for x in RESULTS if not x[1]]
    print("\n===== guards regression: %d/%d passed, %d failed =====" %
          (len(RESULTS) - len(failed), len(RESULTS), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
