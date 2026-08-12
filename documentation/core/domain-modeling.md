# 领域建模：实体、值对象与聚合根

> 本文档说明 `io.pragmatic.ddd.base` 包提供的领域建模能力。相关文档：[业务规则引擎](./business-rules.md) · [领域事件](./domain-events.md) · [仓储](./repository.md)。

## 1. 概述

### 1.1 核心定位

`io.pragmatic.ddd.base` 提供 DDD 战术建模的基类与接口：实体标识、值对象、聚合根、规则消息码。继承或实现后，框架统一管理 ID、审计字段、等同性、规则校验、版本号、领域事件与操作追踪，开发者无需手写样板代码。

### 1.2 概念层级与依赖关系

```text
IEntity<T>                     实体标识契约
  └─ AbstractEntity<T>        实体基类（ID / 软删 / 审计 / 等同性）
       └─ AggregateRoot<T>    聚合根基类（规则 / 版本 / 事件 / 操作）

IValueObject                   值对象标记接口（仅标记）
  └─ ValueObject              结构相等值对象基类（可选继承）

IEnumValue<T,K>               枚举值对象接口（替代 Java enum）

MessageCode                   record：规则消息码（局部码 + 描述）
BrokenRuleRegistry           规则消息注册表基类（反射自动注册）
BrokenRuleObject             规则违反收集器（AggregateRoot 组合持有）
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IEntity<T>` | `io.pragmatic.ddd.base` | 约束实体暴露 `getEntityId()` |
| `AbstractEntity<T>` | `io.pragmatic.ddd.base` | 实体通用能力 |
| `AggregateRoot<T>` | `io.pragmatic.ddd.base` | 聚合根，所有需持久化的实体应继承 |
| `ValueObject` / `IValueObject` | `io.pragmatic.ddd.base` | 值对象 |
| `IEnumValue<T,K>` | `io.pragmatic.ddd.base` | 枚举值对象 |
| `MessageCode` / `BrokenRuleRegistry` | `io.pragmatic.ddd.base` | 规则消息码与注册表 |

## 2. 核心概念详解

### 2.1 实体（Entity）

#### 契约 / 接口：`IEntity<T>`

```java
public interface IEntity<T> {
    T getEntityId();   // 返回实体标识
}
```

#### 基类能力：`AbstractEntity<T>`

字段与方法（Lombok 注解：`@Getter` + `@Setter(AccessLevel.PROTECTED)`）：

| 成员 | 可见性 | 说明 |
| --- | --- | --- |
| `entityId` | `protected set` / `get` | 身份标识，经 `setEntityId(T)` 赋值 |
| `entityDelete` | `protected set` / `get` | 软删标记 |
| `createdAt` / `updatedAt` | `protected set` / `get` | 审计时间戳（`LocalDateTime`） |
| `createdBy` / `updatedBy` | `protected set` / `get` | 审计操作人 |
| `markCreated()` | `protected` | 设 `createdAt` 与 `updatedAt` 为当前时间，构造末尾调用一次 |
| `markModified()` | `protected` | 刷新 `updatedAt` 为当前时间 |

`equals` / `hashCode` / `toString` 行为：

| 方法 | 规则 |
| --- | --- |
| `equals` | 两实体 `getEntityId()` **均非空且相等** 才相等；否则 `false` |
| `hashCode` | ID 非空时取 `entityId.hashCode()`，为空退回父类哈希 |
| `toString` | 形如 `ClassName{id=...}` |

#### 关键约束

> **重要约束**：实体等同性**仅**由 `entityId` 决定。ID 相等即视为同一实体，与其他字段无关。重建对象（ID 相同）与内存新对象判等为真，但两者状态不自动同步。

> **重要约束**：审计时间戳只能经 `markCreated()` / `markModified()` 写入，禁止直接调用 Lombok 的 `setCreatedAt` / `setUpdatedAt` 以外的途径；审计字段 setter 为 `protected`，非聚合外部可写。

#### 示例代码

```java
public class Address extends AbstractEntity<Long> {

    private String province;
    private String city;

    public Address(Long id, String province, String city) {
        this.setEntityId(id);
        this.province = province;
        this.city = city;
        this.markCreated();
    }

    public void changeCity(String city) {
        this.city = city;
        this.markModified();
    }
}
```

### 2.2 值对象（Value Object）

#### 标记接口：`IValueObject`

```java
public interface IValueObject { }   // 纯标记，供反射识别，不承载行为契约
```

与 MyBatis JSON 通道配合时，`IValueObject` 被 `GenericJsonTypeHandler` 自动识别，整体序列化到 JSON 列（详见 [MyBatis 集成](../integration/mybatis.md)）。

