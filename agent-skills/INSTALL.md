# 安装与移植说明

> 本包是 Pragmatic DDD 的 AI 能力套件：10 个 Skill + 1 个隔离评审角色 + 共享知识包（KB）+ 3 个脚本。
> 面向要把这套能力装到**其他智能体 / 其他项目**的使用者。

## 1. 包里有什么

```text
agent-skills/
├── INSTALL.md              ← 本文件
├── USAGE.md                ← 使用者入口：我现在该用哪个、说句话会触发什么
├── README.md               ← 维护者入口：目录结构、能力总览
├── agents/ddd-reviewer.md  ← 隔离评审角色（只读、零写入）
├── ddd-project-init/       ← S1 工程初始化
├── ddd-aggregate-scaffold/ ← S2 聚合目录骨架
├── ddd-infra-config/       ← S3 基础设施装配
├── ddd-aggregate-create/   ← S4 聚合生成（核心）
├── ddd-aggregate-extend/   ← S5 聚合扩展
├── ddd-projection-create/  ← S6 投影读模型
├── ddd-code-review/        ← S7 合规评审
├── ddd-modeling-guide/     ← P2 建模引导
├── ddd-design-doc/         ← 设计文档产出（三契约 + 一快照）
├── ddd-kb-consult/         ← 咨询入口（怎么用 / 为什么这么设计）
├── kb/                     ← 共享知识包：35 张 bp 卡 + 硬约束 + API 契约 + 索引清单
├── scripts/                ← derive-kb.sh / verify-kb.sh / design-doc-workspace.sh / package-skills.sh / package-qoder-plugin.sh
└── reference/              ← 设计文档模板原文（随包只读引用，不落入目标工程，见 §3 末）
```

## 2. 安装前必须知道的两条依赖

**（1）相对路径依赖**

每个 `SKILL.md` 里用相对路径引用知识包：

- `../kb/api-contract.md`、`../kb/hard-constraints.md`、`../kb/bp/*.md`
- `../kb/README.md`（路由表）
- `../reference/best-practices/design-doc-*.md`（设计文档模板，`ddd-design-doc` 用）

因此 `ddd-*/`、`agents/`、`kb/`、`scripts/`、`reference/` **必须保持同级的相对位置**。只复制某个 Skill 目录、不带 `kb/`，Skill 会因找不到知识而停止生成（这是刻意设计的 fail-fast，防止凭记忆编签名）。

**（2）目标工程的工作区依赖**

设计与实施类 Skill 会读写目标工程的 `docs/design/`：

```text
docs/design/
├── prerequisites.md                    项目级前置条件清单（10 项）
├── changes/active/{需求}/{agg}/        ① semantics.md ② aggregate-design.md ③ projection-read.md
├── changes/archive/{需求}/{agg}/       归档后的契约
└── aggregates/{agg}/facts.md           ④ 聚合事实（当前态）
```

首次使用前先跑一次初始化：

```bash
bash scripts/design-doc-workspace.sh init
```

## 3. 安装方式

### 方式 A：支持「目录即 Skill」的智能体（推荐）

Claude Code、Qoder 等支持 `SKILL.md` 目录约定的工具，任选其一：

1. **整包放置**：把 `agent-skills/` 整体放到该工具的 skills 根目录下，使 `ddd-*/SKILL.md` 与 `kb/` 仍在同一父目录内。
2. **逐目录安装**：把 `ddd-*` 与 `agents/` 逐个复制到 skills 根目录，同时把 `kb/`、`scripts/` 复制到 **这些目录的共同父级**，保证 `../kb/` 可解析。

装完自查：打开任一 `SKILL.md`，确认它引用的 `../kb/bp/xxx.md` 在磁盘上真实存在。

**模板不落盘到目标工程**

`ddd-design-doc` 与 `ddd-kb-consult` 需要的设计文档模板（结构与流程权威）随包放在 `reference/best-practices/`，Skill 通过 `../reference/best-practices/…` 只读引用。

- **不要**把模板复制到目标工程的 `documentation/best-practices/`：那是框架仓库文档站的路径。
- 目标工程只需要产出自己的 `docs/design/` 下设计文档（① 语义 / ② 写设计 / ③ 投影读设计 / ④ 事实），不因使用本能力多出模板文件。
- 模板升级靠重新安装 / 重新打包，不改目标工程。

