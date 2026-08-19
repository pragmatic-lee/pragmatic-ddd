# 操作注册表设计

> 本文档介绍 Pragmatic DDD 中操作注册表（`OperationRegistry`）的设计指导原则：先明确它解决什么问题，再结合 `io.pragmatic.ddd.operation` 包基类的实际行为讲清反射机制与约束，最后落到编写规范与常见反模式。前置阅读：[聚合设计原则](./aggregate-design.md) · [领域操作（核心机制）](../core/domain-operation.md)。

## 1. 操作注册表解决什么问题

聚合根在一次业务变更里会执行一个明确的业务操作（创建、支付、取消、发货……）。操作注册表把这类操作**集中声明为可校验的常量**，让聚合根只能记录注册表内声明过的操作，并让领域事件自动携带"最近一次操作"作为因果归属。

### 1.1 概念层级（对应 `io.pragmatic.ddd.operation`）

```text
IEntityOperation                  操作契约（code + description）
  └─ EntityOperation              操作描述符（不可变值对象，非枚举）
OperationRegistry                 操作注册表基类（反射自动注册 static 字段）
TriggeredOperations               已触发操作收集器（一次工作单元内的全部操作）
OperationException                操作异常（继承 PragmaticException）
```

### 1.2 核心价值

- **因果追踪**：领域事件 `operationCode` 自动取自最近一次 `recordOperation`，无需手动透传。
- **操作判断**：业务逻辑可依据"已执行过哪些操作"做分支校验（如 `hasOperation` / `hasAllOperations` / `hasAnyOperation`）。
- **防游离操作码**：`TriggeredOperations.put` 校验操作必须已在注册表中，拼写错误的操作码会直接抛 `OperationException`，从根上保证因果链干净。

## 2. 基类实际行为（基于 `io.pragmatic.ddd.operation`）

### 2.1 内置操作

`OperationRegistry` 构造时先注册两个内置操作，再扫描子类声明的常量：

| 操作 | code | description |
| --- | --- | --- |
| `NEW` | `NEW` | 新建 |
| `DELETE` | `DELETE` | 删除 |

### 2.2 反射自动注册机制

基类构造器（`public OperationRegistry()`）执行两步：

1. `register(NEW, DELETE)` 注册内置操作；
2. 遍历 `getClass().getDeclaredFields()`，对**本子类声明**的 `static` 且类型为 `EntityOperation` 的字段调用 `field.get(null)` 自动注册；字段访问失败（`IllegalAccessException`）被**静默忽略**，注册继续。

```java
public OperationRegistry() {
    this.register(NEW, DELETE);
    // 反射：扫描"本子类"声明的 static EntityOperation 字段，自动注册
    for (Field f : getClass().getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers())
                && EntityOperation.class.isAssignableFrom(f.getType())) {
            try {
                register((EntityOperation) f.get(null));
            } catch (IllegalAccessException ignored) {
                // 字段访问失败静默忽略，注册继续
            }
        }
    }
}
```

> 因为扫描的是 `getDeclaredFields()`，操作常量必须声明在**最终子类**上；中间抽象类里声明的字段不会被孙类扫到。又因 `EntityOperation` 为 `final`，`isAssignableFrom` 实际等价于"字段类型恰为 `EntityOperation`"——声明成 `IEntityOperation` 类型不会注册。

### 2.3 关键约束：子类必须 `public`

反射 `field.get(null)` 发生在基类构造器中。若子类为包级私有，`IllegalAccessException` 被静默吞掉，**操作未注册且不报错**——症状延后到 `TriggeredOperations.put` 时才以 `OperationException`（"operation not found in OperationRegistry"）暴露。

```java
// ❌ 反模式：包级私有子类，反射注册失败且无构造期报错
class OrderOperationRegistry extends OperationRegistry { ... }

// ✅ 推荐：public 子类
public class OrderOperationRegistry extends OperationRegistry { ... }
```

### 2.4 操作描述符 `EntityOperation`：不可变、`code` 即身份

`EntityOperation` 是不可变值对象（非枚举），是注册的基本单元：

| 规则 | 说明 |
| --- | --- |
| 构造 | `private` 构造器，仅经 `of(code, description)` / `of(code)` 工厂创建 |
| 相等性 | **仅按 `code` 判定**（`description` 不参与），注册表以 `code` 为 key |
| 不可变 | `code` / `description` 均 `final`，无 setter |

### 2.5 已触发操作校验：`TriggeredOperations`

聚合根组合持有 `TriggeredOperations`，一次工作单元内收集已触发的操作。`put` 是唯一的写入入口：

```java
public void put(EntityOperation operation) {
    if (!this.operationRegistry.operations().containsKey(operation.code())) {
        throw new OperationException("operation not found in OperationRegistry: " + operation.code());
    }
    this.triggeredMap.put(operation.code(), operation);
}
```

