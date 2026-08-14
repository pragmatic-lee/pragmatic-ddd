# 领域操作：实体业务操作与因果归属

> 本文档说明 `io.pragmatic.ddd.operation` 包提供的领域操作能力，以及它与聚合根、领域事件的集成方式。相关文档：[领域建模](./domain-modeling.md) · [领域事件](./domain-events.md) · [仓储](./repository.md)。

## 1. 概述

### 1.1 核心定位

`io.pragmatic.ddd.operation` 提供 DDD 战术建模中的**领域操作**能力：在一次工作单元内记录聚合根执行了哪些业务操作（如创建、支付、取消、发货），并作为领域事件的因果归属依据。

### 1.2 概念层级与依赖关系

```text
IEntityOperation                  操作标记接口（code + description）
  └─ EntityOperation              操作描述符（不可变值对象，非枚举）

OperationRegistry                 操作注册表基类（反射自动注册 static 字段）
TriggeredOperations               已触发操作收集器（一次工作单元内的全部操作）
OperationException                操作异常（继承 PragmaticException）

AggregateRoot<T>                  聚合根基类，组合使用上述能力
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IEntityOperation` | `io.pragmatic.ddd.operation` | 操作契约标记接口，供 ArchUnit 与 VO 枚举区分 |
| `EntityOperation` | `io.pragmatic.ddd.operation` | 业务操作描述符（不可变值对象） |
| `OperationRegistry` | `io.pragmatic.ddd.operation` | 操作注册表基类，反射自动注册 |
| `TriggeredOperations` | `io.pragmatic.ddd.operation` | 聚合根内已触发操作的收集与判断 |
| `OperationException` | `io.pragmatic.ddd.operation` | 操作相关异常 |

### 1.3 核心价值

- **因果追踪**：领域事件的 `operationCode` 自动取自最近一次 `recordOperation`，无需手动透传。
- **操作判断**：业务逻辑可依据"已执行过哪些操作"做分支校验（如未支付不可发货）。
- **审计能力**：`TriggeredOperations` 记录一次工作单元内聚合根触发的全部操作。

## 2. 核心概念详解

### 2.1 操作契约：`IEntityOperation`

轻量标记接口，定义操作的两个基本属性。框架层（如 `EntityOperation`）实现此接口，以便与值对象枚举（`IValueObject`）在类型体系上区分，从根上规避"操作"与"值对象枚举"的归类陷阱。

```java
public interface IEntityOperation {
    String code();        // 操作编码（业务唯一 key）
    String description(); // 操作描述
}
```

### 2.2 操作描述符：`EntityOperation`

不可变值对象（非枚举），是 `OperationRegistry` 反射自动注册的基本单元。

构造工厂：

```java
public final class EntityOperation implements IEntityOperation {
    public static EntityOperation of(String code, String description);  // 带描述
    public static EntityOperation of(String code);                      // 描述为空串

    @Override public String code();        // 返回 code
    @Override public String description(); // 返回 description
}
```

| 规则 | 说明 |
| --- | --- |
| 相等性 | **仅**按 `code` 判定（`description` 不参与） |
| 不可变 | `code` / `description` 均为 `final`，无可变 setter |
| 构造 | 构造器 `private`，仅经 `of(...)` 工厂创建 |

#### 示例代码

```java
EntityOperation CANCEL = EntityOperation.of("CANCEL", "取消订单");
EntityOperation SHIP   = EntityOperation.of("SHIP");   // 描述为空
```

### 2.3 操作注册表：`OperationRegistry`

注册表基类，构造时先注册内置操作，再反射扫描子类声明的 `static EntityOperation` 字段并自动注册。子类只需声明常量、提供 `INSTANCE`，无需模板方法。

内置操作（构造时即注册）：

| 操作 | code | description |
| --- | --- | --- |
| `NEW` | `NEW` | 新建 |
| `DELETE` | `DELETE` | 删除 |

方法：

| 方法 | 可见性 | 说明 |
| --- | --- | --- |
| `OperationRegistry()` | `public` | 构造：注册 `NEW`/`DELETE`，再反射扫描子类 `static EntityOperation` 字段 |
| `register(EntityOperation...)` | `protected final` | 以 `code()` 为 key 注册 |
| `operations()` | `package-private` | 返回只读（`unmodifiableMap`）映射，供 `TriggeredOperations` 校验 |

反射扫描规则：遍历 `getClass().getDeclaredFields()`，对 **`static` 且类型为 `EntityOperation`** 的字段调用 `field.get(null)` 注册；字段访问失败（`IllegalAccessException`）**静默忽略**，注册继续。

#### 关键约束

> **重要约束**：注册表子类**必须**为 `public`。构造函数通过反射 `field.get(null)` 读取 `static EntityOperation` 字段；若子类为包级私有，`IllegalAccessException` 被静默吞掉，导致该操作**未注册**——后续 `TriggeredOperations.put(...)` 对其抛 `OperationException`（"operation not found in OperationRegistry"）。

#### 示例代码

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

### 2.4 已触发操作收集器：`TriggeredOperations`

