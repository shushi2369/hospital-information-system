#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HIS 四期端到端验收脚本（输血闭环 + 耗材批次 FEFO + CDSS 增强 + 互认标识）
用法: python e2e_phase4.py [BASE_URL]
可重复执行。前置：V28~V30 迁移已应用；患者 75 在院。
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
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:130]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败: %s" % r
    return r["data"]["token"]


def main():
    uid = str(int(time.time() * 1000))[-8:]
    today = time.strftime("%Y-%m-%d")
    tomorrow = (datetime.datetime.strptime(today, "%Y-%m-%d") + datetime.timedelta(days=3)).strftime("%Y-%m-%d")

    admin = login("admin")
    doctor = login("dr.li")
    doctor2 = login("dr.wang")
    bb_tech = login("bb.tech")
    nurse = login("nurse.wang")
    check("1. 角色登录（含血库人员）", all([admin, doctor, doctor2, bb_tech, nurse]))

    # ================= 输血闭环（三道门禁） =================
    st, r = call("POST", "/bb/bags", bb_tech, {
        "bagNo": "XDJ-FX-" + uid, "bloodType": 4, "rh": 1, "component": 1,
        "volumeMl": 200, "expireDate": tomorrow}, idem="p4-b1-" + uid)
    check("2. 血袋入库(XDJ)", r["code"] == "OK", r)
    st, r = call("POST", "/bb/bags", bb_tech, {
        "bagNo": "XDJ-EXP-" + uid, "bloodType": 4, "rh": 1, "component": 1,
        "expireDate": "2020-01-01"}, idem="p4-b2-" + uid)
    check("3. 过期血袋入库拦截", r["code"] != "OK", r)

    st, r = call("POST", "/bb/requests", doctor, {
        "admissionId": 75, "patientId": 999999999, "bloodType": 4, "rh": 1,
        "component": 1, "volumeMl": 200, "usePurpose": "跨患者"}, idem="p4-r0-" + uid)
    check("4a. [二十五#1] 跨患者用血申请拦截", r["code"] != "OK", r)
    st, r = call("POST", "/bb/requests", doctor, {
        "admissionId": 75, "patientId": 415, "bloodType": 4, "rh": 1,
        "component": 1, "volumeMl": 200, "usePurpose": "四期验收"}, idem="p4-r1-" + uid)
    check("4. 用血申请(XY)", r["code"] == "OK" and str(r.get("data", "")).startswith("XY"), r)
    st, rl = call("GET", "/bb/requests?admissionId=75&status=10", bb_tech)
    req = [x for x in rl["data"]["list"] if x["reqNo"].endswith(uid) or "四期" in str(x.get("usePurpose"))][0]
    req_id = req["id"]
    st, r = call("POST", "/bb/requests/%d/review?approved=true" % req_id, bb_tech, idem="p4-r2-" + uid)
    check("5. 血库审核(10→20)", r["code"] == "OK", r)

    st, av = call("GET", "/bb/bags/available?bloodType=4&component=1", bb_tech)
    bags = [b for b in av["data"] if b["bagNo"] == "XDJ-FX-" + uid]
    check("6. 可用血袋查询(含新袋)", len(bags) == 1, [b["bagNo"] for b in av["data"]])
    new_bag_id = bags[0]["id"]
    st, demo_bags = call("GET", "/bb/bags/available?bloodType=4&component=1", bb_tech)
    demo_bag = next((b for b in demo_bags["data"] if b["bagNo"] == "XDJ20260001"), None)
    check("7. V30 演示血袋在库", demo_bag is not None, "")

    # 门禁①：不相容配血 → 发血硬阻断
    st, r = call("POST", "/bb/requests/%d/cross-match" % req_id, bb_tech, {
        "bagId": demo_bag["id"], "crossMethod": "盐水介质", "crossResult": 2,
        "note": "不相容演练"}, idem="p4-x1-" + uid)
    check("8. 不相容配血登记", r["code"] == "OK", r)
    st, rl20 = call("GET", "/bb/requests?status=20", bb_tech)
    check("8b. 不相容不推进状态(仍配血中)", any(x["id"] == req_id for x in rl20["data"]["list"]), "")
    # 二十五#2：血型/成分不匹配（XDJ20260003 为 A 型血浆 vs O 型红细胞申请）→ 配血拦截
    st, bl3 = call("GET", "/bb/bags?pageNum=1&pageSize=50", bb_tech)
    mismatch_bag = next((b for b in bl3["data"]["list"] if b["bagNo"] == "XDJ20260003"), None)
    st, r = call("POST", "/bb/requests/%d/cross-match" % req_id, bb_tech, {
        "bagId": mismatch_bag["id"], "crossMethod": "抗人球", "crossResult": 1}, idem="p4-x2b-" + uid)
    check("10a. [二十五#2] 血型/成分不匹配配血拦截", r["code"] != "OK", r)
    st, r = call("POST", "/bb/requests/%d/cross-match" % req_id, bb_tech, {
        "bagId": new_bag_id, "crossMethod": "抗人球", "crossResult": 1}, idem="p4-x2-" + uid)
    check("10. 相容配血(20→30)", r["code"] == "OK", r)
    st, r = call("POST", "/bb/requests/%d/issue" % req_id, bb_tech, {
        "bagId": demo_bag["id"], "receiverId": 6}, idem="p4-i1-" + uid)
    check("9. 门禁①：不相容血袋发血硬阻断", r["code"] != "OK" and "不相容" in str(r.get("message", "")), r)
    st, r = call("POST", "/bb/requests/%d/issue" % req_id, bb_tech, {
        "bagId": new_bag_id, "receiverId": 6}, idem="p4-i2-" + uid)
    check("11. 门禁②：发血成功(取血护士签收, 30→40)", r["code"] == "OK", r)

    # 门禁③：双人双签
    st, r = call("POST", "/bb/requests/%d/transfusion" % req_id, nurse, {
        "bagId": new_bag_id, "checker1Id": 6, "checker2Id": 6,
        "vitalBefore": "T36.5 P80 R18 BP120/80"}, idem="p4-t1-" + uid)
    check("12. 门禁③：同签人拦截", r["code"] != "OK", r)
    st, r = call("POST", "/bb/requests/%d/transfusion" % req_id, nurse, {
        "bagId": demo_bag["id"], "checker1Id": 6, "checker2Id": 2,
        "vitalBefore": "T36.5"}, idem="p4-t1b-" + uid)
    check("13a. [二十五#3] 未发血袋输注拦截", r["code"] != "OK", r)
    st, r = call("POST", "/bb/requests/%d/transfusion" % req_id, nurse, {
        "bagId": new_bag_id, "checker2Id": 2,
        "vitalBefore": "T36.5 P80 R18 BP120/80"}, idem="p4-t2-" + uid)
    check("13. 双签通过开始输注(40→50, 核对签1=执行护士本人)", r["code"] == "OK", r)
    st, r = call("POST", "/bb/requests/%d/adverse" % req_id, nurse, {
        "type": 1, "severity": 1, "handleNote": "轻度发热，减慢滴速观察"}, idem="p4-a1-" + uid)
    check("14. 不良反应登记", r["code"] == "OK", r)
    st, r = call("POST", "/bb/requests/%d/finish" % req_id, nurse, {
        "outcome": 2, "note": "轻度发热反应，处理后缓解"}, idem="p4-t3-" + uid)
    check("15. 输血结束闭环(50→60)", r["code"] == "OK", r)
    st, d = call("GET", "/bb/requests/%d" % req_id, admin)
    det = d["data"]
    check("16. 闭环聚合留痕(配血2+发血1+执行1+不良反应1)",
          len(det["crossMatches"]) == 2 and len(det["issues"]) == 1
          and len(det["transfusions"]) == 1 and len(det["adverses"]) == 1,
          {k: len(det[k]) for k in ("crossMatches", "issues", "transfusions", "adverses")})
    st, bl = call("GET", "/bb/bags?bloodType=4&status=2", bb_tech)
    used = [b for b in bl["data"]["list"] if b["id"] == new_bag_id]
    check("17. 血袋状态已发用", len(used) == 1, len(used))

    # ================= 耗材批次 FEFO =================
    code = "P4B" + uid
    call("POST", "/mat/materials", admin, {"materialCode": code, "name": "四期批次物资" + uid,
         "category": 1, "unit": "支", "price": 3.00, "safeStock": 50}, idem="p4-m1-" + uid)
    st, ml = call("GET", "/mat/materials?category=1", admin)
    mat = next((m for m in ml["data"]["list"] if m["materialCode"] == code), None)
    mat_id = mat["id"]
    call("POST", "/mat/purchases", admin, {"supplierId": 1, "materialId": mat_id,
         "quantity": 30, "unitPrice": 2.00}, idem="p4-m2-" + uid)
    st, pl = call("GET", "/mat/purchases?status=10", admin)
    po1 = next((p for p in pl["data"]["list"] if p["materialId"] == mat_id and p["quantity"] == 30), None)
    call("POST", "/mat/purchases/%d/approve" % po1["id"], admin, idem="p4-m3-" + uid)
    exp_far = (datetime.datetime.strptime(today, "%Y-%m-%d") + datetime.timedelta(days=200)).strftime("%Y-%m-%d")
    st, r = call("POST", "/mat/purchases/%d/receive?batchNo=B-LOT-A-%s&expireDate=%s" % (po1["id"], uid, exp_far),
                 admin, idem="p4-m4-" + uid)
    check("18. 批次A入库(30, 效期远)", r["code"] == "OK", r)
    call("POST", "/mat/purchases", admin, {"supplierId": 1, "materialId": mat_id,
         "quantity": 20, "unitPrice": 2.00}, idem="p4-m5-" + uid)
    st, pl = call("GET", "/mat/purchases?status=10", admin)
    po2 = next((p for p in pl["data"]["list"] if p["materialId"] == mat_id and p["quantity"] == 20), None)
    call("POST", "/mat/purchases/%d/approve" % po2["id"], admin, idem="p4-m6-" + uid)
    exp_near = (datetime.datetime.strptime(today, "%Y-%m-%d") + datetime.timedelta(days=20)).strftime("%Y-%m-%d")
    st, r = call("POST", "/mat/purchases/%d/receive?batchNo=B-LOT-B-%s&expireDate=%s" % (po2["id"], uid, exp_near),
                 admin, idem="p4-m7-" + uid)
    check("19. 批次B入库(20, 效期近≤30天)", r["code"] == "OK", r)
    st, bt = call("GET", "/mat/batches?materialId=%d" % mat_id, admin)
    blist = bt["data"]
    check("20. FEFO 排序(效期升序)", len(blist) == 2 and blist[0]["batchNo"].endswith("B-" + uid), [b["batchNo"] for b in blist])
    check("21. 效期预警标记(批次B ≤30天)", blist[0]["expireSoon"] is True and blist[1]["expireSoon"] is False, blist)
    st, r = call("POST", "/mat/requisitions", admin, {
        "materialId": mat_id, "deptId": 1, "quantity": 25, "purpose": "FEFO 验收"}, idem="p4-m8-" + uid)
    check("22. 跨批次领用(25=20+5)", r["code"] == "OK", r)
    st, rl = call("GET", "/mat/requisitions?materialId=%d" % mat_id, admin)
    req_row = rl["data"]["list"][0]
    bd = req_row.get("breakdown")
    breakdown = json.loads(bd) if isinstance(bd, str) else bd
    check("23. FEFO 拆分留痕(B批20+A批5)",
          breakdown and breakdown[0]["batchNo"].endswith("B-" + uid) and breakdown[0]["quantity"] == 20
          and breakdown[1]["quantity"] == 5, breakdown)

    # ================= CDSS 增强（type=4 / 年龄维度） =================
    st, dl = call("GET", "/basedata/drugs?status=1", admin)
    drugs = (dl["data"]["list"] if isinstance(dl["data"], dict) else dl["data"]) or []
    drug = drugs[0]
    st, r = call("POST", "/cdss/rules", admin, {
        "ruleCode": "P4-ALG-" + uid, "ruleType": 4, "refAId": drug["id"],
        "allergyKeyword": "磺胺", "message": "患者对磺胺类过敏，医嘱含禁忌药品"}, idem="p4-c1-" + uid)
    check("24. type=4 过敏映射规则创建", r["code"] == "OK", r)
    # 过敏患者（含磺胺）入院开药 → 精确命中
    id_card = "34010519940101" + uid[-4:]
    call("POST", "/patients", admin, {"name": "四期过敏患者" + uid, "gender": 1,
         "birthDate": "1994-01-01", "idCardNo": id_card, "phone": "132" + uid,
         "allergyHistory": "对磺胺类药物过敏（四期验收）"}, idem="p4-pt1-" + uid)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("四期过敏患者" + uid), admin)
    alg_pid = pl["data"]["list"][0]["id"]
    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    n = 0
    while not beds["data"]:
        n += 1
        call("POST", "/inp/beds", admin, {"wardId": 1, "bedNo": "P4-" + uid + "-" + str(n), "chargeItemId": 10},
             idem="p4-bed-" + uid + str(n))
        st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    bed = beds["data"][0]
    st, r = call("POST", "/inp/admissions", admin, {
        "patientId": alg_pid, "deptId": 1, "wardId": bed["wardId"], "bedId": bed["id"],
        "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "四期验收",
        "depositAmount": 1000, "payMethod": 1}, idem="p4-adm-" + uid)
    alg_adm = r["data"]["id"]
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": alg_adm, "orderClass": 2, "category": 1, "frequency": "qd",
        "items": [{"drugId": drug["id"], "quantity": 1}]}, idem="p4-c2-" + uid)
    check("25. 过敏映射：开单成功(提示不阻断)", r["code"] == "OK", r)
    st, hl = call("GET", "/cdss/hits?pageNum=1&pageSize=10", admin)
    alg_hit = next((h for h in hl["data"]["list"] if "磺胺" in h["message"] and "过敏史含" in h["message"]), None)
    check("26. type=4 精确命中(含匹配依据)", alg_hit is not None, [h["message"][:40] for h in hl["data"]["list"][:3]])
    # 年龄维度：type=3 剂量规则 ageMin=18，儿童患者(2020 出生)不生效
    st, r = call("POST", "/cdss/rules", admin, {
        "ruleCode": "P4-AGE-" + uid, "ruleType": 3, "refAId": drug["id"], "refBId": 5,
        "ageMin": 18, "message": "成人剂量上限演练"}, idem="p4-c3-" + uid)
    check("27. type=3 年龄维度规则创建", r["code"] == "OK", r)
    id_card2 = "34010520200101" + uid[-4:]
    call("POST", "/patients", admin, {"name": "四期儿童患者" + uid, "gender": 1,
         "birthDate": "2020-06-01", "idCardNo": id_card2, "phone": "131" + uid}, idem="p4-pt2-" + uid)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("四期儿童患者" + uid), admin)
    kid_pid = pl["data"]["list"][0]["id"]
    st, beds2 = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    n2 = 0
    while not beds2["data"]:
        n2 += 1
        call("POST", "/inp/beds", admin, {"wardId": 1, "bedNo": "P4K-" + uid + "-" + str(n2), "chargeItemId": 10},
             idem="p4-bed2-" + uid + str(n2))
        st, beds2 = call("GET", "/inp/beds?wardId=1&bedStatus=1", admin)
    if True:
        bed2 = beds2["data"][0]
        st, r = call("POST", "/inp/admissions", admin, {
            "patientId": kid_pid, "deptId": 1, "wardId": bed2["wardId"], "bedId": bed2["id"],
            "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "四期验收",
            "depositAmount": 500, "payMethod": 1}, idem="p4-adm2-" + uid)
        kid_adm = r["data"]["id"]
        st, r = call("POST", "/doc/orders", doctor, {
            "admissionId": kid_adm, "orderClass": 2, "category": 1, "frequency": "qd",
            "items": [{"drugId": drug["id"], "quantity": 1}]}, idem="p4-c4-" + uid)
        st, hl = call("GET", "/cdss/hits?pageNum=1&pageSize=20", admin)
        age_hit = next((h for h in hl["data"]["list"]
                        if "成人剂量上限" in h["message"] and h.get("doctorId")), None)
        check("28. type=3 年龄维度(儿童不触发成人规则)", age_hit is None,
              [h["message"][:30] for h in hl["data"]["list"][:3]])

    # ================= 互认标识 =================
    st, rl = call("GET", "/lis/reports?pageNum=1&pageSize=5", doctor)
    lis_report = rl["data"]["list"][0]
    check("29. LIS 报告含互认字段", "mutualFlag" in lis_report, list(lis_report.keys()))
    st, rl = call("GET", "/ris/reports?pageNum=1&pageSize=5", doctor)
    ris_report = rl["data"]["list"][0]
    check("30. RIS 报告含互认字段", "mutualFlag" in ris_report, list(ris_report.keys()))

    failed = [n for n, ok, _ in results if not ok]
    print("\n===== 四期 e2e 结果: %d/%d 通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:")
        for name in failed:
            print("  - " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
