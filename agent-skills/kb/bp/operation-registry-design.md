# BP 卡：操作注册表设计（operation-registry-design）

> 源：`documentation/best-practices/operation-registry-design.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

操作注册表把聚合的业务操作（创建、支付、取消、发货……）**集中声明为可校验的常量**，让聚合根只能记录注册表内声明过的操作，并让领域事件自动携带"最近一次操作"作为因果归属。

- 解决：操作码集中声明、事件成因可追踪、操作合法性可校验。
- 核心特征：`public static final EntityOperation` 常量 + 单例 `INSTANCE`；基类构造时自动注册内置 `NEW`/`DELETE` 与子类声明常量。
- 与写模型边界：只声明操作常量，**不承载触发逻辑**；触发由聚合根业务方法 `recordOperation` 完成。

## 2. 命名与包结构

```text
domain/order/
├── model/         聚合根、实体、值对象
├── operation/     操作注册表
│   └── OrderOperationRegistry.java
└── rule/          规则容器 + 规则注册表
```

| 命名 | 约定 |
| --- | --- |
| 注册表类 | `{聚合}OperationRegistry`（继承 `OperationRegistry`），如 `OrderOperationRegistry` |
| 操作常量 | `public static final EntityOperation`，`code` 全大写下划线、不带聚合前缀，如 `PAY` |
| 单例 | `public static final INSTANCE`，如 `OrderOperationRegistry.INSTANCE` |

## 3. 落地方式（核心）

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

编写规则：

- 操作常量字段类型**必须是 `EntityOperation`**（不能声明成 `IEntityOperation`，否则不满足基类反射注册）。
- `code` 全大写下划线、**不带聚合/领域前缀**（内置 `NEW`/`DELETE` 即无前缀）；`code` 与常量字段名**完全一致**。
- 常量必须声明在**最终子类**上——中间抽象类声明的字段不会被基类反射扫到。
- 提供 `public static final INSTANCE` 单例与 `private` 构造。

### 3.1 聚合根接入与触发

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
    this.collectEvent(OrderCancelledEvent.buildEvent(this));    // 事件 operationCode 自动取 "CANCEL"
}
```

> ⚠️ 每次业务行为必须**先 `recordOperation`，后 `collectEvent`**。启用操作体系却跳过 `recordOperation` 直接 `collectEvent` 会抛 `OperationException`；确需解耦用显式重载 `collectEvent(event, operation)`。

## 4. 关键机制与避坑

- **反射自动注册，子类必须 `public`**：基类构造时先注册内置 `NEW`/`DELETE`，再扫描子类 `static EntityOperation` 字段自动注册。子类包级私有时反射 `field.get(null)` 失败被**静默吞掉**——症状**延后**到业务方法 `recordOperation` 才以 `OperationException`（"operation not found"）暴露，比规则注册表更晚发现。
- **`EntityOperation` 仅按 `code` 判等**：`description` 不参与判等。
- **`operationRegistry()` 返回 `null` 即不启用操作体系**：此时 `recordOperation`/`hasOperation*` 抛 `OperationException`，但 `collectEvent` 仍可用（事件 `operationCode` 为 `null`）。与规则注册表不同——`brokenRuleRegistry()` **不能**返回 `null`。
- **一次工作单元只记一个操作**：`recordOperation` 重复记同一 `code` 幂等，记录第二个不同 `code` 立即抛 `MultipleOperationsException`（继承 `OperationException`）。复合意图应拆为多个命令（saga）或命名新原子操作。
- **因果操作判断**：聚合根提供 `hasOperation`/`hasAnyOperation`/`hasExceptOperation`，供规则激活条件与业务分支判断。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 注册表写成包级私有 | 子类必须 `public` |
| 常量声明在中间抽象类 | 声明在最终子类上 |
| 字段类型声明为 `IEntityOperation` | 字段类型必须是 `EntityOperation` |
| `code` 与常量字段名不一致 | `code` 与字段名完全一致 |
| 每次 `operationRegistry()` 都 new | 提供 `INSTANCE` 单例 |
| 启用操作体系却跳过 `recordOperation` | 严格"先 `recordOperation` 后 `collectEvent`" |

## 6. 被读于

S4 `ddd-aggregate-create`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
