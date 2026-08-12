# 校验规则领域服务

> 本文档介绍使用 Pragmatic DDD 编写校验规则领域服务的最佳实践：领域层定义校验契约，应用层实现；`EntityRule` 作为校验容器统一通过 `execute()` 模板执行。

## 1. 整体思路

> **领域层定义校验契约接口，应用层实现；`EntityRule` 作为校验容器，注入校验规则领域服务，统一通过 `execute()` 模板执行。**

- 领域层：定义错误码注册表、校验规则契约接口（`extends IDomainService`）、`EntityRule` 子类
- 应用层：实现校验规则领域服务（`@Component`），注入到 `EntityRule` 中
- 应用服务：只需注入 `EntityRule` Bean，不直接依赖各个校验规则领域服务

---

## 2. 领域层定义

### 2.1 定义错误码注册表

继承 `BrokenRuleRegistry`，定义 `static final MessageCode` 字段，**必须是 `public` 类**。

```java
// domain/order/service/OrderValidationRegistry.java
public class OrderValidationRegistry extends BrokenRuleRegistry {

    /** 用户不存在或已冻结 */
    public static final MessageCode USER_NOT_VALID =
            MessageCode.of("USER_NOT_VALID", "用户 %s 不存在或已被冻结");

    /** 库存不足 */
    public static final MessageCode STOCK_INSUFFICIENT =
            MessageCode.of("STOCK_INSUFFICIENT", "商品 %s 库存不足，需要 %d 件，实际库存 %d 件");

    /** 信用额度不足 */
    public static final MessageCode CREDIT_LIMIT_EXCEEDED =
            MessageCode.of("CREDIT_LIMIT_EXCEEDED", "信用额度不足，需要 %s，可用 %s");

    public static final OrderValidationRegistry INSTANCE = new OrderValidationRegistry();
}
```

> **注意**：`BrokenRuleRegistry` 构造时通过反射扫描子类的 `static MessageCode` 字段自动注册，子类必须为 `public`，否则反射访问失败。

### 2.2 定义校验规则契约接口

每个校验条件定义独立接口，继承 `IDomainService`，返回 `RuleCheckResult`。

```java
// domain/order/service/IUserValidityRule.java
public interface IUserValidityRule extends IDomainService {
    RuleCheckResult check(String userId);
}

// domain/order/service/IStockAvailabilityRule.java
public interface IStockAvailabilityRule extends IDomainService {
    RuleCheckResult check(List<OrderItem> items);
}

// domain/order/service/ICreditLimitRule.java
public interface ICreditLimitRule extends IDomainService {
    RuleCheckResult check(String userId, BigDecimal orderAmount);
}
```

### 2.3 定义 EntityRule 子类

标注 `@Component`，通过构造器注入校验规则领域服务，在 `init()` 中将它们适配为 `ICheckRule`。

```java
// domain/order/model/OrderEntityRule.java
@Component
public class OrderEntityRule extends EntityRule<Order> {

    private final IUserValidityRule userValidityRule;
    private final IStockAvailabilityRule stockAvailabilityRule;
    private final ICreditLimitRule creditLimitRule;

    public OrderEntityRule(IUserValidityRule userValidityRule,
                           IStockAvailabilityRule stockAvailabilityRule,
                           ICreditLimitRule creditLimitRule) {
        this.userValidityRule = userValidityRule;
        this.stockAvailabilityRule = stockAvailabilityRule;
        this.creditLimitRule = creditLimitRule;
        this.init();
    }

    @Override
    public void init() {
        // 外部依赖校验
        this.addRule(
                (order, old) -> userValidityRule.check(order.getUserId()),
                OrderValidationRegistry.USER_NOT_VALID
        );
        this.addRule(
                (order, old) -> stockAvailabilityRule.check(order.getOrderItemList()),
                OrderValidationRegistry.STOCK_INSUFFICIENT
        );
        this.addRule(
                (order, old) -> creditLimitRule.check(
                        order.getUserId(), order.getTotalPrice()),
                OrderValidationRegistry.CREDIT_LIMIT_EXCEEDED
        );

        // 内部不变量校验
        this.addRule(
                (order, old) -> RuleCheckResult.of(
                        order.getTotalPrice().compareTo(BigDecimal.ZERO) > 0
                ),
                OrderBrokenRuleRegistry.TOTAL_PRICE_ERROR
        );
        this.addRule(
                (order, old) -> RuleCheckResult.of(
                        !order.getOrderItemList().isEmpty()
                ),
                OrderBrokenRuleRegistry.ORDER_ITEM_ERROR
        );
    }
}
```

