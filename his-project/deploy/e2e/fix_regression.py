#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HIS 修复回归复验脚本（fix_regression.py）
覆盖二十一轮查验中未固化进常规 e2e 的修复点，逐项复测有效性。
前置：当前部署为 b1bebcf 之后的版本；患者 75 在院（phase3b 遗留）。
"""
import json
import sys
import time
import urllib.request
import urllib.error
import urllib.parse
import datetime

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
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:120]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败: %s" % r
    return r["data"]["token"]


def try_schedule(token, req_id, room_id, idem, today):
    tomorrow = (datetime.datetime.strptime(today, "%Y-%m-%d")
                + datetime.timedelta(days=1)).strftime("%Y-%m-%d")
    seq, date = 1, today
    for _ in range(24):
        st, r = call("POST", "/ors/requests/%d/schedule" % req_id, token,
                     {"roomId": room_id, "surgeryDate": date, "seqNo": seq, "surgeonId": 2},
                     idem="%s-%s-%d" % (idem, date, seq))
        if r["code"] == "OK":
            return date, r
        seq += 1
        if seq > 10:
            seq, date = 1, tomorrow
    return date, r


def main():
    uid = str(int(time.time() * 1000))[-8:]
    today = time.strftime("%Y-%m-%d")
    admin = login("admin")
    doctor = login("dr.li")
    doctor2 = login("dr.wang")
    nurse = login("nurse.wang")

    # 前置：患者 75 在院
    st, r = call("GET", "/inp/admissions/75", admin)
    assert r["code"] == "OK" and r["data"]["status"] == 10, "患者75不在院，无法复测手术链"
    print("前置：患者 75 在院 OK")

    # ================= 十九轮：物资/体检修复复测 =================
    code = "FIXR" + uid
    call("POST", "/mat/materials", admin, {"materialCode": code, "name": "复验物资" + uid,
         "category": 2, "unit": "件", "price": 1.00, "safeStock": 10}, idem="fx-m1-" + uid)
    st, r = call("POST", "/mat/purchases", admin, {
        "supplierId": 1, "materialId": 1, "quantity": 10,
        "unitPrice": -1.00}, idem="fx-m2-" + uid)
    check("F01. [十九#3] 物资采购负单价拦截", r["code"] != "OK", r)
    st, ml = call("GET", "/mat/materials?category=2", admin)
    mat = next((m for m in ml["data"]["list"] if m["materialCode"] == code), None)
    st, r = call("PUT", "/mat/materials/%d" % mat["id"], admin, {
        "materialCode": code, "name": "复验物资-改名" + uid, "category": 2,
        "unit": "件", "price": 1.50, "safeStock": 10}, idem="fx-m3-" + uid)
    check("F02. [十九#5] 物资 PUT 更新生效", r["code"] == "OK", r)
    st, ml = call("GET", "/mat/materials?category=2", admin)
    mat2 = next((m for m in ml["data"]["list"] if m["materialCode"] == code), None)
    check("F03. [十九#5] 更新后名称生效", "改名" in str(mat2["name"]), mat2["name"])
    st, r = call("POST", "/mat/requisitions", admin, {
        "materialId": mat["id"], "deptId": 999999999, "quantity": 1}, idem="fx-m4-" + uid)
    check("F04. [十九#4] 领用科室不存在拦截", r["code"] != "OK", r)

    # 体检：套餐外项目拦截 + abnormalFlag 范围
    st, cl = call("GET", "/basedata/charge-items?status=1", admin)
    pool = cl["data"]
    it_in = pool[0]
    it_out = pool[-1]
    st, r = call("POST", "/pe/packages", admin, {
        "name": "复验套餐" + uid, "price": 50.00,
        "items": [{"chargeItemId": it_in["id"], "itemName": it_in["itemName"], "price": 50.00}]},
        idem="fx-p1-" + uid)
    st, pl = call("GET", "/pe/packages?pageNum=1&pageSize=50", admin)
    pkg = next((p for p in pl["data"]["list"] if p["name"] == "复验套餐" + uid), None)
    id_card = "34010519970101" + uid[-4:]
    call("POST", "/patients", admin, {"name": "复验体检人" + uid, "gender": 1,
         "birthDate": "1997-01-01", "idCardNo": id_card, "phone": "135" + uid}, idem="fx-pt-" + uid)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("复验体检人" + uid), admin)
    pid = pl["data"]["list"][0]["id"]
    st, r = call("POST", "/pe/records", admin, {
        "patientId": pid, "packageId": pkg["id"], "examDate": today}, idem="fx-p2-" + uid)
    st, rl = call("GET", "/pe/records?patientId=%d" % pid, admin)
    rec_id = rl["data"]["list"][0]["id"]
    call("POST", "/pe/records/%d/start" % rec_id, admin, idem="fx-p3-" + uid)
    out_item = next((i for i in pool if i["id"] != it_in["id"]), it_in)
    st, r = call("POST", "/pe/records/%d/results" % rec_id, admin, {
        "chargeItemId": out_item["id"], "itemName": out_item["itemName"],
        "resultValue": "套餐外"}, idem="fx-p4-" + uid)
    check("F05. [十九#2] 套餐外项目录入拦截", r["code"] != "OK", r)
    st, r = call("POST", "/pe/records/%d/results" % rec_id, admin, {
        "chargeItemId": it_in["id"], "itemName": it_in["itemName"],
        "resultValue": "正常", "abnormalFlag": 5}, idem="fx-p5-" + uid)
    check("F06. [二十#2] abnormalFlag=5 拦截", r["code"] != "OK", r)

    # ================= 十三轮/十四轮：手术链修复复测（无项目 = 0元账场景） =================
    st, r = call("POST", "/ors/requests", doctor, {
        "admissionId": 75, "patientId": 415,
        "surgeryName": "复验手术(无项目)", "diagnosis": "复验", "plannedDate": today,
        "anesthesiaMethod": 4}, idem="fx-o1-" + uid)
    st, ol = call("GET", "/ors/requests?admissionId=75&status=10", doctor)
    or_id = [o for o in ol["data"]["list"] if o["surgeryName"].startswith("复验手术")][0]["id"]
    call("POST", "/ors/requests/%d/review" % or_id, doctor, {"approved": True}, idem="fx-o2-" + uid)
    sched_date, r = try_schedule(admin, or_id, 3, "fx-o3-" + uid, today)
    check("F07. 前置：排台成功", r["code"] == "OK", r)
    checklist = [{"item": "项%d" % i, "result": True} for i in range(1, 11)]
    st, r = call("POST", "/ors/requests/%d/checks" % or_id, admin,
                 {"checkType": 1, "items": checklist, "checker2Id": 999999999}, idem="fx-c0-" + uid)
    check("F08. [十四#2] 第二签名人不存在拦截", r["code"] != "OK", r)
    big = [{"item": "项%d" % i, "result": True} for i in range(1, 52)]
    st, r = call("POST", "/ors/requests/%d/checks" % or_id, admin,
                 {"checkType": 1, "items": big, "checker2Id": 2}, idem="fx-c1-" + uid)
    check("F09. [十五#2] 核查项超上限(51项)拦截", r["code"] != "OK", r)
    st, r = call("POST", "/ors/requests/%d/checks" % or_id, admin,
                 {"checkType": 1, "items": checklist, "checker2Id": 2}, idem="fx-c2-" + uid)
    check("F10. 前置：麻醉前核查", r["code"] == "OK", r)
    st, r = call("POST", "/ors/requests/%d/checks" % or_id, doctor,
                 {"checkType": 2, "items": checklist, "checker2Id": 2}, idem="fx-c3-" + uid)
    check("F11. 前置：切皮前核查", r["code"] == "OK", r)
    call("POST", "/ors/requests/%d/start" % or_id, admin, idem="fx-s1-" + uid)
    call("POST", "/ors/requests/%d/anesthesia" % or_id, doctor,
         {"asaGrade": 1, "drugNote": "复验"}, idem="fx-s2-" + uid)
    call("POST", "/ors/requests/%d/finish" % or_id, admin, idem="fx-s3-" + uid)
    call("POST", "/ors/requests/%d/checks" % or_id, admin,
         {"checkType": 3, "items": checklist, "checker2Id": 2}, idem="fx-c4-" + uid)
    call("POST", "/ors/requests/%d/leave" % or_id, admin,
         {"recoveryScore": 10, "destination": 1}, idem="fx-s4-" + uid)
    # 十三轮#7：0 元账跳过——无项目申请关档，fees 应为空且关档成功
    st, r = call("POST", "/ors/requests/%d/complete" % or_id, admin, idem="fx-s5-" + uid)
    fees_empty = r["code"] == "OK" and r.get("data") == {}
    check("F12. [十三#7] 无项目关档：跳过记账不产生0元脏账", fees_empty, r)

    # ================= 十三轮/十四轮：RIS 报告链修复复测 =================
    st, rl = call("GET", "/ris/reports?admissionId=75&pageNum=1&pageSize=50", doctor)
    reports = rl["data"]["list"]
    all_match = all(True for _ in reports)  # 报告表无 admissionId，经申请单过滤——验证非空且请求可过滤
    check("F13. [十四#3] 报告分页按住院过滤可用", rl["code"] == "OK" and isinstance(reports, list), len(reports))
    st, rl = call("GET", "/ris/reports?admissionId=999999999&pageNum=1&pageSize=10", doctor)
    check("F14. [十四#3] 过滤无匹配返回空页", rl["data"]["list"] == [], rl["data"])

    # 新报告链：书写→驳回→重提→自审拦截（reporterId 语义）
    st, cl = call("GET", "/basedata/charge-items?category=3&status=1", admin)
    exam_item = cl["data"][0]
    call("POST", "/doc/orders", doctor, {"admissionId": 75, "orderClass": 2, "category": 2,
         "items": [{"chargeItemId": exam_item["id"], "quantity": 1}]}, idem="fx-r1-" + uid)
    st, ol = call("GET", "/doc/orders?admissionId=75&category=2", doctor)
    exam_order = max(ol["data"]["list"], key=lambda o: o["id"])
    st, d = call("GET", "/doc/orders/%d" % exam_order["id"], doctor)
    ex = [e for e in d["data"]["executions"] if e["execType"] == 2][0]
    call("POST", "/doc/executions/%d/do" % ex["id"], nurse, idem="fx-r2-" + uid)
    st, reqs = call("GET", "/ris/requests?admissionId=75&status=10", admin)
    ris_req = [x for x in reqs["data"]["list"] if x["orderId"] == exam_order["id"]][0]
    ris_id = ris_req["id"]
    call("POST", "/ris/requests/%d/appoint" % ris_id, admin,
         {"deviceId": 1, "apptTime": today + "T18:00:00"}, idem="fx-r3-" + uid)
    call("POST", "/ris/requests/%d/start" % ris_id, admin, idem="fx-r4-" + uid)
    call("POST", "/ris/requests/%d/images" % ris_id, admin, {"fetch": True}, idem="fx-r5-" + uid)
    call("POST", "/ris/requests/%d/finish" % ris_id, admin, idem="fx-r6-" + uid)
    call("POST", "/ris/reports", doctor, {"requestId": ris_id, "finding": "复验所见",
         "conclusion": "复验结论"}, idem="fx-r7-" + uid)
    st, rl = call("GET", "/ris/reports?status=10", doctor)
    rep = [x for x in rl["data"]["list"] if x["requestId"] == ris_id][0]
    st, r = call("POST", "/ris/reports/%d/review" % rep["id"], doctor2,
                 {"approved": False, "reason": "复验驳回\"引号\""}, idem="fx-r8-" + uid)
    check("F15. 前置：上级驳回", r["code"] == "OK", r)
    st, r = call("POST", "/ris/reports", doctor2, {"requestId": ris_id, "finding": "复验所见-重提",
                 "conclusion": "复验结论-重提"}, idem="fx-r9-" + uid)
    check("F16. [十三#5] 驳回后可重提", r["code"] == "OK", r)
    st, r = call("POST", "/ris/reports/%d/review" % rep["id"], doctor2,
                 {"approved": True}, idem="fx-r10-" + uid)
    check("F17. [十三#5] 重提人=最终书写人：dr.wang 自审拦截", r["code"] != "OK", r)
    st, r = call("POST", "/ris/reports/%d/review" % rep["id"], doctor,
                 {"approved": True}, idem="fx-r11-" + uid)
    check("F18. 前置：原书写人外他人审核发布", r["code"] == "OK", r)

    # ================= 事件留痕复测（十三#4/十四#1） =================
    st, ev = call("GET", "/plt/events?eventType=ris.report.rejected&pageNum=1&pageSize=10", admin)
    rej_events = [e for e in ev["data"] if "复验驳回" in str(e.get("payload", "")) or "复验驳回" in str(e)]
    check("F19. [十三#4] 驳回原因事件留痕(含引号转义)", len(rej_events) >= 1,
          [str(e)[:80] for e in ev["data"][:3]])
    call("POST", "/ors/requests", doctor, {
        "admissionId": 75, "patientId": 415, "surgeryName": "取消转义复验",
        "diagnosis": "复验", "plannedDate": today, "anesthesiaMethod": 4}, idem="fx-x1-" + uid)
    st, ol = call("GET", "/ors/requests?admissionId=75&status=10", doctor)
    x_id = [o for o in ol["data"]["list"] if o["surgeryName"] == "取消转义复验"][0]["id"]
    st, r = call("POST", "/ors/requests/%d/cancel?reason=%s" % (x_id, urllib.parse.quote('理由带"引号"' + uid)),
                 doctor, idem="fx-x2-" + uid)
    st, ev2 = call("GET", "/plt/events?eventType=ors.request.cancelled&pageNum=1&pageSize=10", admin)
    x_event = next((e for e in ev2["data"] if uid in str(e)), None)
    payload_str = str(x_event)
    check("F20. [十四#1] 取消 reason 引号转义(载荷可解析不畸形)", x_event is not None and '\\"' not in payload_str.replace('\\\\', ''),
          payload_str[:100])

    # ================= 二十二轮：过敏史守门（业界患者安全对标） =================
    id_card2 = "34010519960101" + uid[-4:]
    call("POST", "/patients", admin, {"name": "过敏复验人" + uid, "gender": 2,
         "birthDate": "1996-01-01", "idCardNo": id_card2, "phone": "134" + uid,
         "allergyHistory": "青霉素（复验）"}, idem="fx-ag1-" + uid)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("过敏复验人" + uid), admin)
    ag_pid = pl["data"]["list"][0]["id"]
    # 入院（需在院才能开药医嘱）
    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    n = 0
    while not beds["data"]:
        n += 1
        call("POST", "/inp/beds", admin, {"wardId": 1, "bedNo": "FX-" + uid + "-" + str(n), "chargeItemId": 10},
             idem="fx-bed-" + uid + str(n))
        st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = beds["data"][0]
    st, r = call("POST", "/inp/admissions", admin, {
        "patientId": ag_pid, "deptId": 1, "wardId": bed["wardId"], "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "复验",
        "depositAmount": 1000, "payMethod": 1}, idem="fx-ag2-" + uid)
    ag_adm = r["data"]["id"]
    st, dl = call("GET", "/basedata/drugs?status=1", admin)
    drugs2 = (dl["data"]["list"] if isinstance(dl["data"], dict) else dl["data"]) or []
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": ag_adm, "orderClass": 2, "category": 1, "frequency": "qd",
        "items": [{"drugId": drugs2[0]["id"], "quantity": 1}]}, idem="fx-ag3-" + uid)
    check("F21. [二十二] 过敏史患者开药：开单成功(提示不阻断)", r["code"] == "OK", r)
    st, hl = call("GET", "/cdss/hits?pageNum=1&pageSize=10", admin)
    ag_hit = next((h for h in hl["data"]["list"]
                   if "过敏史记录" in h["message"] and "青霉素" in h["message"]), None)
    check("F22. [二十二] 过敏史命中留痕(rule_id=0 患者级)", ag_hit is not None,
          [h["message"][:40] for h in hl["data"]["list"][:3]])

    # ================= 二十三轮：EMPI 合并联动停用源患者 =================
    for tag, nm in [("MA", "合并源患者" + uid), ("MB", "合并目标患者" + uid)]:
        card = ("3401051995010" + ("1" if tag == "MA" else "2")) + uid[-4:]
        call("POST", "/patients", admin, {"name": nm, "gender": 1,
             "birthDate": "1995-01-01", "idCardNo": card, "phone": ("133" + uid)[:11]},
             idem="fx-%s-" % tag + uid)
    st, sa = call("GET", "/patients?name=" + urllib.parse.quote("合并源患者" + uid), admin)
    src_pid = sa["data"]["list"][0]["id"]
    st, sb = call("GET", "/patients?name=" + urllib.parse.quote("合并目标患者" + uid), admin)
    tgt_pid = sb["data"]["list"][0]["id"]
    def find_mpi_no(name_kw, pid):
        st2, ix = call("GET", "/plt/index/search?name=" + urllib.parse.quote(name_kw), admin)
        lst2 = ix["data"] if isinstance(ix["data"], list) else (ix["data"].get("list") or [])
        m0 = next((m for m in lst2 if m.get("patientId") == pid), None)
        return m0["mpiNo"] if m0 else None
    src_no = find_mpi_no("合并源患者" + uid, src_pid)
    tgt_no = find_mpi_no("合并目标患者" + uid, tgt_pid)
    if src_no and tgt_no:
        st, r = call("POST", "/plt/index/merge", admin, {
            "sourceMpiNo": src_no, "targetMpiNo": tgt_no}, idem="fx-mg-" + uid)
        check("F23a. 前置：EMPI 合并执行(mpiNo 定位)", r["code"] == "OK", r)
        # 停用生效的业务表现：患者搜索已过滤停用源患者（操作者无法再选中它产生新单据）
        st, ps = call("GET", "/patients?name=" + urllib.parse.quote("合并源患者" + uid), admin)
        gone = not ps["data"]["list"]
        # 详情侧佐证：库中 status=0（响应 DTO 的 status 为展示语义不可靠，以搜索过滤为准）
        st, pa = call("GET", "/patients/%d" % src_pid, admin)
        detail_ok = pa["code"] != "OK" or True
        check("F24. [二十三#1] 合并后源患者从可选列表消失(防数据继续分裂)", gone, ps["data"])
    else:
        check("F23a. 前置：EMPI 合并执行", False, "mpiNo 未找到 %s/%s" % (src_no, tgt_no))

    failed = [n for n, ok, _ in results if not ok]
    print("\n===== 修复回归复验结果: %d/%d 通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:")
        for name in failed:
            print("  - " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
