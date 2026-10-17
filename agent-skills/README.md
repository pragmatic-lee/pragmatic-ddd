# Pragmatic DDD · AI Skills 与知识包（v1.0 完整草稿，含阶段 3）

- [1. 这是什么](#1-这是什么)
- [2. 目录结构](#2-目录结构)
- [3. 与 `docs/design/ai` 路线图的关系](#3-与-docsdesignai-路线图的关系)
- [4. 七个 Skill + 建模引导总览](#4-七个-skill--建模引导总览)
- [5. 知识包（KB）使用纪律](#5-知识包kb使用纪律)
- [6. 验证方法](#6-验证方法)
- [7. 阶段 3 已完成项](#7-阶段-3-已完成项)
- [8. 后续可选增强](#8-后续可选增强)

## 1. 这是什么

本目录是 [Pragmatic DDD 的 AI 能力实施路线图](../docs/design/ai/ai-implementation-roadmap.md) 的 **v1 完整产物**：一套「7 个 Skill + 1 个共享知识包（KB）+ 1 个隔离评审角色 + 1 个建模引导 + 校验/派生脚本」。

- `kb/`：共享知识包（派生自 `documentation/best-practices/`，单物理副本，所有 Skill 只读引用）。
- 顶层各 `ddd-*/`：按用户意图拆分的 Skill / 引导，每个目录一个 `SKILL.md`。
- `agents/ddd-reviewer.md`：隔离评审角色（只读、零写入）。
- `scripts/`：KB 漂移校验（`verify-kb.sh`）+ 派生（`derive-kb.sh`）。

> 目标架构以路线图 **v1（7 Skill + 共享 KB）** 为准；早期 `docs/design/ai/README.md` 的「单 Skill + cards/」思路为历史稿，已标注「已被 v1 取代」（阶段 0 收口）。

## 2. 目录结构

```text
agent-skills/
├── README.md                       ← 本文件（索引）
├── kb/                             ← 共享知识包
│   ├── README.md                   ← KB 索引与溯源
│   ├── _manifest.json              ← 派生清单（source_sha 已回填真实值）
│   ├── hard-constraints.md         ← 14 条硬约束 + 枚举出入参规则
│   ├── api-contract.md             ← 框架真实 API 契约（防幻觉）
│   ├── bp/                         ← 26 篇 best-practice 派生卡（含 projection-api / aggregate-identification / aggregate-evolution-design）
│   └── （无外部示例依赖，KB 自足）
├── ddd-project-init/               ← S1 项目初始化
├── ddd-aggregate-scaffold/         ← S2 聚合骨架
├── ddd-infra-config/               ← S3 基础设施配置
├── ddd-aggregate-create/           ← S4 聚合创建（核心）
├── ddd-aggregate-extend/           ← S5 聚合扩展
├── ddd-projection-create/          ← S6 投影读模型创建
├── ddd-code-review/               ← S7 类合规评审
├── ddd-modeling-guide/            ← P2 聚合建模引导（长线）
│   ├── SKILL.md
│   └── templates/modeling-worksheet.md
├── ddd-design-doc/                ← D 设计文档产出（全新/加聚合/改聚合，出标准版本化文档）
│   └── SKILL.md
├── agents/ddd-reviewer.md          ← 隔离评审角色（只读、零写入）
└── scripts/                        ← verify-kb.sh（CI 卡口）+ derive-kb.sh（半自动派生）
```

## 3. 与 `docs/design/ai` 路线图的关系

- 本目录是路线图 §7 的**完整产物**（阶段 1 落 S2/S4/S7 + 最小 KB；阶段 2 补 S1/S3/S5/S6 + 全量 KB；阶段 3 做 verbatim+sha / CI / 派生脚本 / 隔离角色 / 路由表 / 建模引导）。
- 路线图 §5 定义 7 个 Skill（S1~S7）+ P2 建模引导；本版已全部落地。

## 4. 能力总览（Skill / 引导 / 设计文档）

| 入口 | 类型 | 触发意图 | 读哪些 KB |
| --- | --- | --- | --- |
| S1 `ddd-project-init` | 初始化 | 新建 DDD 工程 | aggregate-structure, hard-constraints |
| S2 `ddd-aggregate-scaffold` | 骨架 | 建聚合目录骨架 | aggregate-design, application-collaboration, hard-constraints(9) |
| S3 `ddd-infra-config` | 基础设施 | 配 MySQL/MQ/Outbox/ES/Redis/建表 | repository-design, aggregate-database-design, projection-design, event-subscriber-pattern, hard-constraints(9) |
| S4 `ddd-aggregate-create` | 创建（核心） | 生成完整聚合 | 全部 bp + hard-constraints + api-contract |
| S5 `ddd-aggregate-extend` | 扩展 | 给聚合加操作/校验/订阅/子实体/投影源 | 对应 bp + hard-constraints |
| S6 `ddd-projection-create` | 投影 | 生成查询侧读模型 | projection-design, event-subscriber-pattern, enum-value(E3), hard-constraints |
| S7 `ddd-code-review` | 评审 | 评审代码合规 | hard-constraints + api-contract |
| P2 `ddd-modeling-guide` | 建模引导 | 聚合边界/聚合度怎么划（只引导，不出文档） | aggregate-design, aggregate-database-design, entity-design |
| D `ddd-design-doc` | 设计文档产出 | 从需求识别聚合并出标准/版本化设计文档（全新/加聚合/改聚合） | aggregate-identification, aggregate-evolution-design, aggregate-design, entity-design, hard-constraints |
| KB `ddd-kb-consult` | 咨询答疑 | 解释 pragmatic-ddd 规范/最佳实践（不产代码） | 按问题路由到对应 bp + hard-constraints + api-contract |

> 隔离角色 `agents/ddd-reviewer.md` 与 S7 同基准，区别在运行隔离（只读、零写入）。

## 5. 知识包（KB）使用纪律

1. **KB 是产物不是源**：卡从 `documentation/best-practices/` 派生；改框架约定 → 先改源文档，再重派生，**绝不反向改卡**。
2. **单物理副本，只读引用**：Skill 只声明「读哪几篇」，不各带一份拷贝。
3. **fail-fast**：Skill 找不到对应 KB 时停止生成并报告，禁止凭记忆续写。
4. **防幻觉**：KB 中出现的框架类/方法签名以 `kb/api-contract.md` 为唯一权威（含全限定 import + 精确方法签名）；拿不准先 Read 该卡，缺符号则向用户提问。**自 v1.3 起 KB 不引用任何仓库内示例工程**（见 `kb/README.md` §6）。
5. **溯源 + 漂移治理**：每张卡头部标「源文档 + 派生时间 + 框架版本」；`_manifest.json` 记真实 `source_sha`；`scripts/verify-kb.sh` 在源 hash 变化时标卡过期（CI 卡口）；`scripts/derive-kb.sh` 半自动回填 sha256。

## 6. 验证方法

按路线图 §8：

- **A1 卡能命中**：给一句任务，AI 能否从 KB 路由表找到正确卡。
- **A2 代码一次编译通过**：按 KB + S4 生成代码，`mvn compile` 无错。
- **A3 硬约束未被违反 + 零幻觉符号**：逐条核对 `hard-constraints.md`。
- **A4 基准回归**（**仅仓库内**，第三方无需执行）：用 S4 从零重建 `examples/order-example` 的 Order 聚合，逐文件 diff。操作级步骤见 [A4/A5 走查方案](./A4-A5-walkthrough.md)。该示例工程属本仓库，**不属于 KB 发布范围**。
- **A5 独立工程跑通**：框架仓库外新建独立 Maven 工程，跑通 S1→S3→S4→S6→S7 零阻断。操作级步骤见 [A4/A5 走查方案](./A4-A5-walkthrough.md)。
- **A6 角色有效**：已知违规代码对照「角色评审 vs Skill 评审」阻断项召回率，且角色产物目录零写入（`git status` 无改动）。

唯一硬标准：**零上下文的 AI 能否靠 KB 一次写对代码**。

## 7. 阶段 3 已完成项

| 项 | 交付 |
| --- | --- |
| **KB 自足（v1.3）** | 已移除 `kb/examples/order/`（verbatim + sha 清单）与全部 example 引用；5 个 Skill + 1 个角色的防幻觉段改指向 `api-contract.md` / `bp/*.md` |
| `_manifest.json` 真实 sha | 25 篇 bp 卡 `source_sha` 已回填真实值；`removed_scopes` 记录已下线的 example 范围 |
| CI 卡口 | `scripts/verify-kb.sh`（源 hash 变化标 STALE 并退出 1） |
| 派生脚本 | `scripts/derive-kb.sh`（半自动回填 source_sha；卡内容蒸馏需 AI+人审） |
| 隔离评审角色 | `agents/ddd-reviewer.md`（只读工具、零写入、同 S7 基准） |
| AGENTS.md 路由表 | 根 `AGENTS.md` 追加「AI Skills 路由（兜底）」段 |
| 建模引导 P2 | `ddd-modeling-guide/SKILL.md` + `templates/modeling-worksheet.md` |
| 设计文档产出 D | `ddd-design-doc/SKILL.md`（聚合识别 + 13 节标准文档，版本化；实现交 S4/S5） |

## 8. 后续可选增强

- **KB 完备性走查**：定期核对 `api-contract.md` 是否覆盖所有 Skill 会用到的框架符号（补 API 卡缺口优先于补示例文件——v1.2 实验已证明 Tier-1/Tier-2 缺口由 API 卡闭合，而非 verbatim 示例）。
- CI 接入：把 `verify-kb.sh` 挂到 PR 检查（源文档改 → 卡过期 → 阻断合并直至重派生）。
- 派生脚本增强：结构化剥离 + AI 语义裁剪 + 人审的端到端半自动流水线。
- 隔离角色接真实 Agent 运行时（如 CodeBuddy `agent`），验证零写入与召回率。

---

> **版本**：v1.4-draft（KB 重建：派生自 documentation/best-practices/ 当前源）
> **状态**：草稿，待按路线图 §8 验收走查；KB 已解耦示例工程依赖，第三方可独立使用
> **适用框架版本**：2.0.0
> **上游**：[AI 能力实施路线图](../docs/design/ai/ai-implementation-roadmap.md) · [聚合生成器落地设计](./design/ai/aggregate-generator-landing-design.md)
