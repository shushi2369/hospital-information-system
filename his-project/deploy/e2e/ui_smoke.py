#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
UI 冒烟（ui_smoke.py）——Playwright 全菜单 404 猎手（bug 模式 14/17 的长期方案）。
流程：API 登录取菜单树 → 无头浏览器逐页导航 → 断言每页真实渲染（无"页面不存在"）。
用法：python ui_smoke.py [baseUrl]（默认 http://localhost）
前置：pip install playwright && playwright install chromium
"""
import json
import sys
import urllib.request

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost"
API = BASE + "/api/v1"
PASSWORD = "His@2026"
results = []


def api(path, token=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(API + path, data=data, method="POST" if body is not None else "GET")
    req.add_header("Content-Type", "application/json;charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    from playwright.sync_api import sync_playwright

    token = api("/auth/login", body={"username": "admin", "password": PASSWORD})["data"]["token"]
    menus = api("/auth/menus", token=token)["data"]
    pages = []

    def collect(nodes, base=""):
        # 与前端 buildDynamicRoutes 一致：子菜单 path 相对父级，拼接为完整路由
        for n in nodes:
            p = n.get("path") or ""
            full = p if p.startswith("/") else (base + "/" + p).replace("//", "/")
            if n.get("menuType") == 2:
                pages.append((n["menuName"], full))
            collect(n.get("children") or [], full)

    collect(menus)
    if not pages:
        print("FAIL 未获取到任何菜单页面")
        sys.exit(1)

    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page()
        # 登录一次，token 进 localStorage，后续导航全部带态
        page.goto(BASE + "/login")
        page.get_by_role("textbox", name="用户名").fill("admin")
        page.get_by_role("textbox", name="密码").fill(PASSWORD)
        page.get_by_role("button").first.click()
        page.wait_for_url("**/system/users", timeout=15000)

        for name, path in pages:
            page.goto(BASE + path)
            page.wait_for_load_state("domcontentloaded")
            page.wait_for_timeout(900)
            body = page.inner_text("body")
            ok = ("页面不存在" not in body) and ("404" not in body[:200] or "HIS" in body)
            # 渲染非空：主区域须有表格/表单/描述/卡片/标签/分页等实质内容
            has_content = page.locator(
                "main .el-table, main form, main .el-descriptions, main .el-card, "
                "main .el-tag, main .el-pagination").count() > 0
            ok = ok and has_content
            results.append((name, path, ok))
            print(("PASS " if ok else "FAIL ") + name + " " + path)
        browser.close()

    failed = [r for r in results if not r[2]]
    print("\n===== UI 冒烟结果: %d/%d 页面通过 =====" % (len(results) - len(failed), len(results)))
    if failed:
        for name, path, _ in failed:
            print("  - " + name + " " + path)
        sys.exit(1)


if __name__ == "__main__":
    main()
