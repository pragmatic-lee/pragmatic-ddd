#!/usr/bin/env bash
# =============================================================================
# verify-kb.sh — KB 漂移校验卡口（CI 用）
#
# 校验 agent-skills/kb/_manifest.json 中登记的 source_sha 与「当前源文档」的
# sha256 是否一致。不一致 => 对应卡已过期，需重派生（derive-kb.sh）。
#
# 用法：
#   agent-skills/scripts/verify-kb.sh
# 退出码：0 = 全部一致；1 = 存在过期卡（CI 应失败并阻断合并）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KB_DIR="$SCRIPT_DIR/../kb"
REPO_ROOT="$(cd "$KB_DIR/../.." && pwd)"
MANIFEST="$KB_DIR/_manifest.json"

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: 需要 python3 计算 sha256" >&2
  exit 2
fi

python3 - "$MANIFEST" "$REPO_ROOT" <<'PY'
import json, hashlib, sys, os

manifest, repo_root = sys.argv[1], sys.argv[2]
data = json.load(open(manifest, encoding="utf-8"))

stale = []
for entry in data.get("cards", []) + data.get("examples", []):
    cid = entry.get("id", "<unknown>")
    src = entry.get("source")
    stored = entry.get("source_sha")
    if not src or stored in (None, "TODO"):
        continue
    # 框架类占位（如 pragmatic-ddd-core ...）目前无单文件源，跳过
    if src.startswith("pragmatic-ddd-core"):
        continue
    # 示例工程（非单文件源）自 KB v1.3 起不在发布范围，跳过
    p = os.path.join(repo_root, src)
    if not os.path.isfile(p):
        continue
    h = hashlib.sha256(open(p, "rb").read()).hexdigest()
    if h != stored:
        stale.append((cid, src, stored[:8], h[:8]))

if stale:
    print("KB STALE — 以下源文档已变更，请重派生对应卡：")
    for cid, src, old, new in stale:
        print(f"  - {cid}: {src}  (记录 {old}... != 当前 {new}...)")
    sys.exit(1)

print("KB OK — 所有已登记 source_sha 与源文档一致。")
PY
