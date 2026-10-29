#!/usr/bin/env bash
# =============================================================================
# design-doc-workspace.sh — 设计文档工作区操作
#
# 目录约定（见 docs/design/agent-skills/文档产物目录规划-v2.md）：
#   docs/design/
#   ├── prerequisites.md                 项目级前置条件清单
#   ├── changes/active/{需求}/{agg}/     活动需求的契约
#   ├── changes/archive/{需求}/{agg}/    已归档需求的契约
#   └── aggregates/{agg}/facts.md        聚合事实（当前态）
#
# 用法：
#   design-doc-workspace.sh init                        建骨架
#   design-doc-workspace.sh new <需求> <agg> [agg...]   建活动需求目录
#   design-doc-workspace.sh archive <需求>              检查判据后归档
#   design-doc-workspace.sh list                        列出活动需求与聚合
#
# 环境变量：DESIGN_ROOT（默认 {仓库根}/docs/design）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DESIGN_ROOT="${DESIGN_ROOT:-$REPO_ROOT/docs/design}"

ACTIVE_DIR="$DESIGN_ROOT/changes/active"
ARCHIVE_DIR="$DESIGN_ROOT/changes/archive"
AGGREGATES_DIR="$DESIGN_ROOT/aggregates"
PREREQUISITES="$DESIGN_ROOT/prerequisites.md"

usage() {
  sed -n '11,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit 1
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_name() {
  local name="$1"
  case "$name" in
    ""|*/*|*\\*|*" "*)
      fail "名称非法：'$name'（不能为空、不能含空格与斜杠）"
      ;;
  esac
}

cmd_init() {
  mkdir -p "$ACTIVE_DIR" "$ARCHIVE_DIR" "$AGGREGATES_DIR"
  if [ ! -f "$PREREQUISITES" ]; then
    cat > "$PREREQUISITES" <<'EOF'
# 实施前置条件清单

> 类型：项目级，跨需求复用。填写规则见 `documentation/best-practices/design-doc-prerequisites.md`。

| # | 前置条件 | 结论 |
| --- | --- | --- |
| 1 | 数据库类型与版本 | 待确认 |
| 2 | 事件总线（可多选）：RocketMQ / Kafka / ThreadPool（线程池） | 待确认 |
| 3 | 是否启用 Outbox | 待确认 |
| 4 | 补偿模式 | 待确认 |
| 5 | Redis 版本与客户端 | 待确认 |
| 6 | ES 版本与安全开关 | 待确认 |
| 7 | ID 生成方式 | 待确认 |
| 8 | 部署实例数 | 待确认 |
| 9 | 时区 | 待确认 |
| 10 | 多租户 / 数据隔离 | 待确认 |
EOF
    echo "已建前置条件清单：$PREREQUISITES"
  else
    echo "前置条件清单已存在，跳过：$PREREQUISITES"
  fi
  echo "骨架就绪：$DESIGN_ROOT"
  echo "  changes/active  changes/archive  aggregates"
}

cmd_new() {
  [ "$#" -ge 2 ] || fail "用法：new <需求> <agg> [agg...]"
  local req="$1"
  shift
  require_name "$req"

  local target="$ACTIVE_DIR/$req"
  if [ -d "$target" ]; then
    echo "需求目录已存在，续做：$target"
  fi
  mkdir -p "$target"

  local agg
  for agg in "$@"; do
    require_name "$agg"
    mkdir -p "$target/$agg"
    echo "  + $req/$agg/"
    if [ ! -f "$AGGREGATES_DIR/$agg/facts.md" ]; then
      echo "    提示：聚合 '$agg' 还没有事实文档（$AGGREGATES_DIR/$agg/facts.md），迭代前建议先补"
    fi
  done
  echo "需求目录就绪：$target"
}

cmd_archive() {
  [ "$#" -eq 1 ] || fail "用法：archive <需求>"
  local req="$1"
  require_name "$req"

  local src="$ACTIVE_DIR/$req"
  [ -d "$src" ] || fail "活动需求不存在：$src"

  # 判据一：至少一个聚合子目录
  local agg_dirs=()
  local d
  for d in "$src"/*/; do
    [ -d "$d" ] && agg_dirs+=("$d")
  done
  [ "${#agg_dirs[@]}" -ge 1 ] || fail "需求目录下没有聚合子目录：$src"

  # 判据二：涉及的每个聚合都有事实文档
  local missing=()
  local agg
  for d in "${agg_dirs[@]}"; do
    agg="$(basename "$d")"
    if [ ! -f "$AGGREGATES_DIR/$agg/facts.md" ]; then
      missing+=("$agg")
    fi
  done
  if [ "${#missing[@]}" -ge 1 ]; then
    fail "以下聚合缺少事实文档，先更新再归档：${missing[*]}"
  fi

  # 判据三（§10.6 回填）无法自动判定，交由人工确认
  echo "自动判据通过：聚合目录齐全、事实文档齐全。"
  echo "人工确认项：② 的 §10.6 实施回填是否已完成？"
  read -r -p "确认后归档 [y/N] " answer
  case "$answer" in
    y|Y) ;;
    *) echo "已取消归档"; exit 0 ;;
  esac

  mkdir -p "$ARCHIVE_DIR"
  if [ -d "$ARCHIVE_DIR/$req" ]; then
    fail "归档目标已存在：$ARCHIVE_DIR/$req"
  fi
  mv "$src" "$ARCHIVE_DIR/$req"
  echo "已归档：$ARCHIVE_DIR/$req"
}

cmd_list() {
  echo "活动需求（${ACTIVE_DIR}）："
  if [ -d "$ACTIVE_DIR" ]; then
    find "$ACTIVE_DIR" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort | sed 's/^/  /'
  fi
  echo "聚合事实（${AGGREGATES_DIR}）："
  if [ -d "$AGGREGATES_DIR" ]; then
    find "$AGGREGATES_DIR" -mindepth 1 -maxdepth 1 -type d -exec basename {} \; | sort | sed 's/^/  /'
  fi
}

main() {
  [ "$#" -ge 1 ] || usage
  local cmd="$1"
  shift
  case "$cmd" in
    init) cmd_init ;;
    new) cmd_new "$@" ;;
    archive) cmd_archive "$@" ;;
    list) cmd_list ;;
    *) usage ;;
  esac
}

main "$@"
