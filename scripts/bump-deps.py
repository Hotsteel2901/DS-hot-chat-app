#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-or-later
"""
把项目里的所有组件升到各自最新的稳定版。

覆盖四类：
  1. gradle/libs.versions.toml 中的全部版本号（AGP、Kotlin、Compose BOM、AndroidX、OkHttp、Coil、测试库……）
  2. Gradle wrapper 自身（--gradle）
  3. .github/workflows/*.yml 中使用的 GitHub Actions（--actions）
  4. 只做保守替换：拒绝 alpha / beta / rc / dev / preview / snapshot 等预发布版

用法：
  python3 scripts/bump-deps.py                 # 升级依赖 + Gradle + Actions
  python3 scripts/bump-deps.py --no-actions    # 只升依赖与 Gradle
  python3 scripts/bump-deps.py --only okhttp,coil
  python3 scripts/bump-deps.py --dry-run       # 只打印，不写文件
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CATALOG = ROOT / "gradle" / "libs.versions.toml"
WRAPPER = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
WORKFLOWS = ROOT / ".github" / "workflows"

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"

# 版本键 -> (仓库, group:artifact)；键名与 libs.versions.toml 的 [versions] 一一对应
CATALOG_COORDS: dict[str, tuple[str, str]] = {
    "agp": (GOOGLE, "com.android.tools.build:gradle"),
    "kotlin": (CENTRAL, "org.jetbrains.kotlin:kotlin-gradle-plugin"),
    "composeBom": (GOOGLE, "androidx.compose:compose-bom"),
    "coreKtx": (GOOGLE, "androidx.core:core-ktx"),
    "activityCompose": (GOOGLE, "androidx.activity:activity-compose"),
    "lifecycle": (GOOGLE, "androidx.lifecycle:lifecycle-runtime-ktx"),
    "datastore": (GOOGLE, "androidx.datastore:datastore-preferences"),
    "serializationJson": (CENTRAL, "org.jetbrains.kotlinx:kotlinx-serialization-json"),
    "coroutines": (CENTRAL, "org.jetbrains.kotlinx:kotlinx-coroutines-android"),
    "okhttp": (CENTRAL, "com.squareup.okhttp3:okhttp"),
    "coil": (CENTRAL, "io.coil-kt:coil-compose"),
    "junit": (CENTRAL, "junit:junit"),
    "androidxTestCore": (GOOGLE, "androidx.test:core"),
    "robolectric": (CENTRAL, "org.robolectric:robolectric"),
}

PRERELEASE = re.compile(
    r"(?:^|[.\-_])(alpha|beta|rc|cr|dev|preview|snapshot|eap|m\d+|b\d+)(?:[.\-_]|\d|$)",
    re.IGNORECASE,
)


def http_text(url: str, timeout: int = 30) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": "deepseek-hot/bump-deps"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", "replace")


def http_json(url: str, timeout: int = 30):
    return json.loads(http_text(url, timeout))


def vkey(version: str) -> tuple:
    """把版本号拆成可比较的元组：'1.10.2' > '1.9.9'。"""
    parts = re.findall(r"\d+", version.split("-")[0].split("+")[0])
    return tuple(int(p) for p in parts) if parts else (0,)


def is_stable(version: str) -> bool:
    if not version or PRERELEASE.search(version):
        return False
    # junit 4.x 之外的分支（5.x 是另一个 artifact）不需要
    return True


def maven_versions(repo: str, coord: str) -> list[str]:
    group, artifact = coord.split(":")
    path = group.replace(".", "/")
    url = f"{repo}/{path}/{artifact}/maven-metadata.xml"
    root = ET.fromstring(http_text(url))
    return [el.text.strip() for el in root.iter("version") if el.text]


def latest_stable(repo: str, coord: str, current: str) -> str | None:
    versions = [v for v in maven_versions(repo, coord) if is_stable(v)]
    if not versions:
        return None
    newest = max(versions, key=vkey)
    return newest if vkey(newest) > vkey(current) else None


def latest_gradle(current: str) -> str | None:
    data = http_json("https://services.gradle.org/versions/current")
    newest = str(data.get("version", "")).strip()
    if not newest:
        return None
    return newest if vkey(newest) > vkey(current) else None


def latest_action(owner_repo: str) -> str | None:
    headers = {"User-Agent": "deepseek-hot/bump-deps"}
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(
        f"https://api.github.com/repos/{owner_repo}/releases/latest", headers=headers
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode())
    except urllib.error.HTTPError:
        return None
    tag = str(data.get("tag_name", "")).strip()
    if not tag or PRERELEASE.search(tag):
        return None
    return tag


def read_sections(text: str) -> tuple[list[str], int, int]:
    lines = text.splitlines(keepends=True)
    start = end = -1
    for i, line in enumerate(lines):
        if line.strip() == "[versions]":
            start = i
        elif start >= 0 and line.strip().startswith("[") and line.strip() != "[versions]":
            end = i
            break
    if start < 0:
        raise SystemExit("libs.versions.toml 缺少 [versions] 段")
    if end < 0:
        end = len(lines)
    return lines, start, end


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--no-gradle", action="store_true")
    parser.add_argument("--no-actions", action="store_true")
    parser.add_argument("--only", default="", help="逗号分隔的版本键，只升级这些")
    args = parser.parse_args()

    only = {k.strip() for k in args.only.split(",") if k.strip()}
    changes: list[tuple[str, str, str]] = []
    skipped: list[str] = []
    errors: list[str] = []

    text = CATALOG.read_text(encoding="utf-8")
    lines, start, end = read_sections(text)
    pattern = re.compile(r'^(\s*)([A-Za-z0-9_]+)(\s*=\s*)"([^"]+)"')

    for i in range(start + 1, end):
        m = pattern.match(lines[i])
        if not m:
            continue
        key, current = m.group(2), m.group(4)
        if only and key not in only:
            continue
        coord = CATALOG_COORDS.get(key)
        if coord is None:
            skipped.append(f"{key}（未登记坐标）")
            continue
        try:
            newest = latest_stable(coord[0], coord[1], current)
        except Exception as exc:  # 网络抖动不应让整个工作流失败
            errors.append(f"{key}: {exc}")
            continue
        if newest:
            lines[i] = f'{m.group(1)}{key}{m.group(3)}"{newest}"\n'
            changes.append((key, current, newest))

    if changes and not args.dry_run:
        CATALOG.write_text("".join(lines), encoding="utf-8")

    # Gradle wrapper
    if not args.no_gradle and (not only or "gradle" in only):
        wtext = WRAPPER.read_text(encoding="utf-8")
        m = re.search(r"gradle-([\d.]+)-(bin|all)\.zip", wtext)
        if m:
            current = m.group(1)
            try:
                newest = latest_gradle(current)
            except Exception as exc:
                newest = None
                errors.append(f"gradle: {exc}")
            if newest:
                wtext = wtext.replace(f"gradle-{current}-", f"gradle-{newest}-")
                changes.append(("gradle", current, newest))
                if not args.dry_run:
                    WRAPPER.write_text(wtext, encoding="utf-8")

    # GitHub Actions
    if not args.no_actions and not only:
        use_re = re.compile(r"(uses:\s*)([\w.-]+/[\w.-]+)@([\w.\-]+)")
        for wf in sorted(WORKFLOWS.glob("*.y*ml")):
            src = wf.read_text(encoding="utf-8")
            updated = src
            for m in use_re.finditer(src):
                repo, pin = m.group(2), m.group(3)
                if repo.startswith("docker://") or repo.startswith("./"):
                    continue
                try:
                    newest = latest_action(repo)
                except Exception as exc:
                    errors.append(f"{repo}: {exc}")
                    continue
                if newest and vkey(newest) > vkey(pin):
                    updated = updated.replace(f"{repo}@{pin}", f"{repo}@{newest}")
                    changes.append((repo, pin, newest))
            if updated != src and not args.dry_run:
                wf.write_text(updated, encoding="utf-8")

    # 汇总
    print("## 依赖升级结果\n")
    if changes:
        print("| 组件 | 原版本 | 新版本 |")
        print("| --- | --- | --- |")
        for name, old, new in changes:
            print(f"| {name} | {old} | {new} |")
    else:
        print("所有组件都已经是当前最新稳定版。")
    if skipped:
        print("\n未处理：" + "，".join(skipped))
    if errors:
        print("\n警告（已跳过）：" + "；".join(errors))

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as fh:
            fh.write("## 依赖升级结果\n\n")
            if changes:
                fh.write("| 组件 | 原版本 | 新版本 |\n| --- | --- | --- |\n")
                for name, old, new in changes:
                    fh.write(f"| {name} | {old} | {new} |\n")
            else:
                fh.write("所有组件都已经是当前最新稳定版。\n")
            if errors:
                fh.write("\n> 部分组件查询失败，已跳过：\n>\n")
                for e in errors:
                    fh.write(f"> - {e}\n")

    return 0


if __name__ == "__main__":
    sys.exit(main())
