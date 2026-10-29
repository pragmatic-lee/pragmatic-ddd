---
name: ddd-aggregate-create
description: 按 Pragmatic DDD 规范从零生成一个完整聚合：聚合根（充血方法 + initData）、枚举值对象、操作注册表、规则注册表、规则容器、领域事件、参数对象、应用层 WriteService/Factory/Updater/Input、仓储抽象。当用户说「生成 {agg} 聚合」「写个订单聚合」「帮我建 Order 的领域模型和业务方法」时触发。严格遵循小聚合、父类能力、Lombok 仅 Getter/Setter(PROTECTED)、先 recordOperation 后 collectEvent、注册表类 public 等硬约束。
---

# ddd-aggregate-create（聚合创建，核心 Skill）

> 类型：S4（创建类，核心）
> 版本：v2.0 · 适用框架：2.1.0-SNAPSHOT
> 共享知识：`../kb/`（只读引用，见 `../kb/README.md` 路由表）

## 前置

- **必须有 ② 聚合写设计文档**：`docs/design/changes/active/{需求}/{agg}/aggregate-design.md`（已归档则读 `changes/archive/{需求}/{agg}/aggregate-design.md`）。类清单、操作码、规则、事件、DDL、执行器选型全部从它来。
- **无 ② 时不得自行推导语义**，先提示用户跑 `ddd-design-doc`（产 ①②③），再回来生成代码。
- 迭代场景另读 ④ `docs/design/aggregates/{agg}/facts.md` §3，确认当前实际类与表，避免与既有代码冲突。
- **前置条件清单**：执行器选型、数据源、总线、Outbox 等项未确认时，对应部分不生成（见 `bp.design-doc-prerequisites`）。
- 必须先有 S2 产出的目录骨架（或用户已有包结构）。无骨架时先建议跑 S2。
- 生成前 Read 知识包：`kb/hard-constraints.md`、`kb/api-contract.md`、`kb/bp/aggregate-design.md`、`kb/bp/operation-registry-design.md`、`kb/bp/registry-design.md`、`kb/bp/order-rule-pattern.md`、`kb/bp/event-modeling.md`、`kb/bp/enum-value.md`、`kb/bp/application-collaboration.md`、`kb/bp/repository-design.md`。
- 拿不准任何签名 → 先 Read `kb/api-contract.md`（含全限定 import + 精确方法签名）；骨架/范式类疑问 Read 对应 `kb/bp/*.md`。**KB 自足，不依赖任何外部示例工程**，禁止臆造。

## 执行步骤（五步生成）

### 步骤 1：读上游文档（不自行澄清语义）

| 读 ② 的哪节 | 用来定什么 |
| --- | --- |
| §2.2 类清单 / §2.1 包布局 | 要生成哪些文件、放哪个包 |
| §3.1 操作清单 / §3.2 注册表 | 操作码常量与充血方法清单 |
| §4.4 覆盖对照 | 每条规则的编号、消息码、激活条件、判定基准 |
| §5.1 事件清单 | 事件类与载荷字段 |
| §6.1–§6.5 | Input / 工厂 / Updater / 写服务 / 规则装配 |
| §6.4 写服务（含执行器选型表） | 用哪个执行器（普通 / Outbox / 可补偿） |
| §6.7 外部依赖与补偿 | 需求声明与补偿动作 |
| §7.2 DDL / §7.3 映射 | 表结构与 TypeHandler |
| §10.4 框架签名 | 逐项核实后再写代码，不臆造 |

文档缺项时的处理：回 ② 补，不在生成阶段“设计” —— 生成阶段的职责是把已定结构落成代码。

### 步骤 2：生成领域模型（model/）

