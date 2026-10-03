#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
前端构建产物回收（cleanup_frontend_assets.py）

背景: deploy.sh 用 `cp -r dist/* runtime/frontend/` 增量复制，Vite 产物文件名带内容哈希，
     每次重新构建都生成新一代文件名，旧代永远留存（40+ 轮后已积上千个尸体文件）。

原理: 从 index.html 引用的入口文件出发做**引用闭包** BFS——Vite 入口 JS 内含全部当前代
     chunk 的文件名清单（modulepreload map），chunk 之间也以文件名字符串互相引用。
     闭包内的文件 = 当前代可用集合；闭包外的 assets 文件 = 旧代尸体，可安全删除。

策略: 默认 dry-run 只报告不删除；--apply 时删除"闭包外 且 mtime 早于 --days 天"的文件。
     保留近 3 天的旧文件是给浏览器缓存的旧 index.html 留缓冲（避免 404 白屏），
     index.html 本身已由 nginx 配 no-cache，3 天足够任何旧缓存过期。

用法:
    python cleanup_frontend_assets.py                 # dry-run，只打印报告
    python cleanup_frontend_assets.py --apply         # 实际删除
    python cleanup_frontend_assets.py --root /c/his-runtime/frontend --days 7 --apply
"""
import argparse
import os
import re
import sys
import time

# Vite 产物文件名（含哈希）与静态资源引用都长这样
ASSET_RE = re.compile(
    r"[A-Za-z0-9][A-Za-z0-9_.@-]*\.(?:js|mjs|css|woff2?|ttf|otf|png|jpe?g|svg|gif|ico|webp)"
)


def build_reachable_set(root, asset_files):
    """从 index.html 出发，扫描每个可达文件内容中引用的文件名，直到闭包收敛"""
    reachable = set()
    queue = []
    index_path = os.path.join(root, "index.html")
    if not os.path.isfile(index_path):
        raise SystemExit("[ERROR] index.html 不存在: %s" % index_path)
    with open(index_path, "rb") as f:
        seed = ASSET_RE.findall(f.read().decode("utf-8", errors="ignore"))
    for name in seed:
        if name in asset_files and name not in reachable:
            reachable.add(name)
            queue.append(name)
    while queue:
        cur = queue.pop()
        with open(os.path.join(root, "assets", cur), "rb") as f:
            content = f.read().decode("utf-8", errors="ignore")
        for name in ASSET_RE.findall(content):
            if name in asset_files and name not in reachable:
                reachable.add(name)
                queue.append(name)
    return reachable


def fmt_size(n):
    return "%.1f MB" % (n / 1048576.0) if n >= 1048576 else "%.1f KB" % (n / 1024.0)


def main():
    ap = argparse.ArgumentParser(description="清理旧代前端构建产物（引用闭包外文件）")
    ap.add_argument("--root", default="C:/his-runtime/frontend", help="前端部署根目录")
    ap.add_argument("--days", type=int, default=3,
                    help="闭包外文件保留天数（保护缓存的旧 index.html），默认 3")
    ap.add_argument("--apply", action="store_true", help="实际执行删除（默认 dry-run）")
    args = ap.parse_args()

    assets_dir = os.path.join(args.root, "assets")
    if not os.path.isdir(assets_dir):
        raise SystemExit("[ERROR] assets 目录不存在: %s" % assets_dir)

    asset_files = {}
    for name in os.listdir(assets_dir):
        path = os.path.join(assets_dir, name)
        if os.path.isfile(path):
            asset_files[name] = path

    reachable = build_reachable_set(args.root, set(asset_files))
    cutoff = time.time() - args.days * 86400

    stale = []
    stale_bytes = 0
    for name, path in asset_files.items():
        if name not in reachable and os.path.getmtime(path) < cutoff:
            stale.append((name, path))
            stale_bytes += os.path.getsize(path)

    print("===== 前端构建产物回收%s =====" % ("（dry-run）" if not args.apply else ""))
    print("assets 总数: %d 个 / %s" % (len(asset_files),
          fmt_size(sum(os.path.getsize(p) for p in asset_files.values()))))
    print("当前代引用闭包: %d 个文件" % len(reachable))
    print("可回收（闭包外 且 >%d 天）: %d 个 / %s" % (args.days, len(stale), fmt_size(stale_bytes)))

    if not stale:
        print("没有可回收文件。")
        return

    ok = err = 0
    freed = 0
    for name, path in sorted(stale):
        if args.apply:
            size = os.path.getsize(path)  # 先取大小再删（删完 getsize 会 WinError 2）
            try:
                os.remove(path)
                ok += 1
                freed += size
            except OSError as e:
                err += 1
                print("  [WARN] 删除失败 %s: %s" % (name, e))
        else:
            ok += 1
    if args.apply:
        print("已删除 %d 个文件，释放 %s%s" % (ok, fmt_size(freed),
              ("，失败 %d 个" % err) if err else ""))
        print("回收后 assets: %d 个文件" % (len(asset_files) - ok))
    else:
        print("（dry-run 未执行删除；确认无误后加 --apply）")


if __name__ == "__main__":
    main()
