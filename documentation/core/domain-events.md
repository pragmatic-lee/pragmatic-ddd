# 领域事件（Domain Events）

## 概述

**核心定位**：`io.pragmatic.ddd.event` 包提供领域事件的建模、收集、注册与本地发布能力。聚合根通过 `collectEvent` 在工作单元内收集事件，应用层在持久化后通过 `IEventManager.publish` 触发订阅者处理；框架内置基于线程池的本地实现，MQ 投递由 `pragmatic-ddd-rocketmq` / `pragmatic-ddd-kafka` 等外部模块承接。

**事件的业务定位**：领域事件是对"聚合根内已发生且不可变更的业务事实"的显式建模，作用是将"发生了什么"与"谁来处理"解耦——聚合根只在业务方法内收集事件，应用层在持久化后统一发布，由订阅者各自响应同一事实，从而在不污染领域逻辑的前提下驱动库存扣减、通知、对账等下游动作。

**概念层级 / 依赖关系**：

```text
IDomainEvent (事件契约接口)
  ↑ implements
BaseDomainEvent (抽象基类, 不可变, 提供 eventId/entityId/occurredOn + operationCode/version)

AggregateRoot (io.pragmatic.ddd.base)
  └── 持有 TriggeredEvents，提供 collectEvent / getDomainEvents / clearWorkUnitState

IEventManager (事件管理器端口)
  ├── extends IEventPublisher   (publish / publishList)
  ├── extends IEventRegistry    (registerSubscriber × 6 重载)
  └── extends IEventLifecycle   (init / start / shutdown)
        ↑ implements
  AbstractEventManager (注册骨架 + 条件判定 + 依赖传播)
        ↑ extends
  ThreadPoolEventManager (本地线程池实现, 调度器+执行器分离, 失败重试)

订阅者侧：
ISubscriber (标记接口, subscribedToEventType)
  ↑ extends
IEventListener<T> (handleEvent)
  └── 由 SubscriberFactory 基于 IHandle<T> 构建
```

## 核心概念详解

### 1. 事件契约：IDomainEvent 与 BaseDomainEvent

**契约 / 接口**：`IDomainEvent` 是事件的顶层契约，规定事件必须提供身份、时间与因果三类信息。

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `getEventId()` | `String` | 事件全局唯一标识，由 `BaseDomainEvent` 自动生成（`UUID`） |
| `getEntityId()` | `String` | 触发事件的实体标识 |
| `getOccurredOn()` | `Instant` | 事件发生时间，构造时记录 |
| `getOperationCode()` | `String` | 触发该事件的实体 Operation 编码，由 `collectEvent` 回填 |
| `getVersion()` | `long` | 发布时刻聚合根版本号，由 `collectEvent` 回填 |
| `getAggregateId()` | `String` | 默认返回 `getEntityId()`，可覆写 |

**基类能力**：`BaseDomainEvent` 提供不可变基类，所有字段经构造函数注入，由 Lombok `@Getter` 生成 getter，无 setter。

| 成员 | 类型 | 说明 |
|------|------|------|
| `eventId` | `String`（final） | 常规构造自动生成；重放构造由入参指定 |
| `entityId` | `String`（final） | 构造必填 |
| `occurredOn` | `Instant`（final） | 构造时 `Instant.now()` |
| `operationCode` | `String`（public 字段） | 由 `collectEvent` 或 Fastjson2 反序列化设置，**子类不应主动赋值** |
| `version` | `long`（public 字段） | 同上 |
| `BaseDomainEvent(String entityId)` | 构造 | 自动生成 eventId + 当前时间 |
| `BaseDomainEvent(String, String, Instant)` | 构造 | 事件重放：显式指定 eventId 与时间 |
| `BaseDomainEvent()` | 构造 | Fastjson2 `FieldBased` 反序列化入口，字段置 null |

**关键设计点 / 限制条件**：

- `BaseDomainEvent` 声明为不可变语义，但 `operationCode` / `version` 为 `public` 可变字段，仅供框架内部 `collectEvent` 与反序列化回填使用，**业务子类禁止赋值**。
- 等同性未覆写 `equals`/`hashCode`，事件实例以对象身份区分，不参与集合去重。

