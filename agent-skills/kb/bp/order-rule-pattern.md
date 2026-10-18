# BP 卡：聚合业务规则（OrderRule 范式）（order-rule-pattern）

> 源：`documentation/best-practices/order-rule-pattern.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

业务规则表达聚合内部的**不变量约束**——聚合生命周期任意时刻都必须成立的条件（如「订单金额必须为正数」）。`EntityRule<T>` 把不变量组合成可校验单元，由应用层在状态变更后触发，违反时收集为 `BrokenRule` 并抛 `BrokenRuleException`。

- 规则容器只声明不变量，不在业务方法内联校验；校验由 `EntityRule` 统一触发。
- 规则只校验聚合自身，不发起跨聚合/跨服务调用；需外部数据时经构造器注入领域服务契约。
- 规则容器**无状态**，可作为单例多线程安全共享。

## 2. 命名与包结构

```text
domain/order/
├── model/Order.java            extends AggregateRoot<Long>
├── rule/
│   ├── OrderRule.java          extends EntityRule<Order>
│   └── OrderRuleRegistry.java  extends BrokenRuleRegistry
└── service/IOrderCustomerPermissionService.java
```

| 类型 | 命名 | 示例 |
| --- | --- | --- |
| 规则容器 | `{聚合}Rule` extends `EntityRule<{聚合}>` | `OrderRule` |
| 错误码注册表 | `{聚合}RuleRegistry` extends `BrokenRuleRegistry` | `OrderRuleRegistry` |
| 外部校验契约 | `I{聚合}{校验目标}Service` extends `IDomainService` | `IOrderCustomerPermissionService` |

## 3. 落地方式（核心）

### 3.1 错误码注册表

继承 `BrokenRuleRegistry`，`public static final MessageCode` 集中声明不变量码，**类必须 `public`**，暴露 `INSTANCE` 单例（详见 `registry-design.md`）。

### 3.2 外部校验契约（按需）

仅当不变量需查库/RPC/依赖其他聚合时，在 `service` 包声明契约（继承 `IDomainService`），由应用层实现，规则容器只持接口引用：

```java
@DomainService(category = DomainServiceCategory.BUSINESS_RULE,
        targetName = "Order",
        description = "校验下单用户是否生效且具备下单资格")
public interface IOrderCustomerPermissionService extends IDomainService {
    RuleCheckResult verifyOrderCreatePermission(Customer customer);
}
```

### 3.3 规则容器

继承 `EntityRule<Order>`，构造器注入外部契约，在私有 `registerRules()` 中 `addRule` 注册全部不变量。内部不变量用 `EntityRule.of(order -> ...)` 适配（不关心旧实体）；需外部数据调用注入契约。

```java
public class OrderRule extends EntityRule<Order> {

    private final IOrderCustomerPermissionService customerPermissionService;

    public OrderRule(IOrderCustomerPermissionService customerPermissionService) {
        super();
        this.customerPermissionService = customerPermissionService;
        this.registerRules();
    }