- **按 ② §2 骨架逐个落文件，不改结构**：类清单少一个或多一个都要回文档核对。
- 聚合根 `{Agg} extends AggregateRoot<ID>`，Lombok 仅 `@Getter` + `@Setter(AccessLevel.PROTECTED)` + `@NoArgsConstructor(AccessLevel.PROTECTED)`（硬约束 8）。
- 状态用枚举（领域内部 `int` code），见 `kb/bp/enum-value.md`；**出入参层字段按 `kb/hard-constraints.md` E1~E3 用 `Integer`/`String`**。
- `initData` 模式：`new {Agg}({Agg}InitData, id)`，构造只赋值 + `markCreated()`（审计时间戳 createdAt/updatedAt）+ `markNew()`（新建路由标志 isNew，仓储据此 INSERT）+ 延迟创建事件（硬约束 6）；两个标记职责不同，新建构造都要调。
- 一对多用 `TrackedList<{Sub}, ID>`。
- 充血方法：纯赋值、先做对外校验再赋值、无 `if+throw` 守卫（硬约束 4）；**先 `recordOperation` 后 `collectEvent`**（硬约束 5）。
- 两个 `protected` 方法：`brokenRuleRegistry()`（不可返回 null）、`operationRegistry()`。

### 步骤 3：生成注册表 / 规则 / 事件

- 操作注册表 `{Agg}OperationRegistry extends OperationRegistry`：`public` 子类 + `private` 构造 + `INSTANCE` + `EntityOperation.of(code, desc)`；常量声明在最终子类，`code` 全大写下划线无前缀（硬约束 1、§5）。
- 规则注册表 `{Agg}BrokenRuleRegistry extends BrokenRuleRegistry`：`public` 子类 + `INSTANCE` + `MessageCode.of(localCode, desc)`；`localCode` 唯一且与常量名一致（硬约束 1、2）。
- 规则容器 `{Agg}Rule extends EntityRule<{Agg}>`：无状态，`addRule(ICheckRule, MessageCode[, IActiveRuleCondition])`；激活条件写第三参（见 `kb/bp/order-rule-pattern.md`）。
- 领域事件 `{Agg}{Op}Event extends BaseDomainEvent`：过去式命名、`buildEvent` 工厂、只带聚合 ID + 少量路由 ID、禁用 `@Data`/`@Builder`（硬约束 7、8）；延迟事件 `() -> XxxEvent.buildEvent(this)`（硬约束 6）。

### 步骤 4：生成应用层 + 仓储抽象

- `input/`：每个操作独立 `{Action}Input`（硬约束 10）。
- `factory/{Agg}Factory implements EntityFactory<{Agg}, Create{Agg}Input>`：先算后赋。
- `updater/{Agg}{Action}Updater implements EntityUpdater<{Agg}, {Action}Input>`：只转换 + 调充血方法，不校验/不持久化/不发事件（硬约束 11）。
- `rule/{Agg}RuleConfig`：`@Configuration` 装配 `new {Agg}Rule(契约...)`。
- `{Agg}WriteService extends AbstractApplicationService implements ICommandApplicationService`：显式注入执行器 + `Supplier<IUnitOfWork>`；`execute(agg, rule, repo, Consumer<T>)`。
- `repository/Abstract{Agg}Repository extends AbstractRepository<ID, {Agg}>`（领域层抽象，不实现 final 方法）。
- 仓储基础设施实现、SQL、Controller、投影/MQ 装配：**本版仅占位提示**，交付「待补事项」。

### 步骤 5：自检（逐条核对）

- 读 `kb/hard-constraints.md` 14 条 + E1~E3，逐条核对。
- 编译验证：`mvn compile`。
- 输出「待补事项」清单：仓储 SQL、Controller、投影/MQ 装配、单测。

## 防幻觉红线

- 任何框架类/方法签名不确定 → 先 Read `kb/api-contract.md`（权威符号表，含全限定 import）或对应主题卡 `kb/bp/*.md`，禁止臆造。
- 注册表类必须 `public`；聚合根禁用 `@Data`；`operationRegistry()` 返回非 null 即启用操作体系。

## 验收（对照路线图 §8）

- A2 `mvn compile` 通过；A3 硬约束 14 条 + E1~E3 零违反、零幻觉符号。
- A4 基准回归（**仅仓库内验证**，第三方无需执行）：用本 Skill 从零重建 `examples/order-example` 的 Order 聚合，逐文件 diff 与 `examples/order-example` 结构一致。该示例工程属本仓库，**不属于 KB 发布范围**。
