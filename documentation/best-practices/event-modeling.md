# 事件建模指南

> 本文档介绍使用 Pragmatic DDD 进行领域事件建模的最佳实践。

## 1. 事件的本质

领域事件代表一个**已发生、不可改变**的领域事实。事件不是命令，不是请求，而是对过去发生的事情的记录。

核心特征：

- **不可变**：事件一旦产生，其内容不可修改
- **过去式**：描述"已发生"的事情
- **轻量**：只携带聚合标识与少量路由 ID，不携带业务快照；权威数据由订阅者反查聚合根获取

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

事件只携带**聚合标识与少量路由 / 上下文 ID**，不携带业务快照。事件表达的是「已发生且不可变」的领域事实，它只需回答两个问题：**发生了什么、作用于哪个聚合**。事件发生时的业务状态会继续演进，权威数据始终以聚合根为准。

**订阅者处理时应反查聚合根**：通过事件携带的聚合 ID 调仓储 `findById` 取当前权威状态，再基于最新状态做后续处理。

```java
// ✅ 推荐：事件只携带聚合标识与少量路由 ID
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderCreatedEvent extends BaseDomainEvent {
    private Long customerId;

    public OrderCreatedEvent(String entityId) {
        super(entityId);
    }

    public static OrderCreatedEvent buildEvent(Order order) {
        OrderCreatedEvent event = new OrderCreatedEvent(order.getEntityId().toString());
        event.setCustomerId(order.getCustomer().getCustomerId());
        return event;
    }
}

// ✅ 订阅者处理时反查聚合根，取权威状态
public void handle(OrderCreatedEvent event) {
    Order order = orderRepository.findById(Long.valueOf(event.getEntityId())).orElseThrow();
    // 基于 order 最新状态做后续处理
}
```

```java
// ❌ 反模式：事件携带整份业务快照
public class OrderCancelledEvent extends BaseDomainEvent {
    private final String customerId;
    private final long refundAmount;
    private final String cancelReason;
    private final List<OrderItemSnapshot> items;   // 快照膨胀、易过期
}
```

::: tip 可以带少量路由 ID，但不要带快照
事件可以携带少量**路由 / 上下文 ID**（如 `customerId`、`orderId`），用于订阅者定位聚合、路由到正确的处理分支；但**不要携带整份业务快照**——快照会过期、会随业务字段增长而膨胀，权威数据始终以聚合根为准。
:::

## 4. 事件的构造方式：buildEvent 静态工厂

事件实例统一通过 `buildEvent(聚合类型)` 静态工厂构造，入参是**当前聚合对象**，而不是零散原始值。聚合根在业务方法里只写 `collectEvent(OrderPaidEvent.buildEvent(this))`，事件字段的提取集中在一处，聚合字段变化时只改工厂。

参考示例（`examples/order-example` 的 `OrderPaidEvent`）：

```java
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderPaidEvent extends BaseDomainEvent {

    private LocalDateTime paidAt;

    private BigDecimal amount;

    public OrderPaidEvent(String entityId) {
        super(entityId);
    }

    public static OrderPaidEvent buildEvent(Order order) {
        OrderPaidEvent event = new OrderPaidEvent(order.getEntityId().toString());
        event.setPaidAt(order.getPaidAt());
        event.setAmount(order.getTotalAmount().getAmount());
        return event;
    }
}
```

**Lombok 约定**（与聚合根 / 值对象一致）：