**代码示例**：

```java
// 事件定义：业务对象 + 动作 命名
// 业务意图：表示"订单已完成支付"这一不可变更的事实，用于驱动库存扣减、账单生成、用户通知等下游动作
public class OrderPaidEvent extends BaseDomainEvent {
    private final String orderId;
    private final BigDecimal amount;
    private final String orderStatus;

    public OrderPaidEvent(String orderId, BigDecimal amount, String orderStatus) {
        super(orderId);                 // 自动 eventId + occurredOn
        this.orderId = orderId;
        this.amount = amount;
        this.orderStatus = orderStatus;
    }
    // getters ...
}
```

### 2. 事件收集：AggregateRoot.collectEvent 与 TriggeredEvents

**契约 / 接口**：事件收集是聚合根工作单元的一部分，由 `io.pragmatic.ddd.base.AggregateRoot` 提供，不直接暴露给应用层。

| 方法 | 说明 |
|------|------|
| `collectEvent(BaseDomainEvent)` | 收集即时事件，自动回填 `operationCode`（取最近 `recordOperation`）与 `version` |
| `collectEvent(BaseDomainEvent, EntityOperation)` | 收集即时事件，显式指定成因操作（优先级最高） |
| `collectEvent(Supplier<IDomainEvent>)` | 收集延迟事件，发布时惰性求值并回填 `operationCode`/`version` |
| `getDomainEvents()` | 返回本工作单元已收集事件的不可变快照 |
| `clearWorkUnitState()` | 清空事件、操作与因果指针，由应用层在分发完成后调用 |
| `triggerDataSyncHook()` | 持久化前钩子，默认空实现，供子类发"聚合自身"异构事件 |

**基类能力**：`TriggeredEvents` 是底层收集容器，区分即时事件与延迟事件。

| 方法 | 说明 |
|------|------|
| `collect(IDomainEvent)` | 立即加入即时列表 |
| `collectDelayed(Supplier<IDomainEvent>)` | 登记 Supplier，读取时才求值 |
| `getEvents()` | 惰性求值全部延迟事件（仅一次）后返回 `List.copyOf` 不可变快照 |
| `drain()` | 原子取空：返回快照并清空内部状态，用于 outbox 派发 |
| `removeEvent(Class)` | 按类型（含子类）移除，调用前先物化延迟事件 |
| `clear()` | 清空即时与延迟登记 |

**关键设计点 / 限制条件**：

- `getNewVersion()` 幂等：首次调用返回 `oldVersion + 1` 并缓存，后续调用返回同一值。因此同一工作单元内所有事件共享同一版本号。
- `collectEvent(BaseDomainEvent)` 在未 `recordOperation` 且实体启用了 operation 体系时抛 `OperationException`：

> ⚠️ **重要约束**：启用 operation 体系的聚合根，调用无操作参数的 `collectEvent` 前必须先 `recordOperation`，或改用 `collectEvent(event, operation)` 显式指定成因。原因：`operationCode` 默认取 `lastRecordedOperation`；当 `operationRegistry()` 非空却无记录操作时，`resolveOperationCode()` 主动抛 `OperationException`，避免事件因果归属缺失。

- 延迟事件（`Supplier` 形式）在 `getEvents()`/`drain()` 时刻才构造，可捕获 flush 时最新实体状态；但 `materializeDeferred()` 保证整个生命周期只物化一次，根治重复读取产生不同实例的问题。
- `TriggeredEvents` 非线程安全，单线程工作单元内使用。

**代码示例**：

```java
public class Order extends AggregateRoot<String> {
    public void place() {
        this.recordOperation(OrderOperations.CREATE);   // 先记录操作
        // 业务作用：聚合根只收集"支付完成"这一事实，不关心谁来响应，响应方由应用层订阅决定
        this.collectEvent(new OrderPlacedEvent(this.getId(), this.orderNo, this.amount));
        // 或延迟事件：发布时才拿最新状态
        this.collectEvent(() -> new OrderPlacedEvent(this.getId(), this.orderNo, this.amount));
    }
}
```

### 3. 事件管理器端口：IEventManager 及其三个子端口

