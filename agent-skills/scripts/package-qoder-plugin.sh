#!/usr/bin/env bash
# =============================================================================
# package-qoder-plugin.sh — 把 agent-skills 打成 Qoder「Plugin（专家套件）」目录
#
# 产物结构（Qoder 插件规范）：
#   pragmatic-ddd-skills/
#   ├── .qoder-plugin/plugin.json   清单（name 必填，其余元信息）
#   ├── skills/<skill>/SKILL.md     10 个技能（含各自资源目录）
#   ├── agents/ddd-reviewer.md      评审角色
#   ├── kb/                         35 张卡 + 硬约束 + API 契约（插件根共享）
#   ├── scripts/                    KB 派生 / 校验 / 工作区脚本
#   ├── USAGE.md
#   └── README.md                   插件说明
#
# 关键差异（相对 package-skills.sh 的 bundle）：技能下沉到 skills/<name>/，
# 因此技能内 `../kb/` 改写为 `../../kb/`，`../USAGE.md` 改写为 `../../USAGE.md`。
#
# 用法：
#   package-qoder-plugin.sh [版本号]       默认取 kb/_manifest.json 的 kb_version
#   OUT=/path/to/out.zip package-qoder-plugin.sh 2.4.0
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILLS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$SKILLS_DIR/.." && pwd)"

PLUGIN_NAME="pragmatic-ddd-skills"
DISPLAY_NAME="领域建模套件"

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  BASE="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1],encoding="utf-8"))["kb_version"].split("-")[0])' \
    "$SKILLS_DIR/kb/_manifest.json")"
  VERSION="${BASE}.0"
fi

DIST_DIR="${DIST:-$REPO_ROOT/dist}"
STAGE="$DIST_DIR/$PLUGIN_NAME"
OUT="${OUT:-$DIST_DIR/$PLUGIN_NAME-$VERSION.zip}"

rm -rf -- "$STAGE"
mkdir -p "$STAGE/.qoder-plugin" "$STAGE/skills" "$STAGE/agents"

# ---------- 技能：下沉到 skills/<name>/ ----------
for dir in "$SKILLS_DIR"/ddd-*/; do
  name="$(basename "$dir")"
  cp -R "$dir" "$STAGE/skills/$name"
  # 技能层级下移一层：跨目录引用补一级
  if [ -f "$STAGE/skills/$name/SKILL.md" ]; then
    perl -pi -e 's#\.\./kb/#../../kb/#g; s#\.\./USAGE\.md#../../USAGE.md#g' \
      "$STAGE/skills/$name/SKILL.md"
  fi
  find "$STAGE/skills/$name" -name '.DS_Store' -delete
done

# ---------- 角色 / 共享资源 / 说明 ----------
cp "$SKILLS_DIR/agents/ddd-reviewer.md" "$STAGE/agents/"
cp -R "$SKILLS_DIR/kb" "$STAGE/kb"
cp -R "$SKILLS_DIR/scripts" "$STAGE/scripts"
cp "$SKILLS_DIR/USAGE.md" "$STAGE/USAGE.md"
find "$STAGE/kb" "$STAGE/scripts" -name '.DS_Store' -delete

# ---------- plugin.json（name 必填，其余元信息可选） ----------
cat > "$STAGE/.qoder-plugin/plugin.json" <<EOF
{
  "name": "$PLUGIN_NAME",
  "displayName": "$DISPLAY_NAME",
  "version": "$VERSION",
  "description": "Pragmatic DDD 领域建模与设计文档套件：10 个技能覆盖聚合识别、聚合生成、聚合扩展、设计文档产出、投影读模型、基础设施装配、代码评审与知识咨询，另含隔离评审角色。",
  "author": {
    "name": "wizard-lee"
  },
  "homepage": "https://github.com/pragmatic-lee/pragmatic-ddd",
  "repository": "https://github.com/pragmatic-lee/pragmatic-ddd",
  "license": "Apache-2.0",
  "keywords": [
    "ddd",
    "domain-driven-design",
    "aggregate",
    "projection",
    "design-doc",
    "java",
    "spring-boot"
  ],
  "skills": "./skills/",
  "agents": "./agents/"
}
EOF

# ---------- README（插件说明，市场展示用） ----------
cat > "$STAGE/README.md" <<'EOF'
# 领域建模套件（pragmatic-ddd-skills）

围绕 **Pragmatic DDD** 方法论的岗位套件：从需求识别聚合，到产出三契约设计文档（语义 / 写设计 / 投影读设计），再到生成与评审代码。

## 技能清单

