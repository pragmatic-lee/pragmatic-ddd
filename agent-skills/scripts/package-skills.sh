#!/usr/bin/env bash
# =============================================================================
# package-skills.sh — 打包可安装给其他智能体的 agent-skills zip
#
# 产物结构（zip 根）：
#   agent-skills/
#   ├── INSTALL.md / USAGE.md / README.md
#   ├── ddd-*/SKILL.md            10 个 Skill
#   ├── agents/ddd-reviewer.md    隔离评审角色
#   ├── kb/                       34 张 bp 卡 + 硬约束 + API 契约
#   ├── scripts/                  派生 / 校验 / 工作区脚本
#   └── reference/best-practices/ 设计文档模板副本（供独立安装时落到目标工程）
#
# 用法：
#   package-skills.sh [版本号]        版本号默认取 kb/_manifest.json 的 kb_version
#   OUT=/path/to/out.zip package-skills.sh 2.3
#
# 说明：内部规划稿（A4-A5-walkthrough.md / KB-REBUILD-PLAN.md）不入包。
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILLS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$SKILLS_DIR/.." && pwd)"

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  VERSION="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1],encoding="utf-8"))["kb_version"].split("-")[0])' \
    "$SKILLS_DIR/kb/_manifest.json")"
fi

OUT="${OUT:-$REPO_ROOT/agent-skills-kb$VERSION.zip}"
STAGE="$(mktemp -d "${TMPDIR:-/tmp}/agent-skills-pkg.XXXXXX")"

cleanup() {
  case "$STAGE" in
    */agent-skills-pkg.*) rm -rf -- "$STAGE" ;;
    *) echo "跳过清理：$STAGE 不在预期临时目录下" >&2 ;;
  esac
}
trap cleanup EXIT

mkdir -p "$STAGE/agent-skills"
rsync -a --exclude '.DS_Store' \
  --exclude 'A4-A5-walkthrough.md' \
  --exclude 'KB-REBUILD-PLAN.md' \
  "$SKILLS_DIR/" "$STAGE/agent-skills/"

mkdir -p "$STAGE/agent-skills/reference/best-practices"
cp "$REPO_ROOT"/documentation/best-practices/design-doc-*.md \
   "$STAGE/agent-skills/reference/best-practices/"

cat > "$STAGE/agent-skills/reference/README.md" <<'EOF'
# reference（安装用附件，不是知识源）

`best-practices/` 下是从 `documentation/best-practices/` 复制的设计文档模板原文，
供**独立安装**（目标工程没有本仓库文档站）时落到目标工程同名路径：

```bash
mkdir -p <目标工程>/documentation/best-practices
cp agent-skills/reference/best-practices/*.md <目标工程>/documentation/best-practices/
```

原因：`ddd-design-doc` 与 `ddd-kb-consult` 对「设计文档骨架」以文档站原文为结构权威，
不走蒸馏卡。缺这批文件时，设计文档类 Skill 只能按 KB 卡骨架降级工作。

注意：这里是**副本**，权威版本永远在仓库 `documentation/best-practices/`；
改了源文档请重新打包，不要直接改这里。
EOF

( cd "$STAGE" && zip -r -q -X "$OUT" agent-skills )

echo "已生成：$OUT"
echo "  版本：$VERSION"
echo "  文件数：$(unzip -l "$OUT" | tail -1 | awk '{print $2}')"
echo "  大小：$(du -h "$OUT" | awk '{print $1}')"
