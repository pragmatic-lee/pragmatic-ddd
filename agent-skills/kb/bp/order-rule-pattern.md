# BP 卡：聚合业务规则容器（OrderRule 范式）

> 源：`documentation/best-practices/order-rule-pattern.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 本质

规则容器（`{Agg}Rule extends EntityRule<{Agg}>`）把业务校验集中为规则单元：每条规则 = 校验函数（`ICheckRule`）+ `MessageCode` +（可选）激活条件（`IActiveRuleCondition`）。容器**无状态**，构造时仅注入领域服务契约引用（硬约束 3）；规则只校验聚合自身，不发起跨聚合/跨服务调用。

## 2. 命名与包

```text
domain/{agg}/rule/
├── {Agg}Rule.java         extends EntityRule<{Agg}>
└── {Agg}RuleRegistry.java extends BrokenRuleRegistry
domain/{agg}/service/
└── I{Agg}{Target}Service.java  extends IDomainService  （外部校验契约，按需）
```

- 注册表：`{Agg}RuleRegistry`，`public` 类 + `public static final INSTANCE`；`MessageCode.localCode` 在表内**不可重复**（重复后者覆盖前者）。
- 外部校验契约：`I{Agg}{校验目标}Service extends IDomainService`（仅当不变量需查库/RPC/外部聚合时才声明）。

## 3. 注册表骨架

```java
public class OrderRuleRegistry extends BrokenRuleRegistry {
    public static final OrderRuleRegistry INSTANCE = new OrderRuleRegistry();
    public static final MessageCode ORDER_AMOUNT_POSITIVE =
            MessageCode.of("ORDER_AMOUNT_POSITIVE", "订单金额必须为正数");
    public static final MessageCode ORDER_AT_LEAST_ONE_ITEM =
            MessageCode.of("ORDER_AT_LEAST_ONE_ITEM", "订单至少需要包含一个订单项");
    private OrderRuleRegistry() {}
}
```
⚠️ 注册表子类**必须 `public`**：`BrokenRuleRegistry` 经反射 `field.get(null)` 读取 `static MessageCode`，包级私有则 `IllegalAccessException` 被静默吞掉 → 该码未注册、描述为空白。

## 4. 规则容器骨架

```java
public class OrderRule extends EntityRule<Order> {
    private final IOrderCustomerPermissionService customerPermissionService;

    public OrderRule(IOrderCustomerPermissionService customerPermissionService) {
        this.customerPermissionService = customerPermissionService;
        this.registerRules();
    }

    private void registerRules() {
        // 内部不变量：纯函数，返回 RuleCheckResult
        this.addRule(
                EntityRule.of(order -> RuleCheckResult.of(
                        order.getTotalAmount() != null
                                && order.getTotalAmount().getAmount().compareTo(BigDecimal.ZERO) > 0)),
                OrderRuleRegistry.ORDER_AMOUNT_POSITIVE);

        // 外部依赖校验：调用注入的契约；第三参是 IActiveRuleCondition（非 ActiveStatus 枚举）
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

### 4.1 关键约定（对照 `api-contract.md`）
- **校验函数返回 `RuleCheckResult`，不是 `boolean`**：`EntityRule.of(fn)` 的 `fn` 形如 `newModel -> RuleCheckResult.of(true)`（通过）或 `RuleCheckResult.fail(params)`（违反，参数经 `MessageCode` 描述 `%s` 占位符 `String.format` 组合）。
- **`addRule` 第三参是 `IActiveRuleCondition<T>`**（不是 `ActiveStatus` 枚举）：`IActiveRuleCondition.of(newModel -> ... ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE)`；`ActiveStatus` 仅作返回值判等，不单独作为第三参。
- **带旧数据对比**：覆盖 `requireOldEntity()` 返回 `true` + `supplyOldEntity(order)`，`fn` 第二参 `old` 为 `null` 表示新建（规则内须空判断）。
- **`failFast`**：默认 `true`（首条失败即止）；`new EntityRule<>(false)` 收集全部违反（前端表单场景）。

### 4.2 激活条件（IActiveRuleCondition）
- **基于状态**：`IActiveRuleCondition.of(order -> order.getStatus()==X ? ACTIVE : INACTIVE)`。
- **基于新旧对比**：`IActiveRuleCondition.of((order,old) -> old != null ? ACTIVE : INACTIVE)`。
- **基于操作（Operation）⭐**：聚合启用操作体系时，读取本次记录的因果操作：
  - `order.hasOperation(OP)` 单操作专属；`order.hasAnyOperation(OP...)` 任一；`order.hasExceptOperation(OP...)` 黑名单 gate（对所有操作生效，未记录操作时返回 false）。
  - ⚠️ `hasExceptOperation` 对**所有操作（含将来新增）**生效，操作集合小且稳定时优先用 `hasAnyOperation` 白名单。
  - ⚠️ 依赖 `recordOperation(...)` 已调用；未启用操作体系时调用 `hasOperation` 抛 `OperationException`。

## 5. 聚合根接入

```java
public class Order extends AggregateRoot<Long> {
    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderRuleRegistry.INSTANCE;
    }
}
```

## 6. 应用层触发

```java
OrderRule orderRule = new OrderRule(orderCustomerPermissionService);
Order order = new Order(command);
if (!order.satisfiesRule(orderRule)) {
    order.throwBrokenRuleAggregateException();   // 收集全部违反
}
// 或：throwBrokenRuleException() 单条
```
⚠️ 校验与抛异常是两步：先 `satisfiesRule` 收集，再显式 `throwBrokenRule*Exception()`；不调用则不中断（硬约束 12）。

## 7. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 注册表包级私有 | 必须 `public` |
| 业务方法内联 `if (!...) throw` | 不变量集中到 `registerRules()` |
| 规则容器内 `@Autowired` 仓储/RPC | 经构造器注入领域服务契约 |
| 激活条件写进契约实现 | `addRule(..., IActiveRuleCondition)` 声明 |
| 同一注册表 `localCode` 重复 | 每个不变量码唯一 |
| 收集违反后不抛异常 | 显式 `throwBrokenRule*Exception()` |
| 校验函数返回 `boolean` | 返回 `RuleCheckResult.of(...)` / `RuleCheckResult.fail(...)` |
| 第三参传 `ActiveStatus` 枚举 | 第三参传 `IActiveRuleCondition.of(...)` |

## 8. 被读于

S4 `ddd-aggregate-create`（步骤 3）、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