**契约 / 接口**：`IEventManager` 组合发布、注册、生命周期三类能力。

| 子端口 | 方法 | 说明 |
|--------|------|------|
| `IEventPublisher` | `publish(T event)` | 触发某事件的所有**根订阅者** |
| `IEventPublisher` | `publish(T event, String subscriber)` | 仅触发指定订阅者 |
| `IEventPublisher` | `publish(T event, String subscriber, boolean onlyThis)` | `onlyThis=true` 时不向后传播依赖订阅者 |
| `IEventPublisher` | `publishList(List<T> events)` | 批量发布，按顺序逐个 `publish` |
| `IEventRegistry` | `registerSubscriber(...)` | 6 个重载，组合 `IHandle` / `IExecuteCondition` / `DeliveryPolicy` / `dependSubscriber` |
| `IEventLifecycle` | `init()` / `start()` / `shutdown()` | 生命周期钩子，`AbstractEventManager` 默认空实现 |
| `IEventManager` | `allEvents()` | 返回事件名 → 订阅者别名列表映射 |
| `IEventManager` | `findEventDependencies(String)` | 返回某事件的依赖边集合 |

**基类能力**：`AbstractEventManager` 维护 `ConcurrentHashMap<String, Map<String, SubscriberInfo>> subscribers`，实现注册与条件判定。

**关键设计点 / 限制条件**：

- 注册别名（subscriberCode）在同一事件下**不允许重复**，重复注册抛 `IllegalArgumentException("<alias> is duplication")`。
- 发布以 `event.getClass().getSimpleName()` 作为事件名路由，注册时同样以 `subscribedToEventType().getSimpleName()` 作为 key，**事件类名即路由标识**。
- `publish(event)` 仅触发"根订阅者"（无前置依赖者），依赖订阅者由传播机制按序触发（见第 5 节）。

**代码示例（发布）**：

```java
// 应用层持久化后触发
List<IDomainEvent> events = order.getDomainEvents();
eventManager.publishList(events);          // 逐个 publish，仅触发各事件的根订阅者
```

### 4. 订阅者模型：ISubscriber / IEventListener / IHandle

**契约 / 接口**：

| 类型 | 约束 |
|------|------|
| `ISubscriber` | 标记接口，必须实现 `subscribedToEventType()` 声明关注事件类型 |
| `IEventListener<T>` | 继承 `ISubscriber`，声明 `handleEvent(T)` 处理逻辑 |
| `IHandle<T>` | 处理函数端口，仅 `handleEvent(T)`；注册时由 `SubscriberFactory.build(cls, handle)` 包装为 `IEventListener` |

**关键设计点 / 限制条件**：

- 注册时通过 `SubscriberFactory.build(cls, handle)` 由 `IHandle` + 事件类型自动生成 `IEventListener` 实例，`subscribedToEventType()` 即入参 `cls`。
- `IExecuteCondition.status(T)` 返回 `ExecuteStatus`（EXECUTE / SKIP）决定事件级是否执行；`switchStatus(alias)` 决定订阅者级开关。条件判定抛异常时统一视为 `SKIP`。

**代码示例（Handler 契约与实现）**：

```java
// 领域层：定义 Handler 契约接口（继承 IDomainService + IHandle<T>）
public interface IInventoryDeductionOnOrderPaid
        extends IDomainService, IHandle<OrderPaidEvent> {
}
```

当库存是**同系统内的其他聚合**时，订阅者经其应用服务发起；当库存是**外部系统**时，订阅者不应触碰其仓储或应用服务，而应在领域层声明一个外部依赖端口（框架 `io.pragmatic.ddd.dependency` 的 `@ExternalDependency` + `IDependency`），以依赖倒置方式跨系统调用：

