#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-10-22 安全与边界动态矩阵。

覆盖：
A. 角色权限矩阵（22 个越权拒绝 + 14 个正向对照）
B. 横向越权（收费员账单读取/退费、医生就诊详情）
C. 身份认证边界（无 token / 伪造 token / 登录限流 / 用户枚举一致性）
D. 输入边界（分页夹取、SQL 注入、超长 ID、负数押金、负退费、金额篡改）
E. 幂等（缺失 key、同 key 重放、同 key 异体）
F. 批量赋值（患者更新夹带 idCardNo/patientNo/status）
用法：python regression_20261022_matrix.py [baseUrl]
"""
import json
import random
import subprocess
import sys
import time
import urllib.error
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8080/api/v1"
PASSWORD = "His@2026"
RUN = str(int(time.time() * 1000))[-8:] + str(random.randint(10, 99))
RESULTS = []
MYSQL = r"C:/his-runtime/mysql-8.0.36-winx64/bin/mysql.exe"
TOKENS = {}


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
    RESULTS.append((name, bool(cond), str(detail)[:300]))
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:300]) if (detail and not cond) else ""))


def login(username, password=PASSWORD):
    st, r = call("POST", "/auth/login", body={"username": username, "password": password})
    if r.get("code") != "OK":
        raise RuntimeError("登录失败 %s: %s" % (username, r))
    return r["data"]["token"]


def denied(r):
    return r.get("code") == "A0003"


def main():
    # ---------- A. 角色权限矩阵 ----------
    roles = ["admin", "dr.wang", "cashier.li", "nurse.wang", "pharm.zhao", "auditor.sun",
             "mrc.zhou", "yb.sun", "dr.li", "lab.chen", "ris.zhang", "bb.tech", "emc.li", "pe.nurse"]
    for u in roles:
        try:
            TOKENS[u] = login(u)
        except Exception as e:
            check("A0. 演示账号可登录 " + u, False, e)
    check("A0. 全部演示角色登录", len(TOKENS) == len(roles), "成功 %d/%d" % (len(TOKENS), len(roles)))

    neg = [
        ("cashier 访问用户管理", "cashier.li", "GET", "/system/users?pageNum=1&pageSize=1", None),
        ("cashier 访问操作日志", "cashier.li", "GET", "/logs/operations?pageNum=1&pageSize=1", None),
        ("doctor 新增收费单", "dr.wang", "POST", "/billing/bills", {"visitId": 1, "payMethod": 1}),
        ("doctor 退费", "dr.wang", "POST", "/billing/refunds", {"billId": 1888, "reason": "越权测试", "details": [{"chargeDetailId": 4301, "refundQuantity": 1}]}),
        ("doctor 访问 KPI", "dr.wang", "GET", "/report/kpi/workload", None),
        ("doctor 审核发药", "dr.wang", "POST", "/pharmacy/prescriptions/1/review", {"pass": True}),
        ("nurse 收费", "nurse.wang", "POST", "/billing/bills", {"visitId": 1, "payMethod": 1}),
        ("nurse 访问人事", "nurse.wang", "GET", "/hr/staff?pageNum=1&pageSize=1", None),
        ("pharmacist 建档", "pharm.zhao", "POST", "/patients",
         {"name": "越权", "gender": 1, "idCardNo": "110101199001011234", "phone": "13800000000"}),
        ("auditor 建档", "auditor.sun", "POST", "/patients",
         {"name": "越权", "gender": 1, "idCardNo": "110101199001011234", "phone": "13800000000"}),
        ("auditor 访问用户管理", "auditor.sun", "GET", "/system/users?pageNum=1&pageSize=1", None),
        ("mrc 访问 CDSS 规则", "mrc.zhou", "GET", "/cdss/rules?pageNum=1&pageSize=1", None),
        ("medclerk 访问用户管理", "yb.sun", "GET", "/system/users?pageNum=1&pageSize=1", None),
        ("cashier 访问 CDSS 规则", "cashier.li", "GET", "/cdss/rules?pageNum=1&pageSize=1", None),
        ("cashier 访问物资采购", "cashier.li", "POST", "/whse/purchase-orders",
         {"supplierId": 1, "drugId": 1, "quantity": 1, "unitPrice": 1}),
        ("cashier 访问体检套餐", "cashier.li", "GET", "/pe/packages?pageNum=1&pageSize=1", None),
        ("lis 访问手术申请", "lab.chen", "GET", "/ors/requests?pageNum=1&pageSize=1", None),
        ("ris 访问血库发血", "ris.zhang", "POST", "/bb/requests/1/issue",
         {"bagId": 1, "receiverId": 1}),
        ("bb 访问处方审核", "bb.tech", "GET", "/pharmacy/prescriptions", None),
        ("emc 访问病案归档", "emc.li", "GET", "/mrc/records?pageNum=1&pageSize=1", None),
        ("pe 访问收费单", "pe.nurse", "GET", "/billing/bills?pageNum=1&pageSize=1", None),
        ("pharmacist 访问医生工作台", "pharm.zhao", "GET", "/clinic/queue", None),
    ]
    for i, (name, user, method, path, body) in enumerate(neg, 1):
        st, r = call(method, path, TOKENS.get(user), body, "zz-neg-" + RUN + "-" + str(i)) if method == "POST" else call(method, path, TOKENS.get(user), body, None)
        check("A%d. %s被拒(A0003)" % (i, name), st == 403 or denied(r), "HTTP %s %s" % (st, r))

    pos = [
        ("cashier 患者检索", "cashier.li", "GET", "/patients?pageNum=1&pageSize=1"),
        ("doctor 候诊队列", "dr.wang", "GET", "/clinic/queue"),
        ("doctor 就诊分页", "dr.wang", "GET", "/clinic/visits?pageNum=1&pageSize=1"),
        ("nurse 病区列表", "nurse.wang", "GET", "/inp/wards"),
        ("pharmacist 待审处方", "pharm.zhao", "GET", "/pharmacy/prescriptions"),
        ("auditor KPI", "auditor.sun", "GET", "/report/kpi/workload"),
        ("auditor 收入报表", "auditor.sun", "GET", "/stats/revenue/daily?startDate=2026-10-01&endDate=2026-10-22"),
        ("mrc 病案列表", "mrc.zhou", "GET", "/mrc/records?pageNum=1&pageSize=1"),
        ("medclerk 医保结算列表", "yb.sun", "GET", "/medins/settles?pageNum=1&pageSize=1"),
        ("doctor 手术申请列表", "dr.wang", "GET", "/ors/requests?pageNum=1&pageSize=1"),
        ("lis 检验列表", "lab.chen", "GET", "/lis/requests?pageNum=1&pageSize=1"),
        ("ris 检查列表", "ris.zhang", "GET", "/ris/requests?pageNum=1&pageSize=1"),
        ("bb 可用血袋", "bb.tech", "GET", "/bb/bags/available"),
        ("emc 分诊列表", "emc.li", "GET", "/emc/triage?pageNum=1&pageSize=1"),
        ("pe 体检记录", "pe.nurse", "GET", "/pe/records?pageNum=1&pageSize=1"),
    ]
    for i, (name, user, method, path) in enumerate(pos, 1):
        st, r = call(method, path, TOKENS.get(user))
        check("A%d+. 正向对照：%s" % (i, name), st == 200 and r.get("code") == "OK", "HTTP %s %s" % (st, r))

    # ---------- B. 横向越权 ----------
    st, r = call("GET", "/billing/bills/1888", TOKENS["cashier.li"])
    check("B1. 原收费员可查看本人账单", st == 200 and r.get("code") == "OK", r)
    other = login("perf0007140000", "Perf@2026x")  # 另一个 CASHIER
    st, r = call("GET", "/billing/bills/1888", other)
    check("B2. 其他收费员查看他人账单被拒(A0003)", st == 403 or denied(r), "HTTP %s %s" % (st, r))
    st, r = call("GET", "/billing/bills?pageNum=1&pageSize=100", other)
    ids = [b["id"] for b in (r.get("data", {}) or {}).get("list", [])] if r.get("code") == "OK" else []
    check("B3. 其他收费员账单列表不含他人账单", r.get("code") == "OK" and 1888 not in ids, r)
    st, r = call("POST", "/billing/refunds", other,
                 {"billId": 1888, "reason": "越权测试", "details": [{"chargeDetailId": 4301, "refundQuantity": 1}]}, idem="zz-refund-other-" + RUN)
    check("B4. 其他收费员退他人账单被拒(A0003)", st == 403 or denied(r), "HTTP %s %s" % (st, r))

    st, r = call("GET", "/clinic/visits/1998", TOKENS["dr.li"])
    check("B5. 接诊医生可查看本人就诊", st == 200 and r.get("code") == "OK", r)
    st, r = call("GET", "/clinic/visits/1998", TOKENS["dr.wang"])
    check("B6. 其他医生查看他人就诊被拒(A0003)", st == 403 or denied(r), "HTTP %s %s" % (st, r))
    st, r = call("GET", "/clinic/visits/1998", TOKENS["admin"])
    check("B7. 管理员可查看任意就诊", st == 200 and r.get("code") == "OK", r)

    # ---------- C. 认证边界 ----------
    st, r = call("GET", "/patients?pageNum=1&pageSize=1")
    check("C1. 无 token 访问被拒(401)", st == 401, "HTTP %s %s" % (st, r))
    st, r = call("GET", "/patients?pageNum=1&pageSize=1", "abc.def.ghi")
    check("C2. 伪造 token 被拒(401)", st == 401, "HTTP %s %s" % (st, r))

    fake = "zz_nouser_" + RUN
    st, r1 = call("POST", "/auth/login", body={"username": fake, "password": "WrongPass1"})
    st, r2 = call("POST", "/auth/login", body={"username": "admin", "password": "WrongPass1"})
    check("C3. 不存在用户与错误密码返回一致(A0002)",
          r1.get("code") == "A0002" and r2.get("code") == "A0002" and r1.get("message") == r2.get("message"),
          (r1, r2))

    codes = []
    for _ in range(4):
        st, r = call("POST", "/auth/login", body={"username": fake, "password": "WrongPass1"})
        codes.append(r.get("code"))
    st, r = call("POST", "/auth/login", body={"username": fake, "password": "WrongPass1"})
    blocked = r.get("code") == "A0007"
    check("C4. 连续失败触发登录限流(A0007)", blocked, codes + [r.get("code")])

    # ---------- D. 输入边界 ----------
    st, r = call("GET", "/patients?name=%27%20OR%201%3D1--", TOKENS["admin"])
    check("D1. 患者姓名 SQL 注入无效果", st == 200 and r.get("code") == "OK"
          and (r.get("data", {}) or {}).get("total") == 0, r)
    st, r = call("GET", "/patients?pageNum=1&pageSize=100000", TOKENS["admin"])
    n = len((r.get("data", {}) or {}).get("list", [])) if r.get("code") == "OK" else -1
    check("D2. 超大 pageSize 被夹取到 ≤200", r.get("code") == "OK" and 0 < n <= 200, "返回 %d 条" % n)
    st, r = call("GET", "/patients?pageNum=-1&pageSize=10", TOKENS["admin"])
    check("D3. 负页码不报错（按第一页处理）", st == 200 and r.get("code") == "OK", "HTTP %s %s" % (st, r))
    st, r = call("GET", "/patients/999999999999999999999", TOKENS["admin"])
    check("D4. 超长路径 ID 返回 4xx 而非 500", 400 <= st < 500, "HTTP %s %s" % (st, r))

    st, r = call("POST", "/inp/admissions/1/deposits", TOKENS["cashier.li"],
                 {"amount": -100, "payMethod": 1})
    check("D5. 负数押金被拒", st == 400 and r.get("code") == "A0001", "HTTP %s %s" % (st, r))
    st, r = call("POST", "/billing/bills", TOKENS["cashier.li"], {"visitId": 1, "payMethod": 9})
    check("D6. 非法支付方式被拒", st == 400, "HTTP %s %s" % (st, r))
    st, r = call("POST", "/billing/refunds", TOKENS["cashier.li"],
                 {"billId": 1888, "reason": "负数测试",
                  "details": [{"chargeDetailId": 4301, "refundQuantity": -1}]})
    check("D7. 负退费数量被拒(B3003)", r.get("code") == "B3003" or st == 400, "HTTP %s %s" % (st, r))

    # ---------- E. 幂等 ----------
    card = "9999" + RUN + "0000"
    card = card[:18].ljust(18, "0")
    idem_body = {"name": "ZZ幂等回归", "gender": 1, "idCardNo": card, "phone": "13900000001"}
    st, r = call("POST", "/patients", TOKENS["cashier.li"], idem_body)
    check("E1. 缺少幂等键被拒(A0004)", r.get("code") == "A0004", r)
    st, r1 = call("POST", "/patients", TOKENS["cashier.li"], idem_body, idem="zz-idem-" + RUN)
    st, r2 = call("POST", "/patients", TOKENS["cashier.li"], idem_body, idem="zz-idem-" + RUN)
    check("E2. 同键同体重放返回相同结果",
          r1.get("code") == "OK" and r2.get("code") == "OK" and r1.get("data") == r2.get("data"), (r1, r2))
    st, r3 = call("POST", "/patients", TOKENS["cashier.li"],
                  {"name": "ZZ幂等异体", "gender": 2, "idCardNo": card, "phone": "13900000002"},
                  idem="zz-idem-" + RUN)
    check("E3. 同键异体被拒(A0005)", r3.get("code") == "A0005", r3)
    st, rl = call("GET", "/patients?idCardNo=" + card, TOKENS["cashier.li"])
    total = (rl.get("data", {}) or {}).get("total") if rl.get("code") == "OK" else -1
    check("E4. 重复提交仅落一条患者档案", total == 1, "total=%s %s" % (total, rl))

    # ---------- F. 批量赋值 ----------
    pid = None
    if rl.get("code") == "OK" and (rl.get("data", {}) or {}).get("list"):
        pid = rl["data"]["list"][0]["id"]
    if pid:
        before = subprocess.run(
            [MYSQL, "-uroot", "-proot123", "--default-character-set=utf8mb4", "-N", "-e",
             "USE his; SELECT patient_no,id_card_hash,status FROM pat_patient WHERE id=%d;" % pid],
            capture_output=True, text=True).stdout.strip()
        st, r = call("PUT", "/patients/%d" % pid, TOKENS["cashier.li"],
                     {"phone": "13900000003", "address": "回归地址",
                      "allergyHistory": "无", "pastHistory": "无",
                      "patientNo": "HACK" + RUN, "idCardNo": "110101199001011234", "status": 0})
        after = subprocess.run(
            [MYSQL, "-uroot", "-proot123", "--default-character-set=utf8mb4", "-N", "-e",
             "USE his; SELECT patient_no,id_card_hash,status FROM pat_patient WHERE id=%d;" % pid],
            capture_output=True, text=True).stdout.strip()
        check("F1. 患者更新夹带 patientNo/idCardNo/status 被拒且数据不变",
              st == 400 and before == after, "HTTP %s %s before=%s after=%s" % (st, r, before, after))
        st, r = call("PUT", "/patients/%d" % pid, TOKENS["cashier.li"],
                     {"phone": "13900000004", "address": "回归地址2",
                      "allergyHistory": "无", "pastHistory": "无"}, idem="zz-upd-" + RUN)
        check("F2. 合法字段更新仍成功", st == 200 and r.get("code") == "OK", "HTTP %s %s" % (st, r))
        # 清理本轮幂等测试患者（MPI 主索引 + 交叉索引 + 患者档案）
        sql = ("DELETE FROM his.plt_id_map WHERE mpi_id IN "
               "(SELECT id FROM his.plt_master_index WHERE patient_id=%d);"
               "DELETE FROM his.plt_master_index WHERE patient_id=%d;"
               "DELETE FROM his.pat_patient WHERE id=%d;") % (pid, pid, pid)
        subprocess.run([MYSQL, "-uroot", "-proot123", "-e", sql], capture_output=True, text=True)

    failed = [x for x in RESULTS if not x[1]]
    print("\n===== 安全矩阵结果: %d/%d 通过 =====" % (len(RESULTS) - len(failed), len(RESULTS)))
    if failed:
        for name, _, detail in failed:
            print("  FAIL " + name + " | " + detail)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
