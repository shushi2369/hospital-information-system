#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
物资字典入参加固回归（2026-10-21）：
M1~M5 建档必填/边界；M6~M10 更新必填/边界与生效。
用法: python regression_20261021_mat.py [BASE_URL]
"""
import json
import sys
import time
import urllib.error
import urllib.request

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
    print(("PASS " if cond else "FAIL ") + name + (("  | " + str(detail)[:160]) if detail and not cond else ""))


def login(username):
    st, r = call("POST", "/auth/login", body={"username": username, "password": PASSWORD})
    assert r.get("code") == "OK", "登录失败: %s" % r
    return r["data"]["token"]


def main():
    run = str(int(time.time() * 1000))[-8:]
    admin = login("admin")
    code = "MATR" + run

    def material(**over):
        body = {"materialCode": code, "name": "回归物资" + run, "category": 1,
                "unit": "件", "price": 3.50, "safeStock": 5}
        body.update(over)
        return body

    st, r = call("POST", "/mat/materials", admin,
                 {"materialCode": "MATRN" + run, "category": 1, "unit": "件",
                  "price": 1, "safeStock": 0}, idem="rm-m1-" + run)
    check("M1. 建档缺名称拒绝", r.get("code") == "A0001", r)

    st, r = call("POST", "/mat/materials", admin,
                 material(name="   "), idem="rm-m2-" + run)
    check("M2. 建档空白名称拒绝", r.get("code") == "A0001", r)

    st, r = call("POST", "/mat/materials", admin,
                 material(price=-5), idem="rm-m3-" + run)
    check("M3. 建档负单价拒绝", r.get("code") == "A0001", r)

    st, r = call("POST", "/mat/materials", admin,
                 material(safeStock=-10), idem="rm-m4-" + run)
    check("M4. 建档负安全库存拒绝", r.get("code") == "A0001", r)

    st, r = call("POST", "/mat/materials", admin,
                 material(unit=""), idem="rm-m5-" + run)
    check("M5. 建档空单位按默认件", r.get("code") == "OK", r)

    st, ml = call("GET", "/mat/materials?category=1&pageSize=100", admin)
    row = next((m for m in ml["data"]["list"] if m["materialCode"] == code), None)
    check("M6. 建档成功且单位默认件", row is not None and row.get("unit") == "件", row)

    st, r = call("PUT", "/mat/materials/%d" % row["id"], admin,
                 material(name=""), idem="rm-m7-" + run)
    check("M7. 更新空白名称拒绝", r.get("code") == "A0001", r)

    st, r = call("PUT", "/mat/materials/%d" % row["id"], admin,
                 material(price=-1), idem="rm-m8-" + run)
    check("M8. 更新负单价拒绝", r.get("code") == "A0001", r)

    st, r = call("PUT", "/mat/materials/%d" % row["id"], admin,
                 material(status=9), idem="rm-m9-" + run)
    check("M9. 更新非法状态拒绝", r.get("code") == "A0001", r)

    st, r = call("PUT", "/mat/materials/%d" % row["id"], admin,
                 material(name="回归物资-改名" + run, price=6.25, safeStock=8),
                 idem="rm-m10-" + run)
    check("M10. 合法更新通过", r.get("code") == "OK", r)

    st, ml = call("GET", "/mat/materials?category=1&pageSize=100", admin)
    row2 = next((m for m in ml["data"]["list"] if m["materialCode"] == code), None)
    check("M11. 更新后名称/单价/安全库存生效",
          row2 is not None and row2.get("name", "").endswith("改名" + run)
          and abs(float(row2.get("price", 0)) - 6.25) < 1e-6
          and row2.get("safeStock") == 8, row2)

    st, r = call("PUT", "/mat/materials/%d" % row["id"], admin,
                 material(name="回归物资-停用" + run, price=6.25, safeStock=8, status=0),
                 idem="rm-m12-" + run)
    check("M12. 停用物资", r.get("code") == "OK", r)

    st, r = call("PUT", "/mat/materials/%d" % row["id"], admin,
                 material(name="回归物资-复启" + run, price=6.25, safeStock=8, status=1),
                 idem="rm-m13-" + run)
    check("M13. 停用后可编辑并重新启用", r.get("code") == "OK", r)

    st, r = call("PUT", "/mat/materials/%d" % row["id"], admin,
                 material(name="回归物资-复启" + run, price=6.25, safeStock=8, status=0),
                 idem="rm-m14-" + run)
    st, r2 = call("POST", "/mat/requisitions", admin,
                  {"materialId": row["id"], "deptId": 1, "quantity": 1},
                  idem="rm-m15-" + run)
    check("M14. 停用物资禁止领用", r2.get("code") == "A0001", r2)

    st, r = call("PUT", "/mat/materials/%d" % row["id"], admin,
                 material(name="回归物资-复启" + run, price=6.25, safeStock=8, status=1),
                 idem="rm-m16-" + run)
    check("M15. 复启成功", r.get("code") == "OK", r)

    passed = sum(1 for _, ok, _ in results if ok)
    print("\n===== %d/%d passed =====" % (passed, len(results)))
    return 0 if passed == len(results) else 1


if __name__ == "__main__":
    sys.exit(main())