> **关键点**：在 `addRule()` 的 Lambda 中，从聚合根（如 `order.getUserId()`）提取参数，传递给校验规则领域服务的 `check()` 方法。

---

## 3. 应用层实现

### 3.1 实现校验规则领域服务

标注 `@Component`，通过构造器注入所需依赖，返回 `RuleCheckResult`。

```java
// application/order/service/UserValidityRule.java
@Component
public class UserValidityRule implements IUserValidityRule {

    private final IRepository<String, User> userRepository;

    public UserValidityRule(IRepository<String, User> userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public RuleCheckResult check(String userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isFrozen())
                .map(user -> RuleCheckResult.pass())
                .orElseGet(() -> RuleCheckResult.fail(new Object[]{userId}));
    }
}
```

```java
// application/order/service/StockAvailabilityRule.java
@Component
public class StockAvailabilityRule implements IStockAvailabilityRule {

    private final IRepository<String, Inventory> inventoryRepository;

    public StockAvailabilityRule(IRepository<String, Inventory> inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public RuleCheckResult check(List<OrderItem> items) {
        for (OrderItem item : items) {
            Inventory inventory = inventoryRepository.findById(item.getProductId());
            if (inventory == null || inventory.getAvailableQuantity() < item.getQuantity()) {
                int availableQty = inventory != null ? inventory.getAvailableQuantity() : 0;
                return RuleCheckResult.fail(
                        new Object[]{item.getProductName(), item.getQuantity(), availableQty}
                );
            }
        }
        return RuleCheckResult.pass();
    }
}
```

```java
// application/order/service/CreditLimitRule.java
@Component
public class CreditLimitRule implements ICreditLimitRule {

    private final CreditService creditService;

    public CreditLimitRule(CreditService creditService) {
        this.creditService = creditService;
    }

    @Override
    public RuleCheckResult check(String userId, BigDecimal orderAmount) {
        BigDecimal availableCredit = creditService.getAvailableCredit(userId);
        if (availableCredit.compareTo(orderAmount) >= 0) {
            return RuleCheckResult.pass();
        }
        return RuleCheckResult.fail(new Object[]{orderAmount, availableCredit});
    }
}
```

### 3.2 ApplicationService 使用方式

应用服务只需注入 `OrderEntityRule`，不再直接依赖各个校验规则领域服务。

```java
// application/order/OrderApplicationService.java
@Service
public class OrderApplicationService extends AbstractApplicationService {

    private final OrderEntityRule orderEntityRule;
    private final IRepository<Long, Order> orderRepository;

    public OrderApplicationService(IEventManager eventManager,
                                   OrderEntityRule orderEntityRule,
                                   IRepository<Long, Order> orderRepository) {
        super(eventManager);
        this.orderEntityRule = orderEntityRule;
        this.orderRepository = orderRepository;
    }

    public void createOrder(CreateOrderCommand command) {
        Order order = new Order(command);
        this.execute(order, orderEntityRule, orderRepository, o -> {
            o.place();
        });
    }
}
```

> **效果**：所有校验（外部依赖 + 内部不变量）在 `execute()` 模板中自动执行，校验失败时 `BrokenRuleException` 由框架自动抛出。

---

## 4. 常见问题

### 4.1 内部不变量 vs 外部依赖校验

| 类型 | 判断标准 | 放在哪里 |
|------|---------|---------|
| **内部不变量** | 仅需访问聚合根自身的字段即可完成校验 | `EntityRule.init()` 中的 Lambda 直接编写 |
| **外部依赖校验** | 需要查询数据库、调用 RPC 或依赖其他聚合 | 定义校验规则领域服务接口 + 实现，注入到 `EntityRule` 中 |

例如，`订单金额 > 0` 是内部不变量，而 `用户是否存在` 需要查库，属于外部依赖校验。

### 4.2 check 参数从哪里来？

在 `EntityRule.init()` 的 `addRule()` Lambda 中，从聚合根提取字段传递给 `check()` 方法：

```java
this.addRule(
    (order, old) -> userValidityRule.check(order.getUserId()),  // 从 order 中提取 userId
    OrderValidationRegistry.USER_NOT_VALID
);
```

### 4.3 如何返回参数化错误消息？

`RuleCheckResult.fail(Object[] params)` 携带的参数会通过 `String.format` 与 `MessageCode` 的 description 模板组合。