| 技能 | 用途 |
| --- | --- |
| `ddd-project-init` | 初始化采用 Pragmatic DDD 的 Maven 工程 |
| `ddd-modeling-guide` | 聚合边界与聚合度的建模引导 |
| `ddd-aggregate-scaffold` | 生成聚合目录骨架 |
| `ddd-aggregate-create` | 从零生成完整聚合（充血方法、注册表、事件、应用层） |
| `ddd-aggregate-extend` | 给既有聚合加操作 / 校验 / 订阅 / 子实体 |
| `ddd-design-doc` | 产出三契约 + 一快照设计文档（闸门制交付） |
| `ddd-projection-create` | 生成查询侧投影读模型（ES / Redis / 同源直读） |
| `ddd-infra-config` | 装配 MySQL / PostgreSQL / MyBatis / MQ / Outbox |
| `ddd-code-review` | 框架约定合规评审（只查硬约束，不判业务） |
| `ddd-kb-consult` | 规范与最佳实践答疑（只读知识包） |

角色：`agents/ddd-reviewer.md`（只读隔离评审角色）。

## 依赖

- 知识包在插件根 `kb/`（35 张卡 + `hard-constraints.md` + `api-contract.md`），技能通过 `../../kb/` 引用。
- `scripts/` 提供 KB 派生 / 校验 / 设计文档工作区脚本。

## 适用场景

- 用 DDD 落地 Java 服务，需要统一的聚合设计与代码规范；
- 需要可评审、可归档的设计文档体系，而不是一次性聊天产出；
- 需要在团队内统一「聚合 / 规则 / 事件 / 投影」的写法。
EOF

# ---------- 校验 ----------
python3 - "$STAGE" "$PLUGIN_NAME" <<'PY'
import json, os, re, sys

stage, plugin_name = sys.argv[1], sys.argv[2]
errors = []

manifest_path = os.path.join(stage, ".qoder-plugin", "plugin.json")
with open(manifest_path, encoding="utf-8") as f:
    manifest = json.load(f)
if manifest.get("name") != plugin_name:
    errors.append(f"plugin.json name 与目录不一致：{manifest.get('name')} != {plugin_name}")

skills_dir = os.path.join(stage, "skills")
skills = sorted(d for d in os.listdir(skills_dir) if os.path.isdir(os.path.join(skills_dir, d)))
if len(skills) != 10:
    errors.append(f"技能数量异常：{len(skills)}")

for skill in skills:
    skill_md = os.path.join(skills_dir, skill, "SKILL.md")
    if not os.path.isfile(skill_md):
        errors.append(f"{skill}: 缺少 SKILL.md")
        continue
    text = open(skill_md, encoding="utf-8").read()
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not m:
        errors.append(f"{skill}: 缺少 frontmatter")
        continue
    fm = m.group(1)
    name = re.search(r"^name:\s*(.+)$", fm, re.M)
    desc = re.search(r"^description:\s*(.+)$", fm, re.M)
    if not name or name.group(1).strip() != skill:
        errors.append(f"{skill}: name 与目录不一致")
    if not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", skill) or len(skill) > 64:
        errors.append(f"{skill}: 名称不符合 kebab-case / 长度限制")
    if not desc:
        errors.append(f"{skill}: 缺少 description")
    elif len(desc.group(1)) > 1024:
        errors.append(f"{skill}: description 超过 1024 字符")
    if re.search(r"(?<!\.\./)\.\./kb/", text):
        errors.append(f"{skill}: 仍存在未改写的 ../kb/ 引用")
    # 校验所有 ../../kb/ 目标存在
    for ref in set(re.findall(r"\.\./\.\./(kb/[^\s)`\]\"']+)", text)):
        if not os.path.exists(os.path.join(stage, ref)):
            errors.append(f"{skill}: 引用不存在 {ref}")

if not os.path.isdir(os.path.join(stage, "agents")):
    errors.append("缺少 agents/ 目录")
if not os.path.isfile(os.path.join(stage, "agents", "ddd-reviewer.md")):
    errors.append("缺少 agents/ddd-reviewer.md")

if errors:
    print("校验失败：")
    for e in errors:
        print("  -", e)
    sys.exit(1)

print(f"校验通过：{len(skills)} 个技能 + agents/ + kb/，plugin.json name={manifest['name']} version={manifest.get('version')}")
PY

# ---------- 打包 ----------
( cd "$DIST_DIR" && rm -f "$OUT" && zip -r -q -X "$OUT" "$PLUGIN_NAME" )

echo "已生成目录：$STAGE"
echo "已生成压缩包：$OUT"
echo "  版本：$VERSION"
echo "  文件数：$(unzip -l "$OUT" | tail -1 | awk '{print $2}')"
echo "  大小：$(du -h "$OUT" | awk '{print $1}')"
