# 聚合业务规则（OrderRule 范式）

> 本文档以 `io.pragmatic.ddd.example.order.domain.order.rule` 包为蓝本，说明如何在聚合的 `domain.order.rule` 下落地业务规则：将聚合的全部不变量集中到 `{聚合}Rule` 容器中，错误码统一声明在 `{聚合}RuleRegistry` 注册表里，外部依赖校验通过领域服务契约构造注入。

## 1. 本质与定位

业务规则用于表达聚合内部的**不变量约束**——在聚合生命周期任意时刻都必须成立的条件（如「订单金额必须为正数」「仅待支付或已支付状态的订单可取消」）。`EntityRule<T>` 是把这些不变量组合成可校验单元的规则容器，由应用层在状态变更后触发，违反时收集为 `BrokenRule` 并抛出 `BrokenRuleException`。

本模式与写模型的边界：

- **规则容器只声明不变量**，不在业务方法（如 `order.place()`）内联校验逻辑；校验由 `EntityRule` 统一触发。
- **规则只校验聚合自身**，不发起跨聚合、跨服务的调用。需要外部数据时，通过构造器注入领域服务契约，由调用方提供实现。
- 规则容器是**无状态**的，不持有 per-call 可变状态，可作为单例在多线程下安全共享。

::: tip
本范式是校验规则的**标准组织方式**：规则容器命名 `{聚合}Rule`、注册表命名 `{聚合}RuleRegistry`，规则在私有 `registerRules()` 中注册，通过构造器接收领域服务契约。
:::

## 2. 命名与包结构

### 2.1 包结构

规则相关类型放在聚合的领域层 `rule` 子包中，与 `model`、`service` 平级：

```text
domain/order/
├── model/                     聚合根、实体、值对象
│   └── Order.java             extends AggregateRoot<Long>
├── rule/                      ← 规则容器 + 注册表
│   ├── OrderRule.java         extends EntityRule<Order>
│   └── OrderRuleRegistry.java extends BrokenRuleRegistry
└── service/                   领域服务契约
    └── IOrderCustomerPermissionService.java
```

### 2.2 命名规范

| 层 | 类型 | 命名格式 | 示例 | 反例 |
|----|------|---------|------|------|
| 领域层 | 规则容器 | `{聚合}Rule`，继承 `EntityRule<{聚合}>` | `OrderRule` | `OrderRuleService` |
| 领域层 | 错误码注册表 | `{聚合}RuleRegistry`，继承 `BrokenRuleRegistry` | `OrderRuleRegistry` | `OrderBrokenRules` |
| 领域层 | 注册表单例 | `{聚合}RuleRegistry.INSTANCE` | `OrderRuleRegistry.INSTANCE` | `new OrderRuleRegistry()` |
| 领域层 | 外部校验契约 | `I{聚合}{校验目标}Service`，继承 `IDomainService` | `IOrderCustomerPermissionService` | `OrderValidator` |

## 3. 数据 / 职责承载

| 组件 | 承载内容 | 不承载 |
|------|---------|--------|
| `{聚合}RuleRegistry` | 该聚合全部不变量的 `MessageCode`（局部码 + 描述文本） | 校验逻辑、外部依赖 |
| `{聚合}Rule` | 聚合全部不变量的校验项（`ICheckRule` + `MessageCode` + 激活条件） | 业务状态变更逻辑 |
| 外部校验契约 | 单条需要外部数据的校验（如用户资格），返回 `RuleCheckResult` | 多条规则的编排 |

派生规则（如「订单项数量为正」依赖 `OrderItem` 集合）在规则容器内部以私有方法实现，不单独抽出领域服务——只有**真正需要外部系统/仓储**的校验才值得声明为契约。

## 4. 落地方式（核心）

落地顺序：先写注册表 → 再写外部校验契约（如有）→ 最后写规则容器。

### 4.1 错误码注册表

继承 `BrokenRuleRegistry`，以 `public static final MessageCode` 字段集中声明不变量码，**类必须是 `public`**，并暴露 `public static final INSTANCE` 单例。

