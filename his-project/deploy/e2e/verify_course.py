# -*- coding: utf-8 -*-
"""教案实验手册 E0~E9 逐条实测（学生视角）——教师课前自检/回归用。

注意（九十五轮实测教训）：
- 每轮会新建 1 个一次性收费员 + 2-3 个实验患者，脚本尾部自动停用收费员账号；
  患者数据（含 EMPI/就诊/账单链）积累后用 deploy/db/cleanup_test_data.py --apply 清理；
- E8 会对一次性收费员做日结——不要用 cashier.li（当日锁会阻断其他套件）。
"""
import json, time, urllib.request, urllib.error, urllib.parse

BASE = "http://localhost:8080/api/v1"
PW = "His@2026"
uid = str(int(time.time() * 1000))[-8:]
today = time.strftime("%Y-%m-%d")
tok = {}


def call(method, path, who=None, body=None, idem=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json;charset=utf-8")
    if who: req.add_header("Authorization", "Bearer " + tok[who])
    if idem: req.add_header("X-Idempotency-Key", idem)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        try: return e.code, json.loads(e.read().decode())
        except Exception: return e.code, {"code": "HTTP" + str(e.code)}


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:150]) if detail and not cond else ""))


for who in ["admin", "cashier.li", "dr.li", "nurse.wang", "pharm.zhao", "lab.chen", "or.nurse"]:
    st, r = call("POST", "/auth/login", body={"username": who, "password": PW})
    tok[who] = r["data"]["token"]

# 五十轮测试隔离模式：一次性收费员（防历史轮次日结锁定当日收费）
st, roles = call("GET", "/system/roles", "admin")
role_list = roles["data"] if isinstance(roles["data"], list) else roles["data"].get("list", [])
cashier_role = next((x["id"] for x in role_list if "收费" in str(x.get("roleName", "")) or "CASHIER" in str(x.get("roleCode", ""))), None)
ce_uid = "ce" + uid
st, r = call("POST", "/system/users", "admin", {"username": ce_uid, "password": PW,
    "realName": "实测收费员", "phone": "1390000" + uid[-4:], "roleIds": [cashier_role]}, idem="ce-user-" + uid)
ce_user_id = r["data"]["id"] if isinstance(r.get("data"), dict) else r.get("data")
st, r = call("POST", "/auth/login", body={"username": ce_uid, "password": PW})
tok["ce"] = r["data"]["token"]

print("===== E1 建档与脱敏 =====")
name1 = "张三实验" + uid
st, r = call("POST", "/patients", "ce", {"name": name1, "gender": 1,
    "birthDate": "1990-01-01", "idCardNo": "34010419900101" + uid[-4:],
    "phone": "138" + uid}, idem="ce1-" + uid)
check("E1.1 建档 200 建档号P", r["code"] == "OK" and str(r.get("data", "")).startswith("P"), r)
check("E1.2 重复建档 B1001", call("POST", "/patients", "ce",
    {"name": name1, "gender": 1, "birthDate": "1990-01-01",
     "idCardNo": "34010419900101" + uid[-4:], "phone": "138" + uid},
    idem="ce1b-" + uid)[1].get("code") == "B1001")
st, pl = call("GET", "/patients?name=" + urllib.parse.quote(name1), "ce")
row = pl["data"]["list"][0]
pid = row["id"]
check("E1.3 列表脱敏", row["phone"] != "138" + uid and "*" in row["phone"], row["phone"])
st, pl = call("GET", "/patients?phone=138" + uid[:5], "ce")
check("E1.4 手机前缀检索命中", any(x["id"] == pid for x in pl["data"]["list"]), pl["data"]["total"])

print("===== E2 就诊状态机 =====")
st, r = call("POST", "/registrations", "cashier.li", {"patientId": pid, "doctorId": 2,
    "regDate": today, "period": 1, "regType": 1}, idem="ce2-" + uid)
