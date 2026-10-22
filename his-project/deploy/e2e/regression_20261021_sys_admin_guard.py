#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-10-21 用户管理提权守卫回归：非管理员（仅有 sys:user:create）建档不得绑定 ADMIN 角色。"""
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


def login(username, password=PASSWORD):
    st, r = call("POST", "/auth/login", body={"username": username, "password": password})
    assert r.get("code") == "OK", "登录失败 %s: %s" % (username, r)
    return r["data"]["token"]


def user_body(name, role_ids):
    return {"username": name, "realName": "临时提权回归", "password": "Zz12345678",
            "phone": "13800000000", "roleIds": role_ids}


def cleanup():
    sql = (
        "DELETE FROM his.sys_user_role WHERE user_id IN "
        "(SELECT id FROM his.sys_user WHERE username LIKE 'zzu{run}%');"
        "DELETE FROM his.sys_user WHERE username LIKE 'zzu{run}%';"
        "DELETE FROM his.sys_user_role WHERE role_id IN "
        "(SELECT id FROM his.sys_role WHERE role_code='ZZROLE{run}');"
        "DELETE FROM his.sys_role_menu WHERE role_id IN "
        "(SELECT id FROM his.sys_role WHERE role_code='ZZROLE{run}');"
        "DELETE FROM his.sys_role WHERE role_code='ZZROLE{run}';"
    ).format(run=RUN)
    subprocess.run([MYSQL, "-uroot", "-proot123", "-e", sql], capture_output=True, text=True)


def main():
    admin = login("admin")
    role_code = "ZZROLE" + RUN
    st, r = call("POST", "/system/roles", admin,
                 {"roleCode": role_code, "roleName": "临时用户管理角色", "description": "regression"},
                 idem="rc-sys-role-" + RUN)
    st2, roles = call("GET", "/system/roles", admin)
    role = next((x for x in roles["data"] if x["roleCode"] == role_code), None)
    check("D1. 临时角色创建", r.get("code") == "OK" and role is not None, r)
    if not role:
        return finish()

    st, r = call("PUT", "/system/roles/%d/menus" % role["id"], admin, {"menuIds": [6]},
                 idem="rc-sys-rm-" + RUN)
    check("D2. 授予临时角色用户新增权限（仅 sys:user:create）", r.get("code") == "OK", r)

    uname = "zzu" + RUN
    st, r = call("POST", "/system/users", admin, user_body(uname, [role["id"]]),
                 idem="rc-sys-u1-" + RUN)
    check("D3. 管理员创建受限用户", r.get("code") == "OK", r)

    token = None
    try:
        token = login(uname, "Zz12345678")
        check("D4. 受限用户登录", bool(token))
    except AssertionError as e:
        check("D4. 受限用户登录", False, e)

    if token:
        esc = "zzu" + RUN + "a"
        st, r = call("POST", "/system/users", token, user_body(esc, [1]),
                     idem="rc-sys-esc-" + RUN)
        check("D5. 仅持 sys:user:create 的用户不得创建 ADMIN 账号",
              r.get("code") == "A0003", r)

        sub = "zzu" + RUN + "b"
        st, r = call("POST", "/system/users", token, user_body(sub, [role["id"]]),
                     idem="rc-sys-sub-" + RUN)
        check("D6. 受限用户仍可创建同角色普通账号（守卫不过度拦截）", r.get("code") == "OK", r)

        call("POST", "/auth/logout", token)
    call("POST", "/auth/logout", admin)
    finish()


def finish():
    cleanup()
    failed = [x for x in RESULTS if not x[1]]
    print("\n===== sys admin guard regression: %d/%d passed, %d failed =====" %
          (len(RESULTS) - len(failed), len(RESULTS), len(failed)))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
