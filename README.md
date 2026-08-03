# Pragmatic DDD

> 🚀 **务实可落地的领域驱动设计框架（Pragmatic Domain-Driven Design Framework）**
>
> 不追求 CQRS / Event Sourcing 的“全家桶”复杂度，聚焦于 DDD 核心战术模式的标准化表达：实体、值对象、聚合根、领域规则、领域事件。让团队用最小的学习成本，把 DDD 真正写进代码里。

[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-pragmatic--ddd--core-blue.svg)](https://central.sonatype.com/artifact/io.pragmatic.ddd/pragmatic-ddd-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build](https://github.com/lixiaojing/pragmatic-ddd/actions/workflows/ci.yml/badge.svg)](https://github.com/lixiaojing/pragmatic-ddd/actions/workflows/ci.yml)

## 目录

- [特性](#特性)
- [模块结构](#模块结构)
- [快速开始](#快速开始)
  - [引入依赖](#引入依赖)
  - [定义聚合根与规则注册表](#定义聚合根与规则注册表)
  - [定义业务规则](#定义业务规则)
  - [定义领域事件](#定义领域事件)
  - [发布与订阅事件](#发布与订阅事件)
  - [MyBatis 集成](#mybatis-集成)
  - [领域模型可视化](#领域模型可视化)
- [设计理念](#设计理念)
- [AI 辅助开发](#ai-辅助开发)
- [文档](#文档)
- [贡献](#贡献)
- [许可证](#许可证)

---

## 特性

| 特性 | 说明 |
|------|------|
| **实体与聚合根** | `AbstractEntity<T>`、`AggregateRoot<T>`，提供统一标识、软删标记、审计字段、乐观锁版本号与实体等同性 |
| **值对象** | `IValueObject`、`IBoxValueObject` 支持变更追踪（track） |
| **业务规则引擎** | `EntityRule` Fluent API 构建器，内置常用校验（`isBlank`、`email`、数值/日期范围等），支持 failFast 与参数化消息 |
| **规则激活条件** | 支持根据实体状态条件性激活规则（`IActiveRuleCondition`） |
| **领域事件** | `BaseDomainEvent`、`@EventName`，聚合根 `collectEvent` 收集，支持同步 / 异步 / 延迟发布 |
| **有序执行** | `SubscriberOrderManager` 基于 DAG 的事件处理编排，支持依赖顺序与延迟投递 |
| **操作追踪** | `OperationRegistry` / `recordOperation`，事件自动归因到触发操作与版本号 |
| **仓储与查询** | `IRepository`、`AbstractRepository`，内置查询对象与对账（reconciliation）能力 |
| **应用层** | `AbstractApplicationService`、`UnitOfWork`、命令执行器与 Outbox 可靠事件 |
| **模型可视化** | `DomainModelVisualManager` 导出领域模型为 Markdown / JSON Schema，用于文档与 AI 上下文 |
| **AI 友好注解** | `@DomainEntity`、`@BusinessRule`、`@EventTrigger` 为 AI 编程助手提供语义元数据 |

---

## 模块结构

```
pragmatic-ddd/
├── pragmatic-ddd-core        ← 核心库（实体、规则、事件、仓储、应用层、可视化）
├── pragmatic-ddd-rocketmq     ← RocketMQ 领域事件基础设施（含 gRPC 模式）
├── pragmatic-ddd-kafka        ← Kafka 领域事件基础设施（规划中）
├── pragmatic-ddd-spring-boot  ← Spring Boot Starter（规划中）
├── pragmatic-ddd-mybatis      ← MyBatis 辅助能力（类型处理器、Outbox、ID 生成等）
└── examples/
    └── order-example          ← 电商订单完整示例
```

> 说明：`pragmatic-ddd-kafka` 与 `pragmatic-ddd-spring-boot` 当前为占位模块（仅有 `pom.xml`）；`examples/order-example` 提供完整可运行的订单示例。

---

## 快速开始

### 引入依赖

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

如需 RocketMQ 集成，额外引入：

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-rocketmq</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 定义聚合根与规则注册表

```java
import io.pragmatic.ddd.base.*;

@DomainEntity(
    aggregateRoot = "Order",
    description = "订单聚合根",
    boundedContext = "order"
)
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

规则消息码与注册表（基于 Java 17 `record`，通过静态字段自动注册）：

```java
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

public class OrderBrokenRuleRegistry extends BrokenRuleRegistry {

    public static final OrderBrokenRuleRegistry INSTANCE = new OrderBrokenRuleRegistry();

    public static final MessageCode PIN_IS_EMPTY =
            MessageCode.of("ORDER_PIN_IS_EMPTY", "用户标识不能为空");
    public static final MessageCode TOTAL_PRICE_ERROR =
            MessageCode.of("ORDER_TOTAL_PRICE_ERROR", "订单金额必须大于 0");
    public static final MessageCode ITEM_COUNT_ERROR =
            MessageCode.of("ORDER_ITEM_COUNT_ERROR", "订单明细数量不能超过 100");
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

聚合根在业务方法内通过 `recordOperation(...)` 记录操作，事件会自动归因到该操作编码与聚合版本号。

### 定义业务规则

```java
import io.pragmatic.ddd.rules.EntityRule;

public class OrderEntityRule extends EntityRule<Order> {
    public OrderEntityRule() {
        this.isBlank("pin", OrderBrokenRuleRegistry.PIN_IS_EMPTY);
        this.numberShouldGreaterThan("totalPrice", java.math.BigDecimal.ZERO,
                OrderBrokenRuleRegistry.TOTAL_PRICE_ERROR);

        // 带激活条件的自定义规则
        this.addRule(order -> order.getItems().size() < 100,
                OrderBrokenRuleRegistry.ITEM_COUNT_ERROR);
    }
}
```

校验失败时，`EntityRule` 会把违反写入聚合根，可通过 `getBrokenRules()` 获取或 `throwBrokenRuleException()` 抛出异常：

```java
Order order = new Order();
if (!order.satisfiesRule(new OrderEntityRule())) {
    order.throwBrokenRuleException(); // 抛出首条违规异常
}
```

### 定义领域事件

```java
import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.event.EventName;

@EventName("OrderPayed")
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

框架通过 `IEventManager` 统一事件发布与订阅。本地场景可使用 `ThreadPoolEventManager`：

```java
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.IHandle;

// 1. 创建事件管理器
IEventManager eventManager = new ThreadPoolEventManager();

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
```

> 分布式场景可替换为 `RocketMqEventManager`（基于 RocketMQ 4.x/5.x），通过 Builder 配置 `RocketMqConfig`、`ITopicResolver` 等，调用 `start()` / `shutdown()` 受控管理生命周期。

### MyBatis 集成

`pragmatic-ddd-mybatis` 提供与 MyBatis 的衔接能力，让聚合根可借助框架基础设施持久化：

- **类型处理器**：`typehandler/json`（基于 Fastjson2 的通用 JSON 处理器）、`typehandler/enums`、`typehandler/list`，可直接映射值对象 / 枚举 / 集合到数据库列。
- **可靠事件 Outbox**：`MybatisOutboxStore` 实现 `IOutboxStore`，事件与聚合同事务落库（`store` 在调用方事务内执行），并提供 `claim` / `markSent` 等独立短事务补偿操作，避免与 MQ 发送耦合。
- **ID 号段分配**：`DbSegmentAllocator` 实现 `IIdSegmentAllocator`，基于数据库 `SELECT ... FOR UPDATE` 自管独立短事务分配号段，与 Spring 无关、仅依赖 MyBatis 核心 API。

```java
// 基于 MyBatis 的号段 ID 分配器
IIdSegmentAllocator allocator = new DbSegmentAllocator(sqlSessionFactory);
IdSegment segment = allocator.allocateNext("order"); // 当前号段 [segment.getStart(), segment.getMax()]

// 基于 MyBatis 的事件箱存储（与聚合同事务）
IOutboxStore outboxStore = new MybatisOutboxStore(outboxMapper, transactionOperations);
outboxStore.store(outboxMessages); // 在调用方事务内批量落库
```

### 领域模型可视化

```java
import io.pragmatic.ddd.visual.DomainModelVisualManager;

// 构造可视化管理器需传入事件管理器（用于解析事件订阅关系）
DomainModelVisualManager visualManager = new DomainModelVisualManager(eventManager);

// 按实体类收集并组装完整可视化描述符
DomainModelVisualInfo info = visualManager.build(Order.class);
// info 可导出为 Markdown 或 JSON Schema，用于文档生成与 AI 上下文注入
```

---

## 设计理念

> **Pragmatic DDD** —— 务实可落地的领域驱动设计框架。
>
> 不追求 CQRS / Event Sourcing 的全家桶复杂度，聚焦于 DDD 核心战术模式的标准化表达：实体、值对象、聚合根、领域规则、领域事件。让团队用最小的学习成本，把 DDD 真正写进代码里。

框架遵循以下原则：

- **核心战术模式优先**：以实体、聚合根、值对象、领域规则、领域事件为骨架，避免引入过度抽象的架构负担。
- **框架通用性**：作为基础库，设计上保持通用，便于其他项目引用并快速集成。
- **现代 Java 特性**：基于 Java 17 开发，充分利用记录类（record）、密封类（sealed）、模式匹配等新特性。
- **AI 友好**：通过语义化注解与可视化能力，让 AI 编程助手能够理解领域模型并生成正确脚手架。

---

## AI 辅助开发

Pragmatic DDD 从设计之初就考虑了与 AI 编程助手的无缝协作：

- **`@DomainEntity` / `@BusinessRule` / `@EventTrigger`** 注解提供语义元数据，便于 AI 理解领域结构。
- **标准化命名约定** 帮助 AI 生成正确的脚手架代码。
- **模型可视化 API** 可将领域元数据导出并注入 AI 上下文，提升生成质量。

> 可参考 [CodeBuddy Skill](./docs/pragmatic-ddd-skill.md) 获取 AI 辅助 DDD 开发指引。

---

## 文档

- [使用文档](./documentation/) 🔧 施工中
- [设计提案与重构计划](./docs/design/core/) 🔧 施工中
- [示例代码](./examples/)

---

## 贡献

欢迎参与贡献！请在提交 Pull Request 前阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)，了解代码规范与提交流程。

## 许可证

本项目基于 Apache License 2.0 开源，详见 [LICENSE](./LICENSE)。
