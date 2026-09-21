#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HIS 三期第二批端到端验收脚本（手术麻醉 ORIS + 影像 RIS + 急诊五大中心 EMC）
用法: python e2e_phase3b.py [BASE_URL]
可重复执行（唯一测试患者）。前置：一期/二期/三期一批 e2e 通过；V17~V19 迁移已应用。
"""
import json
import sys
import time
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime, timedelta

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


def charge_item_by_category(token, category):
    st, r = call("GET", "/basedata/charge-items?category=%d&status=1" % category, token)
    if r.get("code") == "OK" and r["data"]:
        return r["data"][0]["id"], r["data"][0]["itemName"]
    return None, None


def main():
    uid = str(int(time.time() * 1000))[-8:]
    id_card = "34010519950101" + uid[-4:]
    phone = "138" + uid
    today = time.strftime("%Y-%m-%d")
    seq_base = int(uid[-1]) % 4 + 1  # 台次按轮次错开：uk_schedule_slot 唯一索引不含状态，已完成排台行仍占位

    admin = login("admin")
    doctor = login("dr.li")
    doctor2 = login("dr.wang")
    nurse = login("nurse.wang")
    or_nurse = login("or.nurse")
    ris_tech = login("ris.zhang")
    emc_nurse = login("emc.li")
    cashier = login("cashier.li")
    check("1. 角色登录（手术室护士/影像技师/急诊护士）",
          all([admin, doctor, doctor2, nurse, or_nurse, ris_tech, emc_nurse, cashier]))

    # 建档 + 入院（复用三期一批模式）
    call("POST", "/patients", admin, {"name": "三期乙患者" + uid, "gender": 2,
         "birthDate": "1988-03-20", "idCardNo": id_card, "phone": phone}, idem="p3b-pt-" + uid)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("三期乙患者" + uid), admin)
    patient_id = pl["data"]["list"][0]["id"]
    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    n = 0
    while not beds["data"]:
        n += 1
        call("POST", "/inp/beds", admin, {"wardId": 1, "bedNo": "P3B-" + uid + "-" + str(n), "chargeItemId": 10},
             idem="p3b-bed-" + uid + str(n))
        st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = beds["data"][0]
    st, r = call("POST", "/inp/admissions", admin, {
        "patientId": patient_id, "deptId": 1, "wardId": bed["wardId"], "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "急性阑尾炎",
        "depositAmount": 5000, "payMethod": 1}, idem="p3b-adm-" + uid)
    check("2. 入院登记成功", r["code"] == "OK", r)
    admission_id = r["data"]["id"]

    # ================= ORIS 手术麻醉闭环 =================
    st, op_items = call("GET", "/basedata/charge-items?category=9&status=1", admin)
    surgery_item = next((i for i in op_items["data"] if i["itemName"] == "阑尾切除术"), None)
    st, an_items = call("GET", "/basedata/charge-items?category=10&status=1", admin)
    anes_item = next((i for i in an_items["data"] if i["itemName"] == "全身麻醉"), None)
    check("3. 手术/麻醉收费项目就绪（V19）", surgery_item and anes_item,
          (surgery_item or {}).get("itemName"))

    st, r = call("POST", "/ors/requests", doctor, {
        "admissionId": admission_id, "patientId": patient_id,
        "surgeryName": "阑尾切除术", "diagnosis": "急性阑尾炎", "plannedDate": today,
        "anesthesiaMethod": 1,
        "surgeryItemId": surgery_item["id"], "anesthesiaItemId": anes_item["id"]},
        idem="p3b-or1-" + uid)
    check("4. 创建手术申请", r["code"] == "OK", r)
    st, ol = call("GET", "/ors/requests?admissionId=%d" % admission_id, doctor)
    or_a = [o for o in ol["data"]["list"] if o["status"] == 10][0]
    or_a_id = or_a["id"]
    check("5. 申请号前缀 SS（待审核）", or_a["requestNo"].startswith("SS"), or_a["requestNo"])

    st, r = call("POST", "/ors/requests/%d/review" % or_a_id, doctor, {"approved": True}, idem="p3b-or1rv-" + uid)
    check("6. 审核通过(20)", r["code"] == "OK", r)

    st, r = call("POST", "/ors/requests/%d/schedule" % or_a_id, or_nurse, {
        "roomId": 1, "surgeryDate": today, "seqNo": seq_base, "surgeonId": 2}, idem="p3b-or1sc-" + uid)
    check("7. 排台成功(30)", r["code"] == "OK" and str(r.get("data", "")).startswith("PT"), r)

    # 单B：门禁测试（排台后不核查直接开始）
    st, r = call("POST", "/ors/requests", doctor, {
        "admissionId": admission_id, "patientId": patient_id,
        "surgeryName": "清创缝合术", "diagnosis": "外伤", "plannedDate": today,
        "anesthesiaMethod": 4}, idem="p3b-or2-" + uid)
    st, ol = call("GET", "/ors/requests?admissionId=%d&status=10" % admission_id, doctor)
    or_b_id = [o for o in ol["data"]["list"] if o["surgeryName"] == "清创缝合术"][0]["id"]
    call("POST", "/ors/requests/%d/review" % or_b_id, doctor, {"approved": True}, idem="p3b-or2rv-" + uid)
    call("POST", "/ors/requests/%d/schedule" % or_b_id, or_nurse,
         {"roomId": 2, "surgeryDate": today, "seqNo": seq_base + 1, "surgeonId": 2}, idem="p3b-or2sc-" + uid)
    st, r = call("POST", "/ors/requests/%d/start" % or_b_id, or_nurse, idem="p3b-or2st-" + uid)
    check("8. 门禁：缺核查单禁止开始手术", r["code"] != "OK", r)

    # 单C：槽位冲突
    st, r = call("POST", "/ors/requests", doctor, {
        "admissionId": admission_id, "patientId": patient_id,
        "surgeryName": "肿块切除术", "diagnosis": "体表肿块", "plannedDate": today,
        "anesthesiaMethod": 4}, idem="p3b-or3-" + uid)
    st, ol = call("GET", "/ors/requests?admissionId=%d&status=10" % admission_id, doctor)
    or_c_id = [o for o in ol["data"]["list"] if o["surgeryName"] == "肿块切除术"][0]["id"]
    call("POST", "/ors/requests/%d/review" % or_c_id, doctor, {"approved": True}, idem="p3b-or3rv-" + uid)
    st, r = call("POST", "/ors/requests/%d/schedule" % or_c_id, or_nurse,
                 {"roomId": 1, "surgeryDate": today, "seqNo": seq_base, "surgeonId": 2}, idem="p3b-or3sc-" + uid)
    check("9. 同手术间同日同台次冲突拦截", r["code"] != "OK", r)

    # 单A：三方核查（麻醉前 + 切皮前）
    checklist = [{"item": "患者身份核对", "result": True}, {"item": "手术部位标记确认", "result": True},
                 {"item": "术前诊断确认", "result": True}, {"item": "知情同意已签署", "result": True},
                 {"item": "过敏史核对", "result": True}, {"item": "禁食确认", "result": True},
                 {"item": "麻醉设备就绪", "result": True}, {"item": "备血确认", "result": True},
                 {"item": "影像资料备齐", "result": True}, {"item": "保温设备就绪", "result": True}]
    st, r = call("POST", "/ors/requests/%d/checks" % or_a_id, or_nurse,
                 {"checkType": 1, "items": checklist, "checker2Id": 2}, idem="p3b-ck1-" + uid)
    check("10. 麻醉前核查登记", r["code"] == "OK", r)
    st, r = call("POST", "/ors/requests/%d/checks" % or_a_id, doctor,
                 {"checkType": 2, "items": checklist, "checker2Id": 2}, idem="p3b-ck2-" + uid)
    check("11. 切皮前核查登记（双人不同人）", r["code"] == "OK", r)
    st, r = call("POST", "/ors/requests/%d/checks" % or_a_id, doctor,
                 {"checkType": 1, "items": checklist, "checker2Id": 2}, idem="p3b-ck1b-" + uid)
    check("12. 重复核查单拦截", r["code"] != "OK", r)

    st, r = call("POST", "/ors/requests/%d/start" % or_a_id, or_nurse, idem="p3b-or1st-" + uid)
    check("13. 门禁通过开始手术(40)", r["code"] == "OK", r)
    st, r = call("POST", "/ors/requests/%d/anesthesia" % or_a_id, doctor,
                 {"asaGrade": 2, "drugNote": "丙泊酚100mg 诱导，七氟烷维持"}, idem="p3b-anes-" + uid)
    check("14. 麻醉记录保存(前缀MZ)", r["code"] == "OK" and str(r.get("data", "")).startswith("MZ"), r)
    st, r = call("POST", "/ors/requests/%d/finish" % or_a_id, or_nurse, idem="p3b-or1fi-" + uid)
    check("15. 手术结束(50 复苏中)", r["code"] == "OK", r)

    st, r = call("POST", "/ors/requests/%d/leave" % or_a_id, or_nurse,
                 {"recoveryScore": 9, "destination": 1}, idem="p3b-or1lv-" + uid)
    check("16. 门禁：离室前核查缺失禁止离室", r["code"] != "OK", r)
    st, r = call("POST", "/ors/requests/%d/checks" % or_a_id, or_nurse,
                 {"checkType": 3, "items": checklist, "checker2Id": 2}, idem="p3b-ck3-" + uid)
    check("17. 离室前核查登记", r["code"] == "OK", r)
    st, r = call("POST", "/ors/requests/%d/leave" % or_a_id, or_nurse,
                 {"recoveryScore": 9, "destination": 1, "followupNote": "回病房继续观察"}, idem="p3b-or1lv2-" + uid)
    check("18. 离室登记(60 已完成)", r["code"] == "OK", r)

    st, r = call("POST", "/ors/requests/%d/complete" % or_a_id, or_nurse, idem="p3b-or1cp-" + uid)
    check("19. 完成关档自动记账（手术费+麻醉费）", r["code"] == "OK" and
          isinstance(r.get("data"), dict) and "surgeryFee" in r["data"] and "anesthesiaFee" in r["data"], r)
    st, r = call("POST", "/ors/requests/%d/complete" % or_a_id, or_nurse, idem="p3b-or1cp2-" + uid)
    check("20. 重复关档拦截（幂等）", r["code"] != "OK", r)
    st, r = call("GET", "/inp/admissions/%d/daily-fees" % admission_id, cashier)
    fee_names = [i["itemName"] for g in r["data"] for i in g["items"]]
    check("21. 住院费用清单出现手术费/麻醉费", any("阑尾切除" in x for x in fee_names) and any("全身麻醉" in x for x in fee_names), fee_names)

    # ================= RIS 影像检查闭环 =================
    exam_item_id, exam_item_name = charge_item_by_category(admin, 3)
    check("22. 检查费项目就绪", exam_item_id is not None, exam_item_name)
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": admission_id, "orderClass": 2, "category": 2,
        "items": [{"chargeItemId": exam_item_id, "quantity": 1}]}, idem="p3b-exam-" + uid)
    check("23. 开检查医嘱", r["code"] == "OK", r)
    st, ol = call("GET", "/doc/orders?admissionId=%d&category=2" % admission_id, doctor)
    exam_order_id = ol["data"]["list"][0]["id"]
    st, d = call("GET", "/doc/orders/%d" % exam_order_id, doctor)
    exec_item = [e for e in d["data"]["executions"] if e["execType"] == 2][0]
    st, r = call("POST", "/doc/executions/%d/do" % exec_item["id"], nurse, idem="p3b-examex-" + uid)
    check("24. 执行检查医嘱（自动生成 RIS 申请）", r["code"] == "OK", r)
    st, reqs = call("GET", "/ris/requests?admissionId=%d" % admission_id, ris_tech)
    ris_req = reqs["data"]["list"][0]
    ris_id = ris_req["id"]
    modality = ris_req["modality"]
    check("25. RIS 申请自动生成(待预约, 前缀JC)", ris_req["status"] == 10 and ris_req["requestNo"].startswith("JC"), ris_req)

    wrong_device = 3 if modality in (1, 2) else 1
    right_device = {1: 1, 2: 2, 3: 3}.get(modality, 1)
    st, r = call("POST", "/ris/requests/%d/appoint" % ris_id, ris_tech,
                 {"deviceId": wrong_device, "apptTime": today + "T14:00:00"}, idem="p3b-apw-" + uid)
    check("26. 设备类别不匹配预约拦截", r["code"] != "OK", r)
    st, r = call("POST", "/ris/requests/%d/appoint" % ris_id, ris_tech,
                 {"deviceId": right_device, "apptTime": today + "T14:00:00"}, idem="p3b-ap-" + uid)
    check("27. 预约成功(20)", r["code"] == "OK", r)
    st, r = call("POST", "/ris/requests/%d/start" % ris_id, ris_tech, idem="p3b-rs-" + uid)
    check("28. 开始检查(30)", r["code"] == "OK", r)
    st, r = call("POST", "/ris/requests/%d/images" % ris_id, ris_tech, {"fetch": True}, idem="p3b-img-" + uid)
    check("29. Mock 影像归档(前缀IM)", r["code"] == "OK" and str(r.get("data", "")).startswith("IM"), r)
    st, r = call("POST", "/ris/requests/%d/finish" % ris_id, ris_tech, idem="p3b-rf-" + uid)
    check("30. 检查完成", r["code"] == "OK", r)

    st, r = call("POST", "/ris/reports", ris_tech,
                 {"requestId": ris_id, "finding": "未见异常", "conclusion": "正常"}, idem="p3b-rw0-" + uid)
    check("31. 技师无报告书写权限(403)", r["code"] != "OK", r)

    critical_sign = "主动脉夹层征象（演练）" + uid[-3:]
    st, r = call("POST", "/ris/reports", doctor, {
        "requestId": ris_id, "finding": "检查部位未见明显异常结构，个别层面显示欠清。",
        "conclusion": "建议随访复查", "criticalSign": critical_sign}, idem="p3b-rw-" + uid)
    check("32. 医生书写报告(前缀XD)", r["code"] == "OK" and str(r.get("data", "")).startswith("XD"), r)
    st, reports = call("GET", "/ris/reports?status=10", doctor)
    ris_report = [x for x in reports["data"]["list"] if x["requestId"] == ris_id][0]
    ris_report_id = ris_report["id"]
    st, r = call("POST", "/ris/reports/%d/review" % ris_report_id, doctor, {"approved": True}, idem="p3b-rr0-" + uid)
    check("33. 双签：自审自签拦截", r["code"] != "OK", r)
    st, r = call("POST", "/ris/reports/%d/review" % ris_report_id, doctor2, {"approved": True}, idem="p3b-rr-" + uid)
    check("34. 上级审核发布(20)", r["code"] == "OK", r)
    st, reqs = call("GET", "/ris/requests?admissionId=%d" % admission_id, ris_tech)
    check("35. 申请联动已报告(40)", reqs["data"]["list"][0]["status"] == 40, reqs["data"]["list"][0])

    # 危急值 source=2 复用闭环
    st, alerts = call("GET", "/alerts?status=10", doctor)
    hit = [a for a in alerts["data"]["list"] if a.get("criticalValue") == critical_sign]
    check("36. 危急值自动生成(source=2)", len(hit) == 1, [a.get("alertNo") for a in hit])
    alert_id = hit[0]["id"]
    st, r = call("POST", "/alerts/%d/notify" % alert_id, ris_tech, idem="p3b-aln-" + uid)
    check("37. 危急值通知登记", r["code"] == "OK", r)
    st, r = call("POST", "/alerts/%d/confirm" % alert_id, doctor, idem="p3b-alc-" + uid)
    check("38. 危急值医生确认", r["code"] == "OK", r)
    st, r = call("POST", "/alerts/%d/close" % alert_id, doctor,
                 {"handleNote": "急诊手术干预（演练）"}, idem="p3b-alx-" + uid)
    check("39. 危急值处置闭环", r["code"] == "OK", r)

    # ================= EMC 急诊五大中心 =================
    st, r = call("POST", "/emc/triage", emc_nurse, {
        "patientId": patient_id, "chiefComplaint": "胸痛30分钟（演练）", "bodyTemp": 36.8,
        "pulse": 102, "respiration": 22, "bloodPressure": "150/90", "spo2": 95,
        "triageLevel": 2, "centerType": 1, "greenChannel": 1}, idem="p3b-fz1-" + uid)
    check("40. 分诊登记(2级危重, 胸痛, 绿通)", r["code"] == "OK" and str(r.get("data", "")).startswith("FZ"), r)
    st, tl = call("GET", "/emc/triage?patientId=%d" % patient_id, emc_nurse)
    triage_a = tl["data"]["list"][0]

    st, r = call("POST", "/emc/triage", emc_nurse, {
        "patientId": patient_id, "chiefComplaint": "头晕（演练）", "triageLevel": 3, "centerType": 2},
        idem="p3b-fz2-" + uid)
    st, tl = call("GET", "/emc/triage?patientId=%d&status=1" % patient_id, emc_nurse)
    triage_b = [t for t in tl["data"]["list"] if t["centerType"] == 2][0]
    st, r = call("POST", "/emc/visits", emc_nurse, {"triageId": triage_b["id"], "centerType": 1},
                 idem="p3b-jzb-" + uid)
    check("41. 登记中心与分诊预判不一致拦截", r["code"] != "OK", r)

    st, r = call("POST", "/emc/visits", emc_nurse, {"triageId": triage_a["id"], "centerType": 1},
                 idem="p3b-jz-" + uid)
    check("42. 胸痛中心登记(前缀JZ)", r["code"] == "OK" and str(r.get("data", "")).startswith("JZ"), r)
    st, vl = call("GET", "/emc/visits?patientId=%d" % patient_id, emc_nurse)
    emc_visit = [v for v in vl["data"]["list"] if v["status"] == 10][0]
    emc_visit_id = emc_visit["id"]
    start_time = emc_visit["startTime"]

    now = datetime.now().strftime("%Y-%m-%dT%H:%M:%S")
    st, r = call("POST", "/emc/visits/%d/timepoints" % emc_visit_id, emc_nurse,
                 {"nodeCode": "XT_ECG", "nodeTime": now, "note": "演练节点"}, idem="p3b-tp1-" + uid)
    check("43. 时间节点录入(XT_ECG)", r["code"] == "OK", r)
    st, r = call("POST", "/emc/visits/%d/timepoints" % emc_visit_id, emc_nurse,
                 {"nodeCode": "XT_ECG", "nodeTime": now}, idem="p3b-tp1b-" + uid)
    check("44. 重复节点拦截", r["code"] != "OK", r)
    st, r = call("POST", "/emc/visits/%d/timepoints" % emc_visit_id, emc_nurse,
                 {"nodeCode": "ZZ_CT", "nodeTime": now}, idem="p3b-tp2-" + uid)
    check("45. 跨中心节点拦截", r["code"] != "OK", r)
    st, r = call("POST", "/emc/visits/%d/timepoints" % emc_visit_id, emc_nurse,
                 {"nodeCode": "NO_SUCH_NODE", "nodeTime": now}, idem="p3b-tp3-" + uid)
    check("46. 字典外节点拦截", r["code"] != "OK", r)

    future = (datetime.now() + timedelta(minutes=10)).strftime("%Y-%m-%dT%H:%M:%S")
    st, r = call("POST", "/emc/visits/%d/timepoints" % emc_visit_id, emc_nurse,
                 {"nodeCode": "XT_FMC", "nodeTime": future}, idem="p3b-tp4-" + uid)
    check("47. 节点录入(XT_FMC 偏迟用于超时演示)", r["code"] == "OK", r)
    st, r = call("GET", "/emc/visits/%d/timeline" % emc_visit_id, emc_nurse)
    rows = {x["nodeCode"]: x for x in r["data"]}
    check("48. 时间轴达标计算（XT_ECG 达标 / XT_FMC 超时预警）",
          rows.get("XT_ECG", {}).get("onTarget") is True and rows.get("XT_FMC", {}).get("onTarget") is False, rows)

    st, r = call("POST", "/emc/visits/%d/link" % emc_visit_id, emc_nurse,
                 {"admissionId": admission_id}, idem="p3b-lk-" + uid)
    check("49. 后补关联住院", r["code"] == "OK", r)
    st, r = call("POST", "/emc/visits/%d/close" % emc_visit_id, emc_nurse, {"outcome": 1}, idem="p3b-cl-" + uid)
    check("50. 病例关档(转归收住院)", r["code"] == "OK", r)
    st, r = call("POST", "/emc/visits/%d/timepoints" % emc_visit_id, emc_nurse,
                 {"nodeCode": "XT_TPN", "nodeTime": now}, idem="p3b-tp5-" + uid)
    check("51. 关档后节点录入拦截", r["code"] != "OK", r)

    st, r = call("GET", "/emc/stats", admin)
    chest = next((c for c in r["data"] if c["centerType"] == 1), {})
    ecg_node = next((n for n in chest.get("nodes", []) if n["nodeCode"] == "XT_ECG"), {})
    check("52. 达标率统计（胸痛病例≥1, XT_ECG 达标≥1）",
          chest.get("caseCount", 0) >= 1 and ecg_node.get("metCount", 0) >= 1, chest)

    failed = [n for n, ok, _ in results if not ok]
    print("\n===== 三期第二批 e2e 结果: %d/%d 通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:")
        for name in failed:
            print("  - " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
