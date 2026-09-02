# 事件订阅领域服务落地模式

> 事件订阅领域服务（`EVENT_SUBSCRIBER`）是四类领域服务中唯一**由事件总线驱动**的一类：领域层声明"某事件发生之后执行什么业务动作"，应用层提供实现，装配期显式注册到 `IEventRegistry`。
> 前置阅读：[事件建模指南](./event-modeling.md) · [领域服务落地模式](./domain-service.md)。
> 下文以订单（`Order`）的支付事件与数据同步事件为示例贯穿全文；其他模块套用时，把 `Order` 换成目标聚合、把 `Sms` / `Points` / `Es` / `Redis` 换成目标动作或目标存储即可。

## 1. 本质与定位

事件订阅领域服务由**三层角色**构成，并由应用层的一张注册表完成绑定：

| 角色 | 所在层 | 职责 |
| --- | --- | --- |
| 订阅契约 | 领域层 `domain/{agg}/service/` | 声明"关注哪个事件、要做什么业务动作"，只有接口签名 |
| 订阅者实现 | 应用层 `application/{agg}/service/` | `handleEvent` 的具体编排，可依赖仓储、外部依赖端口、投影构件 |
| 订阅注册表 | 应用层 `application/{agg}/subscriber/` | 把「事件类型 + 订阅者别名 + 订阅者实例」绑定到事件总线 |
| 基础设施 | 基础设施层 | 防腐适配器（`IDependency` 实现）、投影器 / 物化器等被调用的构件 |

框架侧的端口关系：

```text
IDomainService (io.pragmatic.ddd.service)          标记接口，category() 读 @DomainService
  └── IEventSubscriberService<T> (service)         extends IDomainService, IHandle<T>
IHandle<T extends IDomainEvent> (event.spi)        void handleEvent(T)
IEventRegistry (event.spi)                         registerSubscriber(...) × 6 重载
  └── IEventManager extends IEventPublisher, IEventRegistry, IEventLifecycle
```

继承 `IEventSubscriberService<T>` 与同时写 `extends IDomainService, IHandle<T>` 是等价的（前者是无方法体的组合接口）；同一个项目内选定一种写法并保持统一。

### 1.1 与写模型、与 MQ 消费者的边界

| 对比项 | 事件订阅领域服务 | 应用服务（写路径） | MQ 消费者 |
| --- | --- | --- | --- |
| 触发源 | 已发布的领域事件 | 外部请求 / 命令 | 中间件投递的报文 |
| 能否编排多个聚合的写操作 | 不在 `handleEvent` 内同步编排 | 可以（工作单元内） | — |
| 依赖形态 | 领域依赖端口（`IDependency`）+ 仓储 | 仓储、领域服务契约 | 客户端 SDK |
| 出现的技术件 | 无（不出现 MQ / ES / Redis 客户端） | 无 | 有（框架内部封装） |

订阅者只依赖 `IHandle<T>` 与领域依赖端口，不出现投递方式（本地线程池 / RocketMQ）与存储类型（ES / Redis）相关的代码：换事件管理器实现或换副本存储时，订阅者与领域契约均不改动。

## 2. 命名与包结构

### 2.1 包结构

```text
domain/order/
├── event/        领域事件（OrderPaidEvent / OrderDataSyncEvent + buildEvent 工厂）
├── dependency/   外部依赖端口（ISmsDependency / IUserPointsDependency）
├── projection/   投影视图 + 目标常量（读模型型订阅者用）
└── service/      事件订阅契约（仅接口）
    ├── IOrderPaidSmsNotifyHandle.java
    ├── IOrderPaidPointsGrantHandle.java
    ├── IOrderDataSyncEsProjectionHandle.java
    └── IOrderRedisCacheHandle.java

application/order/
├── service/      订阅者实现（@Component，接口名去 I）
    ├── OrderPaidSmsNotifyHandle.java
    ├── OrderPaidPointsGrantHandle.java
    ├── OrderDataSyncEsProjectionHandle.java
    └── OrderRedisCacheHandle.java
└── subscriber/
    └── OrderEventSubscriberRegistry.java       事件类型 → 订阅者绑定

infrastructure/order/
├── dependency/   SmsDependencyAdapter 等（实现领域 IDependency 端口）
└── projection/   Projector / Source（读模型型订阅者编排的构件）
```