`operations()` 返回只读映射（`unmodifiableMap`），外部不可篡改。判断方法 `contains` / `containsAll` / `containsAny` 均按 `code` 判重。

## 3. 编写规范

### 3.1 完整示例

```java
public class OrderOperationRegistry extends OperationRegistry {

    public static final EntityOperation CREATE = EntityOperation.of("CREATE", "创建订单");
    public static final EntityOperation PAY    = EntityOperation.of("PAY", "支付订单");
    public static final EntityOperation SHIP   = EntityOperation.of("SHIP", "发货");
    public static final EntityOperation CANCEL = EntityOperation.of("CANCEL", "取消订单");

    private OrderOperationRegistry() {}

    public static final OrderOperationRegistry INSTANCE = new OrderOperationRegistry();
}
```

### 3.2 类与常量可见性

- 注册表子类**必须为 `public`**（反射注册前提，见 §2.3）。
- 操作常量用 `public static final EntityOperation` 声明，供聚合根 `recordOperation(OrderOperationRegistry.PAY)` 引用。
- 字段类型必须是 `EntityOperation`，不能声明成 `IEntityOperation`（不满足反射 `isAssignableFrom`）。

### 3.3 `code` 命名约定

- `code` 全大写下划线，**不带聚合/领域前缀**（内置 `NEW` / `DELETE` 即无前缀），如 `CREATE` / `PAY` / `CANCEL`。
- 约定 `code` 与常量字段名**完全一致**，便于追踪与对账：`public static final EntityOperation CANCEL = EntityOperation.of("CANCEL", ...)`。
- 跨聚合允许同 `code` 重名（各自注册表独立，互不影响）。

### 3.4 单例 `INSTANCE` 与私有构造

提供 `public static final INSTANCE` 单例，聚合根每次 `operationRegistry()` 返回同一个实例，避免重复 new；构造器设为 `private`，防止外部随意 new 出独立注册表。

### 3.5 与聚合根接入

```java
@Override
protected OperationRegistry operationRegistry() {
    return OrderOperationRegistry.INSTANCE;  // 返回非 null 即启用操作体系
}
```

> **关键差异**：`operationRegistry()` 返回 `null` 代表不启用操作体系——此时调用 `recordOperation` / `hasOperation*` 抛 `OperationException`，但 `collectEvent` 仍可用（事件的 `operationCode` 为 `null`）。这与规则注册表不同，`brokenRuleRegistry()` **不能**返回 `null`。

### 3.6 使用顺序：先 `recordOperation` 后 `collectEvent`

```java
public void cancel(String reason) {
    this.status = OrderStatus.CANCELLED;
    this.cancelReason = reason;
    this.markModified();
    this.recordOperation(OrderOperationRegistry.CANCEL);  // 先记录操作
    this.collectEvent(OrderCancelledEvent.buildEvent(this)); // 事件 operationCode 自动取 "CANCEL"
}
```

- `collectEvent(BaseDomainEvent)` 的 `operationCode` 取自最近一次 `recordOperation`（单值因果指针）。
- 启用了操作体系却跳过 `recordOperation` 直接 `collectEvent`，会抛 `OperationException`；确需解耦时用显式重载 `collectEvent(event, operation)`。

## 4. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 注册表写成包级私有 | 反射注册失败，操作未注册且构造期无报错，症状延后为 `OperationException` | 注册表子类必须为 `public` |
| 常量声明在中间抽象类 | `getDeclaredFields()` 只扫最终子类，字段不注册 | 操作常量声明在最终子类上 |
| 字段类型声明为 `IEntityOperation` | 不满足反射 `isAssignableFrom`，未注册 | 字段类型必须是 `EntityOperation` |
| `code` 与常量字段名不一致 | 追踪与对账困难 | 约定 `code` 与常量字段名完全一致 |
| 每次 `operationRegistry()` 都 new 一个注册表 | 浪费实例、语义漂移 | 提供 `public static final INSTANCE` 单例 |
| 启用了操作体系却跳过 `recordOperation` | `collectEvent` 抛 `OperationException` | 严格遵循"先 `recordOperation` 后 `collectEvent`" |

---

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根编码规范与父类能力
- [规则注册表设计](./registry-design.md)：校验消息码（`BrokenRuleRegistry`）的编写规范
- [应用服务层协作](./application-collaboration.md)：操作/事件顺序、延迟事件、工作单元清理与异常响应
- [事件建模指南](./event-modeling.md)
- [核心：领域操作](../core/domain-operation.md)：`OperationRegistry` / `TriggeredOperations` 机制详解
- [核心：领域事件](../core/domain-events.md)：`BaseDomainEvent` 与事件发布