```java
// 领域层声明：本订阅者依赖外部库存系统（扣减能力），仅描述契约，不感知远程调用细节（依赖倒置）
@ExternalDependency(targetName = "InventorySystem", type = DependencyType.EXTERNAL_SYSTEM,
        description = "支付完成后向外部库存系统发起扣减")
public interface IInventoryClientDependency extends IDependency {
    void deduct(String orderId, int amount);
}

// 应用层：实现 Handler，通过外部依赖端口调用库存系统，不直接触碰其仓储或应用服务
@Component
public class InventoryDeductionOnOrderPaid implements IInventoryDeductionOnOrderPaid {
    private final IInventoryClientDependency inventoryClient;

    public InventoryDeductionOnOrderPaid(IInventoryClientDependency inventoryClient) {
        this.inventoryClient = inventoryClient;
    }

    @Override
    public void handleEvent(OrderPaidEvent event) {
        // 业务作用：作为 OrderPaidEvent 的订阅者，在支付完成后向外部库存系统发起扣减，是事件驱动出的跨系统动作
        inventoryClient.deduct(event.getOrderId(), event.getAmount());
    }
}
```

`IInventoryClientDependency` 的实现（防腐适配器）由基础设施层完成 HTTP / RPC 调用，订阅者只依赖端口，符合依赖倒置与框架 `Dependency` 约定。

**代码示例（别名常量）**：

```java
public final class EventSubscriberAliases {
    private EventSubscriberAliases() {}

    public static final String INVENTORY_DEDUCTION_ON_ORDER_PAID = "inventory-deduction-on-order-paid";
    public static final String ORDER_NOTIFICATION_ON_PAID      = "order-notification-on-paid";
}
```

### 5. 依赖顺序与传播：SubscriberOrderManager

**契约 / 接口**：`ISubscriberOrderManager` 维护"事件名 → 依赖边"的顺序图，`SubscriberOrderManager` 为默认内存实现。

**关键设计点 / 限制条件**：

- 注册时通过 `registerDependency(eventName, alias, dependAlias)` 建立有向边：`dependAlias → alias`（即 `alias` 在 `dependAlias` 完成后触发）。`dependAlias` 为空时视为根（依赖 `ROOT_ALIAS`）。
- 发布时 `publish(event)` 取 `findRootSubscribers(eventName)` 作为入口；每个订阅者处理完成后回调 `findNextSubscribers` 触发其后继，形成链式传播。

> ⚠️ **重要约束**：注册依赖关系时对**循环依赖做即时检测**，`registerDependency` 发现新增边会成环时抛 `IllegalStateException("Cyclic dependency detected: ...")`。原因：传播是沿后继链递归触发的，成环会导致无限递归。

**代码示例（依赖顺序注册）**：

```java
// 业务意图：同一支付事件须保证 pre-check → deduction → notification 的严格顺序，避免通知先于扣减造成不一致
// 执行顺序：pre-check → deduction → notification
registry.registerSubscriber("inventory-pre-check",   OrderPaidEvent.class, preCheckHandler);
registry.registerSubscriber("inventory-deduction",   OrderPaidEvent.class, deductionHandler,
        null, "inventory-pre-check");                 // 依赖 pre-check 完成后触发
registry.registerSubscriber("order-notification",    OrderPaidEvent.class, notificationHandler,
        null, "inventory-deduction");                 // 依赖 deduction 完成后触发
```

**代码示例（执行条件注册）**：

```java
// 业务作用：仅当订单状态为 PAID 时才执行库存扣减，过滤掉非目标状态的事件
registry.registerSubscriber(
    EventSubscriberAliases.INVENTORY_DEDUCTION_ON_ORDER_PAID,
    OrderPaidEvent.class, handler,
    event -> "PAID".equals(event.getOrderStatus())
            ? ExecuteStatus.EXECUTE : ExecuteStatus.SKIP
);
```

**代码示例（延时投递注册）**：

```java
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;

// 业务作用：对无需实时响应的订阅者采用延时投递，由调度器等待 deliveryDelayMs 后再提交执行器
registry.registerSubscriber(
    "alias", OrderPaidEvent.class, handler,
    DeliveryPolicy.DELAYED          // 由调度器等待 deliveryDelayMs 后提交执行器
);
```

**代码示例（集中注册引导）**：