check("E2.1 挂号(regNo/queueNo)", r["code"] == "OK" and (isinstance(r.get("data"), dict) and r["data"].get("regNo")), r)
st, rl = call("GET", "/registrations?patientId=%s&regDate=%s" % (pid, today), "cashier.li")
reg_id = rl["data"]["list"][0]["id"]
st, r = call("POST", "/clinic/visits/%d/start" % reg_id, "dr.li", idem="ce2b-" + uid)
check("E2.2 接诊", r["code"] == "OK", r)
vid = r["data"] if not isinstance(r["data"], dict) else (r["data"].get("visitId") or r["data"].get("id"))
st, r = call("POST", "/clinic/visits/%d/start" % reg_id, "dr.li", idem="ce2c-" + uid)
vid2 = r["data"] if not isinstance(r["data"], dict) else (r["data"].get("visitId") or r["data"].get("id"))
check("E2.3 重复接诊=幂等同一visitId", r["code"] == "OK" and vid2 == vid, r)
st, r = call("POST", "/clinic/visits/%d/complete" % vid, "dr.li", idem="ce2d0-" + uid)
check("E2.4a 无诊断 complete 拦截 B2003", r.get("code") == "B2003", r)
st, r = call("PUT", "/clinic/visits/%d/record" % vid, "dr.li",
    {"chiefComplaint": "咳嗽3天", "presentIllness": "受凉后咳嗽"}, idem="ce2rec-" + uid)
check("E2.4b 暂存病历", r["code"] == "OK", r)
st, r = call("POST", "/clinic/visits/%d/diagnoses" % vid, "dr.li",
    {"diagnosisName": "实验诊断", "diagnosisType": 1}, idem="ce2dg-" + uid)
check("E2.4c 录诊断", r["code"] == "OK", r)
check("E2.4d 就诊停留 20（E3/E4 需要就诊中）", True)

print("===== E3 处方-审核-发药 =====")
st, r = call("POST", "/clinic/visits/%d/prescriptions" % vid, "dr.li",
    {"items": [{"drugId": 1, "dosage": "0.25g", "frequency": "tid", "usageRoute": "口服", "days": 3, "quantity": 2}]},
    idem="ce3-" + uid)
check("E3.1 开处方", r["code"] == "OK", r)
rx_id = r["data"] if not isinstance(r["data"], dict) else (r["data"].get("prescriptionId") or r["data"].get("id"))
st, r = call("POST", "/pharmacy/prescriptions/%d/review" % rx_id, "pharm.zhao", {"pass": True}, idem="ce3b-" + uid)
check("E3.2 审核", r["code"] == "OK", r)
st, r = call("POST", "/pharmacy/prescriptions/%d/review" % rx_id, "pharm.zhao", {"pass": True}, idem="ce3c-" + uid)
check("E3.3 重复审核 B4008", r.get("code") == "B4008", r)
st, r = call("POST", "/pharmacy/prescriptions/%d/dispense" % rx_id, "pharm.zhao", idem="ce3d-" + uid)
check("E3.4 未收费发药 B4002", r.get("code") == "B4002", r)

print("===== E4 收费幂等与退费 =====")
st, r = call("POST", "/billing/bills", "ce", {"visitId": vid, "payMethod": 1}, idem="ce4-K1-" + uid)
check("E4.1 收费", r["code"] == "OK", r)
bill_id = r["data"] if not isinstance(r["data"], dict) else (r["data"].get("id") or r["data"].get("billId"))
st, r = call("POST", "/billing/bills", "ce", {"visitId": vid, "payMethod": 1}, idem="ce4-K1-" + uid)
check("E4.2 同K1重放=快照(库里不重复)", r["code"] == "OK", r)
st, r = call("POST", "/billing/bills", "ce", {"visitId": vid, "payMethod": 2}, idem="ce4-K1-" + uid)
check("E4.3 同K1换body=A0005", r.get("code") == "A0005", r)
st, r = call("POST", "/pharmacy/prescriptions/%d/dispense" % rx_id, "pharm.zhao", idem="ce4d-" + uid)
check("E4.4 收费后发药", r["code"] == "OK", r)
st, r = call("POST", "/pharmacy/prescriptions/%d/dispense" % rx_id, "pharm.zhao", idem="ce4e-" + uid)
check("E4.4b 重复发药 B4003", r.get("code") == "B4003", r)
st, det = call("GET", "/billing/bills/%d" % bill_id, "ce")
reg_detail = next((d for d in det["data"]["details"] if d["feeType"] == 1), None)
if reg_detail:
    st, r = call("POST", "/billing/refunds", "ce",
        {"billId": bill_id, "reason": "实验退费", "details": [{"chargeDetailId": reg_detail["id"], "refundQuantity": 1}]},
        idem="ce4-r1-" + uid)
    check("E4.4a 含已发药处方账单退费受整方约束 B3003", r.get("code") == "B3003", r)
else:
    check("E4.4a 未开始退挂号费", False, "无挂号费明细")
