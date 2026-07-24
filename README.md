# Pragmatic DDD

> 🚀 **Pragmatic Domain-Driven Design Framework** — 务实可落地的领域驱动设计框架
>
> 不追求 CQRS / Event Sourcing 的全家桶复杂度，聚焦于 DDD 核心战术模式的标准化表达。

[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/maven-central/v/io.pragmatic.ddd/pragmatic-ddd-core.svg)](https://central.sonatype.com/artifact/io.pragmatic.ddd/pragmatic-ddd-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build](https://github.com/lixiaojing/pragmatic-ddd/actions/workflows/ci.yml/badge.svg)](https://github.com/lixiaojing/pragmatic-ddd/actions/workflows/ci.yml)

**English** | [中文](#中文)

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| **Entity & Aggregate Root** | `EntityBase<T>`, `IAggregateRoot` |
| **Value Object** | `IValueObject`, `IBoxValueObject` with trace collection |
| **Business Rule Engine** | Fluent API rule builder with built-in rules (isBlank, email, number range, date range...) |
| **Rule Activation Conditions** | Conditional rule activation based on entity state |
| **Domain Events** | `BaseDomainEvent`, `@EventName`, sync/async publishing |
| **Subscriber & Ordering** | `SubscriberOrderManager` — DAG-based ordered event execution |
| **Model Visualization** | Export domain model to Markdown / JSON Schema for documentation and AI |
| **AI-Friendly Annotations** | `@DomainEntity`, `@BusinessRule`, `@EventTrigger` — semantic metadata for AI coding assistants |

## 📦 Modules

```
pragmatic-ddd/
├── pragmatic-ddd-core       ← Core library (entity, rules, events, visualization)
├── pragmatic-ddd-rocketmq    ← RocketMQ integration
├── pragmatic-ddd-kafka       ← Kafka integration [planned]
├── pragmatic-ddd-spring-boot ← Spring Boot Starter [planned]
├── pragmatic-ddd-mybatis     ← MyBatis helper [planned]
└── examples/
    └── order-example         ← Complete e-commerce order example
```

## 🚀 Quick Start

### Maven

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Define an Entity

```java
import io.pragmatic.ddd.base.*;

@DomainEntity(
    aggregateRoot = "Order",
    description = "订单聚合根",
    boundedContext = "order"
)
public class Order extends EntityBase<Long> {
    private String pin;
    private BigDecimal totalPrice;
    private int status;

    public void payment() {
        this.status = 1; // paid
        eventCollector.pushEvent(new OrderPayedEvent(this));
    }

    @Override
    public BrokenRuleMessage getBrokenRuleMessages() {
        return new OrderBrokenRuleMessages();
    }

    @Override
    public void validate() {
        new OrderEntityRule().check(this);
    }
}
```

### Define Business Rules

```java
import io.pragmatic.ddd.rules.*;

public class OrderEntityRule extends EntityRule<Order> {
    public OrderEntityRule() {
        this.isBlank("pin", OrderBrokenRuleMessages.PIN_IS_EMPTY);
        this.numberShouldGreaterThan("totalPrice", BigDecimal.ZERO,
            OrderBrokenRuleMessages.TOTAL_PRICE_ERROR);

        // Custom rule with activation condition
        this.addRule(order -> order.getItems().size() < 100,
            OrderBrokenRuleMessages.ITEM_COUNT_ERROR);
    }
}
```

### Define Domain Event

```java
import io.pragmatic.ddd.event.*;

@EventName("OrderPayed")
public class OrderPayedEvent extends BaseDomainEvent {
    private Long orderId;
    private BigDecimal amount;

    public OrderPayedEvent(Order order) {
        super(order.getEntityId().toString());
        this.orderId = order.getEntityId();
        this.amount = order.getTotalPrice();
    }
}
```

### Publish & Subscribe Events

```java
// Publish
DomainEventManager eventManager = new ThreadPoolTaskDomainEventManager(executorService);
eventManager.registerSubscriber(new OrderPayedEventHandler());
eventManager.publishEvent(order.allEvents(), entity);

// Subscriber
public class OrderPayedEventHandler extends AbstractDomainEventSubscriber<OrderPayedEvent> {
    @Override
    public void handle(OrderPayedEvent event) {
        // Handle payment completion
        log.info("Order {} payed, amount: {}", event.getOrderId(), event.getAmount());
    }
}
```

### Visualize Domain Model

```java
DomainModelVisualInfo info = visualManager.build(Order.class);
// Export to Markdown or JSON Schema for documentation and AI context
```

## 🤖 AI-Assisted Development

Pragmatic DDD is designed from the ground up to work seamlessly with AI coding assistants:

- **`@DomainEntity` / `@BusinessRule` / `@EventTrigger`** annotations provide semantic metadata
- **Standardized naming conventions** help AI generate correct scaffolding
- **Model visualization API** exports domain metadata that can be injected into AI context

> Check our [CodeBuddy Skill](./docs/pragmatic-ddd-skill.md) for AI-assisted DDD development.

## 📖 Documentation

- [Getting Started Guide](https://pragmatic-ddd.com/docs/getting-started) 🔧 施工中
- [API Reference](https://pragmatic-ddd.com/docs/api) 🔧 施工中
- [Best Practices](https://pragmatic-ddd.com/docs/best-practices) 🔧 施工中
- [Examples](./examples/)

## 🤝 Contributing

We welcome contributions! Please read [CONTRIBUTING.md](./CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## 📄 License

This project is licensed under the Apache License 2.0 — see [LICENSE](./LICENSE) for details.

---

<a name="中文"></a>

# Pragmatic DDD 中文说明

## 特性

| 特性 | 说明 |
|------|------|
| **实体与聚合根** | `EntityBase<T>`, `IAggregateRoot` |
| **值对象** | `IValueObject`, `IBoxValueObject` 支持变更追踪 |
| **业务规则引擎** | Fluent API 构建器，内置常用校验规则 |
| **规则激活条件** | 支持根据实体状态条件性激活规则 |
| **领域事件** | 事件发布/订阅，支持同步和异步模式 |
| **有序执行** | 基于 DAG 的事件处理编排 |
| **模型可视化** | 导出领域模型为 Markdown / JSON Schema |
| **AI 友好** | `@DomainEntity` 等注解为 AI 编程助手提供语义元数据 |

## 快速开始

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

## 设计理念

> **Pragmatic DDD** — 务实可落地的领域驱动设计框架。
>
> 不追求 CQRS / Event Sourcing 的全家桶复杂度，聚焦于 DDD 核心战术模式的标准化表达：
> 实体、值对象、聚合根、领域规则、领域事件。让团队用最小的学习成本，把 DDD 真正写进代码里。
