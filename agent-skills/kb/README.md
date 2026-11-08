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
- 本版为**全量草稿**：35 篇 bp 卡 + 硬约束 + API 契约（共 37 张，以 `_manifest.json` 为准）。
- **KB 自足**：本目录是完整知识体，**不依赖仓库内任何示例工程**，第三方可独立使用（见 [§6](#6-自足性与发布范围)）。

## 2. 目录（35 篇 bp 卡 + 2 张顶层卡）

| 路径 | 内容 | 被哪些 Skill 读 |
| --- | --- | --- |
| `hard-constraints.md` | 防跑偏硬约束（条数以本卡为准）+ 出入参与投影层类型边界规则（E1~E4：E4 投影字段禁用领域类型） | 全部（尤其 S4 / S7） |
| `api-contract.md` | 框架真实类/接口契约，防幻觉符号 | 全部（生成代码前必读） |
| `bp/aggregate-design.md` | 聚合设计原则（小聚合、父类能力、Lombok、充血方法） | S4 / S7 |
| `bp/aggregate-structure.md` | 聚合四层目录骨架、按图索骥、**类类型 → 包落点判据表（唯一权威）** | S1 / S2 / S4 / S5 / S6 / S7 |
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
| `bp/aggregate-database-design.md` | 聚合数据库设计（两层判定：表边界 + 值对象落列 A/B；标识两模式：自增即 entityId / 代码生成 + 唯一索引） | S3 / S4 / S7 / ddd-design-doc |
| `bp/projection-design.md` | 投影读模型四角色与硬约束 | S6 / S7 |
| `bp/projection-api.md` | 投影基类可编译方法签名（零上下文场景必读） | S6 / S7 |
| `bp/broadcast.md` | 广播事件（聚合间一写多读，仅最终一致） | S5 / S7 |
| `bp/dependency-declaration.md` | 外部依赖声明（`@ExternalDependency` 三档分类 + 契约/网关） | S4 / S5 / S7 |
| `bp/design-doc-template.md` | 设计文档标准（**流程权威**：一轮四阶段 / 落地核对 / 冻结 / 轮次判定 / 回流矩阵 / 一致性校验 / ④ 在流程中的位置） | ddd-design-doc / ddd-kb-consult |
| `bp/design-doc-semantics.md` | 业务语义（契约：8 章 + 3 附录，含增量写法与稿次规则） | ddd-design-doc / ddd-kb-consult |
| `bp/design-doc-write-template.md` | 写聚合设计文档（11 节，含关系表持久化，主文档统管横切） | S3 / S4 / S5 / S7 / ddd-design-doc |
| `bp/design-doc-read-template.md` | 读投影设计文档（6 节，含三读源：ES / Redis / 数据库直读） | S6 / S7 / ddd-design-doc |
| `bp/design-doc-prerequisites.md` | 实施前置条件（项目级 10 项 + 阻断映射） | ddd-design-doc / S3 / S4 / S6 |
| `bp/design-doc-accepted.md` | ④ 判决名单（三份契约之外唯一长期产物，只增不改、永不归档） | ddd-design-doc / S4 / S5 / ddd-kb-consult |
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
- **S2 聚合骨架** → `aggregate-structure`(目录骨架 + 落点判据表) + `application-collaboration`(命名) + `hard-constraints`(9)。
- **S3 基础设施配置** → `repository-design` + `aggregate-database-design` + `design-doc-write-template`(§7 持久化) + `design-doc-prerequisites`(数据库/总线版本) + `projection-design` + `event-subscriber-pattern` + `hard-constraints`(9) + `mysql-config` / `postgresql-config` / `mybatis-config` / `outbox-config` / `rocketmq-config` / `kafka-config` / `elasticsearch-config` / `event-manager-config`。
- **S4 聚合创建** → 全量 bp + `hard-constraints` + `api-contract`；**每个新类的包落点查 `aggregate-structure` 落点判据表（硬约束 15）**。
- **S5 聚合扩展** → 对应 bp（操作→operation-registry/order-rule/event-modeling；校验→domain-service；订阅→event-subscriber-pattern；子实体→entity-design/value-object；投影源→projection-design）+ `hard-constraints`；新增类同查 `aggregate-structure` 落点判据表。
- **S6 投影创建** → `projection-design`(全量) + `projection-api`(全量) + `event-subscriber-pattern` + `enum-value`(E3) + `hard-constraints`。
- **S7 合规评审** → `hard-constraints` 全量 + 相关 bp（含 `code-comment-specification` 的注释核对；包落点约束 15 的判据取 `aggregate-structure`） + `api-contract`。
- **KB 咨询（ddd-kb-consult）** → 按用户问题路由到对应 `bp/*.md` + `hard-constraints` + `api-contract`；只读卡作答，不产代码。
- **④ 判决名单读法**：任何涉及一个已有聚合的需求/扩展，开场先读 `bp/design-doc-accepted.md` §7 的时机表，命中已有判决不重议；目标文件在**目标工程**的 `docs/design/aggregates/{agg}/accepted.md`，不在本包内。
- 任何卡内出现框架符号拿不准 → 先 Read `api-contract.md`（权威符号表，含全限定 import 与精确方法签名），禁止臆造。

## 4. 溯源与派生纪律

- 每张卡头部标注：`源文档 | 派生时间 | 框架版本`。
- 改框架约定 → 先改 `documentation/best-practices/` 源文档 → 重派生对应卡；**不反向改卡**。
- `_manifest.json`：每条记录 `source_kind`（`file` / `code-tree`）与源/产物 `sha256`。
  - `file`：单文件源（绝大多数 bp 卡），源文档 sha 直接比对。
  - `code-tree`：源是代码/pom 而非单文件（`api-contract`、`bp/projection-api`），取「有序文件列表逐文件 sha256 → 汇总 sha256」比对；框架代码或 pom（含 groupId / 版本）变更即标 STALE，提醒重验签名与坐标。
- CI 卡口 `../scripts/verify-kb.sh` 报三类：`STALE`（源变了）/ `BROKEN`（登记的源找不到）/ `PRODUCT DRIFT`（卡被手改）。三者均退出码 1。
- **卡内互引只写卡名 + 主题，不写小节号**：卡是对源文档的蒸馏重写，小节号常与源不一致（历史坑：`aggregate-structure` 曾指向 `aggregate-design §3.4`，而卡内参数对象在 §3.2，循指针必落空）。
- **单一主题的权威表只允许一份**：包落点判据表只在 `bp/aggregate-structure.md`，其他卡与 Skill 只指向不复制（双份清单必然漂移）；卡头「源」里没登记的节不得写进卡——卡比源多内容时哈希卡口查不出来，下次重蒸馏时会被静默冲掉。

## 5. 防幻觉红线

- KB 中出现的类/方法签名以 `api-contract.md` 为**唯一权威**（含全限定 import 与精确方法签名）。
- 第三方使用时**禁止**访问或假设仓库内示例工程的存在（见 [§6](#6-自足性与发布范围)）；缺符号先查 `api-contract.md`，再向用户提问。
- Skill 生成代码前，对任一不确定签名必须先 Read 核实，再写。
- 注册表类必须 `public`、聚合根禁用 `@Data`、参数对象落 `param/` 等硬约束见 `hard-constraints.md`，逐条核对（条数以该卡为准，不写死数字）。

## 6. 自足性与发布范围

**结论：KB 是自足的知识体，第三方拿走 `kb/` + 各 `ddd-*/SKILL.md` 即可工作。**

| 是否属于 KB 发布范围 | 对象 | 说明 |
| --- | --- | --- |
| ✅ 属于 | `kb/**` 全部内容（`hard-constraints.md` / `api-contract.md` / `bp/*.md` / `_manifest.json`） | 唯一权威知识源，可直接分发给第三方 |
| ❌ 不属于 | 仓库内示例工程 `examples/order-example/` | 仅作**仓库内** A4/A5 回归基线（见仓库内走查方案 `A4-A5-walkthrough.md`，不入发布包），第三方环境不存在，**KB 与 Skill 不得引用** |

**为什么移除 example 依赖（2026-09-26 决策）**：

1. **第三方必然阻断**：原 `kb/examples/order/README.md` 指向 `examples/order-example/`，该目录不在 KB 发布范围内；再叠加本 README 的 fail-fast 纪律（找不到 KB 即停止生成），会在第三方环境直接停摆。
2. **`Order.java`（verbatim）污染包名**：其 `package io.pragmatic.ddd.example.order.*` 与同名 import 会被照抄，产生不存在的包。
3. **实验证据**：仓库内 A4/A5 走查方案 §7.2 的零上下文子 agent 在**禁读** `examples/order-example` 的前提下，KB v1.2 下 5 个 Tier-2 易错文件全部写对、`mvn compile` EXIT=0——证明闭合缺口的是 `api-contract.md` 与 `bp/projection-api.md`，而非 verbatim 示例。

**占位约定**：`bp/*.md` 中出现的 `Order` / `OrderRule` 等标识符是**最佳实践文档的范式载体**（源自 `documentation/best-practices/`），生成时须替换为目标聚合名；它们不代表任何仓库内真实示例工程文件。

---

> **版本**：v3.0
> **变更**：v3.0（2026-11-08）**投影领域类型解耦入 KB** —— `hard-constraints` E 表新增 E4（投影读模型字段——含嵌套子投影静态类/行投影/查询条件族/跨聚合子投影——禁用领域值对象/子实体/聚合根类型，一律展开扁平基础类型；判定方法 + 存量降级仿约束 15）；`kb.hard-constraints` 源登记扩为 12 篇（追加 `projection-design`）使卡⊂源成立；源侧 `bp/projection-design` 四处、`design-doc-read-template` 三处内嵌补句；`bp.projection-design` / `bp.projection-api` / `bp.design-doc-read-template` 重蒸馏；技能侧「E1~E3」硬编码统一为「E 系列」，S6/S7 补 E4 核对；E4 覆盖条件族/行投影/跨聚合子投影，无豁免（2026-11-08 确认）。见 `docs/design/agent-skills/projection-domain-type-decoupling-v01.md`。v2.9（2026-11-05）**应用服务层与订阅侧约束入 KB** —— 硬约束新增 16（Write/Read 落 `application/{agg}/` 根层、`service/` 只放 `@DomainService` 契约实现、两服务互不注入、写侧禁投影源 / 读侧禁写仓储与 `IEventManager`）与 17（订阅三要素齐备、`subscriber/` 只放注册表、同聚合绑定）；落点判据表 13 → 25 行（补应用层 5 类 + 实现层 4 类 + `requirement/` `projection/query/`），骨架树补 4 个目录并去掉 `service/` 的 `@Service` 断言；`kb.hard-constraints` 源登记改绑为 11 篇 best-practices 的 code-tree 集合（不再指向 `docs/design/ai/` 过程文档）；重蒸馏 `bp.aggregate-structure` / `bp.application-collaboration` / `bp.event-subscriber-pattern` / `bp.domain-service`；源侧 6 处内嵌补句 + 6 行反模式，未新增约束专章。v2.8（2026-11-02）**包落点入硬约束** —— `bp/aggregate-structure` 新增「类类型 → 包」落点判据表（双条件：类型 + 所在层），成为包落点唯一权威；`bp/aggregate-design` §1 残缺包结构树删除并改指向该表（消除双源，同时补齐 `event/`、`model/enums/`、`model/valueobject/` 等同类空洞）；`hard-constraints.md` 与其源文档新增约束 15（参数对象 → `domain/{agg}/param/`，不落 `model/`；Input 即使实现 `IParamObject` 也不入领域层；存量降级为 Advisory）；S2/S4/S5/S7 的包结构依据与读单改指 `aggregate-structure`；硬约束条数文案解除「14 条」硬编码；卡内互引不再带小节号。详见 `docs/design/agent-skills/kb-package-landing-closure-v01.md`。v2.7（2026-11-02）**标识两模式入卡** —— `bp/aggregate-database-design` 重蒸馏（源 commit `4fcc562`）：§5 主键与标识改为模式一（`{聚合名}_id` 自增主键直接充当 `entityId`，ID 后生成）/ 模式二（物理 `id` 自增主键 + `{聚合名}_id` 唯一索引，ID 代码生成）两选，明确逻辑外键不受模式影响；§6 补「标识模式决定整存整取顺序」（模式一先主后子靠 `useGeneratedKeys` 回填）；`api-contract` 坐标段补红线「仓库 pom 为开发版 `2.2.0-SNAPSHOT`，不得写入消费方坐标」；`ddd-infra-config` §3.4 读卡章节号 `§4`→`§5` 并要求先与用户确认标识模式。v2.6（2026-11-01）**坐标重验 + ④ 入 KB + 卡口硬化** —— `api-contract` Maven 坐标改为真实值 `com.pragmatic-ddd:*:2.1.0`（已发 Central）并声明 `groupId ≠ 包前缀`、artifactId 以 BOM 为唯一权威；清除不存在的 `pragmatic-ddd-outbox` 模块名；新增 `bp/design-doc-accepted`（v2.5 新增的源当时未收口，现补齐源↔卡↔技能三层）；`verify-kb.sh` 区分 STALE / BROKEN / PRODUCT DRIFT 并支持 `code-tree` 源哈希，不再静默跳过缺源；卡数口径按实际修正。v2.5（2026-11-01）**一轮三件套 + 轮次** —— 取消 `bp.design-doc-facts`（现状以代码为准）；文件名加 `-V{NN}`、不再覆盖写；三件套固定产出（取消点单）；落地即冻结（只允许文字勘误与 §10.6 实施备注）；轮次判定三条判据 + 拿不准必问；新增「glob + 取最大 V」解析规则。v2.4（2026-10-29）交付节奏改为闸门制——语义文档多轮迭代后定稿（G1/G2），②③ 由用户点单（G3/G4）；新增回流矩阵（下游发现业务口径缺失先回 ① 再逐级对齐）与三份一致性校验；新增 `bp.design-doc-template` 为流程权威。v2.2（2026-10-29）文档体系改为「三契约 + 一快照」。v2.1（2026-10-29）设计文档体系改为三层（语义 → 写 → 读）。v2.0（2026-10-28）PostgreSQL 支持入 KB。更早变更见 `_manifest.json` 的 note
> **适用框架版本**：`2.1.0`——**以 `_manifest.json` 的 `framework_version` 为单一权威**；各卡头部的「框架版本」记录的是**派生当时**对应的版本，不随整体适用版本回填
