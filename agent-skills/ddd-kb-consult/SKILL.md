---
name: ddd-kb-consult
description: 咨询 Pragmatic DDD 框架的设计规范与最佳实践（只读 KB 答疑，不生成代码）。当用户问「pragmatic-ddd 里 xxx 怎么设计/为什么这样/怎么配」「聚合怎么拆分」「RocketMQ 还是 Kafka 怎么选」「规则容器/操作注册表/规则注册表怎么写」「枚举出入参为什么用 Integer」「Outbox 怎么配」等解释性问题时触发。强制先 Read 对应 `kb/` 卡再作答，禁止凭记忆臆造；拿不准的框架签名以 `kb/api-contract.md` 为唯一权威。
---

# ddd-kb-consult（KB 咨询 / 答疑）

> 类型：咨询类（只读 KB，不产代码）
> 版本：v1.0-draft · 适用框架：2.0.0
> 共享知识：`../kb/`（只读引用，路由表见 `../kb/README.md` §3；全量卡目见 `../kb/README.md` §2）

## 1. 定位

本 Skill 是 KB 的**咨询入口**：把用户的"为什么/怎么设计/怎么配"问题，路由到正确的 KB 卡，基于卡内容作答。它不写代码、不改文件，只解释规范与给出范式出处。

## 2. 工作流程

1. **识别意图** → 用下方「路由速查」把问题映射到 1~N 张 KB 卡。
2. **先 Read 再答**：对本任务涉及的每张卡执行 Read；涉及框架类/方法签名时额外 Read `kb/api-contract.md`。
3. **基于卡内容作答**：引用卡内的范式、约束、反模式表；每条结论标明来源卡（如「见 `bp/order-rule-pattern.md` §4.1」）。
4. **跨卡组合**：问题跨多主题时，组合多卡并指明各自边界（例如"Kafka 还是 RocketMQ"需同时读 `kafka-config` + `rocketmq-config` + `event-manager-config`）。
5. **KB 未覆盖** → 明确说明"KB 未覆盖该点"，建议查 `pragmatic-ddd-core` 源码（`io.pragmatic.ddd.*`）或提 issue；**不要臆造**。

## 3. 路由速查（问题 → 卡）

| 用户问题 | 读哪张卡 |
| --- | --- |
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
| 设计文档模板（含大纲跳转） | `bp/design-doc-template` |
| ES 配置 | `bp/elasticsearch-config` |
| 事件管理器选型（Kafka/RocketMQ/ThreadPool） | `bp/event-manager-config` |
| MySQL 配置 | `bp/mysql-config` |
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

- 答案可追溯到至少一张 KB 卡；无凭记忆臆造的接口/配置。
- 涉及签名的问题，结论与 `api-contract.md` 一致。