```java
@Configuration
public class EventSubscriberBootstrap {

    @Bean
    public ApplicationRunner registerEventSubscribers(
            IEventManager eventManager,
            InventoryDeductionOnOrderPaid inventoryDeduction,
            OrderPaidNotificationHandler orderNotification) {
        return args -> {
            IEventRegistry registry = eventManager;   // IEventManager 同时是 IEventRegistry
            registry.registerSubscriber(
                EventSubscriberAliases.INVENTORY_DEDUCTION_ON_ORDER_PAID,
                OrderPaidEvent.class, inventoryDeduction);
            registry.registerSubscriber(
                EventSubscriberAliases.ORDER_NOTIFICATION_ON_PAID,
                OrderPaidEvent.class, orderNotification);
        };
    }
}
```

### 6. 本地实现：ThreadPoolEventManager 与 Task

**契约 / 接口**：`ThreadPoolEventManager extends AbstractEventManager`，采用"调度器 + 执行器分离"架构。

| 配置项（LocalEventManagerConfig） | 默认值 | 说明 |
|------|--------|------|
| `schedulerThreads` | 2 | 延时调度器线程数（只等待，不执行业务） |
| `corePoolSize` | `max(4, processors)` | 执行器核心线程 |
| `maxPoolSize` | `max(8, processors*2)` | 执行器最大线程 |
| `queueCapacity` | 1000 | 有界队列容量 |
| `keepAliveSeconds` | 60 | 空闲线程存活时间 |
| `deliveryDelayMs` | 1000 | `DELAYED` 策略的延时时长 |
| `maxRetryTimes` | 3 | 处理失败最大重试次数 |
| `retryDelayMs` | 1500 | 重试间隔 |

**关键设计点 / 限制条件**：

- 执行器队列满时采用 `CallerRunsPolicy`（调用方线程直接执行），作为背压保护，不丢弃任务。
- `DeliveryPolicy.DELAYED` 的事件由调度器等待 `deliveryDelayMs` 后提交执行器；`IMMEDIATE` 直接提交。
- 处理失败（`handleEvent` 抛异常）由 `Task` 按 `maxRetryTimes` 延时 `retryDelayMs` 重试；重试耗尽仅 `log.error`，**不抛异常、不阻断其他订阅者**。
- 线程均为守护线程（`setDaemon(true)`），JVM 退出时不会阻止进程结束。

**代码示例（配置绑定）**：

```java
LocalEventManagerConfig config = LocalEventManagerConfig.bind(configSource); // 前缀 event.local.*
ThreadPoolEventManager manager = new ThreadPoolEventManager(config, new SubscriberOrderManager());
```

## 关键机制与避坑指南

**发布双条件判定**：每次 `publish` 对订阅者执行两道判定，任一为 `SKIP` 即跳过：
1. 订阅者级开关 `switchCheck(alias, condition)` —— 用于外部动态启停某订阅者。
2. 事件级条件 `executeCheck(event, condition)` —— 基于事件内容决定。

> ⚠️ **重要约束**：条件判定（`status` / `switchStatus`）抛任何异常都被捕获并视为 `SKIP`，不会向上传播。因此条件逻辑中的 bug 不会中断发布，但会导致订阅者静默不执行，排查时需检查条件实现。

**事件路由基于类名**：`publish` 与 `registerSubscriber` 均以 `getSimpleName()` 作 key。若两个不同事件的简单类名相同（如不同包下同名类），会路由到同一订阅者集合，造成串事件。

> ⚠️ **重要约束**：必须调用 `clearWorkUnitState()`。聚合根的事件在工作单元内累积，若不清理，`getDomainEvents()` 会持续返回历史事件，重复 `publish` 导致同一事件被多次处理。清理时机由应用层在事件分发完成后负责。

**延迟事件与版本回填**：延迟事件在 `getEvents()` 时才构造，`collectEvent(Supplier)` 在构造后回填 `operationCode`/`version`，因此延迟事件拿到的 `version` 是该 Supplier 被求值时刻的 `getNewVersion()` 值，可能与即时事件一致（因 `getNewVersion` 幂等）。

**代码示例（应用层完整流程：收集 → 发布 → 清理）**：

```java
// 1. 业务方法内部 collectEvent（见第 2 节）
Order order = orderRepository.findById(orderId);
order.pay();                         // 内部 collectEvent(new OrderPaidEvent(...))，聚合根记录"支付完成"事实

// 2. 持久化聚合根
orderRepository.save(order);

// 3. 发布事实，由订阅者各自响应
eventManager.publishList(order.getDomainEvents());

// 4. 清理，防止同一事实被重复发布
order.clearWorkUnitState();
```

