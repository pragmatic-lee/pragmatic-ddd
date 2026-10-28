# BP 卡：事件建模指南（event-modeling）

> 源：`documentation/best-practices/event-modeling.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 事件的本质

领域事件代表一个**已发生、不可改变**的领域事实。核心特征：

- **不可变**：内容不可修改。
- **过去式**：描述"已发生"的事情。
- **轻量**：只携带聚合标识与少量路由 ID，不携带业务快照；权威数据由订阅者反查聚合根获取。

## 2. 事件命名规范

事件名用**过去式**：

```java
// ✅ 推荐
OrderCreatedEvent / OrderCancelledEvent / PaymentReceivedEvent / InventoryDeductedEvent
// ❌ 反模式（命令式）
CreateOrderEvent / CancelOrderEvent
```

## 3. 事件携带的数据

只携带**聚合标识与少量路由/上下文 ID**，不携带业务快照。订阅者处理时反查聚合根取权威状态：

```java
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

// 订阅者反查聚合根取权威状态
public void handle(OrderCreatedEvent event) {
    Order order = orderRepository.findById(Long.valueOf(event.getEntityId())).orElseThrow();
}
```

> 可带少量路由 ID（`customerId`/`orderId`）用于定位/路由，但**不要带整份业务快照**（膨胀、易过期）。

## 4. 构造方式：buildEvent 静态工厂

事件统一经 `buildEvent(聚合类型)` 静态工厂构造，入参是**当前聚合对象**（业务方法传 `this`），字段提取集中一处。

**Lombok 约定**（与聚合根/值对象一致）：

- `@Getter` + `@Setter(AccessLevel.PROTECTED)`：字段对外只读，写入仅限 `buildEvent` 与反序列化。
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)`：供持久化/反序列化重建。
- 构造：`Event(String entityId)` 调 `super(entityId)`；`entityId`/`eventId`/`occurredOn` 由基类承载，`operationCode`/`version` 由 `collectEvent` 回填。
- **禁用 `@Data`/`@Builder`**（事件等同性不承载业务含义；`@Builder` 无法把聚合标识传基类构造）。

**例外——异构数据同步事件**：不用 `buildEvent`，继承 `BaseDataSyncEvent` 并提供 `upsert(聚合)` / `purge(聚合)` 两个工厂（对应 `DataSyncAction.UPSERT` / `PURGE`）。`PURGE` 只由 `triggerDataSyncHook` 删除态分支产生，业务方法内只调 `upsert`。

## 5. 即时事件 vs 延迟事件

以**构造期 ID 是否确定**为主：

- **ID 持久化后生成（自增/仓储回填雪花）→ 延迟事件**：`collectEvent(Supplier<IDomainEvent>)`，`Supplier` 发布时才执行，读到真实 ID。
- **ID 业务传入（UUID/雪花）→ 即时事件**：`collectEvent(XxxEvent.buildEvent(this))` 直接构造。

```java
this.collectEvent(OrderCancelledEvent.buildEvent(this));   // 即时（ID 已确定）
this.collectEvent(() -> OrderCreatedEvent.buildEvent(this)); // 延迟（ID 构造期未知）
```

> ⚠️ 事件一律经 `buildEvent(聚合)` 构造；延迟事件写 `() -> XxxEvent.buildEvent(this)`，勿在业务方法里手写 `new Event(...)` 拼零散参数——ID 后生成场景会定格错误 `entityId`。

## 6. 事件粒度

一个操作可产生多个事件，每个表达一个**独立领域事实**；不要合并成"大事件"。

## 7. 事件与操作的关系

- 操作（`EntityOperation`）是"做了什么"，事件（`IDomainEvent`）是"发生了什么"。
- 一个操作可产生零到多个事件；事件 `operationCode` 自动归属最近一次操作；订阅者可据 `operationCode` 判断来源。

## 8. 被读于

S4 `ddd-aggregate-create`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
