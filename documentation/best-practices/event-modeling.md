# 事件建模指南

> 本文档介绍使用 Pragmatic DDD 进行领域事件建模的最佳实践。

## 1. 事件的本质

领域事件代表一个**已发生、不可改变**的领域事实。事件不是命令，不是请求，而是对过去发生的事情的记录。

核心特征：

- **不可变**：事件一旦产生，其内容不可修改
- **过去式**：描述"已发生"的事情
- **自包含**：携带足够的上下文，订阅者无需回查

## 2. 事件命名规范

事件名使用**过去式**，描述"发生了什么"：

```java
// ✅ 推荐：过去式
OrderCreatedEvent          // 订单已创建
OrderCancelledEvent        // 订单已取消
PaymentReceivedEvent       // 支付已收到
InventoryDeductedEvent     // 库存已扣减

// ❌ 反模式：命令式
CreateOrderEvent           // 像命令，不是事件
CancelOrderEvent           // 语义不清
```

## 3. 事件携带的数据

事件应携带**订阅者处理所需的最小完整上下文**，避免订阅者回查聚合根：

```java
// ✅ 推荐：携带足够上下文
public class OrderCancelledEvent extends BaseDomainEvent {

    private final String customerId;
    private final long refundAmount;
    private final String cancelReason;

    public OrderCancelledEvent(String orderId, String customerId,
                               long refundAmount, String cancelReason) {
        super(orderId);
        this.customerId = customerId;
        this.refundAmount = refundAmount;
        this.cancelReason = cancelReason;
    }
}

// ❌ 反模式：只携带 ID，订阅者必须回查
public class OrderCancelledEvent extends BaseDomainEvent {
    // 订阅者需要再查 Order 才知道退款金额、客户信息
}
```

::: warning 不要携带过多数据
也不要把整个聚合根塞进事件。携带"订阅者大概率需要"的字段即可，避免事件膨胀。
:::

## 4. 即时事件 vs 延迟事件

```java
// 即时事件：立即构造，适用于事件内容在业务方法中已确定
this.collectEvent(new OrderCancelledEvent(
        String.valueOf(this.getEntityId()),
        this.customerId,
        this.refundAmount,
        this.cancelReason));

// 延迟事件：发布时才构造，适用于事件内容依赖最终状态
this.collectEvent(() -> new OrderSubmittedEvent(
        String.valueOf(this.getEntityId()),
        this.getTotalAmount()));  // 发布时取最新金额
```

选择依据：

| 场景 | 推荐 |
| --- | --- |
| 事件内容在业务方法中已确定 | 即时事件 |
| 事件内容依赖后续计算或最终状态 | 延迟事件 |
| 事件构造开销大、可能不被发布 | 延迟事件 |

## 5. 事件粒度

一个业务操作可产生多个事件，每个事件表达一个**独立的领域事实**：

```java
public void pay() {
    this.status = "PAID";
    this.paidAt = LocalDateTime.now();
    this.recordOperation(OrderOperationRegistry.PAY);

    // 一个操作产生多个事件
    this.collectEvent(new OrderPaidEvent(...));
    this.collectEvent(new PaymentReceivedEvent(...));
    this.collectEvent(new LoyaltyPointsEarnedEvent(...));
}
```

不要把多个事实合并成一个"大事件"：

```java
// ❌ 反模式：一个事件塞多个事实
this.collectEvent(new OrderPaidAndInventoryDeductedAndPointsEarnedEvent(...));

// ✅ 推荐：拆分为独立事件
this.collectEvent(new OrderPaidEvent(...));
this.collectEvent(new InventoryDeductedEvent(...));
this.collectEvent(new LoyaltyPointsEarnedEvent(...));
```

## 6. 事件与操作的关系

操作（`EntityOperation`）是"做了什么"，事件（`IDomainEvent`）是"发生了什么"：

```java
public void cancel() {
    this.status = "CANCELLED";
    this.recordOperation(OrderOperationRegistry.CANCEL);  // 操作：取消

    // 事件：订单已取消（operationCode 自动取 "CANCEL"）
    this.collectEvent(new OrderCancelledEvent(...));
}
```

- 一个操作可产生零到多个事件
- 事件的 `operationCode` 自动归属到最近一次操作
- 订阅者可据 `operationCode` 判断事件来源

---

下一步：

- [事务性发件箱](./transactional-outbox.md)
- [领域事件](../core/domain-events.md)
