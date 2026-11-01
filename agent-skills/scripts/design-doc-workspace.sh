#!/usr/bin/env bash
# =============================================================================
# design-doc-workspace.sh — 设计文档工作区操作
#
# 目录约定（见 docs/design/agent-skills/设计文档轮次机制调整方案.md）：
#   docs/design/
#   ├── prerequisites.md                 项目级前置条件清单
#   ├── changes/active/{需求}/{agg}/     活动需求：三件套带轮次后缀
#   │                                      semantics-V01.md / aggregate-design-V01.md / projection-read-V01.md
#   └── changes/archive/{需求}/{agg}/    需求交付后整目录移走
#
# 用法：
#   design-doc-workspace.sh init                        建骨架
#   design-doc-workspace.sh new <需求> <agg> [agg...]   建活动需求目录（提示本轮 V 号）
#   design-doc-workspace.sh next-round <需求> <agg>     算下一个轮次号并列出应建文件
#   design-doc-workspace.sh archive <需求> [--yes]      检查判据后归档（--yes 跳过人工确认）
#   design-doc-workspace.sh list                        列出活动需求、各聚合轮次与状态
#
# 环境变量：
#   DESIGN_ROOT        设计目录根（落点优先级：显式 DESIGN_ROOT → 当前 git 仓库根 → 当前工作目录，
#                      均拼上 /docs/design；不再基于脚本自身位置推算）
#   DESIGN_ASSUME_YES  置 1 时 archive 不再交互确认（等价 --yes，供 AI / CI 无 TTY 场景）
# =============================================================================
set -euo pipefail

# 设计目录落点：跟「当前工程」走，不跟脚本自身位置走
#   1) 显式 DESIGN_ROOT（最高优先级）
#   2) 当前 git 仓库根 + /docs/design（在目标工程或其子目录执行都对）
#   3) 当前工作目录 + /docs/design（非 git 工程的兜底）
resolve_design_root() {
  if [ -n "${DESIGN_ROOT:-}" ]; then
    printf '%s\n' "$DESIGN_ROOT"
    return
  fi
  local git_root=""
  if git_root="$(git rev-parse --show-toplevel 2>/dev/null)" && [ -n "$git_root" ]; then
    printf '%s\n' "$git_root/docs/design"
    return
  fi
  printf '%s\n' "$PWD/docs/design"
}

DESIGN_ROOT="$(resolve_design_root)"

ACTIVE_DIR="$DESIGN_ROOT/changes/active"
ARCHIVE_DIR="$DESIGN_ROOT/changes/archive"
PREREQUISITES="$DESIGN_ROOT/prerequisites.md"

# 三件套的固定文件名前缀（实际文件名 = 前缀 + "-V{NN}.md"）
DOC_KINDS="semantics aggregate-design projection-read"

usage() {
  # 内联用法说明（不从文件头按行号截取，避免注释块变动后失效）
  cat >&2 <<'EOF'
用法：
  design-doc-workspace.sh init                        建目录骨架
  design-doc-workspace.sh new <需求> <agg> [agg...]   建活动需求目录
  design-doc-workspace.sh next-round <需求> <agg>     算下一个轮次号并列出应建文件
  design-doc-workspace.sh archive <需求> [--yes]      检查判据后归档
  design-doc-workspace.sh list                        列出活动需求、各聚合轮次与状态

选项：
  --yes            archive 时跳过人工确认（等价环境变量 DESIGN_ASSUME_YES=1）

环境变量：
  DESIGN_ROOT         设计目录根（默认：当前 git 仓库根 + /docs/design，退到当前目录 + /docs/design）
  DESIGN_ASSUME_YES   置 1 时 archive 不再交互确认

落点规则：在目标工程（或其子目录）执行即可，无需传参；落点不对时用 DESIGN_ROOT 覆盖。
EOF
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
  mkdir -p "$ACTIVE_DIR" "$ARCHIVE_DIR"
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
  echo "  changes/active  changes/archive"
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
    print_round_files "$target/$agg" "$(next_round_no "$target/$agg")"
  done
  echo "需求目录就绪：$target"
}