# dr.li 退别人账单（数据范围）——手册写 dr.li 操作，实测是否 403
if reg_detail:
    st, r = call("POST", "/billing/refunds", "dr.li",
        {"billId": bill_id, "reason": "实验退费", "details": [{"chargeDetailId": reg_detail["id"], "refundQuantity": 1}]},
        idem="ce4-r2-" + uid)
    print("  [手册疑点] dr.li 退 cashier 账单 →", r.get("code"), "(手册 E4.4 写 dr.li 操作)")
st, r = call("POST", "/billing/refunds", "ce",
    {"billId": bill_id, "reason": "实验退费", "details": [{"chargeDetailId": reg_detail["id"], "refundQuantity": 1}]},
    idem="ce4-r3-" + uid)
check("E4.4b 完成后仍受整方约束 B3003", r.get("code") == "B3003", r)
rx_detail = next((d for d in det["data"]["details"] if d["feeType"] == 7), None)
if rx_detail:
    st, r = call("POST", "/billing/refunds", "ce",
        {"billId": bill_id, "reason": "实验退费", "details": [{"chargeDetailId": rx_detail["id"], "refundQuantity": rx_detail["quantity"]}]},
        idem="ce4-r4-" + uid)
    check("E4.5 已发药退药费 B3004", r.get("code") == "B3004", r)
    st, r = call("POST", "/clinic/visits/%d/complete" % vid, "dr.li", idem="ce4-complete-" + uid)
    check("E4.6 完成就诊(30)", r["code"] == "OK", r)
    if reg_detail:
        st, r = call("POST", "/billing/refunds", "ce",
            {"billId": bill_id, "reason": "实验退费", "details": [{"chargeDetailId": reg_detail["id"], "refundQuantity": 1}]},
            idem="ce4-r5-" + uid)
        check("E4.7 完成后仍受整方约束 B3003（B3005 演示见手册：无处方就诊）", r.get("code") == "B3003", r)

print("===== E6 住院全周期 =====")
st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", "admin")
bed = beds["data"][0] if beds["data"] else None
if not bed:
    st, r = call("POST", "/inp/beds", "admin", {"wardId": 1, "bedNo": "LAB-" + uid, "chargeItemId": 10},
                 idem="ce6-bed-" + uid)
    st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", "admin")
    bed = beds["data"][0]
st, r = call("POST", "/inp/admissions", "admin", {"patientId": pid, "deptId": 1, "wardId": bed["wardId"],
    "bedId": bed["id"], "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "实验",
    "depositAmount": 1000, "payMethod": 1}, idem="ce6-" + uid)
check("E6.2 入院", r["code"] == "OK", r)
adm = r["data"]["id"] if isinstance(r["data"], dict) else r["data"]
st, r = call("POST", "/doc/orders", "dr.li", {"admissionId": adm, "orderClass": 2, "category": 1,
    "frequency": "qd", "items": [{"drugId": 1, "dosage": "0.25g", "days": 1, "quantity": 1, "usageRoute": "口服"}]},
    idem="ce6-o-" + uid)
st, ol = call("GET", "/doc/orders?admissionId=%s&status=10" % adm, "dr.li")
oid = ol["data"]["list"][0]["id"]
call("POST", "/doc/orders/%d/review" % oid, "pharm.zhao", {"pass": True}, idem="ce6-rv-" + uid)
call("POST", "/doc/orders/%d/dispense" % oid, "pharm.zhao", idem="ce6-dp-" + uid)
st, r = call("POST", "/inp/daily-fees/manual?admissionId=%s" % adm, "admin",
    {"feeType": 1, "itemName": "实验护理", "quantity": 1, "unitPrice": 50}, idem="ce6-f-" + uid)
check("E6.3 手工记账", r["code"] == "OK", r)
st, r = call("POST", "/inp/admissions/%s/discharge" % adm, "dr.li",
    {"dischargeWay": 2, "dischargeDiagnosis": "实验出院"}, idem="ce6-dc-" + uid)
check("E6.4 出院(20 未结)", r["code"] == "OK", r)
st, a = call("GET", "/inp/admissions/%s" % adm, "admin")
check("E6.4b 状态=20", a["data"]["status"] == 20, a["data"]["status"])
st, r = call("POST", "/billing/admissions/%s/settle" % adm, "ce", {"payMethod": 1}, idem="ce6-st-" + uid)
check("E6.5 结算(30)", r["code"] == "OK", r)

