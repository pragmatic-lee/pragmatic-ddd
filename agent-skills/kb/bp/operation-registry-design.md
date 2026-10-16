# BP 卡：操作注册表（operation-registry-design）

> 源：`documentation/best-practices/operation-registry-design.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 本质

操作注册表把聚合业务操作集中声明为可校验常量，让聚合根只能记录注册表内声明过的操作，领域事件自动携带「最近一次操作」作因果归属。只声明操作常量，**不承载触发逻辑**（触发由聚合根业务方法 `recordOperation` 完成）。

## 2. 命名与包

- 注册表类：`{Agg}OperationRegistry extends OperationRegistry`，放 `domain/{agg}/operation/`。
- 操作常量：`public static final EntityOperation`，`code` 全大写下划线、不带聚合前缀。
- 单例：`public static final INSTANCE`。

## 3. 骨架

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

## 4. 聚合根接入

```java
@Override
protected OperationRegistry operationRegistry() {
    return OrderOperationRegistry.INSTANCE;
}

public void cancel(String reason) {
    this.status = OrderStatus.CANCELLED;
    this.cancelReason = reason;
    this.markModified();
    this.recordOperation(OrderOperationRegistry.CANCEL);       // 先记录操作
    this.collectEvent(OrderCancelledEvent.buildEvent(this));   // 事件 operationCode 自动取 "CANCEL"
}
```

## 5. 关键机制与避坑

- **反射自动注册，子类必须 `public`**：基类构造时先注册内置 `NEW`/`DELETE`，再扫描子类 `static EntityOperation` 字段。子类包级私有时反射 `field.get(null)` 失败被**静默吞掉**——症状**延后**到业务方法 `recordOperation` 才以 `OperationException`（"operation not found"）暴露。
- **`EntityOperation` 仅按 `code` 判等**：以 `code` 为 key，`description` 不参与判等。
- **`operationRegistry()` 返回 `null` = 不启用操作体系**：此时 `recordOperation`/`hasOperation*` 抛 `OperationException`，但 `collectEvent` 仍可用（事件 `operationCode` 为 `null`）。这与规则注册表不同——`brokenRuleRegistry()` **不能**返回 `null`。
- **一次工作单元只记一个操作**：`recordOperation` 重复记录同一 `code` 幂等，记录第二个不同 `code` 立即抛 `MultipleOperationsException`（继承 `OperationException`）。复合意图应拆为多个命令（saga）或命名为新原子操作。
- **因果操作判断**：聚合根提供 `hasOperation` / `hasAnyOperation` / `hasExceptOperation`，供规则激活条件与业务分支判断（见 [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)）。
- **重要约束**：每次业务行为必须**先 `recordOperation` 后 `collectEvent`**；跳过 `recordOperation` 直接 `collectEvent` 抛 `OperationException`。确需解耦用显式重载 `collectEvent(event, operation)`。

## 6. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 注册表写成包级私有 | 子类必须 `public` |
| 常量声明在中间抽象类 | `getDeclaredFields()` 只扫最终子类，字段不注册 |
| 字段类型声明为 `IEntityOperation` | 字段类型必须是 `EntityOperation` |
| `code` 与常量字段名不一致 | `code` 与常量字段名完全一致 |
| 每次 `operationRegistry()` 都 new | 提供 `public static final INSTANCE` 单例 |
| 启用了操作体系却跳过 `recordOperation` | 严格遵循"先 `recordOperation` 后 `collectEvent`" |

## 7. 被读于

S4 `ddd-aggregate-create`（步骤 3）、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