### 2.2 命名规范

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 领域事件类 | `{聚合}{动作}Event` | `OrderPaidEvent`、`OrderDataSyncEvent` |
| 订阅契约接口 | `I{事件}{业务意图}Handle` | `IOrderPaidSmsNotifyHandle` |
| 订阅者实现 | 接口名去 `I` | `OrderPaidSmsNotifyHandle` |
| 订阅注册表 | `{聚合}EventSubscriberRegistry` | `OrderEventSubscriberRegistry` |
| 订阅者别名 | kebab-case：`{动作}-on-{触发事件}` | `sms-notify-on-order-paid` |
| 别名常量 | 大写 + 下划线，集中在常量类 | `EventSubscriberAliases.SMS_NOTIFY_ON_ORDER_PAID` |
| 外部依赖端口 | `I{目标系统}Dependency extends IDependency` | `ISmsDependency` |

```java
// ✅ 推荐：接口名带业务意图，实现为接口名去 I
public interface IOrderPaidSmsNotifyHandle extends IDomainService, IHandle<OrderPaidEvent> { }
public class OrderPaidSmsNotifyHandle implements IOrderPaidSmsNotifyHandle { }

// ❌ 反模式：用技术占位词命名，接口名不成领域文档
public interface IOrderPaidHandler extends IDomainService, IHandle<OrderPaidEvent> { }
public interface IOrderEventProcessor extends IDomainService, IHandle<OrderPaidEvent> { }
```

> 接口名必须体现业务意图（发短信 / 发积分 / 投影到 ES / 投影到 Redis），不得用 `Handler` / `Processor` / `Listener` 单独占位。接口名本身即领域文档。

## 3. 职责承载

| 承载 | 不承载 |
| --- | --- |
| 一个明确的业务动作（发通知、发积分、物化副本） | 多个不相关动作塞进同一个 `handleEvent` |
| 一个事件类型（`IHandle<T>` 的 `T`） | 在 `handleEvent` 内按事件子类型 `instanceof` 分支 |
| 回源聚合取权威状态（`findById`） | 事件携带业务快照 |
| 对外部系统的调用经领域依赖端口 `IDependency` | 直接注入第三方 SDK / HTTP 客户端 |
| 读模型副本的物化编排 | 投影映射规则、存储客户端（在基础设施层） |
| 装配期的显式注册绑定 | 自扫描、自注册 |

## 4. 落地方式（核心）

按「事件 → 领域契约 → 应用层实现 → 注册表绑定」的顺序落地。

### 4.1 领域事件：只带聚合标识 + `buildEvent` 工厂

```java
// domain/order/event/OrderDataSyncEvent.java —— 数据同步事件，只带聚合标识
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDataSyncEvent extends BaseDomainEvent {

    public OrderDataSyncEvent(String entityId) {
        super(entityId);
    }

    public static OrderDataSyncEvent buildEvent(Order order) {
        return new OrderDataSyncEvent(order.getEntityId().toString());
    }
}
```

```java
// domain/order/event/OrderPaidEvent.java —— 业务事件，带少量路由 / 上下文 ID
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

编写规则：

- **只携带聚合标识**，可带少量路由 / 上下文 ID（支付时刻、金额），**不携带业务快照**。
- 提供 **静态工厂 `buildEvent(聚合根)`**，把「聚合 → 事件」的取值收口在事件类内部。
- 构造为 `protected` / 无参构造为 `protected`：`@NoArgsConstructor(access = PROTECTED)` 是给反序列化留的入口，业务代码一律走 `buildEvent`。
- `operationCode` / `version` 由框架在 `collectEvent` 时回填，子类不赋值。
- 事件建模的完整规范见 [事件建模指南](./event-modeling.md)。

### 4.2 领域层契约：一行接口 + `@DomainService`

```java
// domain/order/service/IOrderPaidSmsNotifyHandle.java
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderPaidEvent",
        description = "订单支付成功后向用户发送短信通知")
public interface IOrderPaidSmsNotifyHandle
        extends IDomainService, IHandle<OrderPaidEvent> {
}
```

```java
// domain/order/service/IOrderDataSyncEsProjectionHandle.java
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderDataSyncEvent",
        description = "订单数据同步后投影到 ES")