聚合根组合持有，负责收集并校验一次工作单元内已触发的 `EntityOperation`。

| 方法 | 说明 |
| --- | --- |
| `put(EntityOperation)` | 放入已触发操作；若操作不在所属注册表中则抛 `OperationException` |
| `contains(EntityOperation)` | 是否已触发指定操作（单值） |
| `containsAll(EntityOperation...)` | 是否已触发全部指定操作 |
| `containsAny(EntityOperation...)` | 是否已触发任一指定操作 |
| `clear()` | 清空已收集的操作 |

`put` 校验：仅当 `operation.code()` 存在于 `OperationRegistry.operations()` 时才放入；否则抛 `OperationException("operation not found in OperationRegistry: " + code)`。这从根上保证聚合根只能记录注册表内声明过的合法操作，避免拼写错误的游离操作码污染因果链。

### 2.5 操作异常：`OperationException`

```java
public class OperationException extends PragmaticException {
    public OperationException(String message);
}
```

`OperationException` 继承 `PragmaticException`，属于框架统一异常体系的成员。所有框架业务异常均可经 `catch (PragmaticException e)` 兜底捕获。

触发场景：

| 触发点 | 触发条件 | 异常信息 |
| --- | --- | --- |
| `TriggeredOperations.put` | 操作未注册于所属 `OperationRegistry` | `operation not found in OperationRegistry: <code>` |
| `AggregateRoot.recordOperation` / `hasOperation` 等 | 未启用操作体系（`operationRegistry()` 返回 `null`） | 由聚合根抛出 `OperationException` |
| `AggregateRoot` 事件成因解析 | 启用了操作体系但 `collectEvent` 前未 `recordOperation` | 由聚合根抛出 `OperationException` |

## 3. 与聚合根的集成

操作体系通过聚合根的两个抽象方法接入：`brokenRuleRegistry()` 与 `operationRegistry()`。

```java
protected abstract OperationRegistry operationRegistry();  // 返回注册表；null = 不启用操作体系
```

聚合根内置领域操作能力（`protected`，除查询方法）：

| 方法 | 可见性 | 说明 |
| --- | --- | --- |
| `recordOperation(EntityOperation)` | `protected` | 记录一次操作，经 `TriggeredOperations.put` 收集；更新因果指针 |
| `hasOperation / hasAllOperations / hasAnyOperation` | `public` | 判断是否已触发指定操作（委托 `TriggeredOperations`） |
| `collectEvent(BaseDomainEvent)` | `protected` | 收集事件，`operationCode` 自动取自最近一次 `recordOperation` |
| `collectEvent(BaseDomainEvent, EntityOperation)` | `protected` | 收集事件并显式指定成因操作（优先级最高） |
| `clearWorkUnitState()` | `public` | 清空领域事件、已触发操作与因果指针；应用层在事件分发完成后调用 |

### 3.1 启用操作体系

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

### 3.2 不启用操作体系

```java
@Override
protected OperationRegistry operationRegistry() {
    return null;  // 不启用领域操作
}
```

> **重要约束**：`operationRegistry()` 返回 `null` 时，调用 `recordOperation` / `hasOperation*` 会抛 `OperationException`。但 `collectEvent` 仍可正常使用，此时事件的 `operationCode` 为 `null`。

### 3.3 操作条件判断

操作条件判断（`hasOperation` / `hasAllOperations` / `hasAnyOperation`）的本职是**读取"本次工作单元已触发了哪些操作"**，而非在业务方法内守卫调用顺序。它的两个典型落点是**规则激活条件**与**持久化细化更新**，而不是在聚合根业务方法里自查"有没有记录过某操作"。

#### 场景 A — 规则激活条件（领域层）

在 `IRule` / `EntityRule` 实现里，依据已触发的操作决定是否激活某条规则。例如仅当本次触发过 `CANCEL` 时才校验取消原因必填：

```java
public class CancelReasonRequiredRule implements IRule<Order> {

    private final Order order;

    public CancelReasonRequiredRule(Order order) {
        this.order = order;
    }

    @Override
    public boolean isSatisfiedBy(Order order) {
        // 激活条件：仅当本次触发过 CANCEL 才校验
        if (!order.hasOperation(OrderOperationRegistry.CANCEL)) {
            return true;  // 未取消则不激活本规则
        }
        return order.getCancelReason() != null;
    }
}
```

#### 场景 B — 持久化时按已触发操作细化更新（基础设施层）

仓储 `save()` 时，根据聚合根已触发了哪些操作，选择不同的更新策略（不同业务操作更新不同字段），避免全字段 `UPDATE` 覆盖并发修改的无关字段：

```java
public void save(Order order) {
    if (order.hasOperation(OperationRegistry.NEW)) {
        // 新建：全量插入
        orderMapper.insert(order);
        return;
    }
    if (order.hasOperation(OrderOperationRegistry.PAY)) {
        // 支付场景：仅更新金额相关字段 + 状态，不动物流信息
        orderMapper.updatePaymentColumns(order);   // 如 paid_amount / status / paid_at
        return;
    }
    if (order.hasOperation(OrderOperationRegistry.SHIP)) {
        // 发货场景：仅更新物流字段 + 状态，不动金额
        orderMapper.updateShippingColumns(order);  // 如 tracking_no / status / shipped_at
        return;
    }
    // 兜底：通用全字段更新
    orderMapper.update(order);
}
```

