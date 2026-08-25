# 操作注册表设计

> 本文档介绍 Pragmatic DDD 中操作注册表（`OperationRegistry`）的落地方式：先明确它解决什么问题，再给出命名与包结构、落地代码骨架，最后是易错点与反模式。前置阅读：[聚合设计原则](./aggregate-design.md) · [领域操作（核心机制）](../core/domain-operation.md)。

## 1. 本质与定位

操作注册表把聚合的业务操作（创建、支付、取消、发货……）**集中声明为可校验的常量**，让聚合根只能记录注册表内声明过的操作，并让领域事件自动携带"最近一次操作"作为因果归属。

- 解决什么：操作码集中声明、事件成因可追踪、操作合法性可校验。
- 核心特征：`public static final EntityOperation` 常量 + 单例 `INSTANCE`；基类构造时自动注册内置 `NEW` / `DELETE` 与子类声明的常量。
- 与写模型的边界：只声明操作常量，**不承载触发逻辑**；触发由聚合根业务方法 `recordOperation` 完成。

## 2. 命名与包结构

注册表类放在聚合的领域层，与模型 / 规则 / 服务平级：

```text
domain/order/
├── model/         聚合根、实体、值对象
├── operation/     操作注册表
│   └── OrderOperationRegistry.java
└── rule/          规则容器 + 规则注册表
```

| 命名 | 约定 | ✅ 示例 | ❌ 反例 |
| --- | --- | --- | --- |
| 注册表类 | `{聚合}OperationRegistry`，继承 `OperationRegistry` | `OrderOperationRegistry` | `OrderOperations` |
| 操作常量 | `public static final EntityOperation`，`code` 全大写下划线、不带聚合前缀 | `PAY` | `payOrder` |
| 单例 | `public static final INSTANCE` | `OrderOperationRegistry.INSTANCE` | `new OrderOperationRegistry()` |

> 命名风格与规则注册表（`BrokenRuleRegistry`）一致，见 [规则注册表设计](./registry-design.md)。

## 3. 数据 / 职责承载

| 承载 | 不承载 |
| --- | --- |
| 聚合全部业务操作的 `EntityOperation`（code + 描述） | 触发逻辑（业务方法 `recordOperation` 负责） |
| 操作与事件的因果归属（事件 `operationCode` 自动回填） | 校验逻辑（规则容器负责） |

## 4. 落地方式（核心）

落地三步：声明操作常量 → 提供单例 → 聚合根接入并在业务方法内触发。

### 4.1 完整代码骨架

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

### 4.2 编写规则

- 操作常量用 `public static final EntityOperation` 声明，**字段类型必须是 `EntityOperation`**（不能声明成 `IEntityOperation`，否则不满足基类反射注册）。
- `code` 全大写下划线、**不带聚合/领域前缀**（内置 `NEW` / `DELETE` 即无前缀）；`code` 与常量字段名**完全一致**。
- 常量必须声明在**最终子类**上——中间抽象类里声明的字段不会被基类反射扫到。
- 提供 `public static final INSTANCE` 单例与 `private` 构造，防止外部随意 new 出独立注册表。

### 4.3 聚合根接入与触发

```java
@Override
protected OperationRegistry operationRegistry() {
    return OrderOperationRegistry.INSTANCE;  // 返回非 null 即启用操作体系
}

public void cancel(String reason) {
    this.status = OrderStatus.CANCELLED;
    this.cancelReason = reason;
    this.markModified();
    this.recordOperation(OrderOperationRegistry.CANCEL);       // 先记录操作
    this.collectEvent(OrderCancelledEvent.buildEvent(this));   // 事件 operationCode 自动取 "CANCEL"
}
```

> ⚠️ **重要约束**：每次业务行为必须**先 `recordOperation`，后 `collectEvent`**。事件的 `operationCode` 自动取自最近一次操作；启用了操作体系却跳过 `recordOperation` 直接 `collectEvent`，会抛 `OperationException`。确需解耦时用显式重载 `collectEvent(event, operation)`。

## 5. 关键机制与避坑

- **反射自动注册，子类必须 `public`**：基类构造时先注册内置 `NEW` / `DELETE`，再扫描子类声明的 `static EntityOperation` 字段自动注册。子类包级私有时反射 `field.get(null)` 失败被**静默吞掉**——症状**延后**到业务方法 `recordOperation` 时才以 `OperationException`（"operation not found in OperationRegistry"）暴露，比规则注册表更晚发现。
- **`EntityOperation` 仅按 `code` 判等**：注册表以 `code` 为 key，`description` 不参与判等。
- **`operationRegistry()` 返回 `null` 即不启用操作体系**：此时 `recordOperation` / `hasOperation*` 抛 `OperationException`，但 `collectEvent` 仍可用（事件 `operationCode` 为 `null`）。这与规则注册表不同——`brokenRuleRegistry()` **不能**返回 `null`。
- **已触发操作判断**：聚合根提供 `hasOperation` / `hasAnyOperation` / `hasAllOperations`，供规则激活条件与业务分支判断（见 [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md) §4.4.3）。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 注册表写成包级私有 | 反射注册失败，操作未注册且构造期无报错，症状延后为 `OperationException` | 注册表子类必须 `public` |
| 常量声明在中间抽象类 | `getDeclaredFields()` 只扫最终子类，字段不注册 | 操作常量声明在最终子类上 |
| 字段类型声明为 `IEntityOperation` | 不满足反射匹配，未注册 | 字段类型必须是 `EntityOperation` |
| `code` 与常量字段名不一致 | 追踪与对账困难 | 约定 `code` 与常量字段名完全一致 |
| 每次 `operationRegistry()` 都 new 一个注册表 | 浪费实例、语义漂移 | 提供 `public static final INSTANCE` 单例 |
| 启用了操作体系却跳过 `recordOperation` | `collectEvent` 抛 `OperationException` | 严格遵循"先 `recordOperation` 后 `collectEvent`" |

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根编码规范与父类能力
- [规则注册表设计](./registry-design.md)：校验消息码（`BrokenRuleRegistry`）的编写规范
- [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)：基于 Operation 的规则激活条件
- [应用层落地模式](./application-collaboration.md)：WriteService 中的操作/事件顺序
- [核心：领域操作](../core/domain-operation.md)：`OperationRegistry` / `TriggeredOperations` 机制详解
- [核心：领域事件](../core/domain-events.md)：`BaseDomainEvent` 与事件发布