- `@Getter` + `@Setter(AccessLevel.PROTECTED)`：事件字段对外只读，写入仅限 `buildEvent` 工厂与框架反序列化。
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`：供持久化 / 反序列化框架（Fastjson2）重建对象。
- 构造：`Event(String entityId)` 调 `super(entityId)` 把聚合标识交给 `BaseDomainEvent`；`entityId` / `eventId` / `occurredOn` 由基类承载，`operationCode` / `version` 由框架在 `collectEvent` 时回填。
- **禁用 `@Data` / `@Builder`**：事件等同性不承载业务含义、按对象身份区分；`@Builder` 无法把聚合标识传给基类构造。

**要点**：

- 工厂入参是聚合对象（业务方法里传 `this`），`buildEvent` 内先 `new Event(entityId)`、再 `set` 必要字段，避免在业务方法里拼零散参数。
- 事件字段只承载「定位聚合 + 少量路由 ID」，不携带业务快照（见 §3）。

## 5. 即时事件 vs 延迟事件

选择依据以**构造期 ID 是否确定**为主：

- **ID 由持久化后生成（自增主键、仓储回填雪花 ID）→ 延迟事件**：构造期 `getEntityId()` 还是 `null`，用 `collectEvent(Supplier<IDomainEvent>)`，`Supplier` 在事件真正发布时才执行，届时读到真实 ID。
- **ID 由业务传入（UUID / 雪花 ID）→ 即时事件**：事件内容在业务方法中已确定，直接构造。

```java
// 即时事件：ID 已确定，业务方法中直接经 buildEvent 构造
this.collectEvent(OrderCancelledEvent.buildEvent(this));

// 延迟事件：ID 构造期未知，发布时才经 buildEvent 构造、读真实 ID
this.collectEvent(() -> OrderCreatedEvent.buildEvent(this));
```

> ⚠️ **重要约束**：事件一律经 `buildEvent(聚合)` 静态工厂构造（见 §4）。延迟事件写 `() -> XxxEvent.buildEvent(this)`，不要在业务方法里手写 `new Event(...)` 拼零散参数——ID 后生成场景会定格错误的 `entityId`。延迟事件的完整时序见 [应用层落地模式](./application-collaboration.md)。

选择依据：

| 场景 | 推荐 |
| --- | --- |
| 构造期拿不到确定 ID（自增主键、仓储回填雪花 ID） | 延迟事件（强制） |
| 事件内容在业务方法中已确定、ID 已有 | 即时事件 |
| 事件构造开销大、可能不被发布 | 延迟事件 |

## 6. 事件粒度

一个业务操作可产生多个事件，每个事件表达一个**独立的领域事实**：

```java
public void pay() {
    this.status = "PAID";
    this.paidAt = LocalDateTime.now();
    this.recordOperation(OrderOperationRegistry.PAY);

    // 一个操作产生多个事件
    this.collectEvent(OrderPaidEvent.buildEvent(this));
    this.collectEvent(PaymentReceivedEvent.buildEvent(this));
    this.collectEvent(LoyaltyPointsEarnedEvent.buildEvent(this));
}
```

不要把多个事实合并成一个"大事件"：

```java
// ❌ 反模式：一个事件塞多个事实
this.collectEvent(OrderPaidAndInventoryDeductedAndPointsEarnedEvent.buildEvent(this));

// ✅ 推荐：拆分为独立事件
this.collectEvent(OrderPaidEvent.buildEvent(this));
this.collectEvent(InventoryDeductedEvent.buildEvent(this));
this.collectEvent(LoyaltyPointsEarnedEvent.buildEvent(this));
```

## 7. 事件与操作的关系

操作（`EntityOperation`）是"做了什么"，事件（`IDomainEvent`）是"发生了什么"：

```java
public void cancel() {
    this.status = "CANCELLED";
    this.recordOperation(OrderOperationRegistry.CANCEL);  // 操作：取消

    // 事件：订单已取消（operationCode 自动取 "CANCEL"）
    this.collectEvent(OrderCancelledEvent.buildEvent(this));
}
```

- 一个操作可产生零到多个事件
- 事件的 `operationCode` 自动归属到最近一次操作
- 订阅者可据 `operationCode` 判断事件来源

---

下一步：

- [Outbox 链路装配](./outbox-config.md)
- [领域事件](../core/domain-events.md)