```java
public class OrderRuleRegistry extends BrokenRuleRegistry {

    public static final OrderRuleRegistry INSTANCE = new OrderRuleRegistry();

    public static final MessageCode ORDER_AMOUNT_POSITIVE =
            MessageCode.of("ORDER_AMOUNT_POSITIVE", "订单金额必须为正数");

    public static final MessageCode ORDER_AT_LEAST_ONE_ITEM =
            MessageCode.of("ORDER_AT_LEAST_ONE_ITEM", "订单至少需要包含一个订单项");

    public static final MessageCode ORDER_CANCEL_STATUS_INVALID =
            MessageCode.of("ORDER_CANCEL_STATUS_INVALID", "仅待支付或已支付状态的订单可取消");

    private OrderRuleRegistry() {
    }
}
```

> ⚠️ **重要约束**：注册表子类**必须**为 `public`。`BrokenRuleRegistry` 构造时通过反射 `field.get(null)` 读取子类的 `static MessageCode` 字段；若子类为包级私有，`IllegalAccessException` 被静默吞掉，导致该消息码**未注册**——聚合根收集到的违规描述为空白（`code` 不受影响，但前端拿不到描述文本）。

> ⚠️ **重要约束**：同一注册表内 `MessageCode.localCode` 作为 map key **不可重复**，重复注册后者覆盖前者。

### 4.2 外部校验契约（按需）

仅当某条不变量需要查库、调用 RPC 或依赖其他聚合时，才在 `service` 包声明契约接口（继承 `IDomainService`），由应用层提供实现，规则容器只持有接口引用。

```java
@DomainService(category = DomainServiceCategory.BUSINESS_RULE,
        targetName = "Order",
        description = "校验下单用户是否生效且具备下单资格")
public interface IOrderCustomerPermissionService extends IDomainService {
    RuleCheckResult verifyOrderCreatePermission(Customer customer);
}
```

### 4.3 规则容器

继承 `EntityRule<Order>`，构造器注入所需外部契约，在私有 `registerRules()` 中通过 `addRule` 注册全部不变量。内部不变量用 `EntityRule.of(order -> ...)` 适配（不关心旧实体）；需外部数据的规则调用注入契约的方法。

```java
public class OrderRule extends EntityRule<Order> {

    private final IOrderCustomerPermissionService customerPermissionService;

    public OrderRule(IOrderCustomerPermissionService customerPermissionService) {
        super();
        this.customerPermissionService = customerPermissionService;
        this.registerRules();
    }

    private void registerRules() {
        // 内部不变量：仅访问聚合自身字段
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(
                        order.getTotalAmount() != null
                                && order.getTotalAmount().getAmount().compareTo(BigDecimal.ZERO) > 0)),
                OrderRuleRegistry.ORDER_AMOUNT_POSITIVE);

        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(
                        !order.getOrderItems().getAllItems().isEmpty())),
                OrderRuleRegistry.ORDER_AT_LEAST_ONE_ITEM);

        // 外部依赖校验：调用注入的契约
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

编写规则的三条规则：

- **内部不变量**：`EntityRule.of(order -> RuleCheckResult.of(...))` 直接表达，纯函数、无副作用。
- **带参数的失败消息**：`RuleCheckResult.fail(Object[] params)` 携带参数，与 `MessageCode` 描述中的 `%s` 占位符经 `String.format` 组合。
- **派生判断**：聚合项数量、金额等需遍历的集合判断，抽成私有方法用 Stream 表达，保持注册处可读。

### 4.4 激活条件（IActiveRuleCondition）

某些不变量只在特定上下文下才参与校验。`addRule` 三参数重载的 `IActiveRuleCondition` 声明这种上下文，属于规则编排层元数据，不写在被注入的契约实现里。

`IActiveRuleCondition` 有两类职责：

- `status(newModel, oldModel)`：基于模型内容判断（纯函数），是本范式的主战场。
- `switchStatus(messageCode)`：基于规则标识读取外部开关（默认 `ACTIVE`），用于运行时动态启停某条规则。

激活条件可依据三类上下文判断，下面逐一说明：

#### 4.4.1 基于聚合状态激活

当不变量只在聚合处于某状态时成立（如仅 `CREATED` 状态需要校验用户资格）：

```java
// 仅当订单处于 CREATED 状态时激活用户资格校验
IActiveRuleCondition.of(order -> order.getStatus() == OrderStatus.CREATED
        ? ActiveStatus.ACTIVE
        : ActiveStatus.INACTIVE)
