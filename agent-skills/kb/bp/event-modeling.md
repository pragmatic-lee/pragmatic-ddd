# BP 卡：领域事件建模（event-modeling）

> 源：`documentation/best-practices/event-modeling.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 本质

领域事件表达「已发生、不可改变」的领域事实：不可变、过去式、轻量（只带聚合 ID + 少量路由 ID，不携带业务快照）。

## 2. 命名

- 过去式：`OrderCreatedEvent` / `OrderCancelledEvent` / `PaymentReceivedEvent`（✅）。
- 反模式命令式：`CreateOrderEvent` / `CancelOrderEvent`（❌）。

## 3. 事件骨架（buildEvent 工厂）

```java
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(AccessLevel.PROTECTED)
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
```

## 4. 关键约定

- 事件类：`@Getter`+`@Setter(PROTECTED)`+`@NoArgsConstructor(PROTECTED)`，禁用 `@Data`/`@Builder`（硬约束 8）。
- `entityId`/`eventId`/`occurredOn` 由 `BaseDomainEvent` 承载；`operationCode`/`version` 由 `collectEvent` 回填。
- 构造 `Event(String entityId)` 调 `super(entityId)`。
- 只携带聚合 ID + 少量路由 ID；订阅者反查聚合根取权威状态。

## 5. 即时 vs 延迟事件（硬约束 6）

- ID 由业务传入（UUID/雪花）→ 即时：`collectEvent(OrderCancelledEvent.buildEvent(this))`。
- ID 构造期未知（自增/仓储回填）→ **延迟**：`collectEvent(() -> OrderCreatedEvent.buildEvent(this))`，`Supplier` 发布时才读真实 ID。

## 6. 粒度

一个操作可产多个事件，每事件一个独立事实（拆 `OrderPaidEvent`/`PaymentReceivedEvent`，勿合并大事件）。

## 7. 被读于

S4 `ddd-aggregate-create`（步骤 3 事件）、S7 `ddd-code-review`。