public interface IOrderDataSyncEsProjectionHandle
        extends IDomainService, IHandle<OrderDataSyncEvent> {
}
```

编写规则：

- **一个契约只订阅一类事件**；同一事件可以有多个订阅者（短信、积分、ES、Redis 各一个），彼此**平级、互不引用**。
- 必须标注 `@DomainService(category = EVENT_SUBSCRIBER, targetName = "<事件类名>", description = "...")`；`targetName` 填被订阅的事件名。
- 继承 `IHandle<T>` 即获得 `void handleEvent(T)`，不在接口里重复声明该方法。
- 接口内**不写任何实现、不加任何方法**；需要参数化的行为属其他三类领域服务。

> ⚠️ **重要约束**：`category()` 的分类**只来自 `@DomainService` 注解**（查找链：实现类 → 所实现接口 → 父类）。不标注则恒返回 `UNKNOWN`，`handleEvent` 的调用不受影响，但依赖分类的扫描 / 校验逻辑无法识别该服务。

### 4.3 应用层实现：两类形态

订阅者实现按业务意图分两类形态，结构一致：**取标识 → 回源聚合 → 空值短路 → 执行动作**。

#### 形态 A：外部系统联动（副作用型）

以订单支付后发短信、发积分为例：

```java
// application/order/service/OrderPaidSmsNotifyHandle.java
@Component
public class OrderPaidSmsNotifyHandle implements IOrderPaidSmsNotifyHandle {

    private final OrderRepository orderRepository;

    private final IUserDependency userDependency;

    private final ISmsDependency smsDependency;

    public OrderPaidSmsNotifyHandle(
            OrderRepository orderRepository,
            IUserDependency userDependency,
            ISmsDependency smsDependency) {
        this.orderRepository = orderRepository;
        this.userDependency = userDependency;
        this.smsDependency = smsDependency;
    }

    @Override
    public void handleEvent(OrderPaidEvent event) {
        Long id = Long.valueOf(event.getEntityId());
        Order order = orderRepository.findById(id);
        if (order == null) {
            return;
        }
        Customer customer = order.getCustomer();
        String mobile = userDependency.getUserMobile(customer.getCustomerId().toString());
        if (mobile == null || mobile.isBlank()) {
            return;
        }
        String content = "您的订单 " + order.getEntityId()
                + " 已支付成功，实付金额 " + order.getActualAmount().getAmount() + " 元";
        smsDependency.sendSms(new SmsMessage(mobile, content));
    }
}
```

```java
// application/order/service/OrderPaidPointsGrantHandle.java
@Component
public class OrderPaidPointsGrantHandle implements IOrderPaidPointsGrantHandle {

    private static final int POINTS_PER_YUAN = 1;

    private final OrderRepository orderRepository;

    private final IUserPointsDependency userPointsDependency;

    public OrderPaidPointsGrantHandle(
            OrderRepository orderRepository,
            IUserPointsDependency userPointsDependency) {
        this.orderRepository = orderRepository;
        this.userPointsDependency = userPointsDependency;
    }

    @Override
    public void handleEvent(OrderPaidEvent event) {
        Long id = Long.valueOf(event.getEntityId());
        Order order = orderRepository.findById(id);
        if (order == null) {
            return;
        }
        Customer customer = order.getCustomer();
        int points = order.getActualAmount().getAmount().intValue() * POINTS_PER_YUAN;
        if (points <= 0) {
            return;
        }
        // bizId 用聚合标识，供下游做幂等去重
        IncreasePointsCommand command = new IncreasePointsCommand(
                customer.getCustomerId(), points, order.getEntityId().toString());
        userPointsDependency.increasePoints(command);
    }
}
```

编写规则：

- **对外部系统的调用一律经领域依赖端口**（`IDependency` 子接口 + `@ExternalDependency`），实现放基础设施层的防腐适配器（如 `SmsDependencyAdapter`）。订阅者里不出现 HTTP 客户端、第三方 SDK。
- **先 `findById` 回源聚合**再取业务数据：事件不带快照，聚合根是权威状态来源。
- **每一步空值 / 非法值短路 `return`**：聚合不存在、手机号为空、积分为 0 直接结束，不抛业务异常打断事件分发。
- **外部副作用带幂等键**：MQ 重投会重复触发，发积分这类动作把聚合标识作为 `bizId` 传给下游。

#### 形态 B：读模型副本物化

以订单数据同步后投影到 ES、Redis 为例：

```java
// application/order/service/OrderDataSyncEsProjectionHandle.java
@Component
public class OrderDataSyncEsProjectionHandle implements IOrderDataSyncEsProjectionHandle {