```

#### 4.4.2 基于新旧对比激活

当不变量只在「修改场景」（存在旧实体快照）下才需要校验时，用双参数 `BiFunction` 适配：

```java
// 仅修改（存在旧数据）时激活
IActiveRuleCondition.of((order, old) -> old != null
        ? ActiveStatus.ACTIVE
        : ActiveStatus.INACTIVE)
```

#### 4.4.3 基于操作（Operation）激活 ⭐

当聚合启用了领域操作体系（`operationRegistry()` 返回非 null 注册表），校验上下文由「本次工作单元触发了哪个 `Operation`」决定。规则激活条件直接读取聚合根的已触发操作，让**同一条不变量只在特定操作发生时参与校验**。

前置：聚合根实现 `operationRegistry()` 并返回 `OrderOperationRegistry.INSTANCE`（含 `CREATE / PAY / CANCEL / SHIP` 等操作码），业务方法内 `recordOperation(...)`。规则容器持有的是 `AggregateRoot`，因此 `IActiveRuleCondition.status(order, ...)` 中 `order` 可直接调用 `hasOperation*`：

```java
// 仅当本次触发过 CANCEL 操作时，才校验「取消原因必填」
this.addRule(
        EntityRule.of(order -> RuleCheckResult.of(order.getCancelReason() != null)),
        OrderRuleRegistry.ORDER_CANCEL_REASON_REQUIRED,
        IActiveRuleCondition.of(order ->
                order.hasOperation(OrderOperationRegistry.CANCEL)   // 单操作
                        ? ActiveStatus.ACTIVE
                        : ActiveStatus.INACTIVE));

// 仅当本次为支付或取消操作时，才校验「操作人已登录」
this.addRule(
        EntityRule.of(order -> RuleCheckResult.of(order.getOperatorId() != null)),
        OrderRuleRegistry.ORDER_OPERATOR_REQUIRED,
        IActiveRuleCondition.of(order ->
                order.hasAnyOperation(                                // 任一操作
                        OrderOperationRegistry.PAY,
                        OrderOperationRegistry.CANCEL)
                        ? ActiveStatus.ACTIVE
                        : ActiveStatus.INACTIVE));

// 仅当本次同时触发支付与发货（罕见，演示 hasAllOperations）时校验
IActiveRuleCondition.of(order ->
        order.hasAllOperations(OrderOperationRegistry.PAY,
                OrderOperationRegistry.SHIP)
                ? ActiveStatus.ACTIVE
                : ActiveStatus.INACTIVE);
