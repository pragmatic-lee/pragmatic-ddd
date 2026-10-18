# BP 卡：领域服务落地模式（domain-service）

> 源：`documentation/best-practices/domain-service.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

领域服务是**领域层声明、应用层实现的契约接口（端口）**：领域层 `extends IDomainService` 声明"需要什么能力"，应用层 `implements` 提供实现。承载无法归入单一聚合根的逻辑——事件订阅、跨聚合校验、属性计算、领域原语供给。

- 与经典 DDD 差异：形态为「接口（领域层）+ 实现（应用层）」；四类明确分类；跨聚合协作走领域事件（非同步编排多个聚合）；领域层接口零基础设施依赖。
- `@DomainService` 与依赖体系 `@ExternalDependency` 对称：一个声明"需要什么能力"，一个声明"依赖什么外部聚合"。

## 2. 命名与包结构

```text
domain/{agg}/
├── calculator/ 属性计算契约（IOrderTotalAmountCalculator）
├── service/    事件订阅 / 校验规则 / 能力供给契约
└── rule/       规则容器 + 注册表
application/{agg}/
├── service/    领域服务实现（@Service/@Component）
├── resolver/   Command DTO → 领域输入适配
└── factory/    聚合工厂（先算后赋）
```

| 分类 | 领域层接口（`I` 开头） | 实现（去 `I`） | 示例 |
| --- | --- | --- | --- |
| 事件订阅 | `I{事件}{业务意图}Handle/Sender` | `{事件}{业务意图}Handler` | `IOrderDataSyncEsProjectionHandle` |
| 校验规则 | `I{业务对象}{规则意图}Service` | 接口名去 `I` | `IOrderCustomerPermissionService` |
| 属性计算 | `I{结果}Calculator` | 接口名去 `I` | `IOrderTotalAmountCalculator` |
| 能力供给 | `I{产物}Generator/Provider` | 接口名去 `I` | `IOrderIdGenerator` |

> 接口名必须体现业务意图，不得用 `Handler`/`Processor` 作为接口名；接口名本身即领域文档。

## 3. 四类判定（自上而下，命中即止）

| 序 | 检查项 | 归类 | 枚举 |
| --- | --- | --- | --- |
| 1 | 是否 `extends IHandle<T>` | 事件订阅 | `EVENT_SUBSCRIBER` |
| 2 | 方法返回 `RuleCheckResult` | 业务规则 | `BUSINESS_RULE` |
| 3 | `calculate(...)` 由领域输入推导领域输出 | 属性计算 | `ATTRIBUTE_CALCULATOR` |
| 4 | `generate()`/`nextId()` 无/少输入新生产原语 | 能力供给 | `CAPABILITY_PROVIDER` |

> ⚠️ 机器可读的分类**只来自 `@DomainService` 注解**（查找链：实现类 → 所实现接口 → 父类）。未标注则 `category()` 返回 `UNKNOWN`，丢失分类元信息（不影响调用，但依赖分类的扫描/校验无法识别）。

## 4. 落地方式（核心）

通用三步：①领域层定义接口 + 标 `@DomainService(category, description, targetName)` ②应用层 `@Service`/`@Component` 实现 ③按分类接入机制。

- **BUSINESS_RULE**：外部校验契约方法返回 `RuleCheckResult`，标 `BUSINESS_RULE`，构造器注入规则容器 `OrderRule`（见 `order-rule-pattern.md`）。
- **EVENT_SUBSCRIBER**：见 `event-subscriber-pattern.md`。
- **ATTRIBUTE_CALCULATOR**：`IEntityPropertyCalculator<T, E, R>`（`R calculate(T, E)`，`E` 创建场景 `null`）+ 应用层 `@Service` + 场景 resolver（`EntityPropertyResolvers.of`）+ 工厂"先算后赋"。
- **CAPABILITY_PROVIDER**：领域层 `I{产物}Generator/Provider extends IDomainService`（如 `IOrderIdGenerator.nextId()`）+ 应用层包装框架 `IdGeneratorRegistry`。

## 5. 关键机制与避坑

- `category()` 注解查找链：实现类 → 所实现接口 → 父类，递归；标实现类上子类接口同样能读。
- 契约入参**必须是领域类型**（领域对象/值对象/领域上下文），不得用 `Command`/`HttpRequest`/DTO；来自应用层入参用 resolver 先转。
- 事件订阅注册路径：框架不扫描，必须 `{聚合}EventSubscriberRegistry` 显式 `registerSubscriber`。
- 一个契约只属一类，同时命中多项说明接口抽象错误，应拆。

## 6. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 接口名用 `Handler`/`Processor` 占位 | 用具名接口 `I{事件}{业务意图}`/`I{结果}Calculator` |
| 契约入参用 Command/DTO | 用领域类型，Command 经 resolver 适配 |
| 不标 `@DomainService`/category 错 | 标正确 `category`，与语义一致 |
| 一个接口多类判定 | 拆分为多个单类契约 |
| 领域服务同步编排跨聚合 | 领域事件驱动，订阅者各自响应 |
| 属性计算内嵌聚合根方法 | 外移为 `ATTRIBUTE_CALCULATOR`，工厂先算后赋 |

## 7. 被读于

S4 `ddd-aggregate-create`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