#### 结构相等基类：`ValueObject`

| 成员 | 说明 |
| --- | --- |
| `equalityComponents()` | `protected abstract Object[]`，返回参与相等性判定的成分（**顺序敏感**） |
| `equals` | `final`，基于 `Arrays.equals(equalityComponents(), ...)`；要求 `getClass()` 完全相同 |
| `hashCode` | `final`，基于 `Arrays.hashCode(equalityComponents())` |
| `toString` | `getClass().getSimpleName() + Arrays.toString(equalityComponents())` |

#### 关键约束

> **重要约束**：`ValueObject` 的 `equals` / `hashCode` 为 `final`，子类**不可覆盖**。相等判定要求**精确类匹配**（`getClass() != o.getClass()` 直接返回 `false`），子类间即使成分相同也不相等。

> **重要约束**：不可变性由使用者保证。`ValueObject` 不强制不可变，应自行使用全参构造 + 构造期校验，不提供可变 setter。

#### 示例代码

```java
public class Money extends ValueObject {
    private final long amount;
    private final String currency;

    public Money(long amount, String currency) {
        this.amount = amount;
        this.currency = currency;
    }

    @Override
    protected Object[] equalityComponents() {
        return new Object[]{amount, currency};
    }
}
```

`new Money(100, "CNY").equals(new Money(100, "CNY"))` 为 `true`。

### 2.3 枚举值对象（Enum Value Object）

#### 接口：`IEnumValue<T,K>`

```java
public interface IEnumValue<T, K extends Enum<?>> {
    T getValue();             // 持久化 / 传输用的业务 code
    String getName();         // 展示名（label）
    default String getDesc() { return getName(); }
}
```

| 类型参数 | 含义 |
| --- | --- |
| `T` | 业务 code 类型（持久化到 DB 的值） |
| `K` | 枚举类型本身 |

#### 关键约束

> **重要约束**：枚举值对象用于替代 Java `enum`，规避 MyBatis 持久化痛点。持久化经 `EnumRule(CODE)` 写入 `getValue()` 的返回值，推荐使用 `CODE` 模式。

#### 示例代码

```java
public enum OrderStatus implements IEnumValue<String, OrderStatus> {
    CREATED("CREATED", "已创建"),
    PAID("PAID", "已支付"),
    CANCELLED("CANCELLED", "已取消");

    private final String value;
    private final String name;

    OrderStatus(String value, String name) {
        this.value = value;
        this.name = name;
    }

    @Override public String getValue() { return value; }
    @Override public String getName() { return name; }
}
```

### 2.4 聚合根（Aggregate Root）

聚合根是持久化的一致性边界：一个聚合根实例对应一次事务的写范围，聚合内部的不变性（规则校验）、版本（乐观锁）与领域事件均在该边界内保证一致；跨聚合的一致性通过领域事件解耦、由各订阅者分别响应，不在单个聚合根内直接修改其他聚合。

#### 抽象方法（必须实现）

```java
protected abstract BrokenRuleRegistry brokenRuleRegistry();   // 返回规则注册表，不可为 null
protected abstract OperationRegistry operationRegistry();     // 返回操作注册表；返回 null = 不启用操作体系
```

#### 基类能力：`AggregateRoot<T>`

**规则校验**（委托 `BrokenRuleObject`）：

| 方法 | 可见性 | 说明 |
| --- | --- | --- |
| `satisfiesRule(IRule<?>)` | `public` | 以自身为 model 执行规则，`rule==null` 视为通过；返回 `true`/`false` |
| `addBrokenRule(MessageCode)` | `public` | 追加一条规则违反 |
| `addParamBrokenRule(MessageCode, Object[], boolean)` | `public` | 追加支持参数格式化的违反；`isAutoFormat=true` 时用 `String.format(description, params)` |
| `getBrokenRules()` | `public` | 返回已收集违反（只读） |
| `throwBrokenRuleException()` | `public` | 有违反则抛**单条**异常（取第一条） |
| `throwBrokenRuleAggregateException()` | `public` | 有违反则抛**聚合**异常（含全部） |
| `clearBrokenRules()` | `public` | 清空已收集违反 |

**版本与新建标记**：

| 成员 | 可见性 | 说明 |
| --- | --- | --- |
| `oldVersion` | `get` | 上一次持久化版本，仓储 `findById` 回填，默认 `1` |
| `getNewVersion()` | `public` | 返回递增后的新版本号（**幂等**）：首次调用 `oldVersion + 1` 并缓存，之后返回同一值 |
| `isNew` | `get` | 是否新建标记 |
| `markNew()` | `public` | 置 `isNew = true`，仓储 `save()` 据此路由 insert / update |

