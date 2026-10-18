# KB 全量语义重蒸馏计划（v1.7）

- [1. 背景与目标](#1-背景与目标)
- [2. 现状基线](#2-现状基线)
- [3. 重建范围与策略](#3-重建范围与策略)
- [4. 执行步骤](#4-执行步骤)
- [5. 卡格式规范（蒸馏纪律）](#5-卡格式规范蒸馏纪律)
- [6. 验收标准](#6-验收标准)

## 1. 背景与目标

`agent-skills/kb/` 是 pragmatic-ddd 全部 AI Skill 的**唯一共享知识源（单物理副本）**，由其权威源派生（蒸馏）而来。KB 的核心价值是**防幻觉**：零上下文 AI 靠它一次写对可编译代码。

当前 KB 为 `kb_version = 1.6-draft`（2026-10-17 派生），`verify-kb.sh` 已返回 `KB OK`（所有 `source_sha` 与源一致），29 篇 bp 卡 + `hard-constraints` + `api-contract` + `projection-api` 覆盖完整。

但 sha 一致只代表**源文档未变**，不代表**卡内容一定精准反映当前源语义**。`api-contract.md` / `projection-api.md` 的源是 `pragmatic-ddd-core` 源码（`source_sha = TODO`，verify 跳过），`hard-constraints.md` 的源是多篇 best-practices 的聚合，这两处最易静默漂移。本次目标：**忽略 sha 一致，从权威源逐卡重新语义蒸馏，彻底刷新到 v1.7 基线**。

## 2. 现状基线

- `kb_version = 1.6-draft`，`generated_at = 2026-10-17`，`framework_version = 2.0.0`。
- `verify-kb.sh` → `KB OK`（exit 0），无 STALE 卡。
- 覆盖：28 篇 best-practices 文档全部登记为 bp 卡 + `projection-api`（core 源码）+ `hard-constraints`（多源聚合）+ `api-contract`（core 源码）= 31 张卡。
- 旧 `KB-REBUILD-PLAN.md` 针对 v1.4，已被 v1.4→v1.6 多次重建取代，本次重写为 v1.7。

## 3. 重建范围与策略

**来源纪律（不变）**：KB 是产物不是源。改框架约定 → 先改 `documentation/best-practices/` → 再重派生对应卡；不反向改卡。本次只动 `kb/`，不动 `documentation/` 与框架源码。

**重蒸馏策略（全量）**：

| 分组 | 卡 | 源 | 动作 |
| --- | --- | --- | --- |
| BP-A 配置 | mysql-config / mybatis-config / outbox-config / rocketmq-config / kafka-config / event-manager-config / elasticsearch-config | `documentation/best-practices/*.md` | 逐篇重蒸馏，核对前缀（`pragmatic-ddd.event.rocketmq.*` / `pragmatic-ddd.event.kafka.*`）、配置类与装配签名 |
| BP-B 建模 | aggregate-design / aggregate-structure / entity-design / value-object / enum-value / aggregate-database-design | 同上 | 逐篇重蒸馏，核对父类托管能力、`markCreated()`+`markNew()` 并列、Lombok 约定、TrackedList 泛型边界 |
| BP-C 规则/事件 | operation-registry-design / registry-design / order-rule-pattern / event-modeling / event-subscriber-pattern / domain-service | 同上 | 逐篇重蒸馏，核对 `RuleCheckResult` / `IActiveRuleCondition.of` / `hasOperation*` / `@DomainService` |
| BP-D 应用/读 | application-collaboration / repository-design / projection-design / broadcast / dependency-declaration | 同上 | 逐篇重蒸馏，核对事务由 `TransactionOperations`、`IRepository` 语义、投影四角色 |
| BP-E 文档/注释 | code-comment-specification / design-doc-template / aggregate-identification / aggregate-evolution-design | 同上 | 逐篇重蒸馏 |
| 特殊-1 | `api-contract.md` | `pragmatic-ddd-core` 源码（`io.pragmatic.ddd.*`） | 重核全限定 import + Tier-1/Tier-2 签名，钉死 D1–D5 缺口，刷新「不存在的符号」清单 |
| 特殊-2 | `projection-api.md` | core 投影基类源码 | 重核 `AbstractProjectionSource` 5 参构造、`IAggregateProjector`/`IReducer`/`IReadModelReplica` 可编译签名 |
| 特殊-3 | `hard-constraints.md` | 多篇 best-practices 聚合 + 记忆 29822425 | 重蒸馏 14 条硬约束 + E1~E3 枚举规则，刷新派生时间 |

**不重建**：`_manifest.json` 结构、`scripts/`、`agents/`、`ddd-*/` Skill 本体（仅 `kb/` 产物刷新后可能同步路由表微调，见 §4.7）。

## 4. 执行步骤

1. 写本计划文档（agent-skills/KB-REBUILD-PLAN.md，v1.7）。
2. 重蒸馏 BP-A~BP-E 共 29 篇 bp 卡：逐篇 Read 对应 `documentation/best-practices/*.md`，按 [§5](#5-卡格式规范蒸馏纪律) 重写 `kb/bp/*.md`，头部 `派生时间` 改为 2026-10-18。
3. 重蒸馏 `api-contract.md`：对照 `pragmatic-ddd-core` 源码核实全部 import 与方法签名，刷新「不存在的符号」清单。
4. 重蒸馏 `projection-api.md`：对照 core 投影基类源码核实可编译签名。
5. 重蒸馏 `hard-constraints.md`：聚合 14 条硬约束 + E1~E3。
6. 运行 `agent-skills/scripts/derive-kb.sh` 刷新全部 `source_sha`；再跑 `verify-kb.sh` 应 `KB OK`，exit 0。
7. 更新 `_manifest.json`：`kb_version` → `1.7-draft`，`generated_at` → `2026-10-18`，`note` 追加 v1.7 记录。
8. 更新 `kb/README.md`（版本 + 目录保持一致）与顶层 `agent-skills/README.md` 的 `kb_version`、状态、适用框架版本。
9. 输出重建报告：覆盖、各卡溯源、残留风险（core 源码类 `api-contract`/`projection-api` 的 `source_sha` 仍 TODO，verify 跳过）。

## 5. 卡格式规范（蒸馏纪律）

每张 bp 卡遵循统一结构，保证 Skill 可机器路由、AI 可零上下文消费：

1. **标题**：`# BP 卡：<中文名>（<english-slug>）`。
2. **溯源头**（首行引用块）：`> 源：<源文档相对路径> · 派生时间：2026-10-18 · 框架版本：2.0.0`。`api-contract`/`projection-api` 用「派生自 pragmatic-ddd-core 源码」表述。
3. **蒸馏原则**：剥离叙述性铺垫，保留**命名 / 包结构 / 骨架代码 / 关键规则 / 反模式表 / 被读于**。
4. **代码块**：仅保留可编译骨架与最小范例（`Order` 等作范式载体，须说明替换为目标聚合名）；不堆砌完整业务实现。
5. **防幻觉**：出现框架符号一律指向 `api-contract.md` 全限定 import；禁止臆测子包（如 `IEnumValue` 在 `base`、`rules` 在 `io.pragmatic.ddd.rules`）。
6. **自足性**：不引用仓库内 `examples/order-example` 等示例工程；如提及 `Order` 显式标注为范式占位。
7. **章节末**：`## 被读于` 标注被哪些 Skill（S1~S7 / P2 / D / 咨询）消费。

## 6. 验收标准

- [ ] `verify-kb.sh` 退出 0（`KB OK`），无 STALE 卡。
- [ ] `kb/bp/` 含 29 张 bp 卡，全部 `派生时间` 为 2026-10-18（v1.7 基线）。
- [ ] `api-contract.md` / `projection-api.md` / `hard-constraints.md` 内容经源码/多源复核，无 D1–D5 类漂移。
- [ ] 3 张历史错误卡（aggregate-design 的 `markCreated()`+`markNew()` 并列、order-rule-pattern 的 `RuleCheckResult`/`IActiveRuleCondition`、rocketmq-config 的 `pragmatic-ddd.event.rocketmq.*` 前缀）保持正确。
- [ ] `_manifest.json` 的 `kb_version = 1.7-draft`，`source_sha` 全部刷新；`kb/README.md` 与顶层 `README.md` 版本号一致。

> 本计划仅重建 KB 派生物（`kb/`），不改动 `documentation/best-practices/` 权威源与框架源码；所有卡均从当前权威源重新蒸馏。
