#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""2026-10-22 全菜单浏览器审计：按角色登录，逐页收集 JS 异常 / 控制台错误 / 接口 4xx-5xx。

与 ui_smoke.py 的区别：不只断言页面渲染，而是把每个页面的运行期错误按 URL 归类，
用于发现"菜单可见但接口 403 / 前端字段错位 / 运行时报错"这类静态审计难以覆盖的问题。
用法：python ui_audit_20261022.py [baseUrl]
输出：ui_audit_20261022_result.json
"""
import json
import sys
import time
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1"
API = BASE + "/api/v1"
PASSWORD = "His@2026"
ROLES = ["admin", "dr.wang", "cashier.li", "nurse.wang", "pharm.zhao", "auditor.sun", "mrc.zhou", "yb.sun"]
IGNORE_CONSOLE = (
    "Content Security Policy directive 'script-src 'self''",
    "ResizeObserver loop",
    "favicon.ico",
)


def api(path, token=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(API + path, data=data, method="POST" if body is not None else "GET")
    req.add_header("Content-Type", "application/json;charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def menu_pages(token):
    menus = api("/auth/menus", token=token)["data"]
    pages = []

    def walk(nodes, base=""):
        for n in nodes:
            p = n.get("path") or ""
            full = p if p.startswith("/") else (base + "/" + p).replace("//", "/")
            if n.get("menuType") == 2:
                pages.append((n.get("menuName"), full))
            walk(n.get("children") or [], full)

    walk(menus)
    return pages


def main():
    from playwright.sync_api import sync_playwright

    all_results = {}
    with sync_playwright() as p:
        browser = p.chromium.launch()
        for username in ROLES:
            token = api("/auth/login", body={"username": username, "password": PASSWORD})["data"]["token"]
            pages = menu_pages(token)
            ctx = browser.new_context()
            page = ctx.new_page()
            console_errors, page_errors, failed_requests, api_errors = [], [], [], []

            def on_console(msg):
                if msg.type == "error" and not any(x in msg.text for x in IGNORE_CONSOLE):
                    console_errors.append(msg.text[:300])

            def on_pageerror(err):
                page_errors.append(str(err)[:300])

            def on_requestfailed(req):
                failed_requests.append(req.url[:200] + " | " + (req.failure or ""))

            def on_response(resp):
                if "/api/" in resp.url and resp.status >= 400:
                    api_errors.append("%s %s" % (resp.status, resp.url.split("?")[0]))

            page.on("console", on_console)
            page.on("pageerror", on_pageerror)
            page.on("requestfailed", on_requestfailed)
            page.on("response", on_response)

            # UI 登录（与真实用户一致，token 落入 localStorage）
            page.goto(BASE + "/login", wait_until="domcontentloaded")
            page.get_by_role("textbox", name="用户名").fill(username)
            page.get_by_role("textbox", name="密码").fill(PASSWORD)
            page.get_by_role("button").first.click()
            page.wait_for_timeout(2500)
            if "/login" in page.url:
                all_results[username] = {"login": "FAILED", "pages": []}
                print("FAIL 登录失败 " + username)
                ctx.close()
                continue

            role_pages = []
            for name, path in pages:
                console_errors.clear()
                page_errors.clear()
                failed_requests.clear()
                api_errors.clear()
                try:
                    page.goto(BASE + path, wait_until="domcontentloaded", timeout=20000)
                    page.wait_for_timeout(1100)
                    body = page.inner_text("body")
                    render_ok = ("页面不存在" not in body) and len(body.strip()) > 30
                except Exception as e:
                    page_errors.append("导航异常: " + str(e)[:200])
                    render_ok = False
                issues = []
                for e in page_errors:
                    issues.append("JS崩溃: " + e)
                for e in console_errors:
                    issues.append("控制台: " + e)
                for e in failed_requests:
                    issues.append("请求失败: " + e)
                # 接口错误按 方法+路径 去重，保留状态码
                for e in sorted(set(api_errors)):
                    issues.append("接口: " + e)
                if not render_ok:
                    issues.append("渲染: 页面内容为空或显示不存在")
                role_pages.append({"name": name, "path": path, "ok": not issues, "issues": issues})
                print(("PASS " if not issues else "FAIL ") + username + " " + name + " " + path
                      + ("  | " + " ; ".join(issues)[:400] if issues else ""))
            all_results[username] = {"login": "OK", "pages": role_pages}
            ctx.close()
        browser.close()

    with open("ui_audit_20261022_result.json", "w", encoding="utf-8") as f:
        json.dump(all_results, f, ensure_ascii=False, indent=2)
    total = sum(len(v["pages"]) for v in all_results.values())
    bad = sum(1 for v in all_results.values() for pg in v["pages"] if not pg["ok"])
    print("\n===== 浏览器审计: %d 个页面访问，%d 个存在问题 =====" % (total, bad))
    sys.exit(0)


if __name__ == "__main__":
    main()
