#!/usr/bin/env bash
# =============================================================================
# package-workbuddy-expert.sh — 把 agent-skills 打成腾讯 WorkBuddy「专家（Expert）」包
#
# 产物结构（WorkBuddy 专家规范）：
#   pragmatic-ddd-expert/
#   ├── .codebuddy-plugin/plugin.json   专家清单（展示信息 40–50 字中文描述等）
#   ├── avatars/expert.png              512×512 PNG，≤500KB
#   ├── agents/
#   │   ├── pragmatic-ddd-architect.md  主 Agent（agentName）
#   │   └── ddd-reviewer.md             只读评审角色
#   ├── skills/<skill>/SKILL.md         10 个技能（补全 8 个必填 frontmatter）
#   │   ├── references/kb/…             自包含：只带本技能引用的卡片
#   │   ├── references/docs/…           设计文档模板（按需）
#   │   └── references/scripts/…        运行期脚本（按需，如 design-doc-workspace.sh）
#   └── README.md
#
# 与 Qoder 版的关键差异：
#   1) 元数据目录 .codebuddy-plugin（不是 .qoder-plugin）
#   2) 技能内不能引用包外 kb，必须自包含到 references/kb/
#   3) frontmatter 需补 display_name / display_name_en / description_zh / description_en / version / author
#   4) 引用写法改为 @references/…（WorkBuddy 的上下文引用语法）
#
# 用法：
#   package-workbuddy-expert.sh [版本号]
#   OUT=/path/to/out.zip package-workbuddy-expert.sh 2.4.0
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SKILLS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
REPO_ROOT="$(cd "$SKILLS_DIR/.." && pwd)"

VERSION="${1:-$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1],encoding="utf-8"))["plugin"]["version"])' "$SCRIPT_DIR/workbuddy-expert-meta.json")}"

DIST_DIR="${DIST:-$REPO_ROOT/dist}"
STAGE="$DIST_DIR/pragmatic-ddd-expert"
OUT="${OUT:-$DIST_DIR/pragmatic-ddd-expert-$VERSION.zip}"

rm -rf -- "$STAGE"
mkdir -p "$STAGE/.codebuddy-plugin" "$STAGE/agents" "$STAGE/avatars" "$STAGE/skills"

# ---------- 头像：logo.svg → 512×512 PNG ----------
AVATAR_TMP="$DIST_DIR/.avatar.png"
sips -s format png "$REPO_ROOT/logo.svg" --out "$AVATAR_TMP" >/dev/null
sips -z 512 512 "$AVATAR_TMP" >/dev/null
cp "$AVATAR_TMP" "$STAGE/avatars/expert.png"
rm -f -- "$AVATAR_TMP"

python3 - "$SKILLS_DIR" "$REPO_ROOT" "$STAGE" "$SCRIPT_DIR/workbuddy-expert-meta.json" "$VERSION" <<'PY'
import json, os, re, shutil, subprocess, sys

skills_src, repo_root, stage, meta_path, version = sys.argv[1:6]
meta = json.load(open(meta_path, encoding="utf-8"))
plugin = dict(meta["plugin"])
plugin["version"] = version

skills_meta = meta["skills"]
KB_SRC = os.path.join(skills_src, "kb")
DOCS_SRC = os.path.join(repo_root, "documentation", "best-practices")
DOC_SET = [
    "design-doc-template.md", "design-doc-semantics.md", "design-doc-write-template.md",
    "design-doc-read-template.md", "design-doc-facts.md", "design-doc-prerequisites.md",
    "aggregate-database-design.md",
]
FULL_KB_SKILL = "ddd-kb-consult"
# 随技能自包含的运行期脚本（文档提到即随包，引用改写为 @references/scripts/）
SCRIPT_SET = ["design-doc-workspace.sh"]

def ensure_dir(p):
    os.makedirs(p, exist_ok=True)

def copy_kb(rel, refs_dir, copied):
    src = os.path.join(KB_SRC, rel)
    if not os.path.isfile(src):
        raise SystemExit(f"[kb] 源文件不存在：{rel}")
    dst = os.path.join(refs_dir, "kb", rel)
    ensure_dir(os.path.dirname(dst))
    shutil.copy2(src, dst)
    copied.add(rel)

def yaml_str(v):
    return json.dumps(v, ensure_ascii=False)

def yaml_list(key, items):
    lines = [f"{key}:"]
    lines += [f"  - {yaml_str(i)}" for i in items]
    return "\n".join(lines)

