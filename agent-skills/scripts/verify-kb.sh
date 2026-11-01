#!/usr/bin/env bash
# =============================================================================
# verify-kb.sh — KB 漂移校验卡口（CI 用）／同时是 --write 模式的哈希引擎
#
# 校验 agent-skills/kb/_manifest.json 每条登记的三类状态，任一命中即退出码 1：
#   STALE          源已变更，卡需重蒸馏（derive-kb.sh 刷 sha 后由人改卡）
#   BROKEN         登记的源找不到，或卡文件本身缺失
#   UNSEEDED       source_sha / product_sha 仍为 TODO（从未回填，等于没纳入卡口）
#   PRODUCT DRIFT  卡文件与登记的 product_sha 不符（产物被手改，违反「不反向改卡」）
#
# 源有两种形态（由条目字段 source_kind 区分，缺省 file）：
#   file       单文件源；source 允许带 `#章节` 锚点，取锚点前的路径
#   code-tree  源是代码与 pom（api-contract / projection-api），按 source_globs
#              展开 → 逐文件 sha256 → 对「相对路径: 哈希」有序串接再取一次 sha256
#              → 框架源码或 pom（含 groupId / 版本）一变即标 STALE
#
# 用法：
#   agent-skills/scripts/verify-kb.sh            # 只检查（CI 卡口）
#   agent-skills/scripts/verify-kb.sh --write    # 回填 source_sha 与 product_sha（= derive-kb.sh）
# 退出码：0 = 全部一致；1 = 有问题（STALE / BROKEN / UNSEEDED / DRIFT）；2 = 环境缺 python3
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KB_DIR="$SCRIPT_DIR/../kb"
REPO_ROOT="$(cd "$KB_DIR/../.." && pwd)"
MANIFEST="$KB_DIR/_manifest.json"
MODE="check"
case "${1:-}" in
  --write) MODE="write" ;;
  "" | --check) MODE="check" ;;
  *) echo "ERROR: 未知参数 $1（可用：--write / --check）" >&2; exit 2 ;;
esac

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: 需要 python3 计算 sha256" >&2
  exit 2
fi

python3 - "$MANIFEST" "$REPO_ROOT" "$KB_DIR" "$MODE" <<'PY'
import glob
import hashlib
import json
import os
import sys

manifest_path, repo_root, kb_dir, mode = sys.argv[1:5]
data = json.load(open(manifest_path, encoding="utf-8"))


def sha256_file(path):
    with open(path, "rb") as fh:
        return hashlib.sha256(fh.read()).hexdigest()


def rel(path):
    return os.path.relpath(path, repo_root).replace(os.sep, "/")


def hash_file_source(source):
    """单文件源：剥离 #章节 锚点后取哈希；找不到返回 None。"""
    path = os.path.join(repo_root, source.split("#", 1)[0])
    if not os.path.isfile(path):
        return None
    return sha256_file(path)


def hash_code_tree(globs):
    """代码树源：按 globs 收集 → 逐文件哈希 → 汇总；无匹配返回 None。"""
    files = set()
    for pattern in globs:
        for hit in glob.glob(os.path.join(repo_root, pattern), recursive=True):
            if os.path.isfile(hit):
                files.add(rel(hit))
    if not files:
        return None
    joined = "".join(f"{name}:{sha256_file(os.path.join(repo_root, name))}\n"
                     for name in sorted(files))
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()


def current_source_sha(entry):
    kind = entry.get("source_kind", "file")
    if kind == "code-tree":
        return hash_code_tree(entry.get("source_globs") or [])
    source = entry.get("source")
    if not source:
        return None
    return hash_file_source(source)


stale, broken, unseeded, drift = [], [], [], []
written = 0

for entry in data.get("cards", []):
    cid = entry.get("id", "<unknown>")
    card_path = os.path.join(kb_dir, entry.get("path", ""))

    if not os.path.isfile(card_path):
        broken.append((cid, "卡文件缺失", entry.get("path")))
        continue

    source_sha = current_source_sha(entry)
    if source_sha is None:
        broken.append((cid, "源找不到或 glob 无匹配",
                       entry.get("source") or ",".join(entry.get("source_globs") or [])))
        continue

    stored = entry.get("source_sha")
    if stored in (None, "TODO"):
        unseeded.append((cid, "source_sha"))
    elif stored != source_sha:
        stale.append((cid, "源", stored[:8], source_sha[:8]))

    product_sha = sha256_file(card_path)
    stored_product = entry.get("product_sha")
    if stored_product in (None, "TODO"):
        unseeded.append((cid, "product_sha"))
    elif stored_product != product_sha:
        drift.append((cid, stored_product[:8], product_sha[:8]))

    if mode == "write":
        if stored != source_sha or stored_product != product_sha:
            entry["source_sha"] = source_sha
            entry["product_sha"] = product_sha
            written += 1

if mode == "write":
    if written:
        json.dump(data, open(manifest_path, "w", encoding="utf-8"),
                  ensure_ascii=False, indent=2)
    print(f"已回填 {written} 条 source_sha / product_sha（共 {len(data.get('cards', []))} 张卡）。")
    print("提示：STALE 的卡需 AI + 人审重蒸馏正文，本脚本不改写 .md 卡内容。")
    sys.exit(0)

if broken or stale or unseeded or drift:
    if broken:
        print("KB BROKEN — 源或卡找不到（校验未覆盖，必须先修登记）：")
        for cid, why, src in broken:
            print(f"  - {cid}: {why} — {src}")
    if unseeded:
        print("KB UNSEEDED — 哈希仍为 TODO，等于未纳入卡口，请跑 derive-kb.sh 回填：")
        for cid, field in unseeded:
            print(f"  - {cid}: {field}")
    if stale:
        print("KB STALE — 以下源已变更，请重蒸馏对应卡：")
        for cid, kind, old, new in stale:
            print(f"  - {cid}: {kind} sha 记录 {old}… != 当前 {new}…")
    if drift:
        print("KB PRODUCT DRIFT — 以下卡被手改（违反「不反向改卡」，请回源文档改后重派生）：")
        for cid, old, new in drift:
            print(f"  - {cid}: 产物 sha 记录 {old}… != 当前 {new}…")
    sys.exit(1)

print(f"KB OK — {len(data.get('cards', []))} 张卡的源与产物 sha 全部一致。")
PY
