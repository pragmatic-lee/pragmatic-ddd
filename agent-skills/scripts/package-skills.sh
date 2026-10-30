#!/usr/bin/env bash
# =============================================================================
# package-skills.sh — 打包可安装给其他智能体的 agent-skills zip
#
# 产物结构（zip 根）：
#   agent-skills/
#   ├── INSTALL.md / USAGE.md / README.md
#   ├── ddd-*/SKILL.md            10 个 Skill
#   ├── agents/ddd-reviewer.md    隔离评审角色
#   ├── kb/                       35 张 bp 卡 + 硬约束 + API 契约
#   ├── scripts/                  派生 / 校验 / 工作区 / 打包脚本
#   └── reference/best-practices/ 设计文档模板副本（随包只读引用，不落入目标工程）
#
# 用法：
#   package-skills.sh [版本号]        版本号默认取 kb/_manifest.json 的 kb_version
#   OUT=/path/to/out.zip package-skills.sh 2.4
#
# 说明：
#   1) 内部规划稿（A4-A5-walkthrough.md / KB-REBUILD-PLAN.md）不入包。
#   2) 本包为平台中立包：Qoder / CodeBuddy / WorkBuddy 等特定平台的打包脚本、
#      专家清单（workbuddy-expert-meta.json）与平台专属 Agent（pragmatic-ddd-architect.md）
#      一律不入包，安装文档中的平台专属章节在产物副本里被剔除；
#      打包结束前会做一次「平台词零残留」校验。
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
  --exclude 'package-skills.sh' \
  --exclude 'package-qoder-plugin.sh' \
  --exclude 'package-workbuddy-expert.sh' \
  --exclude 'workbuddy-expert-meta.json' \
  --exclude 'pragmatic-ddd-architect.md' \
  "$SKILLS_DIR/" "$STAGE/agent-skills/"

mkdir -p "$STAGE/agent-skills/reference/best-practices"
cp "$REPO_ROOT"/documentation/best-practices/design-doc-*.md \
   "$STAGE/agent-skills/reference/best-practices/"
# 写设计 §7.1 落列形态决策直接引用本文的 §3.4 判据，故一并入包
cp "$REPO_ROOT/documentation/best-practices/aggregate-database-design.md" \
   "$STAGE/agent-skills/reference/best-practices/"

cat > "$STAGE/agent-skills/reference/README.md" <<'EOF'
# reference（随包只读附件，不是知识源）

`best-practices/` 下是从仓库 `documentation/best-practices/` 复制的设计文档模板原文，
由 Skill 通过 `../reference/best-practices/…` 直接只读引用（`ddd-design-doc` 的结构与流程
以 `design-doc-template.md` 为准）。

**不需要、也不应该复制到目标工程的 `documentation/best-practices/`。**
模板属「能力包」的一部分，目标工程只产出自己的设计文档：

```text
<目标工程>/docs/design/…   ① semantics.md ② aggregate-design.md ③ projection-read.md ④ facts.md
```

目标工程不因使用本能力多出任何模板文件；模板升级由重新安装/重新打包完成。

注意：这里是**副本**，权威版本永远在仓库 `documentation/best-practices/`；
改了源文档请重新打包，不要直接改这里。
EOF

# ---------- 模板路径重定向：Skill 改读包内 reference/，不再要求写入目标工程 ----------
python3 - "$STAGE/agent-skills" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
SRC = "documentation/best-practices/"
DST = "../reference/best-practices/"

for md in sorted(root.glob("ddd-*/SKILL.md")):
    text = md.read_text(encoding="utf-8")
    if SRC not in text:
        continue
    md.write_text(text.replace(SRC, DST), encoding="utf-8")

# 校验：Skill 内不得再指向仓库文档路径，且每个具体引用都必须真实存在
for md in sorted(root.glob("ddd-*/SKILL.md")):
    text = md.read_text(encoding="utf-8")
    if SRC in text:
        raise SystemExit(f"[reference] {md.name} 仍引用仓库文档路径：{SRC}")
    for ref in set(re.findall(r"\.\./reference/best-practices/([A-Za-z0-9_.*-]+\.md)", text)):
        if "*" in ref:
            if not list((root / "reference/best-practices").glob(ref)):
                raise SystemExit(f"[reference] {md.parent.name} 通配无匹配：{ref}")
            continue
        if not (root / "reference/best-practices" / ref).is_file():
            raise SystemExit(f"[reference] {md.parent.name} 引用不存在：{ref}")
