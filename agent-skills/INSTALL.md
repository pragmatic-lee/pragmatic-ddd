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
├── kb/                     ← 共享知识包：34 张 bp 卡 + 硬约束 + API 契约 + 索引清单
├── scripts/                ← derive-kb.sh / verify-kb.sh / design-doc-workspace.sh / package-skills.sh
└── reference/              ← 安装附件：设计文档模板原文副本（见 §3 步骤 3）
```

## 2. 安装前必须知道的两条依赖

**（1）相对路径依赖**

每个 `SKILL.md` 里用相对路径引用知识包：

- `../kb/api-contract.md`、`../kb/hard-constraints.md`、`../kb/bp/*.md`
- `../kb/README.md`（路由表）

因此 `ddd-*/`、`agents/`、`kb/`、`scripts/` **必须保持同级的相对位置**。只复制某个 Skill 目录、不带 `kb/`，Skill 会因找不到知识而停止生成（这是刻意设计的 fail-fast，防止凭记忆编签名）。

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

**第 3 步（容易漏）：落设计文档模板**

`ddd-design-doc` 与 `ddd-kb-consult` 把「设计文档骨架」的结构权威指向文档站原文，而在独立安装环境中这个路径不存在。因此需要：

```bash
mkdir -p <目标工程>/documentation/best-practices
cp agent-skills/reference/best-practices/*.md <目标工程>/documentation/best-practices/
```

漏了这一步，设计文档类 Skill 只能按 KB 卡骨架降级工作（缺少完整逐节规约与反例）。

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
| 出设计文档 | 「按这份需求出订单聚合的设计文档」 | `ddd-design-doc`，产出 ①②③ |
| 看现状 | 「订单聚合现在实际是什么」 | `ddd-design-doc` 现状型分支，产出 ④ |
| 生成代码 | 「按设计文档生成订单聚合」 | S4，无 ② 时会拒绝并提示先补设计 |
| 查合规 | 「检查这段代码合不合规」 | S7，只做框架约定与设计一致性评审 |

判断顺序与常见误区见 `USAGE.md`。

## 5. 版本与自检

| 项 | 位置 | 当前值 |
| --- | --- | --- |
| KB 版本 | `kb/_manifest.json` → `kb_version` | `2.3-draft` |
| 框架版本 | `kb/_manifest.json` → `framework_version` | `2.1.0-SNAPSHOT` |
| 卡片数量 | `kb/bp/*.md` | 34 |

在**本仓库内**（源文档都在）可跑：

```bash
bash scripts/verify-kb.sh    # 校验每张卡的 source_sha 与源文档是否一致
bash scripts/derive-kb.sh    # 源文档改过后刷新 source_sha（卡正文仍需人工蒸馏）
```

独立安装后这两个脚本会因找不到 `documentation/best-practices/` 源文档而无法校验——这是正常现象，KB 本身仍是完整可用的只读副本。

重新打包（在本仓库内）：

```bash
bash scripts/package-skills.sh        # 版本号默认取 kb_version，产物在仓库根 agent-skills-kb{版本}.zip
```

## 6. 边界：这套能力不做什么

- 不生成接口层（HTTP / RPC 端点、Request / Response DTO）。
- 不在缺少上游设计文档时自行推导业务语义（会阻断并提示先补文档）。
- 未确认的项目级前置条件（数据库、总线、Outbox、ES / Redis 版本等）对应的产物不生成。
- 不做业务正确性评审，只做框架约定与设计一致性评审。
