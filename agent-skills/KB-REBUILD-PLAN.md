# KB 重建计划（agent-skills/kb）

- [1. 背景与现状](#1-背景与现状)
- [2. 漂移检测结果](#2-漂移检测结果)
- [3. 重建范围与策略](#3-重建范围与策略)
- [4. 执行步骤](#4-执行步骤)
- [5. 验收标准](#5-验收标准)

## 1. 背景与现状

`agent-skills/kb/` 是 pragmatic-ddd 全部 AI Skill 的**唯一共享知识源（单物理副本）**，由 `documentation/best-practices/` 派生（蒸馏）而来。当前 KB 版本 `kb_version = 1.3-draft`，`generated_at = 2026-09-18`，`framework_version = 2.0.0`。

KB 的核心价值是**防幻觉**：零上下文 AI 靠它一次写对可编译代码。一旦 KB 与其权威源（`documentation/best-practices/`）漂移，蒸馏卡就会携带过时甚至错误签名，反而制造幻觉。

## 2. 漂移检测结果

运行 `agent-skills/scripts/verify-kb.sh`，输出 **10 张卡 STALE**（记录的 `source_sha` 与当前源文档不一致）：

| 卡 | 权威源 | 漂移严重度 | 问题摘要 |
| --- | --- | --- | --- |
| `bp.aggregate-design` | aggregate-design.md | 严重 | 骨架未区分 `markNew()`（新建路由标志，置 `isNew`，供仓储 `save` 走 insert/update）与 `markCreated()`（审计时间戳 createdAt/updatedAt），两者并存、新建构造都要调；`@Setter(AROTECTED)` 笔误；旧式内部静态类骨架；未覆盖 §3.3 充血方法（无守卫/无组装）、§3.4 `IParamObject`、§3.5 Lombok 约定 |
| `bp.order-rule-pattern` | order-rule-pattern.md | 严重+错误 | 仍用旧 `boolean` 返回 + `MessageCode.switchStatus(...)` + `ActiveStatus.ACTIVE` 作第三参；当前源用 `RuleCheckResult.of(...)` / `IActiveRuleCondition.of(...)` / `hasOperation*` / `requireOldEntity()`，且样式已更新 |
| `bp.rocketmq-config` | rocketmq-config.md | 中等+错误 | 配置前缀写成 `rocketmq.*`，正确为 `pragmatic-ddd.event.rocketmq.*`；会直接导致用户 `application.yml` 配置漂移 |
| `bp.operation-registry-design` | operation-registry-design.md | 中等 | 缺 §5 关键机制：`public` 反射注册、`operationRegistry()` 返回 `null`=禁用、`MultipleOperationsException`、`hasOperation*` |
| `bp.repository-design` | repository-design.md | 中等 | 缺 §1.5 事务由 `TransactionOperations` 而非 `@Transactional`、§2.1 `IRepository` 方法语义 |
| `bp.application-collaboration` | application-collaboration.md | 轻微 | 基本对齐（09-19 派生）；补 §4.8 读服务无 `AbstractProjectionQuery` 基类、§4.9 工作单元写法细节 |
| `bp.dependency-declaration` | dependency-declaration.md | 轻微 | 基本对齐（09-27 派生）；补外部返回值转领域值可入聚合字段等细节 |
| `bp.event-manager-config` | event-manager-config.md | 轻微 | 基本对齐；补 `KafkaEventManager` 三选一、spring-boot 自动装配说明 |
| `bp.mysql-config` | mysql-config.md | 轻微 | 基本对齐；补 `TypeHandlerContext` 单点来源、集合列别名避坑 |
| `bp.outbox-config` | outbox-config.md | 轻微 | 基本对齐；补 spring-boot 自动装配退位说明 |

**额外缺口（verify-kb 不报，需人工识别）**：
- `documentation/best-practices/kafka-config.md` **无对应 KB 卡**（未在 `_manifest.json` 登记、不在 `kb/bp/`）。需新建 `bp/kafka-config.md` 并登记。

**无需重建**：其余 13 张 bp 卡、`hard-constraints.md`、`api-contract.md` 的 `source_sha` 与当前源一致（或属 `pragmatic-ddd-core` 源，不计入 STALE）。`api-contract.md` 将在执行中做一次 Tier-1/Tier-2 抽查复核（见 §4.6）。

## 3. 重建范围与策略

- **来源纪律**：KB 是产物不是源。改框架约定 → 先改 `documentation/best-practices/` → 再重派生对应卡；不反向改卡。
- **单物理副本**：Skill 只声明「读哪几篇」，不内联重复；本次只动 `kb/`，不动 `documentation/` 与框架源码。
- **重蒸馏策略**：
  - 严重+错误 3 张（aggregate-design / order-rule-pattern / rocketmq-config）**整篇重写**，严格对齐当前源文档签名与约定。
  - 中等 2 张（operation-registry-design / repository-design）**补全缺失关键节**。
  - 轻微 5 张（application-collaboration / dependency-declaration / event-manager-config / mysql-config / outbox-config）**小幅同步**。
  - 新增 1 张 `bp/kafka-config.md`（与 rocketmq-config 平行）。
- **`api-contract.md`**：保持 Tier-1 全限定 import + Tier-2 精确签名；执行中抽查 `EntityFactory` / `IIdGenerator` / `EntityRule` / `BrokenRuleRegistry` / `IEnumValue` 与当前 `pragmatic-ddd-core` 源码一致；若一致仅更新「派生时间」，若有漂移则钉死修正。

## 4. 执行步骤

1. 重写 `kb/bp/aggregate-design.md`（骨架构造并列 `markCreated()`（审计）与 `markNew()`（新建路由），对齐 `IParamObject`、§3.3/§3.5）。
2. 重写 `kb/bp/order-rule-pattern.md`（对齐 `RuleCheckResult` / `IActiveRuleCondition.of` / `hasOperation*` / `@DomainService`）。
3. 重写 `kb/bp/rocketmq-config.md`（前缀修正为 `pragmatic-ddd.event.rocketmq.*`）。
4. 补全 `kb/bp/operation-registry-design.md`、`kb/bp/repository-design.md`。
5. 同步 `kb/bp/application-collaboration.md`、`dependency-declaration.md`、`event-manager-config.md`、`mysql-config.md`、`outbox-config.md`。
6. 新建 `kb/bp/kafka-config.md`，并在 `_manifest.json` 登记 `bp.kafka-config`（含 `source_sha`）。
7. 运行 `agent-skills/scripts/derive-kb.sh` 刷新全部 `source_sha`；再跑 `verify-kb.sh` 应 `KB OK`，退出 0。
8. 抽查 `api-contract.md` 关键签名 vs `pragmatic-ddd-core` 源码；一致则更新「派生时间」，不一致则钉死修正。
9. 更新 `kb/README.md`（版本 + 目录表补 kafka-config）与顶层 `README.md` 的 `kb_version` 与状态。

## 5. 验收标准

- [ ] `verify-kb.sh` 退出 0（`KB OK`），无 STALE 卡。
- [ ] `kb/bp/` 含 24 张 bp 卡（原 23 + 新增 kafka-config）。
- [ ] `_manifest.json` 的 `cards` 含 `bp.kafka-config`，全部 `source_sha` 已刷新。
- [ ] 3 张严重卡不再含错误写法（`boolean` 返回 / `rocketmq.*` 前缀）；aggregate-design 骨架构造已并列 `markCreated()`（审计）与 `markNew()`（新建路由，非废弃）。
- [ ] `api-contract.md` Tier-1/Tier-2 与当前 `pragmatic-ddd-core` 源码一致。
- [ ] `kb/README.md` 与顶层 `README.md` 版本号与目录一致。

> 本计划仅重建 KB 派生物（`kb/`），不改动 `documentation/best-practices/` 权威源与框架源码；所有卡均从当前权威源重新蒸馏。