```java
// 错误码定义
MessageCode.of("STOCK_INSUFFICIENT", "商品 %s 库存不足，需要 %d 件，实际库存 %d 件")

// 校验失败时
RuleCheckResult.fail(new Object[]{"iPhone 15", 3, 1})
// 最终消息: "商品 iPhone 15 库存不足，需要 3 件，实际库存 1 件"
```

### 4.4 需要对比老数据怎么办？

修改操作中经常需要对比新旧数据，例如"仅允许从待支付变更为已取消，不允许从已完成变更为已取消"。

`EntityRule` 已内置老数据加载机制，`addRule()` 的 Lambda 第二个参数 `old` 即老实体。`EntityRule` 子类需覆盖 `requireOldEntity()` 和 `supplyOldEntity()`。

```java
@Component
public class OrderEntityRule extends EntityRule<Order> {

    private final IRepository<Long, Order> orderRepository;

    // ... 构造器注入

    @Override
    protected boolean requireOldEntity() {
        return true;  // 启用老数据加载
    }

    @Override
    protected Order supplyOldEntity(Order currentModel) {
        // currentModel.getId() 为 null 时是创建操作，无需加载
        return currentModel.getId() != null
                ? orderRepository.findById(currentModel.getId()).orElse(null)
                : null;
    }

    @Override
    public void init() {
        // ... 其他规则

        // 带老数据对比的校验规则领域服务
        this.addRule(
                (order, old) -> {
                    if (old == null) {
                        return RuleCheckResult.pass(); // 创建操作，无老数据，直接通过
                    }
                    return orderStatusChangeRule.check(old.getStatus(), order.getStatus());
                },
                OrderValidationRegistry.STATUS_CHANGE_INVALID
        );
    }
}
```

校验规则领域服务接收新旧状态：

```java
public interface IOrderStatusChangeRule extends IDomainService {
    RuleCheckResult check(OrderStatus oldStatus, OrderStatus newStatus);
}

@Component
public class OrderStatusChangeRule implements IOrderStatusChangeRule {

    @Override
    public RuleCheckResult check(OrderStatus oldStatus, OrderStatus newStatus) {
        if (oldStatus == OrderStatus.COMPLETED && newStatus == OrderStatus.CANCELLED) {
            return RuleCheckResult.fail(new Object[]{oldStatus, newStatus});
        }
        return RuleCheckResult.pass();
    }
}
```

> **注意**：`supplyOldEntity()` 在每次 `satisfiesRule()` 调用时执行一次，从数据库加载老实体。创建操作时 `currentModel.getId()` 为 null，应直接返回 null。

### 4.5 如何让规则只在特定条件下激活？

某些校验只在特定条件下才需要执行。例如，仅当订单金额 > 1000 时才校验信用额度。

`EntityRule.addRule()` 支持三参数重载，第三个参数为 `IActiveRuleCondition<T>`，可在运行时动态决定规则是否激活。

```java
this.addRule(
    (order, old) -> creditLimitRule.check(order.getUserId(), order.getTotalPrice()),
    OrderValidationRegistry.CREDIT_LIMIT_EXCEEDED,
    // 激活条件：仅当金额 > 1000 时激活
    (order, old) -> order.getTotalPrice().compareTo(new BigDecimal("1000")) > 0
            ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE
);
```

更多示例：

```java
// 仅修改操作时激活
(order, old) -> old != null ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE

// 仅特定状态时激活
(order, old) -> order.getStatus() == OrderStatus.PENDING
        ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE

// 仅当商品是预售商品时校验库存
(order, old) -> order.hasPresaleItem() ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE
```

> **为什么推荐在 `addRule()` 中指定而非在实现类中判断**：激活条件是"规则元数据"，属于 `EntityRule` 的编排层职责。放在 `addRule()` 中一目了然，且利用框架原生机制——不激活的规则不计入 `BrokenRule`，不影响 `failFast`。

---

## 5. 命名规范速查

| 层 | 类型 | 命名格式 | 示例 |
|----|------|---------|------|
| 领域层 | 校验规则契约接口 | `I{业务对象}{校验目标}Rule` | `IUserValidityRule` |
| 领域层 | 错误码注册表 | `{聚合}ValidationRegistry` | `OrderValidationRegistry` |
| 领域层 | EntityRule 子类 | `{聚合}EntityRule` | `OrderEntityRule` |
| 应用层 | 校验规则实现 | 接口名去 `I` 前缀 | `UserValidityRule` |

---

下一步：

- [聚合设计原则](./aggregate-design.md)
- [业务规则引擎详解](../core/business-rules.md)