# 输出目录下最大的轮次号（无文件则回 0）
current_round_no() {
  local dir="$1"
  local max=0
  local f base num
  for f in "$dir"/*-V[0-9][0-9].md; do
    [ -e "$f" ] || continue
    base="$(basename "$f")"
    num="${base##*-V}"
    num="${num%.md}"
    # 去前导零后比较，避免 08 被当作八进制
    num=$((10#${num}))
    [ "$num" -gt "$max" ] && max="$num"
  done
  printf '%d' "$max"
}

next_round_no() {
  printf '%02d' $(( $(current_round_no "$1") + 1 ))
}

print_round_files() {
  local dir="$1"
  local vno="$2"
  echo "    本轮 V${vno} 应建三份（不适用就薄写）："
  local kind
  for kind in $DOC_KINDS; do
    echo "      $dir/$kind-V${vno}.md"
  done
}

cmd_next_round() {
  [ "$#" -eq 2 ] || fail "用法：next-round <需求> <agg>"
  local req="$1"
  local agg="$2"
  require_name "$req"
  require_name "$agg"
  local dir="$ACTIVE_DIR/$req/$agg"
  [ -d "$dir" ] || fail "聚合目录不存在：${dir}（先跑 new 或手工建目录）"
  local cur next
  cur="$(current_round_no "$dir")"
  next="$(next_round_no "$dir")"
  if [ "$cur" = "0" ]; then
    echo "$req/${agg}：尚无轮次，本轮为 V${next}"
  else
    echo "$req/${agg}：当前最大轮次 V$(printf '%02d' "$cur")，下一轮为 V${next}"
  fi
  print_round_files "$dir" "$next"
}

cmd_archive() {
  local assume_yes="${DESIGN_ASSUME_YES:-0}"
  local req=""
  while [ "$#" -ge 1 ]; do
    case "$1" in
      --yes|-y)
        assume_yes=1
        shift
        ;;
      *)
        req="$1"
        shift
        ;;
    esac
  done
  [ -n "$req" ] || fail "用法：archive <需求> [--yes]"
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

  # 判据二：每个聚合目录下，最大 V 的三件套齐全
  local missing=()
  local agg cur kind
  for d in "${agg_dirs[@]}"; do
    agg="$(basename "$d")"
    cur="$(current_round_no "$d")"
    if [ "$cur" = "0" ]; then
      missing+=("${agg}（无 V*.md）")
      continue
    fi
    for kind in $DOC_KINDS; do
      if [ ! -f "$d/$kind-V$(printf '%02d' "$cur").md" ]; then
        missing+=("$agg/$kind-V$(printf '%02d' "$cur").md")
      fi
    done
  done
  if [ "${#missing[@]}" -ge 1 ]; then
    fail "以下文档缺失（三件套不齐，不适用也要薄写）：${missing[*]}"
  fi

  # 判据三（落地核对与冻结）无法自动判定，交由人工确认
  echo "自动判据通过：聚合目录齐全、各聚合最大 V 的三件套齐全。"
  if [ "$assume_yes" = "1" ]; then
    echo "跳过人工确认（--yes / DESIGN_ASSUME_YES=1）：请自行确保三条落地核对已过、三份已置「已落地」。"
  else
    echo "人工确认项：三条落地核对已过？三份已置「已落地」？"
    local answer=""
    read -r -p "确认后归档 [y/N] " answer || answer=""
    case "$answer" in
      y|Y) ;;
      *)
        echo "已取消归档"
        exit 0
        ;;
    esac
  fi

  mkdir -p "$ARCHIVE_DIR"
  if [ -d "$ARCHIVE_DIR/$req" ]; then
    fail "归档目标已存在：$ARCHIVE_DIR/$req"
  fi
  mv "$src" "$ARCHIVE_DIR/$req"
  echo "已归档：$ARCHIVE_DIR/$req"
}

cmd_list() {
  echo "活动需求（${ACTIVE_DIR}）："
  [ -d "$ACTIVE_DIR" ] || return 0
  local req agg dir cur f base state
  for req in "$ACTIVE_DIR"/*/; do
    [ -d "$req" ] || continue
    echo "  $(basename "$req")"
    for agg in "$req"*/; do
      [ -d "$agg" ] || continue
      dir="${agg%/}"
      cur="$(current_round_no "$dir")"
      if [ "$cur" = "0" ]; then
        echo "    $(basename "$dir")  （尚无轮次）"
        continue
      fi
      # 从语义文档头部读状态（取不到就标未知）
      state="未知"
      f="$dir/semantics-V$(printf '%02d' "$cur").md"
      if [ -f "$f" ]; then
        state="$(grep -m1 -o '状态：[^｜|]*' "$f" 2>/dev/null | sed 's/状态：//' | tr -d ' ' || true)"
        [ -n "$state" ] || state="未知"
      fi
      echo "    $(basename "$dir")  V$(printf '%02d' "$cur")  ${state}"
    done
  done
}

main() {
  [ "$#" -ge 1 ] || usage
  local cmd="$1"
  shift
  echo "设计目录：$DESIGN_ROOT"
  case "$cmd" in
    init) cmd_init ;;
    new) cmd_new "$@" ;;
    next-round) cmd_next_round "$@" ;;
    archive) cmd_archive "$@" ;;
    list) cmd_list ;;
    *) usage ;;
  esac
}

main "$@"