### 方式 B：单文件提示词型智能体

只接受一个系统提示的载体（自定义 GPT、Cursor rules、内置 agent 配置等）：

1. 以 `ddd-kb-consult/SKILL.md` 或 `ddd-design-doc/SKILL.md` 为主干。
2. 把它声明要读的 `kb/` 卡内容**内联追加**在后面（至少：`hard-constraints.md`、`api-contract.md`、若干 `bp/*.md`）。
3. 把 `../kb/` 这类相对引用改写成「见本文第 N 节」。

注意：内联后知识副本会脱离 `_manifest.json` 的溯源校验，源约定变更时需要重新拼一次。

### 方式 C：只做咨询（轻量）

只复制 `ddd-kb-consult/SKILL.md` + `kb/` 全量。得到「问规范即答、不产代码」的问答能力，不涉及工程写入。

## 4. 装完后的第一次运行

| 你想干什么 | 说什么 | 会触发 |
| --- | --- | --- |
| 建工程 | 「初始化一个 DDD 工程」 | S1，顺手产出前置条件清单空模板 |
| 出设计文档 | 「按这份需求出订单聚合的设计文档」 | `ddd-design-doc`：先出 ① 语义并多轮迭代，定稿后再按点单出 ②③ |
| 看现状 | 「订单聚合现在实际是什么」 | `ddd-design-doc` 现状型分支，产出 ④ |
| 生成代码 | 「按设计文档生成订单聚合」 | S4，无 ② 时会拒绝并提示先补设计 |
| 查合规 | 「检查这段代码合不合规」 | S7，只做框架约定与设计一致性评审 |

判断顺序与常见误区见 `USAGE.md`。

## 5. 版本与自检

| 项 | 位置 | 当前值 |
| --- | --- | --- |
| KB 版本 | `kb/_manifest.json` → `kb_version` | `2.4-draft` |
| 框架版本 | `kb/_manifest.json` → `framework_version` | `2.1.0-SNAPSHOT` |
| 卡片数量 | `kb/bp/*.md` | 35 |

在**本仓库内**（源文档都在）可跑：

```bash
bash scripts/verify-kb.sh    # 校验每张卡的 source_sha 与源文档是否一致
bash scripts/derive-kb.sh    # 源文档改过后刷新 source_sha（卡正文仍需人工蒸馏）
```

独立安装后这两个脚本会因找不到 `documentation/best-practices/` 源文档而无法校验——这是正常现象，KB 本身仍是完整可用的只读副本。

重新打包（在本仓库内）：

```bash
bash scripts/package-skills.sh          # 通用安装包：仓库根 agent-skills-kb{版本}.zip
bash scripts/package-qoder-plugin.sh    # Qoder 插件（专家套件）：dist/pragmatic-ddd-skills/
bash scripts/package-workbuddy-expert.sh # 腾讯 WorkBuddy 专家：dist/pragmatic-ddd-expert/
```

三种产物的差别：

| 产物 | 用途 | 结构 |
| --- | --- | --- |
| `agent-skills-kb{V}.zip` | 手动安装 / 内部分发 | 根目录直放 `ddd-*/SKILL.md` + `kb/` |
| `dist/pragmatic-ddd-skills/` | **Qoder 市场提交** | `.qoder-plugin/plugin.json` + `skills/<name>/SKILL.md` + `agents/` + 根共享 `kb/`；技能引用改写为 `../../kb/` |
| `dist/pragmatic-ddd-expert/` | **腾讯 WorkBuddy 专家提交** | `.codebuddy-plugin/plugin.json` + `avatars/expert.png` + `agents/` + `skills/<name>/`（**KB 自包含**到 `references/kb/`，引用改 `@references/…`，并补全 8 个必填 frontmatter 字段） |

## 6. 边界：这套能力不做什么

- 不生成接口层（HTTP / RPC 端点、Request / Response DTO）。
- 不在缺少上游设计文档时自行推导业务语义（会阻断并提示先补文档）。
- 未确认的项目级前置条件（数据库、总线、Outbox、ES / Redis 版本等）对应的产物不生成。
- 不做业务正确性评审，只做框架约定与设计一致性评审。