> **重要约束**：同一聚合根在一次工作单元内通常只触发一个主操作，仓储据此路由到"只动本次操作涉及的列"的 SQL；更新应配合 `version` 乐观锁（`UPDATE ... WHERE version = oldVersion`）以保证并发安全。

三种判断方法：

```java
boolean hasPay        = order.hasOperation(OrderOperationRegistry.PAY);
boolean hasPayAndShip = order.hasAllOperations(
        OrderOperationRegistry.PAY,
        OrderOperationRegistry.SHIP);
boolean hasPayOrCancel = order.hasAnyOperation(
        OrderOperationRegistry.PAY,
        OrderOperationRegistry.CANCEL);
```

## 4. 关键机制与避坑指南

### 4.1 事件成因与操作指针

```text
recordOperation(CANCEL)         ← 记录操作，lastRecordedOperation = CANCEL
    ↓
collectEvent(CancelledEvent)    ← 事件的 operationCode 自动取 "CANCEL"
    ↓
getDomainEvents()               ← 事件携带 operationCode="CANCEL", version=新版本号
    ↓
eventManager.publish(events)    ← 订阅者可据 operationCode 分发处理
```

- 每次业务变更：**先 `recordOperation`，后 `collectEvent`**。
- `collectEvent(BaseDomainEvent)` 的 `operationCode` 取自最近一次 `recordOperation`（单值因果指针）。
- 显式 `collectEvent(event, operation)` 可解耦事件成因与"最近操作"，优先级最高。

### 4.2 注册表反射陷阱

> **边界外不变性/反射约束**：`OperationRegistry` 子类与 `BrokenRuleRegistry` 子类同理——必须是 `public`。反射 `field.get(null)` 对包级私有子类抛 `IllegalAccessException` 被静默吞掉，导致操作**未注册**；症状为 `TriggeredOperations.put` 抛 `OperationException`（"operation not found"），而非在构造期暴露。写注册表时让类为 `public` 即可。

### 4.3 操作唯一性与相等性

- `EntityOperation` 相等性仅由 `code` 决定。注册表以 `code` 为 key，触发收集器以 `code` 判重。
- 同一 `code` 的不同 `EntityOperation` 实例视为同一操作；`description` 不参与去重。

### 4.4 工作单元清理

- `TriggeredOperations.clear()` 仅清空已触发操作，与聚合根的 `clearBrokenRules()`（清规则违反）互不影响。
- 应用层在领域事件分发完成后调用 `clearWorkUnitState()`，统一清空事件、已触发操作与因果指针；下一次工作单元从干净状态开始。

## 5. 异常与错误处理体系

### 5.1 继承关系

```text
RuntimeException
 └─ PragmaticException              所有框架业务异常的抽象基类
     └─ OperationException          实体操作相关异常（未注册操作 / 未启用操作体系 / 成因缺失）
```

### 5.2 触发与处理规范

| 异常 | 触发条件 | 处理建议 |
| --- | --- | --- |
| `OperationException` | 操作未注册于 `OperationRegistry` | 检查注册表子类是否 `public`、对应 `static EntityOperation` 常量是否声明 |
| `OperationException` | 未启用操作体系却调用 `recordOperation` / `hasOperation*` | 确认 `operationRegistry()` 返回正确注册表，而非 `null` |
| `OperationException` | 启用操作体系但 `collectEvent` 前未 `recordOperation` | 严格遵循"先 `recordOperation` 后 `collectEvent`" |

- 统一兜底：`catch (PragmaticException e)` 可捕获 `OperationException` 在内的所有框架异常。
- `OperationException` 仅携带 `message`，无额外结构化字段。

## 6. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| `EntityOperation` | `EntityOperation.of(code, desc)` | 不可变值对象（非枚举）；相等性仅由 `code` 决定 |
| `OperationRegistry` | 子类声明 `public static final EntityOperation` 常量 + `INSTANCE` | 子类必须 `public`，否则 `static` 字段反射未注册；内置 `NEW`/`DELETE` |
| `TriggeredOperations` | 由聚合根组合持有 | `put` 校验操作已在注册表，否则抛 `OperationException` |
| 聚合根集成 | 实现 `operationRegistry()` 返回非 null | 先 `recordOperation` 后 `collectEvent`；返回 `null` 即不启用 |
| `OperationException` | 继承 `PragmaticException` | `catch (PragmaticException)` 统一兜底 |

**下一步阅读**

- [领域建模](./domain-modeling.md)：`AggregateRoot<T>` 与抽象方法全貌
- [领域事件](./domain-events.md)：`BaseDomainEvent` 与事件发布
- [应用服务](./application-service.md)：领域操作在工作单元中的清理