    private final OrderRepository orderRepository;

    private final ProjectorRegistry projectorRegistry;

    public OrderDataSyncEsProjectionHandle(
            OrderRepository orderRepository,
            ProjectorRegistry projectorRegistry) {
        this.orderRepository = orderRepository;
        this.projectorRegistry = projectorRegistry;
    }

    /**
     * 处理订单数据同步事件：加载最新聚合，经投影器生成视图后物化到 ES，写入版本取自事件携带的副本版本。
     *
     * @param event 订单数据同步事件
     */
    @Override
    public void handleEvent(OrderDataSyncEvent event) {
        Long id = Long.valueOf(event.getEntityId());
        Order order = orderRepository.findById(id);
        if (order == null) {
            return;
        }
        aggregateProjectorSupport.sync(order, OrderEsTargets.TARGET_ES_ORDERS);
    }
}
```

```java
// application/order/service/OrderRedisCacheHandle.java —— 同一事件的另一个平级订阅者
@Component
public class OrderRedisCacheHandle implements IOrderRedisCacheHandle {
    // 结构同 OrderDataSyncEsProjectionHandle：findById → aggregateProjectorSupport.sync(order, TARGET_REDIS_ORDERS)
}
```

编写规则：

- **一个副本一个订阅者**：ES 与 Redis 是两个平级订阅者，各自驱动自己的 `Source`（写读一体，落在 `projection/materializer/` 包），互不引用、互不感知。
- **物化版本取 `event.getVersion()`**（`collectEvent` 回填的 `getNewVersion()`），不取 `order.getOldVersion()`——后者是对账补偿路径的口径。
- **编排收敛到 `AggregateProjectorSupport.sync(aggregate, source)`**：订阅者只做 `findById` + `sync` 两件事，project→materialize 的内部四步由门面与源完成；投影映射与存储读写留在基础设施层，订阅者只做装配编排。
- 投影 / 源 / 检索 / 裁剪 / 对账的完整落地见 [投影读模型代码落地指南](./projection-design.md)。

### 4.4 装配绑定：`{聚合}EventSubscriberRegistry`

```java
// application/order/subscriber/OrderEventSubscriberRegistry.java
@Configuration
public class OrderEventSubscriberRegistry {

    public OrderEventSubscriberRegistry(IEventRegistry evtManager,
                                        OrderDataSyncEsProjectionHandle orderDataSyncEsProjectionHandle,
                                        OrderRedisCacheHandle orderRedisCacheHandle,
                                        OrderPaidSmsNotifyHandle orderPaidSmsNotifyHandle,
                                        OrderPaidPointsGrantHandle orderPaidPointsGrantHandle) {

        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, orderDataSyncEsProjectionHandle);
        evtManager.registerSubscriber("redis-cache", OrderDataSyncEvent.class, orderRedisCacheHandle);
        evtManager.registerSubscriber("sms-notify-on-order-paid", OrderPaidEvent.class, orderPaidSmsNotifyHandle);
        evtManager.registerSubscriber("points-grant-on-order-paid", OrderPaidEvent.class, orderPaidPointsGrantHandle);
    }
}
```

推荐把别名收口为常量（本示例用了字面量，多上下文应用建议改为常量类）：

```java
public final class EventSubscriberAliases {
    public static final String ES = "es";
    public static final String REDIS_CACHE = "redis-cache";
    public static final String SMS_NOTIFY_ON_ORDER_PAID = "sms-notify-on-order-paid";
    public static final String POINTS_GRANT_ON_ORDER_PAID = "points-grant-on-order-paid";

