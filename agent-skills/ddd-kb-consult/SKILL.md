---
name: ddd-kb-consult
description: 咨询入口（只读答疑，不生成代码）——分两条路：**怎么用 / 用哪个**读 `USAGE.md`；**为什么这么设计 / 怎么配**读 `kb/` 卡。当用户问「这套 skill 怎么用」「我该从哪个开始」「设计文档分几层、语义文档怎么写」「生成代码前要确认哪些前提」「pragmatic-ddd 里 xxx 怎么设计/为什么这样/怎么配」「聚合怎么拆分」「RocketMQ 还是 Kafka 怎么选」「规则容器/操作注册表/规则注册表怎么写」「枚举出入参为什么用 Integer」「Outbox 怎么配」等问题时触发。强制先 Read 对应文档/卡再作答，禁止凭记忆臆造；拿不准的框架签名以 `kb/api-contract.md` 为唯一权威。
---

# ddd-kb-consult（KB 咨询 / 答疑）

> 类型：咨询类（只读，不产代码）
> 版本：v1.2 · 适用框架：2.1.0-SNAPSHOT
> 共享知识：`../USAGE.md`（使用指引）+ `../kb/`（规范卡）

## 1. 定位

本 Skill 是**唯一咨询入口**，分两条路：

| 用户问什么 | 读什么 | 回答什么 |
| --- | --- | --- |
| 这套东西怎么用、我该用哪个、四份文档怎么协作、说完话会发生什么 | **`../USAGE.md`** | 指向具体 Skill 与产出 |
| 为什么这么设计、规则容器为什么读 old、Outbox 怎么配 | **`kb/` 卡** | 带卡出处的规范解释 |

它不写代码、不改文件，只解释与指路。名字中的 `kb` 沿用旧称，职责已扩到使用指引。

## 2. 工作流程

1. **判型**：
   - 问「怎么用 / 用哪个 / 流程怎么走 / 我该准备什么」→ 走 **USAGE 分支**。
   - 问「为什么 / 怎么设计 / 怎么配」→ 走 **KB 分支**。
   - 两者都沾（如「设计文档怎么写」）→ 先 USAGE 定位产物与步骤，再用 KB 卡补规范细节。
2. **USAGE 分支**：Read `../USAGE.md`，按 §1「我现在的情况 → 用哪个」或 §5「常用说法对照」作答，给出 Skill 名 + 会拿到什么 + 前置要求（如需 ②③）。
3. **KB 分支**：**先 Read 再答**：对本任务涉及的每张卡执行 Read；涉及框架类/方法签名时额外 Read `kb/api-contract.md`。
4. **基于内容作答**：引用卡内的范式、约束、反模式表；每条结论标明来源（如「见 `bp/order-rule-pattern.md` §4.1」或「见 `USAGE.md` §3」）。
5. **跨卡组合**：问题跨多主题时组合多卡并指明各自边界（如「Kafka 还是 RocketMQ」需读 `kafka-config` + `rocketmq-config` + `event-manager-config`）。
6. **未覆盖** → 明确说「文档/卡未覆盖该点」，建议查 `pragmatic-ddd-core` 源码或提 issue；**不臆造**。

## 3. 路由速查（问题 → 文档 / 卡）

| 用户问题 | 读哪张卡 |
| --- | --- |
| 这套 skill 怎么用、我该从哪个开始、说句话会触发什么 | **`USAGE.md`**（非 KB 卡） |
| 聚合怎么拆、充血方法、Lombok、父类能力 | `bp/aggregate-design` |
| 聚合四层目录骨架 | `bp/aggregate-structure` |
| 子实体设计 | `bp/entity-design` |
| 值对象（不可变、equalityComponents） | `bp/value-object` |
| 枚举值对象 `IEnumValue`、出入参为什么用 Integer | `bp/enum-value` + `hard-constraints`(E1~E3) |
| 操作注册表 `OperationRegistry` | `bp/operation-registry-design` |
| 规则注册表 `BrokenRuleRegistry` | `bp/registry-design` |
| 规则容器 `EntityRule`/`OrderRule`、`RuleCheckResult` | `bp/order-rule-pattern` |
| 领域事件建模 | `bp/event-modeling` |
| 事件订阅（显式注册） | `bp/event-subscriber-pattern` |
| 领域服务四分类 | `bp/domain-service` |
| 应用层 WriteService 编排、工作单元 | `bp/application-collaboration` |
| 仓储、TrackedList、乐观锁 | `bp/repository-design` |
| 聚合数据库设计、建表 SQL | `bp/aggregate-database-design` |
| 投影读模型四角色 | `bp/projection-design` |
| 投影基类签名（零上下文必读） | `bp/projection-api` |
| 广播事件（一写多读） | `bp/broadcast` |
| 外部依赖声明、ACL 网关 | `bp/dependency-declaration` |
| 设计文档三层关系、该产出哪几份 | `bp/design-doc-semantics` + `bp/design-doc-write-template` + `bp/design-doc-read-template` + `bp/design-doc-facts`（总说明见 `documentation/best-practices/design-doc-template.md`） |
| 聚合业务语义文档怎么写（8 章 + 3 附录、增量写法） | `bp/design-doc-semantics` |
| 聚合写设计文档怎么写（11 节） | `bp/design-doc-write-template` |
| 投影读设计文档怎么写（6 节、三读源） | `bp/design-doc-read-template` |
| 聚合当前实际是什么、与语义哪里不一致 | `bp/design-doc-facts` |
| 生成代码前必须确认什么、未确认怎么阻 | `bp/design-doc-prerequisites` |
| ES 配置 | `bp/elasticsearch-config` |
| 事件管理器选型（Kafka/RocketMQ/ThreadPool） | `bp/event-manager-config` |
| MySQL 配置（数据源 / 事务） | `bp/mysql-config` |
| MyBatis 配置（会话工厂 / TypeHandler / 框架固定 Mapper） | `bp/mybatis-config` |
| Outbox 配置 | `bp/outbox-config` |
| RocketMQ 配置 | `bp/rocketmq-config` |
| Kafka 配置 | `bp/kafka-config` |
| 14 条硬约束 / 枚举出入参规则 | `hard-constraints` |
| 任何框架类/方法签名 | `api-contract` |

## 4. 防幻觉红线

- **先 Read 后答**：未读对应卡前不作答；签名以 `api-contract.md` 为唯一权威（含全限定 import + 精确方法签名）。
- **KB 自足**：不引用仓库内任何示例工程（`examples/*` 不属于 KB 发布范围）来佐证。
- **标明出处**：每条规范/范式结论附来源卡路径与章节，便于用户溯源。

## 5. 验收

- 答案可追溯到至少一份文档：USAGE 类问题到 `USAGE.md` 具体节，规范类问题到 KB 卡。
- 使用类问题必须给出「Skill 名 + 产出 + 前置要求」，不能只给概念解释。
- 涉及签名的问题，结论与 `api-contract.md` 一致。
- 无凭记忆臆造的接口/配置。