PY

# ---------- README：产物用使用者版（维护者版只留在仓库，不进包） ----------
python3 - "$STAGE/agent-skills" <<'PY'
import pathlib
import sys

root = pathlib.Path(sys.argv[1])
kb_cards = len(list((root / "kb" / "bp").glob("*.md")))
readme = f"""# Pragmatic DDD · AI 能力包（Skills + 知识包 KB）

> 使用者入口：[`USAGE.md`](./USAGE.md)（我现在该用哪个、说句话会触发什么）。
> 安装与移植：[`INSTALL.md`](./INSTALL.md)（目录依赖、三种安装方式、版本自检）。
> 本文件只做能力总览；内部维护信息（路线图、验收走查、建设阶段）仅在框架仓库内保留。

## 1. 包里有什么

10 个 Skill + 1 个隔离评审角色 + 共享知识包（KB）+ 设计文档模板 + 3 个运行期脚本。

```text
agent-skills/
├── INSTALL.md / USAGE.md / README.md
├── ddd-*/SKILL.md                  10 个 Skill
├── agents/ddd-reviewer.md           隔离评审角色（只读、零写入）
├── kb/                              知识包：{kb_cards} 张卡 + 硬约束 + API 契约 + 索引清单
├── reference/best-practices/        设计文档模板（只读引用，不写入目标工程）
└── scripts/                         derive-kb.sh / verify-kb.sh / design-doc-workspace.sh
```

## 2. 能力总览

| 入口 | 类型 | 触发意图 | 读哪些 KB |
| --- | --- | --- | --- |
| `ddd-project-init` | 初始化 | 新建 DDD 工程 | aggregate-structure, hard-constraints |
| `ddd-aggregate-scaffold` | 骨架 | 建聚合目录骨架 | aggregate-design, application-collaboration, hard-constraints |
| `ddd-infra-config` | 基础设施 | 配 MySQL / PostgreSQL / MQ / Outbox / ES / Redis / 建表 | repository-design, aggregate-database-design, projection-design, event-subscriber-pattern, 各 `*-config` 卡 |
| `ddd-aggregate-create` | 创建（核心） | 生成完整聚合 | 全部 bp + hard-constraints + api-contract |
| `ddd-aggregate-extend` | 扩展 | 给聚合加操作 / 校验 / 订阅 / 子实体 / 投影源 | 对应 bp + hard-constraints |
| `ddd-projection-create` | 投影 | 生成查询侧读模型 | projection-design, event-subscriber-pattern, enum-value, hard-constraints |
| `ddd-code-review` | 评审 | 评审代码合规 | hard-constraints + api-contract |
| `ddd-modeling-guide` | 建模引导 | 聚合边界 / 聚合度怎么划（只引导） | aggregate-design, aggregate-database-design, entity-design |
| `ddd-design-doc` | 设计文档产出 | 三契约 + 一快照（语义多轮迭代 → 定稿 → 写 / 读按点单） | design-doc 系列卡 + 模板原文 |
| `ddd-kb-consult` | 咨询答疑 | 怎么用 / 为什么这么设计（不产代码） | `USAGE.md` 或路由到对应 bp 卡 |

**上游文档契约**（部署类 Skill 读哪份文档）

| Skill | 读哪份文档 | 无文档时 |
| --- | --- | --- |
| `ddd-project-init` | — | 产出前置条件清单空模板 |
| `ddd-aggregate-scaffold` | 写设计 §2.1 包布局 | 只建目录，不猜结构 |
| `ddd-infra-config` | 写设计 §7 + 前置条件清单 | 未确认项不装配 |
| `ddd-aggregate-create` | 写设计 §2–§9 | **不自行推导语义**，先跑 `ddd-design-doc` |
| `ddd-aggregate-extend` | 事实文档 §3 + 语义增量 + 写设计 §8 | 先补事实文档 |
| `ddd-projection-create` | 投影读设计 §2–§6 | **不自行设计读模型**，先跑 `ddd-design-doc` |
| `ddd-code-review` | 代码 + 可选设计文档 | 只审硬约束，不做设计符合性对账 |

隔离角色 `agents/ddd-reviewer.md` 与 `ddd-code-review` 同基准，区别在运行隔离（只读、零写入）。

## 3. 使用纪律

1. **KB 是产物不是源**：卡片由框架仓库的 `documentation/best-practices/` 派生；要改约定请在框架仓库改源文档后重新打包，不反向改卡。
2. **单物理副本，只读引用**：Skill 只声明「读哪几篇」，不各带一份拷贝。
3. **fail-fast**：Skill 找不到对应 KB 时停止生成并报告，禁止凭记忆续写。
4. **防幻觉**：框架类 / 方法签名以 `kb/api-contract.md` 为唯一权威；拿不准先读该卡，缺符号就向用户提问。
5. **模板随包，不写入目标工程**：设计文档模板在 `reference/best-practices/`，目标工程只产出 `docs/design/` 下设计文档。
6. **包内文件是只读副本**：发现引用悬空、路径残留等问题，反馈包维护方（在框架仓库内修正后重新打包），不要就地修改。

## 4. 版本

| 项 | 位置 |
| --- | --- |
| KB 版本 / 框架版本 | `kb/_manifest.json` → `kb_version` / `framework_version` |
| 卡片数量 | `kb/bp/*.md`（{kb_cards} 张） |

判断顺序与常见误区见 `USAGE.md`。
"""
root.joinpath("README.md").write_text(readme, encoding="utf-8")
PY