def build_fm_addon(m):
    return "\n".join([
        f"display_name: {yaml_str(m['display_name'])}",
        f"display_name_en: {yaml_str(m['display_name_en'])}",
        f"description_zh: {yaml_str(m['description_zh'])}",
        f"description_en: {yaml_str(m['description_en'])}",
        f"version: {version}",
        "author: wizard-lee",
        yaml_list("examples_zh", m.get("examples_zh", [])),
        yaml_list("examples_en", m.get("examples_en", [])),
    ])

def render_kb_index(path, copied):
    rows = ["| 文件 | 说明 |", "| --- | --- |"]
    for rel in sorted(copied):
        label = "知识包索引" if rel == "README.md" else rel
        rows.append(f"| `kb/{rel}` | {label} |")
    if not copied:
        rows.append("| — | 本技能未直接引用卡片，按需再补 |")
    text = (
        "# 知识包（本技能随包副本）\n\n"
        "只包含本技能直接引用的卡片；完整 35 张卡见 `ddd-kb-consult` 技能。\n\n"
        + "\n".join(rows) + "\n"
    )
    ensure_dir(os.path.dirname(path))
    open(path, "w", encoding="utf-8").write(text)

for name in sorted(skills_meta):
    src_dir = os.path.join(skills_src, name)
    dst_dir = os.path.join(stage, "skills", name)
    shutil.copytree(src_dir, dst_dir)
    md = os.path.join(dst_dir, "SKILL.md")
    text = open(md, encoding="utf-8").read()
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    if not m:
        raise SystemExit(f"[{name}] SKILL.md 缺少 frontmatter")
    fm, body = m.group(1), text[m.end():]

    refs_dir = os.path.join(dst_dir, "references")
    copied = set()

    # 1) ../kb/xxx  →  @references/kb/xxx
    def repl_kb(mo):
        rel = mo.group(1)
        if rel == "" or rel.endswith("/"):
            return "@references/kb/"
        copy_kb(rel, refs_dir, copied)
        return "@references/kb/" + rel

    body = re.sub(r"\.\./kb/([A-Za-z0-9_./-]*)", repl_kb, body)

    # 2) documentation/best-practices/x.md → @references/docs/x.md
    need_docs = bool(re.search(r"documentation/best-practices/", body))
    body = re.sub(
        r"documentation/best-practices/([A-Za-z0-9_.-]+\.md)",
        lambda mo: "@references/docs/" + mo.group(1),
        body,
    )
    # 裸目录引用（不带文件名）也指向随包副本
    body = body.replace("documentation/best-practices/", "@references/docs/")
    need_docs = need_docs or "@references/docs/" in body
    if need_docs:
        for f in DOC_SET:
            src = os.path.join(DOCS_SRC, f)
            dst = os.path.join(refs_dir, "docs", f)
            ensure_dir(os.path.dirname(dst))
            shutil.copy2(src, dst)

    # 3) ../USAGE.md → @references/usage.md
    if "../USAGE.md" in body:
        ensure_dir(refs_dir)
        shutil.copy2(os.path.join(skills_src, "USAGE.md"), os.path.join(refs_dir, "usage.md"))
        body = body.replace("../USAGE.md", "@references/usage.md")

    # 4) 分类技能：整包 KB 自包含
    if name == FULL_KB_SKILL:
        for root, _, files in os.walk(KB_SRC):
            for f in files:
                rel = os.path.relpath(os.path.join(root, f), KB_SRC)
                copy_kb(rel, refs_dir, copied)

    # 5) 代码评审技能：Agent 会引用这两份，强制带上
    if name == "ddd-code-review":
        for rel in ("hard-constraints.md", "api-contract.md"):
            copy_kb(rel, refs_dir, copied)

    # 6) README 索引重建（不复制原 35 卡索引，避免指向未随包的卡）
    readme_path = os.path.join(refs_dir, "kb", "README.md")
    if "README.md" in copied:
        copied.discard("README.md")
        render_kb_index(readme_path, copied)
        copied.add("README.md")
    elif os.path.isdir(os.path.join(refs_dir, "kb")):
        render_kb_index(readme_path, copied)

    # 7) 运行期脚本自包含：文档提到哪个脚本就随技能带哪个，引用改 @references/scripts/
    def self_contain_scripts(text):
        for name in SCRIPT_SET:
            text = text.replace("agent-skills/scripts/" + name, "@references/scripts/" + name)
            text = re.sub(
                r"(?<!@references/)(?<!agent-skills/)scripts/" + re.escape(name),
                "@references/scripts/" + name,
                text,
            )
        return text

    body = self_contain_scripts(body)
    need_scripts = set()
    for script_name in SCRIPT_SET:
        if "@references/scripts/" + script_name in body:
            need_scripts.add(script_name)
    for root_dir, _, files in os.walk(refs_dir):
        for f in files:
            if not f.endswith(".md"):
                continue
            p = os.path.join(root_dir, f)
            text = open(p, encoding="utf-8").read()
            new_text_ref = self_contain_scripts(text)
            if new_text_ref != text:
                open(p, "w", encoding="utf-8").write(new_text_ref)
            for script_name in SCRIPT_SET:
                if "@references/scripts/" + script_name in new_text_ref:
                    need_scripts.add(script_name)
    for script_name in sorted(need_scripts):
        src = os.path.join(skills_src, "scripts", script_name)
        if not os.path.isfile(src):
            raise SystemExit(f"[{name}] 运行期脚本不存在：{src}")
        dst = os.path.join(refs_dir, "scripts", script_name)
        ensure_dir(os.path.dirname(dst))
        shutil.copy2(src, dst)

    # 8) frontmatter 补全
    new_text = "---\n" + fm.rstrip("\n") + "\n" + build_fm_addon(skills_meta[name]) + "\n---\n" + body
    open(md, "w", encoding="utf-8").write(new_text)

