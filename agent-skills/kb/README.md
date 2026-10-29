# 知识包（KB）索引

- [1. 定位](#1-定位)
- [2. 目录](#2-目录)
- [3. 路由表（Skill 如何找卡）](#3-路由表skill-如何找卡)
- [4. 溯源与派生纪律](#4-溯源与派生纪律)
- [5. 防幻觉红线](#5-防幻觉红线)
- [6. 自足性与发布范围](#6-自足性与发布范围)

## 1. 定位

本目录是 Pragmatic DDD 全部 AI Skill 的**唯一共享知识源（单物理副本，只读引用）**。Skill 只写「流程 + 知识索引」，具体命名、骨架、约束全部指向本目录，不内联重复。

- 卡从 `documentation/best-practices/`（VitePress 文档，git 内）派生。
- 本版为**全量草稿**：35 篇 bp 卡 + 硬约束 + API 契约。
- **KB 自足**：本目录是完整知识体，**不依赖仓库内任何示例工程**，第三方可独立使用（见 [§6](#6-自足性与发布范围)）。

## 2. 目录（35 篇 bp 卡）

| 路径 | 内容 | 被哪些 Skill 读 |
| --- | --- | --- |
| `hard-constraints.md` | 14 条防跑偏硬约束 + 枚举出入参规则 | 全部（尤其 S4 / S7） |
| `api-contract.md` | 框架真实类/接口契约，防幻觉符号 | 全部（生成代码前必读） |
| `bp/aggregate-design.md` | 聚合设计原则（小聚合、父类能力、Lombok、充血方法） | S4 / S7 |
| `bp/aggregate-structure.md` | 聚合四层目录骨架与按图索骥 | S1 / S2 / S7 |
| `bp/entity-design.md` | 普通实体（子实体）设计 | S4 / S5 / S7 |
| `bp/value-object.md` | 值对象（不可变、equalityComponents、禁 @Data） | S4 / S5 / S7 |
| `bp/enum-value.md` | 枚举值对象 `IEnumValue` | S4 / S6 / S7 |
| `bp/operation-registry-design.md` | 操作注册表 `OperationRegistry` | S4 / S5 / S7 |
| `bp/registry-design.md` | 规则注册表 `BrokenRuleRegistry` | S4 / S5 / S7 |
| `bp/order-rule-pattern.md` | 规则容器 `EntityRule` 子类范式 | S4 / S5 / S7 |
| `bp/event-modeling.md` | 领域事件建模 | S4 / S5 / S7 |
| `bp/event-subscriber-pattern.md` | 事件订阅领域服务（显式注册） | S5 / S6 / S7 |
| `bp/domain-service.md` | 领域服务四分类与落地 | S4 / S5 / S7 |
| `bp/application-collaboration.md` | 应用层 WriteService 编排 | S4 / S7 |
| `bp/code-comment-specification.md` | 代码注释规范（实体字段 / 业务方法 / Input 类与字段） | S4 / S5 / S7 |
| `bp/repository-design.md` | 仓储设计（AbstractRepository / TrackedList / 乐观锁） | S4 / S3 / S7 |
| `bp/aggregate-database-design.md` | 聚合数据库设计（两层判定：表边界 + 值对象落列 A/B） | S3 / S4 / S7 / ddd-design-doc |
| `bp/projection-design.md` | 投影读模型四角色与硬约束 | S6 / S7 |
| `bp/projection-api.md` | 投影基类可编译方法签名（零上下文场景必读） | S6 / S7 |
| `bp/broadcast.md` | 广播事件（聚合间一写多读，仅最终一致） | S5 / S7 |
| `bp/dependency-declaration.md` | 外部依赖声明（`@ExternalDependency` 三档分类 + 契约/网关） | S4 / S5 / S7 |
| `bp/design-doc-template.md` | 设计文档标准（**流程权威**：四道闸门 / 迭代轮次 / 回流矩阵 / 一致性校验） | ddd-design-doc / ddd-kb-consult |
| `bp/design-doc-semantics.md` | 本次需求语义（契约：8 章 + 3 附录，含增量写法与迭代轮次） | ddd-design-doc / ddd-kb-consult |
| `bp/design-doc-write-template.md` | 聚合写设计文档（11 节，含关系表持久化，主文档统管横切） | S3 / S4 / S5 / S7 / ddd-design-doc |
| `bp/design-doc-read-template.md` | 投影读设计文档（6 节，含三读源：ES / Redis / 数据库直读） | S6 / S7 / ddd-design-doc |
| `bp/design-doc-prerequisites.md` | 实施前置条件（项目级 10 项 + 阻断映射） | ddd-design-doc / S3 / S4 / S6 |
| `bp/design-doc-facts.md` | 聚合事实文档（快照：6 节、与语义偏离、从代码推导） | ddd-design-doc / S7 / ddd-kb-consult |
| `bp/elasticsearch-config.md` | ES 配置（RestClient 单 Bean、投影写入、限流） | S3 / S7 |
| `bp/event-manager-config.md` | 事件管理器三选一（Kafka/RocketMQ/ThreadPool）装配 | S3 / S7 |
| `bp/mysql-config.md` | MySQL 配置（数据源、连接池、事务管理器） | S3 / S7 |
| `bp/mybatis-config.md` | MyBatis 配置（SqlSessionFactory、TypeHandler 装配、框架固定 Mapper） | S3 / S7 |
| `bp/outbox-config.md` | Outbox 配置（MybatisOutboxStore + Relay） | S3 / S7 |
| `bp/rocketmq-config.md` | RocketMQ 配置（应用级装配、前缀 `pragmatic-ddd.event.rocketmq.*`） | S3 / S7 |
| `bp/kafka-config.md` | Kafka 配置（应用级装配、三选一之一、前缀 `pragmatic-ddd.event.kafka.*`） | S3 / S7 |
| `bp/postgresql-config.md` | PostgreSQL 配置（方言 XML、`databaseId`、建表脚本、JSON 列策略） | S3 / S7 |
| `bp/aggregate-identification.md` | 聚合识别（事件风暴四步，从需求识别聚合） | ddd-design-doc / ddd-kb-consult |
| `bp/aggregate-evolution-design.md` | 聚合演进设计（改动清单、三层根因、版本取舍） | ddd-design-doc / ddd-kb-consult |

## 3. 路由表（Skill 如何找卡）

- **S1 项目初始化** → `aggregate-structure` + `hard-constraints`。
- **S2 聚合骨架** → `aggregate-design`(§1) + `application-collaboration`(§1) + `hard-constraints`(9)。
- **S3 基础设施配置** → `repository-design` + `aggregate-database-design` + `design-doc-write-template`(§7 持久化) + `design-doc-prerequisites`(数据库/总线版本) + `projection-design` + `event-subscriber-pattern` + `hard-constraints`(9) + `mysql-config` / `postgresql-config` / `mybatis-config` / `outbox-config` / `rocketmq-config` / `kafka-config` / `elasticsearch-config` / `event-manager-config`。
- **S4 聚合创建** → 全量 bp + `hard-constraints` + `api-contract`。
- **S5 聚合扩展** → 对应 bp（操作→operation-registry/order-rule/event-modeling；校验→domain-service；订阅→event-subscriber-pattern；子实体→entity-design/value-object；投影源→projection-design）+ `hard-constraints`。
- **S6 投影创建** → `projection-design`(全量) + `projection-api`(全量) + `event-subscriber-pattern` + `enum-value`(E3) + `hard-constraints`。
- **S7 合规评审** → `hard-constraints` 全量 + 相关 bp（含 `code-comment-specification` 的注释核对） + `api-contract`。
- **KB 咨询（ddd-kb-consult）** → 按用户问题路由到对应 `bp/*.md` + `hard-constraints` + `api-contract`；只读卡作答，不产代码。
- 任何卡内出现框架符号拿不准 → 先 Read `api-contract.md`（权威符号表，含全限定 import 与精确方法签名），禁止臆造。

## 4. 溯源与派生纪律

- 每张卡头部标注：`源文档 | 派生时间 | 框架版本`。
- 改框架约定 → 先改 `documentation/best-practices/` 源文档 → 重派生对应卡；**不反向改卡**。
- `_manifest.json`：记录源/产物 `sha256`，CI 在源 hash 变化时标卡过期（见 `../scripts/verify-kb.sh`）。

## 5. 防幻觉红线

- KB 中出现的类/方法签名以 `api-contract.md` 为**唯一权威**（含全限定 import 与精确方法签名）。
- 第三方使用时**禁止**访问或假设仓库内示例工程的存在（见 [§6](#6-自足性与发布范围)）；缺符号先查 `api-contract.md`，再向用户提问。
- Skill 生成代码前，对任一不确定签名必须先 Read 核实，再写。
- 注册表类必须 `public`、聚合根禁用 `@Data` 等硬约束见 `hard-constraints.md`，逐条核对。

## 6. 自足性与发布范围

**结论：KB 是自足的知识体，第三方拿走 `kb/` + 各 `ddd-*/SKILL.md` 即可工作。**

| 是否属于 KB 发布范围 | 对象 | 说明 |
| --- | --- | --- |
| ✅ 属于 | `kb/**` 全部内容（`hard-constraints.md` / `api-contract.md` / `bp/*.md` / `_manifest.json`） | 唯一权威知识源，可直接分发给第三方 |
| ❌ 不属于 | 仓库内示例工程 `examples/order-example/` | 仅作**仓库内** A4/A5 回归基线（见 `../A4-A5-walkthrough.md`），第三方环境不存在，**KB 与 Skill 不得引用** |

**为什么移除 example 依赖（2026-09-26 决策）**：

1. **第三方必然阻断**：原 `kb/examples/order/README.md` 指向 `examples/order-example/`，该目录不在 KB 发布范围内；再叠加本 README 的 fail-fast 纪律（找不到 KB 即停止生成），会在第三方环境直接停摆。
2. **`Order.java`（verbatim）污染包名**：其 `package io.pragmatic.ddd.example.order.*` 与同名 import 会被照抄，产生不存在的包。
3. **实验证据**：`../A4-A5-walkthrough.md` §7.2 的零上下文子 agent 在**禁读** `examples/order-example` 的前提下，KB v1.2 下 5 个 Tier-2 易错文件全部写对、`mvn compile` EXIT=0——证明闭合缺口的是 `api-contract.md` 与 `bp/projection-api.md`，而非 verbatim 示例。

**占位约定**：`bp/*.md` 中出现的 `Order` / `OrderRule` 等标识符是**最佳实践文档的范式载体**（源自 `documentation/best-practices/`），生成时须替换为目标聚合名；它们不代表任何仓库内真实示例工程文件。

---

> **版本**：v2.2-draft
> **变更**：v2.4（2026-10-29）交付节奏改为闸门制——语义文档多轮迭代后定稿（G1/G2），②③ 由用户点单（G3/G4）；新增回流矩阵（下游发现业务口径缺失先回 ① 再逐级对齐）与三份一致性校验；新增 `bp.design-doc-template` 为流程权威。v2.2（2026-10-29）文档体系改为「三契约 + 一快照」——语义文档改为本次需求契约（验收后归档）+ 增量写法；新增 `bp.design-doc-facts`（从代码推导、每次实施后覆盖）。v2.1（2026-10-29）设计文档体系改为三层（语义 → 写 → 读）——新增 `bp.design-doc-semantics` / `bp.design-doc-prerequisites` 两张卡；写 / 读模板按新骨架重蒸馏（写 11 节含元素级追溯与实施回填；读 6 节含三读源与澄清机制）；`ddd-design-doc` 工作流从 7 步扩为 10 步。v2.0（2026-10-28）PostgreSQL 支持入 KB——新增 `bp.postgresql-config`；重蒸馏 7 张 STALE 卡；`api-contract.md` 坐标钉死 2.1.0-SNAPSHOT 并补「MyBatis TypeHandler 与方言」段。v1.9（2026-10-28）新增异构数据同步事件契约。v1.7（2026-10-18）全量语义重蒸馏。更早变更见 `_manifest.json` 的 note
> **适用框架版本**：2.1.0-SNAPSHOT