    private void registerRules() {
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(
                        order.getTotalAmount() != null
                                && order.getTotalAmount().getAmount().compareTo(BigDecimal.ZERO) > 0)),
                OrderRuleRegistry.ORDER_AMOUNT_POSITIVE);

        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(!order.getOrderItems().getAllItems().isEmpty())),
                OrderRuleRegistry.ORDER_AT_LEAST_ONE_ITEM);

        this.addRule(
                EntityRule.of(order -> this.verifyCustomer(order.getCustomer())),
                OrderRuleRegistry.ORDER_CUSTOMER_QUALIFIED,
                IActiveRuleCondition.of(order -> order.getStatus() == OrderStatus.CREATED
                        ? ActiveStatus.ACTIVE
                        : ActiveStatus.INACTIVE));
    }

    private RuleCheckResult verifyCustomer(Customer customer) {
        return this.customerPermissionService.verifyOrderCreatePermission(customer);
    }
}
```

编写规则三条：

- **内部不变量**：`EntityRule.of(order -> RuleCheckResult.of(...))` 纯函数、无副作用。
- **带参失败消息**：`RuleCheckResult.fail(Object[] params)` 携参，与 `MessageCode` 描述 `%s` 占位符经 `String.format` 组合。
- **派生判断**：集合判断抽私有方法用 Stream 表达。

### 3.4 激活条件（IActiveRuleCondition）

某些不变量只在特定上下文参与校验。`addRule` 三参重载的 `IActiveRuleCondition` 声明上下文（规则编排层元数据，不写在被注入契约实现里）。两类职责：

- `status(newModel, oldModel)`：基于模型内容判断（纯函数），主战场。
- `switchStatus(messageCode)`：基于规则标识读外部开关（默认 `ACTIVE`）。

| 依据 | 写法 |
| --- | --- |
| 基于聚合状态 | `IActiveRuleCondition.of(order -> order.getStatus() == CREATED ? ACTIVE : INACTIVE)` |
| 基于新旧对比 | `IActiveRuleCondition.of((order, old) -> old != null ? ACTIVE : INACTIVE)` |
| 基于操作（Operation）⭐ | `order.hasOperation(CANCEL)` / `hasAnyOperation(PAY, CANCEL)` / `hasExceptOperation(ADD_ITEM, REMOVE_ITEM)` |

`hasOperation*` 语义（来自 `io.pragmatic.ddd.operation`）：

| 方法 | 含义 |
| --- | --- |
| `hasOperation(op)` | 当前因果操作 == `op` |
| `hasAnyOperation(op...)` | 当前因果操作 ∈ 集合 |
| `hasExceptOperation(op...)` | 当前因果操作存在且 ∉ 集合（黑名单 gate，含将来新增码；未记录操作时返回 `false`） |

> ⚠️ 一次工作单元只记一个操作——记第二个不同 `code` 抛 `MultipleOperationsException`；`hasAllOperations(...)` 已移除。`hasExceptOperation` 对**所有操作生效**（含新增），未记录操作时返回 `false`（保守不激活）；操作集合小且稳定时用 `hasAnyOperation` 列全（白名单）更安全。
> ⚠️ 基于 Operation 激活依赖聚合根已 `recordOperation(...)`；若 `operationRegistry()` 返回 `null`，调用 `hasOperation` 抛 `OperationException`，切勿对未启用操作的聚合用此激活。

### 3.5 聚合根接入

```java
public class Order extends AggregateRoot<Long> {
    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderRuleRegistry.INSTANCE;
    }
    // 业务方法内不内联校验，交由规则容器统一触发
}
```

### 3.6 应用层触发

```java
OrderRule orderRule = new OrderRule(orderCustomerPermissionService);
Order order = new Order(command);
if (!order.satisfiesRule(orderRule)) {
    order.throwBrokenRuleAggregateException();   // 含全部违反
}
```

> ⚠️ 校验与抛异常是两步：先 `satisfiesRule` 收集，再 `throwBrokenRuleException()`/`throwBrokenRuleAggregateException()`；不调用抛异常方法流程不中断。

## 4. 关键机制与避坑

- **failFast 与短路**：`EntityRule` 默认 `failFast=true`，遇首条失败即中止；需一次性返回全部字段错误时用 `new EntityRule<>(false)` 收集全部违反。
- **内部不变量 vs 外部依赖校验**：仅访问聚合自身字段 → `EntityRule.of(...)` 直接写；需查库/RPC/依赖其他聚合 → 声明 `I{聚合}{目标}Service` 契约 + 注入实现。
- **带旧数据对比**：覆盖 `requireOldEntity()` 返回 `true` 并实现 `supplyOldEntity(order)`，规则 Lambda 第二参 `old` 即旧实体快照；`old` 为 `null` 代表新建，规则内必须空判断；`supplyOldEntity` 每次 `satisfiesRule` 执行一次。
- **无状态与线程安全**：构造后不变更字段，`EntityRule.of` 的 Lambda 无状态，可 `@Component` 单例共享，无需 `prototype`。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 注册表类包级私有 | 类必须 `public` |
| 业务方法内联 `if (!amount>0) throw` | 不变量集中 `OrderRule.registerRules()` |
| 规则容器里 `@Autowired` 仓储/RPC | 经构造器注入领域服务契约 |
| 激活条件写在契约实现里 | 在 `addRule(..., IActiveRuleCondition)` 声明 |
| 同一注册表内 `localCode` 重复 | 每个不变量码唯一 |
| 收集违反不调抛异常方法 | 收集后显式 `throwBrokenRule*Exception()` |

## 6. 被读于

S4 `ddd-aggregate-create`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