# ---------- agents ----------
arch_src = os.path.join(skills_src, "agents", "pragmatic-ddd-architect.md")
shutil.copy2(arch_src, os.path.join(stage, "agents", "pragmatic-ddd-architect.md"))

reviewer = open(os.path.join(skills_src, "agents", "ddd-reviewer.md"), encoding="utf-8").read()
rm = re.match(r"^---\n(.*?)\n---\n", reviewer, re.S)
rfm, rbody = rm.group(1), reviewer[rm.end():]
rfm = re.sub(
    r"^description:.*$",
    "description: Isolated compliance reviewer: checks aggregate code against framework hard constraints "
    "and writes findings only to a scratch area, never touching user code.",
    rfm, count=1, flags=re.M,
)
rfm += "\ndisplayName:\n  en: \"Compliance Reviewer\"\n  zh: \"合规评审员\"\nprofession:\n  en: \"Framework Compliance Reviewer\"\n  zh: \"框架合规评审员\"\nmaxTurns: 40"
rbody = rbody.replace("../kb/hard-constraints.md", "../skills/ddd-code-review/references/kb/hard-constraints.md")
rbody = rbody.replace("../kb/api-contract.md", "../skills/ddd-code-review/references/kb/api-contract.md")
rbody = rbody.replace("../kb/bp/", "../skills/ddd-kb-consult/references/kb/bp/")
rbody = rbody.replace("`../kb/`", "`../skills/ddd-kb-consult/references/kb/`")
rbody = rbody.replace("../ddd-code-review/SKILL.md", "../skills/ddd-code-review/SKILL.md")
open(os.path.join(stage, "agents", "ddd-reviewer.md"), "w", encoding="utf-8").write(
    "---\n" + rfm.rstrip("\n") + "\n---\n" + rbody
)

# ---------- plugin.json / README ----------
plugin["skills"] = [f"./skills/{n}" for n in sorted(skills_meta)]
open(os.path.join(stage, ".codebuddy-plugin", "plugin.json"), "w", encoding="utf-8").write(
    json.dumps(plugin, ensure_ascii=False, indent=2) + "\n"
)

readme = f"""# {plugin['displayName']['zh']}（{plugin['name']}）

{plugin['displayDescription']['zh']}

## 技能

| 技能 | 用途 |
| --- | --- |
""" + "\n".join(f"| `{n}` | {skills_meta[n]['description_zh']} |" for n in sorted(skills_meta)) + """

## 角色

| Agent | 用途 |
| --- | --- |
| `pragmatic-ddd-architect` | 主 Agent：判定场景、按闸门推进、路由到对应技能 |
| `ddd-reviewer` | 只读合规评审：只查框架硬约束，产物写临时区 |

## 不做什么

- 不生成接口层（HTTP / RPC 端点、Request / Response DTO）。
- 缺上游设计文档时不自编业务语义，会阻断并提示先补文档。
"""
open(os.path.join(stage, "README.md"), "w", encoding="utf-8").write(readme)