事件异常继承链：

```text
PragmaticException (基类)
  └── EventException (abstract)
        ├── PublishEventException      发布失败（携带 cause）
        └── RegisterDomainEventException 注册失败（携带 cause）
```

| 异常 | 触发场景 | 构造 |
|------|----------|------|
| `PublishEventException` | 发布过程失败（如订阅者处理抛错且未被 Task 内部吞掉的场景） | `(String, Throwable)` |
| `RegisterDomainEventException` | 注册阶段失败 | `(String)` / `(String, Throwable)` |
| `IllegalArgumentException` | 同事件重复注册别名 | `"<alias> is duplication"` |
| `IllegalStateException` | 注册产生循环依赖 | `"Cyclic dependency detected: ..."` |
| `OperationException` | 未 recordOperation 却调用无参 collectEvent（启用 operation 体系时） | 内部消息 |

**最佳捕获实践**：

- `Task` 已在本地实现内部捕获订阅者异常并走重试/日志，应用层通常无需捕获单个订阅者错误。
- 注册阶段异常（`IllegalArgumentException` / `IllegalStateException`）应在启动时 fail-fast，避免带病运行。
- `PublishEventException` / `RegisterDomainEventException` 均为受检语义的 `RuntimeException` 子类，由调用方按需捕获。

## 总结速查

| 概念 | 使用方式 | 最关键约束 |
|------|----------|------------|
| `BaseDomainEvent` | 继承并实现业务字段，构造传 `entityId` | `operationCode`/`version` 由框架回填，子类禁赋值 |
| `AggregateRoot.collectEvent` | 业务方法内 `recordOperation` 后调用 | 启用 operation 体系时必带操作，否则抛 `OperationException` |
| `TriggeredEvents` | 经聚合根 `getDomainEvents()` 读取 | 非线程安全；延迟事件仅物化一次 |
| `IEventManager.registerSubscriber` | 6 重载组合条件/依赖/策略 | 同事件别名不可重复；类名即路由 key |
| `SubscriberOrderManager` | 注册时声明 `dependSubscriber` | 循环依赖注册即抛 `IllegalStateException` |
| `ThreadPoolEventManager` | 配 `LocalEventManagerConfig` 构造 | 满队列 CallerRuns 背压；失败重试耗尽仅日志 |
| `clearWorkUnitState()` | 应用层分发完成后调用 | 不调用会重复发布历史事件 |

## 命名规范速查

结合框架事实约束（发布与注册均以 `getSimpleName()` 作事件路由 key，别名在同一事件下不可重复），约定如下：

| 元素 | 格式 | 示例 |
|------|------|------|
| 领域事件类 | `{业务对象}{动作}Event`，类名即路由标识 | `OrderPaidEvent` |
| 事件字段 / 载荷 | 业务语义命名，不可变 final 字段 | `orderId`、`amount` |
| Handler 契约接口 | `I{业务动作}On{触发事件}`（继承 `IDomainService` + `IHandle<T>`） | `IInventoryDeductionOnOrderPaid` |
| Handler 实现类 | `{业务动作}On{触发事件}`（去 `I` 前缀） | `InventoryDeductionOnOrderPaid` |
| 订阅者别名（alias） | `kebab-case`：`{上下文}-{动作}-on-{触发事件}`，同一事件下唯一 | `inventory-deduction-on-order-paid` |
| 别名常量 | 大写 + 下划线，集中放 `EventSubscriberAliases` | `INVENTORY_DEDUCTION_ON_ORDER_PAID` |
| 外部依赖接口 | `I{目标系统}ClientDependency`（继承 `IDependency`，标 `@ExternalDependency`） | `IInventoryClientDependency` |

> ⚠️ **重要约束**：事件类名（简单名）直接作为路由 key，因此不同事件**不允许简单类名相同**（即使包不同也会串事件）；订阅者别名在同一事件下**不允许重复**，重复注册抛 `IllegalArgumentException("<alias> is duplication")`。