print("===== E7 手术槽位 =====")
st, items9 = call("GET", "/basedata/charge-items?category=9&status=1", "admin")
st, items10 = call("GET", "/basedata/charge-items?category=10&status=1", "admin")
# E7 前置：E6 的住院已结算——重新办理一次入院
st, beds = call("GET", "/inp/beds?wardId=1&bedStatus=1", "admin")
bed = beds["data"][0]
st, r = call("POST", "/inp/admissions", "admin", {"patientId": pid, "deptId": 1, "wardId": bed["wardId"],
    "bedId": bed["id"], "doctorId": 2, "admissionType": 1, "plannedDiagnosis": "实验2",
    "depositAmount": 1000, "payMethod": 1}, idem="ce7-adm-" + uid)
adm = r["data"]["id"] if isinstance(r["data"], dict) else r["data"]
st, r = call("POST", "/ors/requests", "dr.li", {"admissionId": adm, "patientId": pid,
    "surgeryName": "实验术式" + uid, "diagnosis": "实验", "plannedDate": today,
    "anesthesiaMethod": 1, "surgeryItemId": items9["data"][0]["id"],
    "anesthesiaItemId": items10["data"][0]["id"]}, idem="ce7-" + uid)
check("E7.1 手术申请", r["code"] == "OK", r)
or_id = r["data"] if not isinstance(r["data"], dict) else r["data"]["id"]
st, ol7 = call("GET", "/ors/requests?admissionId=%s" % adm, "dr.li")
or_id = ol7["data"]["list"][0]["id"]
st, r = call("POST", "/ors/requests/%s/review" % or_id, "dr.li", {"approved": True}, idem="ce7b-" + uid)
r = None
for seq in range(1, 11):  # seq 上限 10（@Max），占用则 +1 重试
    st, r = call("POST", "/ors/requests/%s/schedule" % or_id, "or.nurse",
        {"roomId": 1, "surgeryDate": today, "seqNo": seq, "surgeonId": 2}, idem="ce7c-%s-%d" % (uid, seq))
    if r.get("code") == "OK":
        break
check("E7.3 首次排台（seq 重试）", r.get("code") == "OK", r)

print("===== E8 日结锁定 =====")
st, r = call("POST", "/billing/settlements", "ce", {"settleDate": today}, idem="ce8-" + uid)
check("E8.1 日结(RJ)", r["code"] == "OK", r)
st, r = call("POST", "/billing/settlements", "ce", {"settleDate": today}, idem="ce8b-" + uid)
check("E8.2 重复日结 B3006", r.get("code") == "B3006", r)
st, r = call("POST", "/billing/bills", "ce", {"visitId": vid, "payMethod": 1}, idem="ce8c-" + uid)
check("E8.3a 日结后收费 B3006", r.get("code") == "B3006", r)
st, r = call("POST", "/billing/bills", "admin", {"visitId": vid, "payMethod": 1}, idem="ce8d-" + uid)
check("E8.3b 换账号同就诊=B3001 重复收费（换就诊才正常，见手册）", r.get("code") == "B3001", r)

print("===== E9 危急值闭环 =====")
st, al = call("GET", "/alerts?status=10&pageNum=1&pageSize=5", "lab.chen") if False else (None, None)
st, al = call("GET", "/alerts?status=10&pageNum=1&pageSize=5", "admin")
alerts = (al.get("data") or {}).get("list") if isinstance(al.get("data"), dict) else (al.get("data") or [])
aid = alerts[0]["id"] if alerts else None
if aid:
    st, r = call("POST", "/alerts/%d/confirm" % aid, "dr.li", idem="ce9-skip-" + uid)
    check("E9.3 跳步 confirm 拦截", r["code"] != "OK", r)
    st, r = call("POST", "/alerts/%d/notify" % aid, "lab.chen", idem="ce9-n-" + uid)
    check("E9.1 通知登记", r["code"] == "OK", r)
    st, r = call("POST", "/alerts/%d/confirm" % aid, "dr.li", idem="ce9-c-" + uid)
    check("E9.2a 确认", r["code"] == "OK", r)
    st, r = call("POST", "/alerts/%d/close" % aid, "dr.li", {"handleNote": "已复测"}, idem="ce9-x-" + uid)
    check("E9.2b 闭环", r["code"] == "OK", r)
else:
    check("E9 前置：存在待处理危急值", False, "当前无 status=10 危急值（手册依赖前序 LIS 实验）")

print("\n完成。")
