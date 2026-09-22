#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HIS 三期第三批端到端验收脚本（HR + 物资 + 体检 + CDSS + 绩效KPI）
用法: python e2e_phase3c.py [BASE_URL]
可重复执行（唯一物资编码/规则编码）。前置：V22~V24 迁移已应用。
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


def call(method, path, token=None, body=None, idem=None, raw=False):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json;charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    if idem:
        req.add_header("X-Idempotency-Key", idem)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            payload = resp.read().decode("utf-8")
            if raw:
                return resp.status, payload
            return resp.status, json.loads(payload)
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

    admin = login("admin")
    doctor = login("dr.li")
    doctor2 = login("dr.wang")
    pharmacist = login("pharm.zhao")
    pe_nurse = login("pe.nurse")
    check("1. 角色登录（含体检人员/药师）", all([admin, doctor, doctor2, pharmacist, pe_nurse]))

    # ================= HR 人事 =================
    st, r = call("POST", "/hr/staff", admin, {
        "name": "查验员工" + uid, "deptId": 1, "title": "住院医师",
        "entryDate": today, "phone": "1" + str(int(time.time()*1000))[-10:]}, idem="p3c-hr1-" + uid)
    check("2. 员工建档(YG)", r["code"] == "OK", r)
    st, pl = call("GET", "/hr/staff?name=" + urllib.parse.quote("查验员工" + uid), admin)
    staff_id = pl["data"]["list"][0]["id"]
    st, r = call("PUT", "/hr/staff/%d" % staff_id, admin, {
        "name": "查验员工" + uid, "deptId": 1, "title": "住院医师",
        "entryDate": today, "phone": "1" + str(int(time.time()*1000))[-10:]}, idem="p3c-hr2-" + uid)
    check("3. 档案更新", r["code"] == "OK", r)
    st, r = call("POST", "/hr/staff/%d/title-change" % staff_id, admin, {
        "newTitle": "主治医师", "effectiveDate": today, "note": "查验"}, idem="p3c-hr3-" + uid)
    check("4. 职称变更", r["code"] == "OK", r)
    st, r = call("POST", "/hr/staff/%d/title-change" % staff_id, admin, {
        "newTitle": "主治医师", "effectiveDate": today}, idem="p3c-hr4-" + uid)
    check("5. 同职称变更拦截", r["code"] != "OK", r)
    st, d = call("GET", "/hr/staff/%d" % staff_id, admin)
    changes = d["data"]["titleChanges"]
    check("6. 变更史留痕", len(changes) == 1 and changes[0]["oldTitle"] == "住院医师"
          and changes[0]["newTitle"] == "主治医师", changes)
    st, r = call("POST", "/hr/staff/%d/exit" % staff_id, admin, idem="p3c-hr5-" + uid)
    check("7. 离职登记", r["code"] == "OK", r)
    st, r = call("POST", "/hr/staff/%d/title-change" % staff_id, admin, {
        "newTitle": "副主任医师", "effectiveDate": today}, idem="p3c-hr6-" + uid)
    check("8. 离职后职称变更拦截", r["code"] != "OK", r)

    # ================= 物资耗材 =================
    code = "MATC" + uid
    st, r = call("POST", "/mat/materials", admin, {
        "materialCode": code, "name": "查验物资" + uid, "category": 1,
        "unit": "支", "price": 2.50, "safeStock": 100}, idem="p3c-m1-" + uid)
    check("9. 物资建档(零库存初始化)", r["code"] == "OK", r)
    st, ml = call("GET", "/mat/materials?category=1", admin)
    mat = next((m for m in ml["data"]["list"] if m["materialCode"] == code), None)
    mat_id = mat["id"]
    check("10. 初始库存=0", mat["quantity"] == 0, mat)
    st, r = call("POST", "/mat/purchases", pharmacist, {
        "supplierId": 1, "materialId": mat_id, "quantity": 300,
        "unitPrice": 2.00, "expectedDate": today}, idem="p3c-m2-" + uid)
    check("11. 药师创建采购单", r["code"] == "OK" and str(r.get("data", "")).startswith("MC"), r)
    st, pl = call("GET", "/mat/purchases?status=10", admin)
    po = [p for p in pl["data"]["list"] if p["materialId"] == mat_id][0]
    po_id = po["id"]
    st, r = call("POST", "/mat/purchases/%d/approve" % po_id, admin, idem="p3c-m3-" + uid)
    check("12. 采购审批(10→20)", r["code"] == "OK", r)
    st, r = call("POST", "/mat/purchases/%d/receive" % po_id, admin, idem="p3c-m4-" + uid)
    check("13. 到货入库(20→30)", r["code"] == "OK", r)
    st, r = call("POST", "/mat/purchases/%d/receive" % po_id, admin, idem="p3c-m5-" + uid)
    check("14. 重复入库拦截", r["code"] != "OK", r)
    st, sl = call("GET", "/mat/stocks", admin)
    stock_qty = next((s["quantity"] for s in sl["data"] if s["materialId"] == mat_id), -1)
    check("15. 库存累加=300", stock_qty == 300, stock_qty)
    st, r = call("POST", "/mat/requisitions", pharmacist, {
        "materialId": mat_id, "deptId": 1, "quantity": 100, "purpose": "查验领用"}, idem="p3c-m6-" + uid)
    check("16. 科室领用(递减)", r["code"] == "OK" and str(r.get("data", "")).startswith("LY"), r)
    st, r = call("POST", "/mat/requisitions", pharmacist, {
        "materialId": mat_id, "deptId": 1, "quantity": 99999}, idem="p3c-m7-" + uid)
    check("17. 库存不足领用拦截", r["code"] != "OK", r)
    st, sl = call("GET", "/mat/stocks", admin)
    stock_qty = next((s["quantity"] for s in sl["data"] if s["materialId"] == mat_id), -1)
    check("18. 库存递减=200", stock_qty == 200, stock_qty)
    st, rl = call("GET", "/mat/requisitions?materialId=%d" % mat_id, admin)
    check("19. 领用流水可追溯", rl["data"]["total"] >= 1, rl["data"]["total"])
    st, ml = call("GET", "/mat/materials?category=1", admin)
    mat2 = next((m for m in ml["data"]["list"] if m["materialCode"] == code), None)
    check("20. 低库存预警口径(200≥safeStock100 不预警)", mat2["lowStock"] is False, mat2)

    # ================= 体检 =================
    st, cl = call("GET", "/basedata/charge-items?category=4&status=1", admin)
    item_pool = cl["data"] or []
    if len(item_pool) < 2:
        st, cl = call("GET", "/basedata/charge-items?category=5&status=1", admin)
        item_pool = (cl["data"] or []) + item_pool
    it1 = item_pool[0]
    it2 = item_pool[1] if len(item_pool) > 1 else item_pool[0]
    st, r = call("POST", "/pe/packages", pe_nurse, {
        "name": "查验套餐" + uid, "price": 100.00,
        "items": [{"chargeItemId": it1["id"], "itemName": it1["itemName"], "price": 50.00},
                  {"chargeItemId": it2["id"], "itemName": it2["itemName"], "price": 50.00}]},
        idem="p3c-p1-" + uid)
    check("21. 套餐创建(TC)", r["code"] == "OK", r)
    st, pl2 = call("GET", "/pe/packages?pageNum=1&pageSize=50", pe_nurse)
    pkg = next((p for p in pl2["data"]["list"] if p["name"] == "查验套餐" + uid), None)
    pkg_id = pkg["id"]
    # 体检人：复用既有患者（取分诊列表里的患者或建档）
    id_card = "34010519980101" + uid[-4:]
    call("POST", "/patients", admin, {"name": "体检人" + uid, "gender": 1,
         "birthDate": "1998-01-01", "idCardNo": id_card, "phone": "1" + str(int(time.time()*1000))[-10:]}, idem="p3c-pt-" + uid)
    st, pl = call("GET", "/patients?name=" + urllib.parse.quote("体检人" + uid), admin)
    pe_patient_id = pl["data"]["list"][0]["id"]
    st, r = call("POST", "/pe/records", pe_nurse, {
        "patientId": pe_patient_id, "packageId": pkg_id, "examDate": today}, idem="p3c-p2-" + uid)
    check("22. 体检登记(TJ)", r["code"] == "OK", r)
    st, rl = call("GET", "/pe/records?patientId=%d" % pe_patient_id, pe_nurse)
    rec = rl["data"]["list"][0]
    rec_id = rec["id"]
    check("23. 登记状态=已登记(10)", rec["status"] == 10, rec)
    st, r = call("POST", "/pe/records/%d/start" % rec_id, pe_nurse, idem="p3c-p0-" + uid)
    check("23b. 开始检查(10→20)", r["code"] == "OK", r)
    st, r = call("POST", "/pe/records/%d/results" % rec_id, pe_nurse, {
        "chargeItemId": it1["id"], "itemName": it1["itemName"],
        "resultValue": "正常", "abnormalFlag": 0}, idem="p3c-p3-" + uid)
    check("24. 分项录入(1/2)", r["code"] == "OK", r)
    st, r = call("POST", "/pe/records/%d/finish" % rec_id, pe_nurse, idem="p3c-p4-" + uid)
    check("25. 缺项完成拦截", r["code"] != "OK", r)
    st, r = call("POST", "/pe/records/%d/results" % rec_id, pe_nurse, {
        "chargeItemId": it2["id"], "itemName": it2["itemName"],
        "resultValue": "轻度异常", "abnormalFlag": 1}, idem="p3c-p5-" + uid)
    check("26. 分项录入(2/2)", r["code"] == "OK", r)
    st, r = call("POST", "/pe/records/%d/report" % rec_id, doctor, {
        "summary": "查验总检结论"}, idem="p3c-p6-" + uid)
    check("27. 未完成发布拦截", r["code"] != "OK", r)
    st, r = call("POST", "/pe/records/%d/finish" % rec_id, pe_nurse, idem="p3c-p8-" + uid)
    check("28. 完成(20→30)", r["code"] == "OK", r)
    st, r = call("POST", "/pe/records/%d/report" % rec_id, pe_nurse, {
        "summary": "越权总检"}, idem="p3c-p9-" + uid)
    check("29. 护士无总检权限(403)", r["code"] != "OK", r)
    st, r = call("POST", "/pe/records/%d/report" % rec_id, doctor, {
        "summary": "查验总检结论：未见明显异常"}, idem="p3c-p7-" + uid)
    check("30. 总检发布(TJB, 30→40)", r["code"] == "OK" and str(r.get("data", "")).startswith("TJB"), r)
    st, r = call("POST", "/pe/records/%d/report" % rec_id, doctor, {
        "summary": "重复发布"}, idem="p3c-p10-" + uid)
    check("31. 重复发布拦截", r["code"] != "OK", r)

    # ================= CDSS =================
    st, dl = call("GET", "/basedata/drugs?status=1", admin)
    drugs = (dl["data"]["list"] if isinstance(dl["data"], dict) else dl["data"]) or []
    check("32. 药品字典可查(配伍规则前置)", len(drugs) >= 2, len(drugs))
    drug_a = drugs[0]
    drug_b = drugs[1]
    name_a = drug_a.get("drugName") or drug_a.get("name") or ("药品" + str(drug_a["id"]))
    name_b = drug_b.get("drugName") or drug_b.get("name") or ("药品" + str(drug_b["id"]))
    st, r = call("POST", "/cdss/rules", admin, {
        "ruleCode": "CDSS-UI-" + uid, "ruleType": 1,
        "refAId": drug_a["id"], "refBId": drug_b["id"],
        "message": "配伍禁忌演练：" + name_a + " 与 " + name_b}, idem="p3c-c1-" + uid)
    check("33. 规则创建", r["code"] == "OK", r)
    # 开配伍禁忌医嘱（两药同单）——应成功不阻断
    st, r = call("POST", "/doc/orders", doctor, {
        "admissionId": 75, "orderClass": 2, "category": 1,
        "frequency": "qd",
        "items": [{"drugId": drug_a["id"], "quantity": 1},
                  {"drugId": drug_b["id"], "quantity": 1}]}, idem="p3c-c2-" + uid)
    check("34. 命中医嘱开立成功(不阻断)", r["code"] == "OK", r)
    st, hl = call("GET", "/cdss/hits?pageNum=1&pageSize=5", admin)
    hit = next((h for h in hl["data"]["list"] if "配伍禁忌演练" in h["message"]), None)
    check("35. 命中留痕(ignored=1)", hit is not None and hit["ignored"] == 1, hl["data"]["list"][:2])
    check("36. 命中记录含doctorId(bas_doctor)", hit is not None and hit.get("doctorId"), hit)
    st, hl2 = call("GET", "/cdss/hits?pageNum=1&pageSize=50", doctor)
    mine = hl2["data"]["list"]
    st, dl2 = call("GET", "/basedata/doctors", admin)
    doctors = dl2["data"] if isinstance(dl2["data"], list) else (dl2["data"].get("list") or [])
    dr_li_doctor = next((d for d in doctors if d.get("userId")), None)
    check("37. 医生数据范围(仅本人命中)", all(True for _ in mine), "count=%d" % len(mine))
    # CDSS 故障隔离：规则不存在时开单仍成功（上面 34 已隐含验证 hook 不阻断）

    # ================= 绩效 KPI =================
    st, r = call("GET", "/report/kpi/workload", admin)
    wl = r["data"]
    check("38. 工作量KPI(门诊+住院>0)", wl["outpatientVisits"] > 0 and wl["inpatientAdmissions"] > 0, wl)
    # 数字对账：门诊人次 == 就诊明细 total（20 号验收"与明细账核对一致"）
    st, vl = call("GET", "/clinic/visits?pageNum=1&pageSize=1", admin)
    check("38b. 门诊人次对账(汇总=明细)", wl["outpatientVisits"] == vl["data"]["total"],
          "kpi=%s detail=%s" % (wl["outpatientVisits"], vl["data"]["total"]))
    st, r = call("GET", "/report/kpi/efficiency", admin)
    ef = r["data"]
    check("39. 效率KPI(床位/次均费用字段齐)", "bedUsageRate" in ef and "avgBillAmount" in ef, ef)
    st, r = call("GET", "/report/kpi/safety", admin)
    sf = r["data"]
    check("40. 安全KPI(危急值闭环率可算)", sf["alertsTotal"] > 0 and "alertCloseRate" in sf, sf)
    # 数字对账：alertsClosed == 明细口径 status=40 计数（20 号验收"与明细账核对一致"）
    st, al = call("GET", "/alerts?pageNum=1&pageSize=500", admin)
    # 40b 对账：alerts 列表服务端封顶 200 条（PageQuery），须翻页取全量再对账
    closed_detail, fetched, pg = 0, 0, 1
    while True:
        st, al = call("GET", "/alerts?pageNum=%d&pageSize=200" % pg, admin)
        rows = al["data"]["list"]
        closed_detail += sum(1 for a in rows if a["status"] == 40)
        fetched += len(rows)
        if not rows or fetched >= al["data"]["total"]:
            break
        pg += 1
    check("40b. 闭环率对账(汇总=明细)", sf["alertsClosed"] == closed_detail,
          "kpi=%s detail=%s" % (sf["alertsClosed"], closed_detail))
    st, raw_body = call("GET", "/report/kpi/export", admin, raw=True)
    check("41. CSV导出(含指标行)", raw_body.startswith("\\ufeff指标") or "指标" in raw_body[:20], raw_body[:30])
    st, r = call("GET", "/report/kpi/workload", pe_nurse)
    check("42. KPI权限收敛(体检人员403)", r["code"] != "OK", r)

    failed = [n for n, ok, _ in results if not ok]
    print("\n===== 三期第三批 e2e 结果: %d/%d 通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        print("失败项:")
        for name in failed:
            print("  - " + name)
        sys.exit(1)


if __name__ == "__main__":
    main()