**领域事件**（均为 `protected`）：

| 方法 | 说明 |
| --- | --- |
| `collectEvent(BaseDomainEvent)` | 收集立即事件；自动回填 `operationCode`（最近一次 `recordOperation`）与 `version` |
| `collectEvent(BaseDomainEvent, EntityOperation)` | 收集事件并显式指定成因操作（优先级最高） |
| `collectEvent(Supplier<IDomainEvent>)` | 收集**延迟事件**，发布时才执行 supplier 并回填 `operationCode` / `version` |
| `getDomainEvents()` | 返回本工作单元已收集事件（`public`） |
| `triggerDataSyncHook()` | 持久化落库前由仓储调用；默认空实现，子类覆写以发异构事件 |

**操作追踪**（均为 `protected`，除查询方法）：

| 方法 | 可见性 | 说明 |
| --- | --- | --- |
| `recordOperation(EntityOperation)` | `protected` | 记录一次操作，更新多值集合与因果指针 |
| `hasOperation / hasAllOperations / hasAnyOperation` | `public` | 判断已触发操作是否包含指定项 |

**工作单元清理**：

| 方法 | 说明 |
| --- | --- |
| `clearWorkUnitState()` | 清空领域事件、已触发操作与因果指针；应用层在事件分发完成后调用 |

#### 关键约束

> **重要约束**：事件成因缺失将抛异常。调用 `collectEvent(BaseDomainEvent)`（无显式操作参数）前，必须先 `recordOperation(...)`；否则若启用了操作体系（`operationRegistry() != null`），`resolveOperationCode()` 抛 `OperationException`。

> **重要约束**：`operationRegistry()` 返回 `null` 时，调用 `recordOperation` / `hasOperation` 抛 `OperationException`。即：未声明操作注册表即代表不启用操作体系。

> **重要约束**：`getNewVersion()` 幂等。首次调用生成 `oldVersion + 1` 并缓存；后续调用返回同一值。并发控制依赖持久化层 `UPDATE ... WHERE version = oldVersion`，影响行数为 0 即版本冲突。

> **重要约束**：`collectEvent` 系列为 `protected`，仅聚合根内部业务方法可调用；外部（应用层）经 `getDomainEvents()` 读取、`clearWorkUnitState()` 清理。

#### 示例代码

```java
public class Order extends AggregateRoot<Long> {

    private String status;

    public Order(Long id) {
        this.setEntityId(id);
        this.markCreated();
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderRuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return OrderOperationRegistry.INSTANCE;
    }

    public void cancel() {
        this.status = "CANCELLED";
        this.markModified();
        this.recordOperation(OrderOperationRegistry.CANCEL);   // 先于 collectEvent
        this.collectEvent(OrderCancelledEvent.buildEvent(this));
    }
}
```

### 2.5 消息码与注册表（Message Code & Registry）

#### `MessageCode`

```java
public record MessageCode(String localCode, String description) {
    public static MessageCode of(String localCode, String description);
    public static MessageCode of(String localCode);
    public String code();   // 返回 localCode，作为 map key 与异常 code
}
```

| 规则 | 说明 |
| --- | --- |
| 相等性 | **仅**按 `localCode` 判定（`description` 不参与） |
| `code()` | 返回 `localCode`，业务上以它作为 key |

#### `BrokenRuleRegistry`

构造时反射扫描子类声明的 `static MessageCode` 字段并自动注册。

| 方法 | 说明 |
| --- | --- |
| `register(MessageCode...)` | `protected final`，以 `code()` 为 key 注册 |
| `getRuleDescription(String)` | 按局部码返回描述；未注册返回空串 |
| `createException(String)` | 构造单条规则违反异常 |
| `createExceptionWithParam(String, Object...)` | 构造参数格式化异常（`String.format`） |
| `of(MessageCode...)` | `static` 内联工厂，免建子类 |

#### 关键约束

> **重要约束**：注册表子类**必须**为 `public`。构造函数通过反射 `field.get(null)` 读取 `static MessageCode` 字段；若子类为包级私有，`IllegalAccessException` 被静默吞掉，导致该消息码**未注册**——`getRuleDescription` 返回空串，且 `addBrokenRule` 收集到的描述为空白（仅影响描述文本，不影响 code）。

#### 示例代码

```java
public class OrderRuleRegistry extends BrokenRuleRegistry {
    public static final MessageCode EMPTY_ITEMS =
            MessageCode.of("ORDER_EMPTY_ITEMS", "订单不能为空");
    public static final MessageCode INVALID_QUANTITY =
            MessageCode.of("ORDER_INVALID_QUANTITY", "数量必须大于0，当前为 %s");

    private OrderRuleRegistry() {}
    public static final OrderRuleRegistry INSTANCE = new OrderRuleRegistry();
}
```

