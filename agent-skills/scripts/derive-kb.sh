#!/usr/bin/env bash
# =============================================================================
# derive-kb.sh — KB 派生的机械步骤：登记源哈希 + 回填产物哈希
#
# 本脚本只做哈希回填，不改写任何 .md 卡正文；卡内容的语义蒸馏（剥离叙述、保留
# 命名/位置/骨架/关键规则/反模式）由 AI + 人审完成。
#
# 哈希计算与 STALE / BROKEN / UNSEEDED / PRODUCT DRIFT 判定统一由 verify-kb.sh
# 实现，本脚本只做转发，避免两份实现漂移。
#
# 用法：
#   agent-skills/scripts/derive-kb.sh            # 回填 source_sha 与 product_sha
#   agent-skills/scripts/derive-kb.sh --check    # 仅检查，不写文件（等同 verify-kb.sh）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANIFEST="$SCRIPT_DIR/../kb/_manifest.json"

if [ ! -f "$MANIFEST" ]; then
  echo "ERROR: 找不到清单 $MANIFEST" >&2
  exit 2
fi

if [ "${1:-}" = "--check" ]; then
  exec "$SCRIPT_DIR/verify-kb.sh" --check
fi

if [ "$#" -gt 0 ]; then
  echo "ERROR: 未知参数 $1（可用：--check，或不传参执行回填）" >&2
  exit 2
fi

exec "$SCRIPT_DIR/verify-kb.sh" --write
