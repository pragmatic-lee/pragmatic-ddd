#!/usr/bin/env bash
# =============================================================================
# derive-kb.sh — KB 派生脚本（原型 / 半自动）
#
# 对 agent-skills/kb/_manifest.json 中登记的每张卡：
#   1. 计算源文档当前 sha256，回填 source_sha；
#   2. （占位）打印「需 AI 语义蒸馏 + 人审」的提示。
# 真正的卡内容蒸馏（剥离叙述、保留命名/位置/骨架/规则/反模式）由 AI + 人审完成，
# 本脚本只负责「源 sha 登记」与「产物 sha 回填」两个机械步骤，避免手动不同步。
#
# 用法：
#   agent-skills/scripts/derive-kb.sh            # 刷新 source_sha
#   agent-skills/scripts/derive-kb.sh --check    # 仅检查，不写文件（等同 verify-kb.sh）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KB_DIR="$SCRIPT_DIR/../kb"
REPO_ROOT="$(cd "$KB_DIR/../.." && pwd)"
MANIFEST="$KB_DIR/_manifest.json"
CHECK_ONLY="${1:-}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "ERROR: 需要 python3" >&2
  exit 2
fi

if [ "$CHECK_ONLY" = "--check" ]; then
  exec "$SCRIPT_DIR/verify-kb.sh"
fi

python3 - "$MANIFEST" "$REPO_ROOT" <<'PY'
import json, hashlib, sys, os

manifest, repo_root = sys.argv[1], sys.argv[2]
data = json.load(open(manifest, encoding="utf-8"))
changed = 0

for group in ("cards", "examples"):
    for entry in data.get(group, []):
        src = entry.get("source")
        if not src or src.startswith("pragmatic-ddd-core"):
            continue
        p = os.path.join(repo_root, src)
        if not os.path.isfile(p):
            continue
        h = hashlib.sha256(open(p, "rb").read()).hexdigest()
        if entry.get("source_sha") != h:
            print(f"刷新 source_sha: {entry.get('id')} -> {h[:12]}...")
            entry["source_sha"] = h
            changed += 1

if changed:
    json.dump(data, open(manifest, "w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print(f"已刷新 {changed} 条 source_sha，请人工重蒸馏对应卡内容后提交。")
else:
    print("无需刷新 source_sha。")

print("提示：卡内容蒸馏需 AI + 人审，本脚本不自动改写 .md 卡正文。")
PY
