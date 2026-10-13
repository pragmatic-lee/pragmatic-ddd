# 领域操作：实体业务操作与单值因果

> 本文档说明 `io.pragmatic.ddd.operation` 包提供的领域操作能力，以及它与聚合根、领域事件的集成方式。相关文档：[领域建模](./domain-modeling.md) · [领域事件](./domain-events.md) · [仓储总览](./repository.md)。

## 目录

- [1. 概述](#1-概述)
  - [1.1 核心定位](#11-核心定位)
  - [1.2 概念层级与依赖关系](#12-概念层级与依赖关系)
  - [1.3 核心价值](#13-核心价值)
- [2. 核心概念详解](#2-核心概念详解)
  - [2.1 操作契约：`IEntityOperation`](#21-操作契约ientityoperation)
  - [2.2 操作描述符：`EntityOperation`](#22-操作描述符entityoperation)
  - [2.3 操作注册表：`OperationRegistry`](#23-操作注册表operationregistry)
  - [2.4 因果操作持有者：`TriggeredOperations`](#24-因果操作持有者triggeredoperations)
  - [2.5 多操作冲突异常：`MultipleOperationsException`](#25-多操作冲突异常multipleoperationsexception)
  - [2.6 操作异常：`OperationException`](#26-操作异常operationexception)
- [3. 与聚合根的集成](#3-与聚合根的集成)
  - [3.1 启用操作体系](#31-启用操作体系)
  - [3.2 不启用操作体系](#32-不启用操作体系)
  - [3.3 操作条件判断](#33-操作条件判断)
- [4. 关键机制与避坑指南](#4-关键机制与避坑指南)
  - [4.1 事件成因与单值因果](#41-事件成因与单值因果)
  - [4.2 单值不变量与 fail-fast](#42-单值不变量与-fail-fast)
  - [4.3 复合意图的逃生门](#43-复合意图的逃生门)
  - [4.4 注册表反射陷阱](#44-注册表反射陷阱)
  - [4.5 操作唯一性与相等性](#45-操作唯一性与相等性)
  - [4.6 工作单元清理与实例复用](#46-工作单元清理与实例复用)
- [5. 异常与错误处理体系](#5-异常与错误处理体系)
  - [5.1 继承关系](#51-继承关系)
  - [5.2 触发与处理规范](#52-触发与处理规范)
- [6. 总结速查](#6-总结速查)

## 1. 概述

### 1.1 核心定位

`io.pragmatic.ddd.operation` 提供 DDD 战术建模中的**领域操作**能力：在一次工作单元（一个命令 / 一个事务边界）内记录聚合根执行的**那一个**业务操作（如创建、支付、取消、发货），并作为领域事件的因果归属依据。

框架把「一个命令只有一个因果起源」定为**硬约束**：一次工作单元只允许记录一个操作，记录第二个不同操作即在运行期 fail-fast。

### 1.2 概念层级与依赖关系

```text
IEntityOperation                  操作标记接口（code + description）
  └─ EntityOperation              操作描述符（不可变值对象，非枚举）

OperationRegistry                 操作注册表基类（反射自动注册 static 字段）
TriggeredOperations               因果操作持有者（单值：一次工作单元只记一个操作）
OperationException                操作异常（继承 PragmaticException）
  └─ MultipleOperationsException  多操作冲突异常（继承 OperationException）

AggregateRoot<T>                  聚合根基类，组合使用上述能力
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IEntityOperation` | `io.pragmatic.ddd.operation` | 操作契约标记接口，供 ArchUnit 与 VO 枚举区分 |
| `EntityOperation` | `io.pragmatic.ddd.operation` | 业务操作描述符（不可变值对象） |
| `OperationRegistry` | `io.pragmatic.ddd.operation` | 操作注册表基类，反射自动注册 |
| `TriggeredOperations` | `io.pragmatic.ddd.operation` | 因果操作持有者（单值）与判断 |
| `OperationException` | `io.pragmatic.ddd.operation` | 操作相关异常 |
| `MultipleOperationsException` | `io.pragmatic.ddd.operation` | 单值不变量被破坏时抛出 |

### 1.3 核心价值

- **因果追踪**：领域事件的 `operationCode` 自动取自本次工作单元记录的因果操作，无需手动透传。
- **操作判断**：业务逻辑可依据"本次记录的是哪个操作"做分支校验（如仅取消时校验取消原因）。
- **因果唯一性**：单值不变量把"一个命令一个因果"从隐含约定提升为运行期约束，复合意图在记录第二个操作时即刻暴露。

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

> **重要约束**：注册表子类**必须**为 `public`。构造函数通过反射 `field.get(null)` 读取 `static EntityOperation` 字段；若子类为包级私有，`IllegalAccessException` 被静默吞掉，导致该操作**未注册**——后续 `TriggeredOperations.record(...)` 对其抛 `OperationException`（"operation not found in OperationRegistry"）。

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

### 2.4 因果操作持有者：`TriggeredOperations`

聚合根组合持有，作为**单值**持有者承载本次工作单元的因果操作。类名保留复数形式（控制 API 面变更），语义已收敛为单值。

| 方法 | 说明 |
| --- | --- |
| `record(EntityOperation)` | 记录本次工作单元的因果操作；操作不在所属注册表中则抛 `OperationException` |
| `contains(EntityOperation)` | 当前因果操作是否为指定操作（单值） |
| `current()` | 返回当前因果操作，未记录时返回空 `Optional` |
| `containsAny(EntityOperation...)` | 当前因果操作是否属于指定操作之一 |
| `containsExceptOperation(EntityOperation...)` | 当前因果操作是否存在且不属于指定集合；未记录时返回 `false` |
| `clear()` | 清空当前因果操作 |

`record` 的三段语义：

| 情形 | 行为 |
| --- | --- |
| 操作 `code()` 不在所属 `OperationRegistry.operations()` 中 | 抛 `OperationException("operation not found in OperationRegistry: " + code)` |
| 首次记录（当前为空） | 置为当前因果操作 |
| 重复记录**同一** code | 幂等，忽略（不抛异常） |
| 记录**第二个不同** code | 抛 `MultipleOperationsException`（携带两个冲突 code） |

校验的第一段从根上保证聚合根只能记录注册表内声明过的合法操作，避免拼写错误的游离操作码污染因果链；单值段则保证一次工作单元只有一个因果起源。

> **重要约束**：`current()` 返回 `Optional<EntityOperation>`，未记录操作时为空。不要用 `null` 判断因果是否存在，统一走 `Optional`。

### 2.5 多操作冲突异常：`MultipleOperationsException`

继承 `OperationException`，在"一次工作单元记录第二个不同 code"时抛出，表达**一个命令只能有一个因果起源**的不变量被破坏。

```java
public class MultipleOperationsException extends OperationException {
    public MultipleOperationsException(String firstCode, String secondCode);
}
```

消息中携带冲突的两个 code，并给出出口提示：复合意图应命名为新的原子操作，或拆分为多个命令（saga）。

### 2.6 操作异常：`OperationException`

```java
public class OperationException extends PragmaticException {
    public OperationException(String message);
}
```

`OperationException` 继承 `PragmaticException`，属于框架统一异常体系的成员。所有框架业务异常均可经 `catch (PragmaticException e)` 兜底捕获。

触发场景：

| 触发点 | 触发条件 | 异常信息 |
| --- | --- | --- |
| `TriggeredOperations.record` | 操作未注册于所属 `OperationRegistry` | `operation not found in OperationRegistry: <code>` |
| `TriggeredOperations.record` | 记录第二个不同 code | `MultipleOperationsException` 消息（含两个 code） |
| `AggregateRoot.recordOperation` / `hasOperation` 等 | 未启用操作体系（`operationRegistry()` 返回 `null`） | 由聚合根抛出 `OperationException` |
| `AggregateRoot` 事件成因解析 | 启用了操作体系但 `collectEvent` 前未 `recordOperation` | 由聚合根抛出 `OperationException` |

## 3. 与聚合根的集成

操作体系通过聚合根的抽象方法接入：`brokenRuleRegistry()` 与 `operationRegistry()`。

```java
protected abstract OperationRegistry operationRegistry();  // 返回注册表；null = 不启用操作体系
```

聚合根内置领域操作能力（`protected`，除查询方法）：

| 方法 | 可见性 | 说明 |
| --- | --- | --- |
| `recordOperation(EntityOperation)` | `protected` | 记录本次工作单元的因果操作，委托 `TriggeredOperations.record` |
| `hasOperation(EntityOperation)` | `public` | 当前因果操作是否为指定操作 |
| `hasAnyOperation(EntityOperation...)` | `public` | 当前因果操作是否属于指定操作之一 |
| `hasExceptOperation(EntityOperation...)` | `public` | 当前因果操作存在且不属于指定集合（未记录时返回 `false`） |
| `collectEvent(BaseDomainEvent)` | `protected` | 收集事件，`operationCode` 自动取自本次记录的因果操作 |
| `collectEvent(BaseDomainEvent, EntityOperation)` | `protected` | 收集事件并显式指定成因操作（优先级最高） |
| `collectEvent(Supplier<IDomainEvent>)` | `protected` | 收集延迟事件，发布时回填 `operationCode` 与 `version` |
| `getDomainEvents()` | `public` | 返回本工作单元已收集的领域事件 |
| `clearWorkUnitState()` | `public` | 清空领域事件、外部需求与因果操作；应用层在事件分发完成后调用 |

> **重要约束**：`hasAllOperations(...)` 已移除。单值语义下"全部指定操作"中的 distinct code 恒不成立，该 API 为构造性死代码；需要"多操作之一"用 `hasAnyOperation(...)`，需要"除指定外"用 `hasExceptOperation(...)`。

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
        this.recordOperation(OrderOperationRegistry.CANCEL);  // 记录因果操作
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

操作条件判断（`hasOperation` / `hasAnyOperation` / `hasExceptOperation`）的本职是**读取"本次工作单元记录的是哪个操作"**，而非在业务方法内守卫调用顺序。它的两个典型落点是**规则激活条件**与**持久化细化更新**，而不是在聚合根业务方法里自查"有没有记录过某操作"。

三种判断方法的语义与适用场景：

| 方法 | 语义 | 适用场景 |
| --- | --- | --- |
| `hasOperation(op)` | 当前操作 == `op` | 单操作专属校验 |
| `hasAnyOperation(op...)` | 当前操作 ∈ 集合 | 多个操作共享同一校验 |
| `hasExceptOperation(op...)` | 当前操作存在且 ∉ 集合 | 除指定操作外都要激活（黑名单 gate） |

#### 场景 A — 规则激活条件（领域层）

在 `IRule` / `EntityRule` 实现里，依据本次记录的操作决定是否激活某条规则。例如仅当本次触发过 `CANCEL` 时才校验取消原因必填：

```java
public class CancelReasonRequiredRule implements IRule<Order> {

    private final Order order;

    public CancelReasonRequiredRule(Order order) {
        this.order = order;
    }

    @Override
    public boolean satisfiesRule(Order order) {
        // 激活条件：仅当本次记录的是 CANCEL 才校验
        if (!order.hasOperation(OrderOperationRegistry.CANCEL)) {
            return true;  // 非取消则不激活本规则
        }
        return order.getCancelReason() != null;
    }
}
```

#### 场景 B — 持久化时按因果操作细化更新（基础设施层）

仓储 `save()` 时，根据聚合根记录的因果操作，选择不同的更新策略（不同业务操作更新不同字段），避免全字段 `UPDATE` 覆盖并发修改的无关字段：

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

> **重要约束**：一次工作单元只记录一个因果操作，仓储据此路由到"只动本次操作涉及的列"的 SQL；更新应配合 `version` 乐观锁（`UPDATE ... WHERE version = oldVersion`）以保证并发安全。

三种判断方法：

```java
boolean isPay        = order.hasOperation(OrderOperationRegistry.PAY);
boolean isPayOrCancel = order.hasAnyOperation(
        OrderOperationRegistry.PAY,
        OrderOperationRegistry.CANCEL);
// 除加/减订单项外的操作都激活（未记录操作时不激活）
boolean isNotItemOp   = order.hasExceptOperation(
        OrderOperationRegistry.ADD_ITEM,
        OrderOperationRegistry.REMOVE_ITEM);
```

> **重要约束**：`hasExceptOperation(...)` 是**黑名单 gate**，对**所有操作生效，包含将来新增的操作码**；新增操作会被自动纳入该规则且无编译期提示。使用时应在规则旁注明"对所有操作生效，新增操作默认纳入"；若操作集合小且稳定，用 `hasAnyOperation(...)` 列全（白名单）更安全。未记录操作时 `hasExceptOperation` 返回 `false`（保守方向：没有操作就不激活）。

## 4. 关键机制与避坑指南

### 4.1 事件成因与单值因果

```text
recordOperation(CANCEL)         ← 记录因果操作，holder.current = CANCEL
    ↓
collectEvent(CancelledEvent)    ← 事件的 operationCode 自动取 "CANCEL"
    ↓
getDomainEvents()               ← 事件携带 operationCode="CANCEL", version=新版本号
    ↓
eventManager.publish(events)    ← 订阅者可据 operationCode 分发处理
```

- 每次业务变更：**先 `recordOperation`，后 `collectEvent`**。
- `collectEvent(BaseDomainEvent)` 的 `operationCode` 取自本次记录的因果操作（holder 承载的唯一值，不再有独立的因果指针字段）。
- 显式 `collectEvent(event, operation)` 可解耦事件成因与"当前操作"，优先级最高。

### 4.2 单值不变量与 fail-fast

> **不变量**：一次工作单元内，`recordOperation` 记录的 **distinct operation code 数量 ≤ 1**。

| 场景 | 期望行为 |
| --- | --- |
| 同一工作单元记录**第二个不同** operation code | 抛 `MultipleOperationsException`（fail-fast） |
| 同一工作单元**重复记录相同** operation code | 幂等，不抛（按 code 去重，非按调用次数） |
| 未 `recordOperation` 却 `collectEvent` | 抛既有 `OperationException` |
| 回填只写事实字段、不记操作 | 合法，不抛 |

按 distinct **code** 计数（而非调用次数）放行了合法的重复：单聚合内循环 `addItem` 三次、同一个 `ADD_ITEM` code 去重后仍算 1 个操作；多个聚合各自持有 holder，互不影响。

### 4.3 复合意图的逃生门

真有"一个命令多个意图"的需求时，合法出口有两条，**优先级不同**：

1. **拆成多个命令（saga）** —— 首选。当子动作的失败/重试要求不同、或下游要分别响应时，本质就是两个事务边界。
2. **命名为一个新的原子操作** —— 次选、就地简化。例如"一键完成"把审批 + 关闭 + 通知收敛成一个 `CLOSE_TICKET` 操作（内部可收集多条事件）。

> **重要约束**："复合即命名"要防止**操作组合爆炸**（`PAY_AND_SHIP`、`PAY_AND_SHIP_AND_NOTIFY`……）。命名只应是最后一档的就地简化，能拆就拆。

### 4.4 注册表反射陷阱

> **边界外不变性/反射约束**：`OperationRegistry` 子类与 `BrokenRuleRegistry` 子类同理——必须是 `public`。反射 `field.get(null)` 对包级私有子类抛 `IllegalAccessException` 被静默吞掉，导致操作**未注册**；症状为 `TriggeredOperations.record` 抛 `OperationException`（"operation not found"），而非在构造期暴露。写注册表时让类为 `public` 即可。

### 4.5 操作唯一性与相等性

- `EntityOperation` 相等性仅由 `code` 决定。注册表以 `code` 为 key，因果操作持有者以 `code` 判等与幂等。
- 同一 `code` 的不同 `EntityOperation` 实例视为同一操作；`description` 不参与去重。
- 判断方法一律按 `code` 比较（`contains` / `containsAny` / `containsExceptOperation`），不依赖实例等同性。

### 4.6 工作单元清理与实例复用

- `TriggeredOperations.clear()` 仅清空当前因果操作，与聚合根的 `clearBrokenRules()`（清规则违反）互不影响。
- 应用层在领域事件分发完成后调用 `clearWorkUnitState()`，统一清空事件、外部需求与因果操作；下一次工作单元从干净状态开始。

> **重要约束**：单值不变量是 fail-fast，其正确性**完全依赖工作单元的生命周期铁律**——`clearWorkUnitState()` 必须在**每条路径（含规则校验失败等异常路径）**上执行。一旦有路径漏清，同一聚合实例被复用（重试、长生命周期聚合、测试复用）时，残留操作会被误判为"第一个"，下一条正常命令再记录操作即触发**假阳性** `MultipleOperationsException`。建议在命令入口先清场一次，并保证 `finally` 覆盖所有分支。

## 5. 异常与错误处理体系

### 5.1 继承关系

```text
RuntimeException
 └─ PragmaticException                     所有框架业务异常的抽象基类
     └─ OperationException                 实体操作相关异常（未注册操作 / 未启用操作体系 / 成因缺失）
         └─ MultipleOperationsException    一次工作单元记录多个不同因果操作
```

### 5.2 触发与处理规范

| 异常 | 触发条件 | 处理建议 |
| --- | --- | --- |
| `OperationException` | 操作未注册于 `OperationRegistry` | 检查注册表子类是否 `public`、对应 `static EntityOperation` 常量是否声明 |
| `MultipleOperationsException` | 一次工作单元记录了第二个不同 code | 确认命令是否表达复合意图；拆分为多个命令（saga）或命名为新的原子操作 |
| `OperationException` | 未启用操作体系却调用 `recordOperation` / `hasOperation*` | 确认 `operationRegistry()` 返回正确注册表，而非 `null` |
| `OperationException` | 启用操作体系但 `collectEvent` 前未 `recordOperation` | 严格遵循"先 `recordOperation` 后 `collectEvent`" |

- 统一兜底：`catch (PragmaticException e)` 可捕获 `MultipleOperationsException` / `OperationException` 在内的所有框架异常。
- `OperationException` 仅携带 `message`，无额外结构化字段；`MultipleOperationsException` 亦仅携带消息（消息内含两个冲突 code）。

## 6. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| `EntityOperation` | `EntityOperation.of(code, desc)` | 不可变值对象（非枚举）；相等性仅由 `code` 决定 |
| `OperationRegistry` | 子类声明 `public static final EntityOperation` 常量 + `INSTANCE` | 子类必须 `public`，否则 `static` 字段反射未注册；内置 `NEW`/`DELETE` |
| `TriggeredOperations` | 由聚合根组合持有（单值） | `record` 校验操作已在注册表；同 code 幂等，不同 code 抛 `MultipleOperationsException` |
| 聚合根集成 | 实现 `operationRegistry()` 返回非 null | 一次工作单元只记一个操作；先 `recordOperation` 后 `collectEvent`；返回 `null` 即不启用 |
| 操作判断 | `hasOperation` / `hasAnyOperation` / `hasExceptOperation` | `hasAllOperations` 已移除；`hasExceptOperation` 未记录操作时返回 `false` |
| `OperationException` | 继承 `PragmaticException` | `catch (PragmaticException)` 统一兜底 |
| `MultipleOperationsException` | 继承 `OperationException` | 单值不变量被破坏即 fail-fast；出口为拆 saga 或命名新原子操作 |

**下一步阅读**

- [领域建模](./domain-modeling.md)：`AggregateRoot<T>` 与抽象方法全貌
- [领域事件](./domain-events.md)：`BaseDomainEvent` 与事件发布
- [应用服务](./application-service.md)：领域操作在工作单元中的清理
