# BP 卡：领域服务（domain-service）

> 源：`documentation/best-practices/domain-service.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 本质

领域层声明、应用层实现的契约接口（`extends IDomainService`）：承载无法归入单一聚合根的逻辑——事件订阅、跨聚合校验、属性计算、领域原语供给。与经典 DDD 差异：**领域层定接口、应用层实现；跨聚合走领域事件，不在单服务内同步编排多聚合**。

## 2. 四类判定（命中即止）

| 序 | 检查 | 归类 | 注解 |
| --- | --- | --- | --- |
| 1 | `extends IHandle<T>` | 事件订阅 | `EVENT_SUBSCRIBER` |
| 2 | 返回 `RuleCheckResult` | 业务规则 | `BUSINESS_RULE` |
| 3 | `calculate(...)` 推导 | 属性计算 | `ATTRIBUTE_CALCULATOR` |
| 4 | `generate()`/`nextId()` 产出 | 能力供给 | `CAPABILITY_PROVIDER` |

## 3. 命名

| 分类 | 领域接口（I 开头） | 应用实现（去 I） |
| --- | --- | --- |
| 事件订阅 | `I{事件}{业务意图}Handle` | `XxxHandle` |
| 校验规则 | `I{对象}{规则意图}Service` | 去 I |
| 属性计算 | `I{结果}Calculator` | 去 I |
| 能力供给 | `I{产物}Generator/Provider` | 去 I |

接口名须带业务意图，禁用 `Handler`/`Processor` 占位词。

## 4. 落地三步

1. 领域层接口 + `@DomainService(category, description, targetName)`。
2. 应用层 `@Service`/`@Component` 实现，可依赖基础设施。
3. 按分类接入机制（事件订阅注册 / 注入规则容器 / 计算进工厂 / 能力进构造）。

## 5. 关键机制

- `category()` 只来自 `@DomainService` 注解（查找链：实现→接口→父类）；未标注返回 `UNKNOWN`。
- 契约入参必须是领域类型（值对象/领域上下文），**不得**用 Command/DTO/HttpRequest；来自应用层入参用 resolver 适配。
- 属性计算外移（`ATTRIBUTE_CALCULATOR`），工厂"先算后赋"，派生属性一处定义 N 场景复用。
- 事件订阅注册显式（见 `event-subscriber-pattern.md`）。

## 6. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 接口用 `Handler` 占位词 | 具名业务意图接口 |
| 契约入参用 Command/DTO | 用领域类型 + resolver |
| 不标 `@DomainService` | 标正确 category |
| 同步编排跨聚合（银行转账式） | 领域事件驱动 |
| 计算内嵌聚合根 | 外移为 ATTRIBUTE_CALCULATOR |

## 7. 被读于

S4 `ddd-aggregate-create`（外部校验契约）、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
