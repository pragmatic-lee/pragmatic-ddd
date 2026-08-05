# Pragmatic DDD

> 🚀 **务实可落地的领域驱动设计框架（Pragmatic Domain-Driven Design Framework）**
>
> 不追求 CQRS / Event Sourcing 的“全家桶”复杂度，聚焦于 DDD 核心战术模式的标准化表达：实体、值对象、聚合根、领域规则、领域事件。让团队用最小的学习成本，把 DDD 真正写进代码里。

[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-pragmatic--ddd--core-blue.svg)](https://central.sonatype.com/artifact/io.pragmatic.ddd/pragmatic-ddd-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## 目录

- [特性](#特性)
- [模块结构](#模块结构)
- [快速开始](#快速开始)
  - [引入依赖](#引入依赖)
  - [定义聚合根](#定义聚合根)
  - [定义业务规则](#定义业务规则)
  - [定义领域事件](#定义领域事件)
  - [发布与订阅事件](#发布与订阅事件)
  - [事务性 Outbox](#事务性-outbox)
  - [MyBatis 集成](#mybatis-集成)
  - [RocketMQ 集成](#rocketmq-集成)
- [设计理念](#设计理念)
- [文档](#文档)
- [贡献](#贡献)
- [许可证](#许可证)

---

## 特性

| 特性 | 说明 |
|------|------|
| **实体与聚合根** | `AbstractEntity<T>`、`AggregateRoot<T>`，提供统一标识、软删标记、审计字段、乐观锁版本号与基于标识的等同性 |
| **值对象** | `ValueObject` 基于 `equalityComponents()` 提供结构相等性，`IValueObject` 作为语义标记 |
| **业务规则引擎** | `EntityRule` 无状态规则容器，校验项接收「新模型 + 旧模型」双参数，支持 failFast、激活条件、运行时增删改 |
| **规则激活条件** | `IActiveRuleCondition` 支持「规则码级开关」与「模型级条件」两级激活判定 |
| **领域事件** | `BaseDomainEvent`、`IDomainEvent`，聚合根 `collectEvent` 收集，事件自动归因到操作编码与版本号 |
| **有序执行** | `ISubscriberOrderManager` 基于 DAG 的订阅者执行顺序编排，支持依赖顺序与延迟投递 |
| **操作追踪** | `OperationRegistry` / `recordOperation`，事件自动归因到触发操作与聚合版本号 |
| **仓储与查询** | `IRepository`、`AbstractRepository`（落库前数据同步钩子）、Q 侧聚合查询与读模型投影（query） |
| **读模型对账** | `Reconciler` / `ReconciliationManager` 读模型补偿、去重与版本对账 |
| **应用层** | `AbstractApplicationService`、`UnitOfWork`、命令执行器（含 DryRun 试跑）与事务性 Outbox |
| **事务性 Outbox** | `OutboxUnitOfWork` 同事务落 outbox + 提交后主动推送，`OutboxRelay` 兜底轮询补偿、死信兜底 |
| **变更追踪** | `track` 包提供 `TrackedList` / `TrackedMap` 变更追踪集合 |
| **ID 生成** | `base.id` 号段 ID 生成器体系，可扩展自定义分配器 |

---

## 模块结构

```
pragmatic-ddd/
├── pragmatic-ddd-parent        ← 统一父 POM（Java 17、插件、依赖版本）
├── pragmatic-ddd-bom           ← BOM，集中管理内部模块版本，供消费者一键引入
├── pragmatic-ddd-core          ← 核心库（实体、值对象、规则、事件、仓储、应用层、Outbox、追踪）
├── pragmatic-ddd-rocketmq      ← RocketMQ 领域事件基础设施（Remoting + gRPC 两种通道）
├── pragmatic-ddd-kafka         ← Kafka 领域事件基础设施（规划中）
├── pragmatic-ddd-spring-boot   ← Spring Boot Starter（规划中）
├── pragmatic-ddd-mybatis       ← MyBatis 辅助能力（类型处理器、Outbox 存储、号段 ID 分配）
└── examples/
    └── order-example           ← 电商订单示例（构建示例见下）
```

> **模块状态说明**：`pragmatic-ddd-kafka` 与 `pragmatic-ddd-spring-boot` 当前为占位模块（仅有 `pom.xml`）。
> 示例模块默认不参与构建，如需连同示例一起编译请使用 `mvn install -Pexamples`。

---

## 快速开始

### 引入依赖

核心库：

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

如需 RocketMQ / MyBatis 集成，额外引入对应模块：

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-rocketmq</artifactId>
    <version>2.0.0</version>
</dependency>

<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-mybatis</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 定义聚合根

聚合根继承 `AggregateRoot<T>`，实现 `brokenRuleRegistry()` 与 `operationRegistry()` 两个抽象方法，在业务方法内通过 `recordOperation` 记录操作、通过 `collectEvent` 收集领域事件：

```java
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;

public class Order extends AggregateRoot<Long> {

    private String pin;
    private java.math.BigDecimal totalPrice;
    private int status;

    /** 支付：记录操作并收集领域事件 */
    public void payment() {
        this.status = 1; // 已支付
        this.recordOperation(OrderOperation.PAY);
        this.collectEvent(new OrderPayedEvent(this));
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderBrokenRuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return OrderOperation.REGISTRY;
    }
}
```

规则消息码注册表（基于 Java 17 `record`，通过静态字段自动注册）：

```java
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

public class OrderBrokenRuleRegistry extends BrokenRuleRegistry {

    public static final OrderBrokenRuleRegistry INSTANCE = new OrderBrokenRuleRegistry();

    public static final MessageCode PIN_IS_EMPTY =
            MessageCode.of("ORDER_PIN_IS_EMPTY", "用户标识不能为空");
    public static final MessageCode TOTAL_PRICE_ERROR =
            MessageCode.of("ORDER_TOTAL_PRICE_ERROR", "订单金额必须大于 0");
    public static final MessageCode AMOUNT_LIMIT_ERROR =
            MessageCode.of("ORDER_AMOUNT_LIMIT_ERROR", "订单金额不能超过 1000");
}
```

操作注册表（事件归因用，可选但推荐）：

```java
import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;

public class OrderOperation extends OperationRegistry {

    public static final OrderOperation REGISTRY = new OrderOperation();

    public static final EntityOperation PAY = EntityOperation.of("PAY", "支付订单");
    public static final EntityOperation CANCEL = EntityOperation.of("CANCEL", "取消订单");
}
```

> 事件会自动归因到 `recordOperation` 记录的最近一次操作编码与聚合版本号（`version`）。

### 定义业务规则

`EntityRule` 是无状态规则容器，可通过校验器基类或 Lambda 校验项追加规则：

```java
import io.pragmatic.ddd.base.RuleCheckResult;
import io.pragmatic.ddd.rules.ActiveStatus;
import io.pragmatic.ddd.rules.BaseRuleValidator;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.rules.IActiveRuleCondition;

public class OrderEntityRule extends EntityRule<Order> {
    public OrderEntityRule() {
        // 1) 继承 BaseRuleValidator 的校验器（推荐：可复用、可测试）
        this.addRule(new PinNotEmptyRule(), OrderBrokenRuleRegistry.PIN_IS_EMPTY);

        // 2) 或使用 ICheckRuleBuilder / Lambda 校验项（新模型 + 旧模型双参数）
        this.addRule((newModel, oldModel) ->
                        newModel.getTotalPrice() != null
                                && newModel.getTotalPrice().compareTo(java.math.BigDecimal.ZERO) > 0
                                ? RuleCheckResult.pass()
                                : RuleCheckResult.fail(),
                OrderBrokenRuleRegistry.TOTAL_PRICE_ERROR);

        // 3) 带激活条件的规则：仅当订单处于未支付状态（status == 0）时生效
        this.addRule((newModel, oldModel) ->
                        newModel.getTotalPrice() != null
                                && newModel.getTotalPrice().compareTo(new java.math.BigDecimal("1000")) <= 0
                                ? RuleCheckResult.pass()
                                : RuleCheckResult.fail(),
                OrderBrokenRuleRegistry.AMOUNT_LIMIT_ERROR,
                IActiveRuleCondition.of(model ->
                        model.getStatus() == 0 ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE));
    }

    /** 示例校验器：复用且可单测 */
    static class PinNotEmptyRule extends BaseRuleValidator<Order> {
        @Override
        protected boolean validate(Order newModel, Order oldModel) {
            return newModel.getPin() != null && !newModel.getPin().isBlank();
        }
    }
}
```

`EntityRule` 支持运行时增删改：`appendRule(...)`（指定前后位置）、`replaceRule(...)`、`removeRule(...)`，也支持新旧模型对比（覆写 `requireOldEntity()` 与 `supplyOldEntity()`）。

校验失败时，违规会写入聚合根，可通过 `getBrokenRules()` 获取或抛出异常：

```java
Order order = new Order();
if (!order.satisfiesRule(new OrderEntityRule())) {
    order.throwBrokenRuleException();            // 抛出首条违规异常
    // 或 order.throwBrokenRuleAggregateException(); 抛出聚合异常
}
```

### 定义领域事件

领域事件继承 `BaseDomainEvent`，保持不可变：

```java
import io.pragmatic.ddd.event.BaseDomainEvent;

public class OrderPayedEvent extends BaseDomainEvent {

    private final Long orderId;
    private final java.math.BigDecimal amount;

    public OrderPayedEvent(Order order) {
        super(order.getEntityId().toString());
        this.orderId = order.getEntityId();
        this.amount = order.getTotalPrice();
    }

    public Long getOrderId() {
        return orderId;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }
}
```

### 发布与订阅事件

框架通过 `IEventManager`（组合发布、注册、生命周期三类能力）统一事件发布与订阅。本地场景可使用 `ThreadPoolEventManager`：

```java
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.IHandle;

// 1. 创建事件管理器
IEventManager eventManager = new ThreadPoolEventManager();
eventManager.init();
eventManager.start();

// 2. 注册订阅者（通过 IHandle 函数式接口承载处理逻辑）
eventManager.registerSubscriber("orderPayedLogger", OrderPayedEvent.class,
        (IHandle<OrderPayedEvent>) event ->
                log.info("订单 {} 已支付，金额：{}", event.getOrderId(), event.getAmount()));

// 3. 业务方法内收集事件，发布聚合根上全部领域事件
Order order = new Order();
order.payment();
eventManager.publishList(order.getDomainEvents());

// 4. 事件分发完成后清理聚合根工作单元临时状态
order.clearWorkUnitState();

// 5. 关闭
eventManager.shutdown();
```

订阅者支持条件执行（`IExecuteCondition`）、延迟/立即投递（`DeliveryPolicy`）与前置依赖订阅者（DAG 顺序编排）：

```java
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;

// 依赖顺序：subscriberB 在 subscriberA 之后执行
eventManager.registerSubscriber("subscriberA", OrderPayedEvent.class, handlerA);
eventManager.registerSubscriber("subscriberB", OrderPayedEvent.class, handlerB,
        null, "subscriberA", DeliveryPolicy.IMMEDIATE);
```

> 分布式场景可替换为 `RocketMqEventManager`（见下文 RocketMQ 集成），通过 Builder 配置，`start()` / `shutdown()` 受控管理生命周期。

### 事务性 Outbox

`application.outbox` 提供可靠事件投递，保证「本地事务落库」与「事件投递」的最终一致性：

- **`OutboxUnitOfWork`**：同一事务内逐条 `save` + 整批落 outbox（PENDING），事务提交后由 `EagerOutboxPublisher` 主动推送，失败保持 PENDING。
- **`OutboxRelay`**：兜底轮询器，周期性认领超时 PENDING 记录补偿重发，重试超限转死信（FAILED）。
- **`IOutboxStore`**：outbox 存储 SPI，由基础设施模块（如 `pragmatic-ddd-mybatis` 的 `MybatisOutboxStore`）实现。

```java
import io.pragmatic.ddd.application.IUnitOfWork;
import io.pragmatic.ddd.application.outbox.EagerOutboxPublisher;
import io.pragmatic.ddd.application.outbox.OutboxUnitOfWork;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.mybatis.outbox.MybatisOutboxStore;
import io.pragmatic.ddd.mybatis.typehandler.json.Fastjson2JsonSerializer;

import java.util.concurrent.Executors;

// 组合装配（示意）
TransactionOperations txOps = ...;   // 由集成层实现：绑定"聚合写 + outbox 写"到同一 DB 事务
IOutboxStore outboxStore = new MybatisOutboxStore(outboxMapper, txOps);
IEventManager eventManager = new ThreadPoolEventManager();
IUnitOfWork uow = new OutboxUnitOfWork(outboxStore, txOps,
        new Fastjson2JsonSerializer(),
        new EagerOutboxPublisher(outboxStore, eventManager, Executors.newCachedThreadPool()));

// 在应用服务中使用
Order order = new Order();
uow.register(order, new OrderEntityRule(), orderRepository, Order::payment);
uow.commit();   // 同事务落库 + 落 outbox，提交后推送事件
```

> 默认 `UnitOfWork` 与 `OutboxUnitOfWork` 并存：前者直接发布事件，后者走 outbox 可靠通道，按需选用。

### MyBatis 集成

`pragmatic-ddd-mybatis` 提供与 MyBatis 的衔接能力，让聚合根可借助框架基础设施持久化：

- **统一类型处理器装配**：`TypeHandlerContext` 集中持有枚举策略、VO 类型与共享组件，构建完 `SqlSessionFactory` 后调用一次 `registerInto(...)` 即可同时注册枚举、JSON、集合三类处理器，零 Spring 依赖。
  - `enums`：`UniversalEnumTypeHandler` + `EnumValueResolver`，按策略（CODE/ORDINAL/NAME）映射枚举。
  - `json`：`GenericJsonTypeHandler`，把值对象整体读写为数据库原生 JSON 列（PG `jsonb` / MySQL `JSON`）。
  - `list`：`ListTypeHandler`，单列 JSON 数组处理器，按列标签还原 `List<E>` 元素类型。
- **可靠事件 Outbox**：`MybatisOutboxStore` 实现 `IOutboxStore`，`store` 在调用方事务内执行，`claim`/`markSent` 等为独立短事务，`markSent` 带状态守卫保证幂等。
- **ID 号段分配**：`DbSegmentAllocator` 实现 `IIdSegmentAllocator`，基于数据库 `SELECT ... FOR UPDATE` 自管独立短事务分配号段，仅依赖 MyBatis 核心 API。

```java
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.base.id.IIdSegmentAllocator;
import io.pragmatic.ddd.mybatis.id.DbSegmentAllocator;
import io.pragmatic.ddd.mybatis.outbox.MybatisOutboxStore;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;

// 基于 MyBatis 的号段 ID 分配器
IIdSegmentAllocator allocator = new DbSegmentAllocator(sqlSessionFactory);
IdSegment segment = allocator.allocateNext("order"); // 当前号段 [segment.getStart(), segment.getMax()]

// 基于 MyBatis 的事件箱存储（与聚合同事务）
IOutboxStore outboxStore = new MybatisOutboxStore(outboxMapper, transactionOperations);
outboxStore.store(outboxMessages); // 在调用方事务内批量落库

// 统一注册枚举 / JSON / 集合类型处理器（构建完 SqlSessionFactory 后调用一次）
TypeHandlerContext context = new TypeHandlerContext(resolver, serializer,
        new PgJdbcJsonValue(), enumRules, voTypes, collectionsConfig);
context.registerInto(sqlSessionFactory);
```

### RocketMQ 集成

`pragmatic-ddd-rocketmq` 提供两种 RocketMQ 通道的领域事件管理器：

- **`RocketMqEventManager`**（Remoting 协议）：基于 `rocketmq-client` 的 `DefaultMQProducer` / `DefaultMQPushConsumer`，兼容 RocketMQ 4.x / 5.x Broker。
- **`RocketMqGrpcEventManager`**（gRPC 协议）：基于 `rocketmq-client-java`（5.x Proxy，可选依赖）。

通过 `RocketMqConfig` 统一配置，`RocketMqEventManager.builder()` 构建，`start()` / `shutdown()` 受控管理生命周期：

```java
import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import io.pragmatic.ddd.rocketmq.RocketMqConfig;
import io.pragmatic.ddd.rocketmq.RocketMqEventManager;

RocketMqConfig config = new RocketMqConfig()
        .setNameServer("127.0.0.1:9876")
        .setProducerGroup("ORDER_PRODUCER_GROUP")
        .setConsumerGroup("ORDER_CONSUMER_GROUP");

// 解析事件 → Topic；可自行实现 ITopicResolver，也可用内置的 ConfigurableTopicResolver 快速上手
ITopicResolver topicResolver = new ConfigurableTopicResolver.Builder()
        .globalDefaultTopic("order-event-topic")
        .eventTopic("OrderPayedEvent", "order-pay-topic")
        .build();

IEventManager eventManager = RocketMqEventManager.builder()
        .config(config)
        .topicResolver(topicResolver)
        .build();

eventManager.start();
// ... 注册订阅者 / 发布事件
eventManager.shutdown();
```

> `ConfigurableTopicResolver` 提供三层次解析（订阅者级 → 事件级 → 全局默认），位于 `io.pragmatic.ddd.event.internal.defaults`；如需更精细的路由，可实现自己的 `ITopicResolver`。

---

## 设计理念

> **Pragmatic DDD** —— 务实可落地的领域驱动设计框架。
>
> 不追求 CQRS / Event Sourcing 的全家桶复杂度，聚焦于 DDD 核心战术模式的标准化表达：实体、值对象、聚合根、领域规则、领域事件。让团队用最小的学习成本，把 DDD 真正写进代码里。

框架遵循以下原则：

- **核心战术模式优先**：以实体、聚合根、值对象、领域规则、领域事件为骨架，避免引入过度抽象的架构负担。
- **框架通用性**：作为基础库，设计上保持通用、零 Spring 强依赖，便于其他项目引用并快速集成。
- **现代 Java 特性**：基于 Java 17 开发，充分利用记录类（record）、密封类（sealed）、模式匹配、方法引用等特性。
- **规则无状态化**：校验项接收「新模型 + 旧模型」双参数，规则对象可单例化、多线程安全共享。

---

## 文档

- [使用文档](./documentation/core/) —— 领域建模、业务规则使用指引
- [设计提案与重构计划](./docs/design/core/) —— 各模块的设计提案、分析与重构计划
- [最佳实践](./docs/best-practice/) —— 聚合根、规则校验、领域服务等最佳实践
- [示例代码](./examples/)

---

## 贡献

欢迎参与贡献！请在提交 Pull Request 前阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)，了解代码规范与提交流程。

## 许可证

本项目基于 Apache License 2.0 开源，详见 [LICENSE](./LICENSE)。