```

判断方法语义（来自 `io.pragmatic.ddd.operation` 包）：

| 方法 | 含义 | 激活表达式骨架 |
|------|------|---------------|
| `hasOperation(op)` | 已触发指定操作 | 单操作专属校验 |
| `hasAnyOperation(op...)` | 已触发任一操作 | 多个操作共享同一校验 |
| `hasAllOperations(op...)` | 已触发全部操作 | 多操作同时满足才校验 |

> ⚠️ **重要约束**：基于 Operation 的激活依赖聚合根已调用 `recordOperation(...)`。`satisfiesRule` 在业务方法执行后即触发校验，而 `recordOperation` 发生在业务方法内——因此 `hasOperation` 读取到的正是本次工作单元已触发的操作。若聚合根未启用操作体系（`operationRegistry()` 返回 `null`），调用 `hasOperation` 会抛 `OperationException`，切勿在规则内对未启用操作的聚合使用此激活方式。

> 💡 **范式对比**：`hasOperation` 判定的是「本次工作单元执行了什么操作」，属**过程维度**；4.4.1 的状态判定是「聚合当前处在什么状态」，属**结果维度**。两者可叠加：例如「仅当本次触发 `CANCEL` 且当前状态为 `CANCELLED` 时校验取消原因」。

### 4.5 聚合根接入

聚合根实现 `AggregateRoot` 的抽象方法 `brokenRuleRegistry()`，返回注册表单例，使自身具备违规收集能力。

```java
public class Order extends AggregateRoot<Long> {
    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderRuleRegistry.INSTANCE;
    }
    // 业务方法内不内联校验，交由规则容器统一触发
}
```

### 4.6 应用层触发

规则容器由调用方构造（注入领域服务实现），在状态变更后调用 `aggregate.satisfiesRule(rule)` 收集违反，再决定抛单条还是聚合异常。

```java
OrderRule orderRule = new OrderRule(orderCustomerPermissionService);
Order order = new Order(command);
if (!order.satisfiesRule(orderRule)) {
    order.throwBrokenRuleAggregateException();   // 含全部违反
}
```

> ⚠️ **重要约束**：校验与抛异常是两步操作——先 `satisfiesRule` 收集违反，再 `throwBrokenRuleException()` / `throwBrokenRuleAggregateException()`。不调用抛异常方法，流程不会中断。

## 5. 关键机制与避坑

### 5.1 failFast 与短路

`EntityRule` 默认 `failFast=true`，遇首条失败即中止后续规则。需要一次性返回全部字段错误（如前端表单）时，用 `new EntityRule<>(false)` 构造容器收集全部违反，此时 `BrokenRuleAggregateException` 才有意义。

### 5.2 内部不变量 vs 外部依赖校验

| 类型 | 判断标准 | 落地方式 |
|------|---------|---------|
| 内部不变量 | 仅访问聚合自身字段即可完成 | `registerRules()` 中 `EntityRule.of(...)` 直接编写 |
| 外部依赖校验 | 需要查库、RPC 或依赖其他聚合 | 声明 `I{聚合}{目标}Service` 契约 + 注入实现 |

示例：`订单金额 > 0`、`订单至少一项` 是内部不变量；`用户是否生效且具备下单资格` 需查外部系统，属于外部依赖校验。

### 5.3 带旧数据对比

需要对比修改前后状态（如「仅允许从待支付变更为已取消」）时，覆盖 `requireOldEntity()` 返回 `true` 并实现 `supplyOldEntity(order)`，规则 Lambda 第二个参数 `old` 即旧实体快照。`old` 为 `null` 代表新建场景，规则内部必须做空判断。`supplyOldEntity` 每次 `satisfiesRule` 调用执行一次。

### 5.4 无状态与线程安全

`OrderRule` 不在构造后变更字段，`EntityRule.of` 的 Lambda 也是无状态纯函数。因此规则容器实例可安全作为单例共享；若以 Spring Bean 形式托管，直接 `@Component` 并注入契约即可，无需 `prototype` 作用域。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
|--------|------|---------|
| 注册表类写成包级私有 | `static MessageCode` 反射注册失败，描述文本变空白 | 注册表类必须 `public` |
| 业务方法内联 `if (!amount > 0) throw ...` | 不变量散落、难以统一收集与复用 | 不变量集中到 `OrderRule.registerRules()` |
| 规则容器里直接 `@Autowired` 仓储 / RPC | 规则容器承担跨聚合调用，破坏一致性边界 | 经构造器注入领域服务契约，由应用层实现 |
| 激活条件写在被注入的契约实现里 | 丢失框架原生开关能力，且条件与规则元数据分离 | 在 `addRule(..., IActiveRuleCondition)` 声明 |
| 同一注册表内 `localCode` 重复 | 后者覆盖前者，码与描述错位 | 每个不变量码唯一 |
| 收集违反后不调用抛异常方法 | 校验通过却未中断，脏数据被持久化 | 收集后显式 `throwBrokenRule*Exception()` |

## 7. 下一步

- [聚合设计原则](./aggregate-design.md)：聚合边界与不变量来源
- [业务规则引擎详解](../core/business-rules.md)：`EntityRule` / `MessageCode` / `BrokenRule` 底层契约
