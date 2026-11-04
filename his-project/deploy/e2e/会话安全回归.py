#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""真实接口验证多端登录与会话撤销；仅创建并停用本轮临时用户。"""
import json
import sys
import urllib.error
import urllib.request
import uuid

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080/api/v1").rstrip("/")
PASSWORD = "His@2026"
results = []


def call(method, path, token=None, body=None):
    request = urllib.request.Request(
        BASE + path, method=method,
        data=json.dumps(body).encode("utf-8") if body is not None else None,
        headers={"Content-Type": "application/json", "X-Idempotency-Key": str(uuid.uuid4())},
    )
    if token:
        request.add_header("Authorization", "Bearer " + token)
    try:
        response = urllib.request.urlopen(request, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        return response.status, json.loads(response.read().decode("utf-8"))


def success(method, path, token=None, body=None):
    status, response = call(method, path, token, body)
    assert status == 200 and response.get("code") == "OK", (
        f"接口失败：{method} {path}，状态={status}，错误码={response.get('code')}"
    )
    return response.get("data")


def login(username, password=PASSWORD):
    return success("POST", "/auth/login", body={"username": username, "password": password})["token"]


def check(name, condition):
    results.append(bool(condition))
    print(("通过：" if condition else "失败：") + name, flush=True)


def authorized(token):
    return call("GET", "/auth/me", token)[0] == 200


def revoked(token):
    return call("GET", "/auth/me", token)[0] == 401


def main():
    admin = login("admin")
    username = "session_" + uuid.uuid4().hex[:12]
    roles = success("GET", "/system/roles", admin)
    role_id = next(role["id"] for role in roles if role["roleCode"] == "CASHIER")
    user_id = success("POST", "/system/users", admin, {
        "username": username, "realName": "会话安全回归用户", "password": PASSWORD, "roleIds": [role_id],
    })
    try:
        first, second = login(username), login(username)
        check("同账户多端登录均可使用", authorized(first) and authorized(second))
        success("POST", "/auth/logout", first)
        check("退出使同账户旧会话全部失效", revoked(first) and revoked(second))
        current = login(username)
        check("重新登录可正常访问", authorized(current))
        check("重新登录不能复活退出前的令牌", revoked(first) and revoked(second))

        success("PUT", "/auth/password", current, {"oldPassword": PASSWORD, "newPassword": "New-pass2026"})
        check("本人改密立即撤销旧令牌", revoked(current))
        changed = login(username, "New-pass2026")
        check("新密码可登录且旧令牌仍失效", authorized(changed) and revoked(current))

        success("PUT", f"/system/users/{user_id}/password/reset", admin, {"newPassword": "Reset-pass2026"})
        check("管理员重置密码立即撤销旧令牌", revoked(changed))
        reset = login(username, "Reset-pass2026")
        check("重置后重新登录不能复活旧令牌", authorized(reset) and revoked(changed))

        success("PUT", f"/system/users/{user_id}/status", admin, {"status": 0})
        check("停用用户立即撤销会话", revoked(reset))
        success("PUT", f"/system/users/{user_id}/status", admin, {"status": 1})
        enabled = login(username, "Reset-pass2026")
        check("重新启用并登录不能复活停用前令牌", authorized(enabled) and revoked(reset))
    finally:
        success("PUT", f"/system/users/{user_id}/status", admin, {"status": 0})
    print(f"会话安全回归：{sum(results)}/{len(results)} 通过")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