# ---------- 剔除平台专属表述：产物副本里的安装文档改为平台中立 ----------
python3 - "$STAGE/agent-skills" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
PLATFORM = re.compile(r"qoder|codebuddy|workbuddy", re.I)

# 行内含多处信息，先做短语级替换，避免整行丢失
PHRASES = {
    "INSTALL.md": [
        (" / package-skills.sh / package-qoder-plugin.sh", ""),
        ("Claude Code、Qoder 等支持 `SKILL.md` 目录约定的工具，任选其一：",
         "支持 `SKILL.md` 目录约定（目录即 Skill）的智能体可直接安装，任选其一："),
    ],
}

# 打包脚本不入包，安装文档里的「重新打包」整节（含多平台产物对比）随之剔除
CUT_SECTIONS = {
    "INSTALL.md": ("重新打包（在本仓库内）：", "## 6. "),
}

for name in ("INSTALL.md", "README.md"):
    path = root / name
    text = path.read_text(encoding="utf-8")
    for old, new in PHRASES.get(name, []):
        if old not in text:
            raise SystemExit(f"[neutralize] {name} 未匹配到待替换片段：{old[:40]}")
        text = text.replace(old, new)
    if name in CUT_SECTIONS:
        head, tail = CUT_SECTIONS[name]
        start, end = text.find(head), text.find(tail)
        if start == -1 or end == -1 or end <= start:
            raise SystemExit(f"[neutralize] {name} 未能定位待剔除章节：{head}")
        text = text[:start].rstrip("\n") + "\n\n" + text[end:]
    kept = [line for line in text.splitlines() if not PLATFORM.search(line)]
    text = re.sub(r"\n{3,}", "\n\n", "\n".join(kept))
    path.write_text(text.rstrip("\n") + "\n", encoding="utf-8")
PY

# ---------- 平台词零残留校验 ----------
if grep -rniE 'qoder|codebuddy|workbuddy' "$STAGE/agent-skills" > /dev/null; then
  echo "打包失败：产物中仍存在平台专属内容——" >&2
  grep -rniE 'qoder|codebuddy|workbuddy' "$STAGE/agent-skills" >&2
  exit 1
fi

# 统一文件时间戳 → 可复现构建（默认取最近一次提交时间，不在 git 仓库时用固定值）
STAMP="$(git -C "$REPO_ROOT" log -1 --format=%cd --date=format:'%Y%m%d%H%M.%S' 2>/dev/null || true)"
STAMP="${STAMP:-202001010000}"
find "$STAGE" -exec touch -t "$STAMP" {} +

# 必须先删旧包：zip 是增量追加，不删会残留上一版已排除的文件
# -X 不写额外属性；-D 不写目录条目（解压时自动建目录）→ 产物可复现，重打 SHA 不变
( cd "$STAGE" && rm -f "$OUT" && zip -r -q -X -D "$OUT" agent-skills )

echo "已生成：$OUT"
echo "  平台中立：不含 Qoder / CodeBuddy / WorkBuddy 专属脚本、Agent 与表述"
echo "  版本：$VERSION"
echo "  文件数：$(unzip -l "$OUT" | tail -1 | awk '{print $2}')"
echo "  大小：$(du -h "$OUT" | awk '{print $1}')"
