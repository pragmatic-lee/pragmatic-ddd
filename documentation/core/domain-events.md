# 领域事件

> 本文档介绍领域事件体系（`io.pragmatic.ddd.event`）的核心概念与用法。
> 前置阅读：[领域建模](./domain-modeling.md)。后续阅读：[应用服务](./application-service.md)。

## 1. 概述

领域事件代表一个**已发生、不可改变**的领域事实。在 Pragmatic DDD 中，事件由聚合根在业务方法中收集，由应用层在落库后统一发布。

核心设计：

- **即时事件与延迟事件分离**：即时事件立即构造，延迟事件用 `Supplier` 惰性求值（发布时才构造）
- **因果自动归属**：事件的 `operationCode` 自动从聚合根最近一次 `recordOperation` 获取
- **订阅者依赖图**：通过拓扑排序精确声明订阅者执行顺序
- **双重条件过滤**：运维级开关 + 业务级条件

## 2. 事件契约

### 2.1 `IDomainEvent`

```java
public interface IDomainEvent {
    String getEventId();         // 事件全局唯一标识
    String getEntityId();        // 实体身份标识
    Instant getOccurredOn();     // 事件发生时间
    String getOperationCode();   // 触发该事件的实体 Operation 编码
    long getVersion();           // 发布时刻的聚合根版本号
    default String getAggregateId() { return getEntityId(); }
}
```

### 2.2 `BaseDomainEvent`

`BaseDomainEvent` 是不可变事件基类，所有字段通过构造函数注入，不提供 setter：

```java
public abstract class BaseDomainEvent implements IDomainEvent {

    private final String eventId;
    private final String entityId;
    private final Instant occurredOn;

    // operationCode 和 version 由 AggregateRoot.collectEvent() 自动设置
    public String operationCode;
    public long version;

    // 常规构造：自动生成 eventId + 记录当前时间
    protected BaseDomainEvent(String entityId) { ... }

    // 事件重放构造：指定 eventId + 时间（用于从 Outbox 恢复）
    protected BaseDomainEvent(String entityId, String eventId, Instant occurredOn) { ... }
}
```

定义你自己的事件只需继承并添加业务字段：

```java
public class OrderCancelledEvent extends BaseDomainEvent {

    private final String cancelReason;

    public OrderCancelledEvent(String entityId, String cancelReason) {
        super(entityId);
        this.cancelReason = cancelReason;
    }

    public String getCancelReason() { return cancelReason; }
}
```

## 3. 在聚合根中收集事件

### 3.1 即时事件

```java
public class Order extends AggregateRoot<Long> {

    public void cancel() {
        this.status = "CANCELLED";
        this.markModified();
        this.getNewVersion();
        this.recordOperation(OrderOperationRegistry.CANCEL);

        // 即时收集：立即构造事件
        this.collectEvent(new OrderCancelledEvent(String.valueOf(this.getEntityId())));
    }
}
```

`collectEvent(BaseDomainEvent)` 会自动填充：

- `operationCode`：取最近一次 `recordOperation` 的 code
- `version`：取 `getNewVersion()` 的当前值

### 3.2 延迟事件（Supplier 惰性求值）

```java
public void submit() {
    this.status = "SUBMITTED";
    this.recordOperation(OrderOperationRegistry.SUBMIT);

    // 延迟收集：发布时才构造事件（适用于需要等最终状态的事件）
    this.collectEvent(() -> new OrderSubmittedEvent(
            String.valueOf(this.getEntityId()),
            this.getTotalAmount()));  // 发布时取最新金额
}
```

延迟事件在**发布时**才执行 `Supplier.get()`，此时 `operationCode` 和 `version` 已在收集时捕获并回填。

### 3.3 事件的因果归属

```java
// 方式一：自动取最近一次 recordOperation 的 code
this.recordOperation(OrderOperationRegistry.CANCEL);
this.collectEvent(new OrderCancelledEvent(...));  // operationCode = "CANCEL"

// 方式二：显式指定成因操作（优先级最高）
this.collectEvent(new OrderCancelledEvent(...), OrderOperationRegistry.CANCEL);
```

::: warning 必须先 recordOperation
如果启用了操作体系（`operationRegistry()` 返回非 null），在 `collectEvent` 前必须先 `recordOperation`，否则抛 `OperationException`。若不想启用操作体系，让 `operationRegistry()` 返回 `null` 即可。
:::

## 4. 事件管理器

### 4.1 `IEventManager` 端口

`IEventManager` 是组合端口，继承三类能力：

```java
public interface IEventManager extends IEventPublisher, IEventRegistry, IEventLifecycle {
    Map<String, List<String>> allEvents();                         // 全部事件名及订阅者
    List<ISubscriberOrderManager.OrderEdge> findEventDependencies(String eventName);
}
```