# ---------- 校验 ----------
errors = []
req = ["name", "expertType", "version", "description", "author", "agents", "agentName",
       "displayName", "profession", "displayDescription", "avatar", "categoryId",
       "defaultInitPrompt", "plugin", "tags", "quickPrompts"]
for k in req:
    if not plugin.get(k):
        errors.append(f"plugin.json 缺必填字段 {k}")
if plugin.get("expertType") != "agent":
    errors.append("expertType 必须为 agent")
if plugin.get("agentName") not in [os.path.basename(a)[:-3] for a in plugin.get("agents", [])]:
    errors.append("agentName 必须对应 agents/ 下的文件")
if plugin.get("plugin") != plugin.get("name"):
    errors.append("plugin 必须与 name 一致")
if plugin.get("defaultInitPrompt") != plugin.get("quickPrompts", [None])[0]:
    errors.append("defaultInitPrompt 必须与 quickPrompts 第一条一致")
if len(plugin.get("tags", [])) != 3:
    errors.append("tags 必须固定 3 个")
if len(plugin.get("quickPrompts", [])) != 3:
    errors.append("quickPrompts 必须固定 3 个")
zh_len = len(plugin["displayDescription"]["zh"])
if not 40 <= zh_len <= 50:
    errors.append(f"displayDescription.zh 长度 {zh_len}，要求 40–50 字")

for a in plugin.get("agents", []) + ["./agents/pragmatic-ddd-architect.md"]:
    if not os.path.isfile(os.path.join(stage, a.lstrip("./"))):
        errors.append(f"agent 文件不存在：{a}")

for af in ("pragmatic-ddd-architect.md", "ddd-reviewer.md"):
    agent_text = open(os.path.join(stage, "agents", af), encoding="utf-8").read()
    if re.search(r"\.\./kb/", agent_text):
        errors.append(f"agents/{af}: 仍有 ../kb/ 引用（插件内无根 kb）")
    if "../ddd-code-review/" in agent_text:
        errors.append(f"agents/{af}: Skill 引用未补 skills/ 一层")

SKILL_REQ = ["name", "display_name", "display_name_en", "description", "description_zh",
             "description_en", "version", "author"]
for name in sorted(skills_meta):
    d = os.path.join(stage, "skills", name)
    md = os.path.join(d, "SKILL.md")
    text = open(md, encoding="utf-8").read()
    m = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    fm, body = m.group(1), text[m.end():]
    for k in SKILL_REQ:
        if not re.search(rf"^{k}:", fm, re.M):
            errors.append(f"{name}: 缺 frontmatter 字段 {k}")
    if re.search(r"^name:\s*(.+)$", fm, re.M).group(1).strip() != name:
        errors.append(f"{name}: name 与目录不一致")
    if re.search(r"\.\./(kb|USAGE\.md)", body):
        errors.append(f"{name}: 仍存在包外相对引用")
    if "documentation/best-practices/" in body:
        errors.append(f"{name}: 仍引用仓库文档路径")
    for ref in set(re.findall(r"@references/([A-Za-z0-9_./*-]+)", body)):
        if ref.endswith("/") or "*" in ref:
            continue
        if not os.path.isfile(os.path.join(d, "references", ref)):
            errors.append(f"{name}: @references 目标不存在 {ref}")

avatar = os.path.join(stage, "avatars", "expert.png")
if not os.path.isfile(avatar):
    errors.append("缺少 avatars/expert.png")
else:
    size = os.path.getsize(avatar)
    dims = subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", avatar],
                          capture_output=True, text=True).stdout
    if size > 500 * 1024:
        errors.append(f"头像 {size} 字节，超过 500KB")
    if "pixelWidth: 512" not in dims or "pixelHeight: 512" not in dims:
        errors.append("头像不是 512×512")

if errors:
    print("校验失败：")
    for e in errors:
        print("  -", e)
    sys.exit(1)

total = sum(len(f) for _, _, f in os.walk(stage))
print(f"校验通过：{len(skills_meta)} 技能 + {len(plugin['agents'])} agents；文件总数 {total}；"
      f"displayDescription.zh={zh_len} 字")
PY

# ---------- 打包 ----------
( cd "$DIST_DIR" && rm -f "$OUT" && zip -r -q -X "$OUT" pragmatic-ddd-expert )

echo "已生成目录：$STAGE"
echo "已生成压缩包：$OUT"
echo "  版本：$VERSION"
echo "  文件数：$(unzip -l "$OUT" | tail -1 | awk '{print $2}')"
echo "  大小：$(du -h "$OUT" | awk '{print $1}')"
