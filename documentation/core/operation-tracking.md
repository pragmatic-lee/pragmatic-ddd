# 操作追踪

> 本文档介绍操作追踪体系（`io.pragmatic.ddd.operation`）的概念与用法。
> 前置阅读：[领域建模](./domain-modeling.md)。

## 1. 概述

操作追踪体系用于记录聚合根在一次工作单元内**执行了哪些业务操作**（如创建、支付、取消、发货），并为领域事件提供因果归属。

核心价值：

- **因果追踪**：领域事件的 `operationCode` 自动从最近一次 `recordOperation` 获取
- **操作判断**：业务逻辑可根据"已执行过哪些操作"做分支
- **审计能力**：记录一次工作单元内的全部操作

## 2. 核心概念

| 类 | 说明 |
| --- | --- |
| `EntityOperation` | 操作描述符（不可变值对象），如 `CANCEL`、`PUBLISH` |
| `OperationRegistry` | 操作注册表基类，构造时反射扫描 `static EntityOperation` 字段 |
| `TriggeredOperations` | 操作收集器，记录一次工作单元内的全部操作 |
| `IEntityOperation` | 操作接口契约（`code()` + `description()`） |

## 3. 定义操作注册表

```java
public class OrderOperationRegistry extends OperationRegistry {

    public static final EntityOperation CREATE =
            EntityOperation.of("CREATE", "创建订单");
    public static final EntityOperation PAY =
            EntityOperation.of("PAY", "支付订单");
    public static final EntityOperation CANCEL =
            EntityOperation.of("CANCEL", "取消订单");
    public static final EntityOperation SHIP =
            EntityOperation.of("SHIP", "发货");

    public static final OrderOperationRegistry INSTANCE = new OrderOperationRegistry();
}
```

::: tip 自动注册
`OperationRegistry` 构造时反射扫描子类声明的 `static EntityOperation` 字段并自动注册，子类只需声明常量即可。内置 `NEW` 和 `DELETE` 两个操作。
:::

## 4. 在聚合根中记录操作

```java
public class Order extends AggregateRoot<Long> {

    @Override
    protected OperationRegistry operationRegistry() {
        return OrderOperationRegistry.INSTANCE;  // 返回非 null 启用操作体系
    }

    public void cancel() {
        this.status = "CANCELLED";
        this.markModified();
        this.getNewVersion();
        this.recordOperation(OrderOperationRegistry.CANCEL);  // 记录操作
        this.collectEvent(new OrderCancelledEvent(...));       // 事件的 operationCode 自动取 "CANCEL"
    }
}
```

如果不启用操作体系，让 `operationRegistry()` 返回 `null`：

```java
@Override
protected OperationRegistry operationRegistry() {
    return null;  // 不启用操作追踪
}
```

::: warning
`operationRegistry()` 返回 `null` 时，调用 `recordOperation` / `hasOperation` 会抛 `OperationException`。此时 `collectEvent` 可以正常使用（`operationCode` 为 `null`）。
:::

## 5. 操作条件判断

```java
public void ship() {
    // 只有已支付的订单才能发货
    if (!hasOperation(OrderOperationRegistry.PAY)) {
        throw new IllegalStateException("未支付的订单不能发货");
    }

    this.status = "SHIPPED";
    this.recordOperation(OrderOperationRegistry.SHIP);
}
```

三种判断方法：

```java
boolean hasPay = order.hasOperation(OrderOperationRegistry.PAY);
boolean hasPayAndShip = order.hasAllOperations(
        OrderOperationRegistry.PAY,
        OrderOperationRegistry.SHIP);
boolean hasPayOrCancel = order.hasAnyOperation(
        OrderOperationRegistry.PAY,
        OrderOperationRegistry.CANCEL);
```

## 6. 操作与事件的因果追踪

```
recordOperation(CANCEL)     ← 记录操作，lastRecordedOperation = CANCEL
    ↓
collectEvent(CancelledEvent) ← 事件的 operationCode 自动取 "CANCEL"
    ↓
getDomainEvents()           ← 事件携带 operationCode="CANCEL", version=新版本号
    ↓
eventManager.publish(events) ← 订阅者可据 operationCode 分发处理
```

也可显式指定成因操作（优先级最高）：

```java
this.collectEvent(new OrderCancelledEvent(...), OrderOperationRegistry.CANCEL);
```

---

下一步：

- [领域事件](./domain-events.md)：事件的完整体系
- [应用服务](./application-service.md)：操作在工作单元中的清理