## 3. 关键机制与避坑指南

### 3.1 等同性判定边界

| 类型 | 相等判定依据 | 边界 |
| --- | --- | --- |
| `AbstractEntity` | 两方 `entityId` 均非空且 `equals` | 一方 ID 为空即 `false`（含新建未赋值对象与持久化对象之间） |
| `ValueObject` | `equalityComponents()` 内容 + **精确类相同** | 子类间即使成分相同也不等 |
| `MessageCode` | `localCode` | `description` 不影响相等与去重 |

### 3.2 事件成因与操作指针

- 每次业务变更：**先 `recordOperation`，后 `collectEvent`**。
- `collectEvent(BaseDomainEvent)` 的 `operationCode` 取自 `lastRecordedOperation`（单值因果指针）。
- 延迟事件 `collectEvent(Supplier)` 在**发布时**捕获成因并回填 `operationCode` / `version`，适用于 ID 构造期未知（自增/回填）的场景。
- 显式 `collectEvent(event, operation)` 可解耦事件成因与"最近操作"。

### 3.3 版本号与乐观锁

- `oldVersion` 默认值 `1`，由仓储 `findById` 回填为数据库持久化值。
- `getNewVersion()` 首次调用返回 `oldVersion + 1` 并缓存（幂等）。
- 持久化层应执行 `UPDATE ... SET version = newVersion WHERE version = oldVersion`；影响行数 0 即并发冲突，需上层重试或抛错。

### 3.4 规则违反收集时机

- 规则违反由 `AggregateRoot` 委托 `BrokenRuleObject` 收集，可多次 `addBrokenRule`。
- 抛异常时机由调用方决定：`throwBrokenRuleException()`（单条）/ `throwBrokenRuleAggregateException()`（全量）。
- `clearBrokenRules()` 可重置；与 `clearWorkUnitState()`（清事件/操作）互不影响。

### 3.5 一致性边界约束

> **边界外不变性不由聚合根保证。** 聚合根业务方法内不得直接调用另一个聚合根或修改其状态；跨聚合写操作通过领域事件发布 + 订阅者响应完成。违反此约束会破坏事务边界，导致不可预期的并发与一致性问题。

## 4. 异常与错误处理体系

### 4.1 继承关系

```text
RuntimeException
 └─ PragmaticException              所有框架业务异常的抽象基类（RuntimeException 子类）
     └─ RuleException               业务规则校验异常抽象基类
         ├─ BrokenRuleException           单条规则违反（code + message + source）
         └─ BrokenRuleAggregateException  聚合异常，持有 List<BrokenRuleException>
```

### 4.2 异常字段

| 异常类 | 关键字段 | 说明 |
| --- | --- | --- |
| `BrokenRuleException` | `code` (`String`)、`message`、`source` (`transient Object`) | `code` 即消息局部码；`source` 为触发源，不序列化 |
| `BrokenRuleAggregateException` | `exceptions` (`List<BrokenRuleException>`)、`getSource()` | `getSource()` 返回首个子异常的 source |

### 4.3 捕获与映射规范

- 统一兜底：`catch (PragmaticException e)` 可捕获所有框架异常。
- 规则校验：`catch (BrokenRuleException e)` 取 `e.getCode()` 映射为前端错误码；`catch (BrokenRuleAggregateException e)` 遍历 `e.getExceptions()` 返回全部违反。
- `source` 为 `transient`，跨进程/序列化场景勿依赖。

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 实体 | 继承 `AbstractEntity<T>` | 等同性仅由 `entityId` 决定；审计只能经 `markCreated`/`markModified` |
| 值对象 | 继承 `ValueObject` 或实现 `IValueObject` | `equals/hashCode` 为 `final`；要求精确类匹配；不可变性自行保证 |
| 枚举值对象 | 实现 `IEnumValue<T,K>` | 持久化写 `getValue()`，推荐 `CODE` 模式 |
| 聚合根 | 继承 `AggregateRoot<T>` | 实现两抽象方法；先 `recordOperation` 后 `collectEvent`；`getNewVersion()` 幂等 |
| 消息码 | `MessageCode.of(...)` + `BrokenRuleRegistry` | 注册表子类必须 `public`，否则码未注册 |
| 异常 | `PragmaticException` 体系 | `catch (PragmaticException)` 统一兜底；`source` 为 `transient` |

**下一步阅读**

- [业务规则引擎](./business-rules.md)：`IRule` / `EntityRule` 聚合级校验
- [领域事件](./domain-events.md)：`BaseDomainEvent` 与事件发布
- [仓储](./repository.md)：聚合持久化与版本对账