    private EventSubscriberAliases() {
    }
}
```

编写规则：

- **注册表只做绑定，不做业务**：一行一条 `registerSubscriber`，不在这里写判断、不在这里拼装参数。
- **注入 `IEventRegistry`**：`IEventManager` 继承 `IEventRegistry`，装配的 `eventManager` Bean 可直接按 `IEventRegistry` 注入。
- **在 `@Configuration` 构造期注册**：Spring 先构造配置类 Bean，再由 `ApplicationRunner` 调 `eventManager.start()`（RocketMQ 场景），注册天然早于消费启动。
- **一个聚合一张注册表**：不建全局注册表，也不按业务动作拆多张。
- 需要顺序保证时用 `dependSubscriber` 重载；需要按内容过滤时用 `IExecuteCondition` 重载（见 §5）。

## 5. 关键机制与避坑

### 5.1 注册是显式的，框架不扫描

> ⚠️ **重要约束**：框架**不扫描** `@Component` 的 `IHandle` 实现，也没有 Spring Boot 自动装配模块做这件事。订阅者必须经 `IEventRegistry.registerSubscriber(...)` 显式注册，否则事件发布后 `handleEvent` 不会被调用，且**没有任何日志或异常提示**。
>
> 判定方法：在注册表里数一遍 `registerSubscriber` 的条数，与订阅者实现类的数量比对。

### 5.2 事件只带标识，处理时回源聚合

> ⚠️ **重要约束**：`handleEvent` 内必须 `findById` 重新加载聚合，不能依赖事件载荷做业务判断。事件从产生到被处理可能跨越任意时长（MQ 积压、延迟投递、消费重试），载荷里的快照会过期；聚合根是唯一权威状态来源。

### 5.3 事件类名即路由 key

`registerSubscriber` 以 `subscribedToEventType().getSimpleName()` 注册，`publish` 以 `event.getClass().getSimpleName()` 路由。

> ⚠️ **重要约束**：不同包下的两个事件**不得同名**（简单类名相同会串事件，路由到同一订阅者集合）。注册时传入的 `Class<T>` 必须与 `IHandle<T>` 的 `T` 一致，否则编译期通过、运行期永不触发。

### 5.4 别名在同一事件下唯一

订阅者表是两级映射 `Map<事件名, Map<别名, SubscriberInfo>>`，因此：

> ⚠️ **重要约束**：别名唯一性是**按事件**判定的，同一事件下重复注册同一别名抛 `IllegalArgumentException("<alias> is duplication")`。跨事件允许同名（`OrderDataSyncEvent` 下可以有 `"es"`，另一事件下也可以有 `"es"`），但建议**全局唯一**——`IEventManager.allEvents()` 按事件分组返回别名列表，同名别名在排查事件分发与 topic 路由时无法直接定位。

### 5.5 两重条件判定，异常一律视为 SKIP

`IExecuteCondition` 有两条判定：`switchStatus(alias)`（订阅者级开关，可按配置中心动态启停）与 `status(event)`（事件级条件，按内容过滤）。缺省实现返回 `EXECUTE`。

> ⚠️ **重要约束**：条件实现抛任何异常都会被 `AbstractEventManager` 捕获并当作 `SKIP`，**不会上抛、不会中断事件分发**。条件逻辑里的 bug 表现为「订阅者静默不执行」，排查时优先检查条件实现而不是事件发布链路。

### 5.6 顺序、传播与依赖

- `publish(event)` 只触发**根订阅者**（无 `dependSubscriber` 的），依赖订阅者由传播机制按序触发。
- `publish(event, alias)` 只触发指定别名的订阅者；`publish(event, alias, true)` 不再向后传播依赖。
- 依赖登记成环时注册即抛 `IllegalStateException("Cyclic dependency detected: ...")`。

> ⚠️ **重要约束**：需要严格顺序（如「预校验 → 扣减 → 通知」）时用 `dependSubscriber` 重载显式声明，**不要依赖注册顺序**——注册顺序不构成执行顺序保证。

### 5.7 未登记构件的两种行为

| 调用 | 未登记时 | 原因 |
| --- | --- | --- |
| `projectorRegistry.resolveProjector(...)` | 返回 `null` | 投影器可选，缺登记则本次不物化 |
| `projectorRegistry.resolveSource(source)` | 返回 `null` | 源可选，缺登记则本次不物化 |
| `projectorRegistry.getSearcher(...)` / `getByIdSearcher(...)` | **抛异常** | 检索器缺失属接线 bug |

因此读模型型订阅者经 `AggregateProjectorSupport.sync` 桥接即可，`sync` 内部对缺失投影器 / 源做 `null` 短路，但**不要**把它当正常情况静默——副本会持续落后，应配合启动自检暴露。

### 5.8 幂等与版本

- 事件可能被重复投递（MQ 重投、消费重试）。外部副作用必须带幂等键（示例用订单标识作 `bizId`）。
- 副本物化用 External 版本：写入版本为 `event.getVersion()`，落后版本会被存储拒绝并抛冲突异常，由 [对账补偿](./projection-design.md) 兜底。
- 顺序颠倒的事件（先到版本大、后到版本小）会被存储拒绝，不会写脏副本。

### 5.9 投递策略与生命周期

- `DeliveryPolicy.DELAYED` 由调度器延迟提交，适合无需实时响应的订阅者；`IMMEDIATE` 为默认。
- RocketMQ 场景下 `eventManager.start()` 必须执行（示例由 `ApplicationRunner` 触发），漏掉则 Consumer 不订阅、事件无人消费。装配见 [RocketMQ 配置设计原则](./rocketmq-config.md) 与 [事件管理器装配与选择](./event-manager-config.md)。

### 5.10 异常不要吞

> ⚠️ **重要约束**：`handleEvent` 内不要 `try-catch` 后静默返回。基础设施异常上抛后由事件管理器按 `DeliveryPolicy` 与重试策略处理（RocketMQ 场景返回 `RECONSUME_LATER`，重试耗尽进死信）。吞掉异常会让副本落后或动作丢失，且对账路径无法感知。
>
> 例外：业务上「无需处理」的判断（聚合不存在、手机号为空、积分为 0）用 `if` 短路 `return`，这是**正常的空值处理**，不是吞异常。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 以为 `@Component` 会被自动扫描注册 | 事件发布后 `handleEvent` 从不触发，无日志无异常 | 在 `{聚合}EventSubscriberRegistry` 里显式 `registerSubscriber` |
| 事件携带整份业务快照 | 延迟处理后用旧数据覆盖新副本 / 旧状态发通知 | 事件只带聚合标识，处理时 `findById` 回源 |
| 一个契约/实现内 `instanceof` 分支处理多个事件 | 契约失去单一意图，分类判定失效 | 一个契约一个事件类型，多事件就多个契约 |
| 一个订阅者里塞多个不相关动作 | 任一动作失败拖垮其余；无法单独启停 | 按业务意图拆分，用别名独立注册、独立开关 |
| 订阅者里直接注入第三方 SDK / HTTP 客户端 | 领域意图与技术件耦合，无法替换实现、无法单测 | 抽 `IDependency` 领域端口，适配器放基础设施层 |
| 依赖注册顺序保证执行顺序 | 注册顺序不构成顺序保证 | 用 `registerSubscriber(..., dependSubscriber)` 显式声明依赖 |
| `handleEvent` 里 `try-catch` 静默返回 | 副本落后 / 动作丢失被掩盖，对账失效 | 基础设施异常上抛；仅业务「无需处理」用 `if` 短路 |
| 不标 `@DomainService` 或 `category` 标错 | `category()` 返回 `UNKNOWN`，丢失分类元信息 | 标注 `EVENT_SUBSCRIBER`，`targetName` 填事件类名 |
| 不同包下定义同名事件类 | 简单类名即路由 key，串事件 | 事件类名全局唯一 |
| 物化时版本取 `order.getOldVersion()` | 事件路径与对账路径版本口径混用，副本版本错乱 | 事件路径取 `event.getVersion()`，对账路径才用 `getOldVersion()` |
| 外部副作用不带幂等键 | MQ 重投导致重复发短信 / 重复加积分 | 用聚合标识作 `bizId` 传给下游 |
| 在 `handleEvent` 内同步编排多个聚合的写操作 | 跨聚合事务、锁竞争 | 写编排放应用服务；订阅者只做自己那一个动作 |

## 7. 下一步

- [事件建模指南](./event-modeling.md)：事件只带聚合标识、`buildEvent` 工厂、即时/延迟事件
- [领域服务落地模式](./domain-service.md)：四类契约的分类判定与通用落地步骤
- [投影读模型代码落地指南](./projection-design.md)：读模型型订阅者的 Projector / 源（Source）/ 对账
- [事件管理器装配与选择](./event-manager-config.md)：`IEventManager` 二选一装配与本地线程池实现
- [RocketMQ 配置设计原则](./rocketmq-config.md)：应用级装配与聚合侧只注册订阅的分工
- [核心：领域服务](../core/domain-service.md)：四类契约与 `@DomainService` 注解机制
- [核心：领域事件](../core/domain-events.md)：`IEventRegistry` 六个重载、条件判定与依赖传播