| 端口 | 职责 |
| --- | --- |
| `IEventPublisher` | `publish(event)` / `publishList(events)` 发布事件 |
| `IEventRegistry` | `registerSubscriber(...)` 注册订阅者 |
| `IEventLifecycle` | `init()` / `start()` / `shutdown()` 生命周期 |

### 4.2 本地实现 `ThreadPoolEventManager`

核心库内置了基于线程池的本地事件管理器，适合无需 MQ 的场景：

```java
LocalEventManagerConfig config = LocalEventManagerConfig.defaultConfig();
// 或从配置源绑定：
// LocalEventManagerConfig.bind(source)

ThreadPoolEventManager eventManager = new ThreadPoolEventManager(config);
eventManager.start();   // 启动
// ... 使用 ...
eventManager.shutdown(); // 关闭
```

`LocalEventManagerConfig` 配置项：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `schedulerThreads` | 2 | 调度线程数（延迟投递） |
| `corePoolSize` | `max(4, processors)` | 核心线程数 |
| `maxPoolSize` | `max(8, processors*2)` | 最大线程数 |
| `queueCapacity` | 1000 | 任务队列容量 |
| `deliveryDelayMs` | 1000 | 延迟投递间隔（毫秒） |
| `maxRetryTimes` | 3 | 最大重试次数 |
| `retryDelayMs` | 1500 | 重试间隔（毫秒） |

## 5. 订阅者注册

### 5.1 基本注册

```java
eventManager.registerSubscriber(
        "notify-customer",                          // 订阅者别名（唯一标识）
        OrderCancelledEvent.class,                   // 事件类型
        event -> {                                   // 处理逻辑
            sendNotification(event.getEntityId(), "订单已取消");
        });
```

### 5.2 执行条件

```java
eventManager.registerSubscriber(
        "refund",
        OrderCancelledEvent.class,
        event -> doRefund(event.getEntityId()),
        event -> event.getCancelReason() != null    // IExecuteCondition：仅取消原因非空时执行
);
```

### 5.3 投递策略

```java
// 延迟投递
eventManager.registerSubscriber(
        "delayed-notification",
        OrderCancelledEvent.class,
        event -> sendNotification(...),
        DeliveryPolicy.DELAYED);

// 立即投递（默认）
eventManager.registerSubscriber(
        "instant-log",
        OrderCancelledEvent.class,
        event -> logEvent(...),
        DeliveryPolicy.IMMEDIATE);
```

### 5.4 订阅者依赖顺序

声明某订阅者必须在另一个订阅者之后执行：

```java
eventManager.registerSubscriber(
        "update-read-model",
        OrderCancelledEvent.class,
        event -> updateReadModel(...),
        new DefaultExecuteCondition<>(),   // 执行条件
        "notify-customer");                // 依赖 "notify-customer" 先执行
```

框架通过拓扑排序保证依赖顺序，循环依赖会在启动时 fail-fast。

## 6. 事件序列化

`IEventSerializer` 是事件序列化端口，由各 MQ 集成模块提供实现：

```java
public interface IEventSerializer {
    String serialize(Object obj);
    <T> T deserialize(String json, Class<T> type);
    <T> T deserialize(String json, Type type);
}
```

- `pragmatic-ddd-mybatis` 的 `Fastjson2JsonSerializer` 同时实现此接口（FieldBased 模式）
- `pragmatic-ddd-rocketmq` 的 `Fastjson2EventSerializer` 提供独立的 RocketMQ 序列化实现

## 7. 事件生命周期

```java
eventManager.init();      // 初始化（注册内置组件）
eventManager.start();     // 启动（启动消费者/线程池）
// ... publish / register ...
eventManager.shutdown();  // 关闭（释放资源）
```

## 8. 事件与工作单元清理

聚合根收集的领域事件是**工作单元临时状态**，在事件分发完成后必须清空，防止事件泄漏到下一次操作：

```java
// CommandExecutor 模板内部自动调用：
aggregateRoot.clearWorkUnitState();  // 清空事件、操作记录与因果指针
```

- `CommandExecutor.execute()` 在 `persistAndDispatch` 之后自动调用
- `UnitOfWork.commit()` 在事件分发后自动调用
- 手动使用聚合根时，需在事件处理完后手动调用 `clearWorkUnitState()`

---

下一步：

- [应用服务](./application-service.md)：命令执行器与工作单元如何编排事件发布
- [操作追踪](./operation-tracking.md)：`recordOperation` 与事件的因果追踪
- [RocketMQ 集成](../integration/rocketmq.md)：事件的 MQ 可靠投递
