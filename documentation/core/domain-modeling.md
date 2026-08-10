# 领域建模：实体、值对象与聚合根

> 本文档属于 pragmatic-ddd 使用文档 `core` 系列，介绍领域建模层（`io.pragmatic.ddd.base`）的核心概念与用法。
> 阅读前建议先完成 [快速开始](../getting-started/quick-start.md)。本系列后续文档：[业务规则引擎](./business-rules.md) · [领域事件](./domain-events.md)。

## 1. 概述

### 1.1 这一层解决什么问题

`io.pragmatic.ddd.base` 是整个框架的基础，也是使用者最频繁接触的包。它把 DDD 的四个核心战术概念——**实体（Entity）、值对象（Value Object）、聚合根（Aggregate Root）、领域服务（Domain Service）**——做成了开箱即用的 Java 基类与接口：

- 通过继承，你无需再手写 `equals/hashCode`、ID 字段、审计字段、软删标记等样板代码。
- 通过组合，聚合根自动获得规则校验、乐观锁版本号、领域事件收集、操作追踪等聚合级能力（这些能力在本系列后续文档展开）。

### 1.2 概念层级关系

```
IEntity<T>                    实体标识契约（getEntityId）
   └── AbstractEntity<T>      实体基类：ID、软删、审计、基于 ID 的 equals/hashCode
         └── AggregateRoot<T> 聚合根：+ 规则校验 + 版本号 + 事件收集 + 操作追踪 + 新建标记

IValueObject                  值对象标记
   └── ValueObject            可选基类：按 equalityComponents() 结构相等
   └── IEnumValue<T,K>        枚举值对象（替代 Java enum）

IDomainService                领域服务标记

MessageCode / BrokenRule      规则违反消息码与明细（与聚合根协作）
```

## 2. 实体（Entity）

### 2.1 实体标识契约 `IEntity<T>`

`IEntity<T>` 是实体标识接口，约束实体暴露其标识：

```java
public interface IEntity<T> {
    T getEntityId();
}
```

所有实体必须能回答"我是谁"。绝大多数情况下你无需直接实现它，继承基类即可。

### 2.2 实体基类 `AbstractEntity<T>`

`AbstractEntity<T>` 是一个纯数据容器，承载：

| 能力 | 说明 |
| --- | --- |
| `entityId` | 身份标识，`getEntityId()` 获取，`@Setter(PROTECTED)` 只能子类内部赋值 |
| `entityDelete` | 软删标记，`isEntityDelete()` / `setEntityDelete()` |
| 审计字段 | `createdAt / updatedAt / createdBy / updatedBy` |
| `markCreated()` | 构造末尾调用，一次性填充 `createdAt` 与 `updatedAt` |
| `markModified()` | 每次修改后调用，刷新 `updatedAt` |
| `equals/hashCode` | 基于身份标识：两实体 ID 均非空且相等才视为同一实体 |

关键设计点：

- **等同性基于 ID**：只要 ID 相同就是同一实体，即使其他字段内容不同。这是实体与值对象的本质区别。
- **审计字段为受保护 setter**：时间戳只能通过 `markCreated()` / `markModified()` 更新，避免外部随意篡改审计信息。
- 注意：`AbstractEntity` 不持有规则校验、版本号、事件收集等**聚合级能力**，这些由 `AggregateRoot` 提供。

### 2.3 定义你自己的实体

```java
public class Address extends AbstractEntity<Long> {

    public Address(Long id, String province, String city) {
        this.setEntityId(id);
        this.province = province;
        this.city = city;
        this.markCreated();
    }

    private String province;
    private String city;

    public void changeCity(String city) {
        this.city = city;
        this.markModified();
    }
}
```

## 3. 值对象（Value Object）

### 3.1 值对象标记接口 `IValueObject`

`IValueObject` 是纯标记接口，供可视化模块反射识别，不承载行为契约。

### 3.2 结构相等基类 `ValueObject`

`ValueObject` 是**可选**的"胖"值对象基类。它基于 `equalityComponents()` 提供结构相等性，并统一生成 `equals` / `hashCode` / `toString`：

```java
public abstract class ValueObject implements IValueObject {

    protected abstract Object[] equalityComponents();

    @Override
    public final boolean equals(Object o) { /* 基于 equalityComponents() */ }

    @Override
    public final int hashCode() { /* 基于 equalityComponents() */ }
}
```

要点：

- **只声明"哪些成分决定相等"**：`equalityComponents()` 不一定是全部字段，只返回语义上真正决定相等性的成分即可（顺序敏感）。
- **不可变性由你保证**：框架不强制不可变，建议通过全参构造器 + 构造期校验实现。
- **`equals/hashCode` 为 final**：不允许子类覆盖，保证结构相等语义一致。

### 3.3 定义你自己的值对象

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

两个 `Money(100, "CNY")` 实例 `equals` 为 `true`——这正是值对象"内容相等"的语义。

> 提示：与 MyBatis JSON 通道配合时，`IValueObject` 会被 `GenericJsonTypeHandler` 自动识别，整体序列化到 JSON 列。详见 [MyBatis 集成](../integration/mybatis.md)。

### 3.4 枚举值对象 `IEnumValue<T,K>`

`IEnumValue<T,K>` 用于**替代 Java enum**，规避 MyBatis 对 enum 持久化的痛点：

```java
public interface IEnumValue<T, K extends Enum<?>> {
    T getValue();      // 持久化 / 传输用的业务 code
    String getName();  // 展示名（label）
    default String getDesc() { return getName(); }
}
```

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

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getName() {
        return name;
    }
}
```

`T` 是业务 code 类型（持久化到 DB 的值），`K` 是枚举类型本身。MyBatis 通过 `EnumRule(NAME/ORDINAL/CODE/LABEL)` 控制持久化形态，推荐使用 `CODE`（`getValue()`）。

## 4. 聚合根（Aggregate Root）

### 4.1 聚合根基类 `AggregateRoot<T>`

`AggregateRoot<T>` 继承 `AbstractEntity<T>` 并**组合** `BrokenRuleObject`，作为 DDD 聚合的唯一外部入口点。**所有需要持久化的实体都应继承它。**

它在实体基础上额外提供：

| 能力 | 方法 | 说明 |
| --- | --- | --- |
| 规则校验 | `satisfiesRule(IRule)` / `getBrokenRules()` / `throwBrokenRuleException()` | 聚合级不变性约束 |
| 规则违反追加 | `addBrokenRule(MessageCode)` / `addParamBrokenRule(...)` | 手动收集规则违反 |
| 版本控制 | `getOldVersion()` / `getNewVersion()` | CAS 乐观锁 |
| 新建标记 | `markNew()` / `isNew()` | 仓储据此路由 insert/update |
| 领域事件 | `collectEvent(...)` / `getDomainEvents()` | 收集与取回事件（详见领域事件文档） |
| 操作追踪 | `recordOperation(...)` / `hasOperation(...)` | 记录与判断操作（详见操作追踪文档） |
| 工作单元清理 | `clearWorkUnitState()` | 清空事件/操作临时状态 |
| 数据同步钩子 | `triggerDataSyncHook()` | 落库前发异构事件 |

聚合根是**抽象类**，使用者必须实现两个抽象方法：

```java
protected abstract BrokenRuleRegistry brokenRuleRegistry();
protected abstract OperationRegistry operationRegistry();
```

- `brokenRuleRegistry()`：返回规则消息注册表（见第 5 节）。
- `operationRegistry()`：返回操作注册表；**返回 `null` 表示不启用操作体系**。

### 4.2 定义你自己的聚合根

```java
public class Order extends AggregateRoot<Long> {

    private final List<OrderItem> items = new ArrayList<>();
    private String status;

    public Order(Long id, String customerName) {
        this.setEntityId(id);
        this.customerName = customerName;
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

    // 业务方法：修改状态并记录操作（操作体系需在操作追踪文档中定义）
    public void cancel() {
        this.status = "CANCELLED";
        this.markModified();
    }
}
```

### 4.3 规则违反收集

聚合根可以手动收集规则违反，或通过规则引擎自动校验（详见 [业务规则引擎](./business-rules.md)）：

```java
// 手动收集：校验不通过时收集，最终由应用层统一决定是否抛出
if (items.isEmpty()) {
    this.addBrokenRule(OrderRuleRegistry.EMPTY_ITEMS);
}

// 支持参数格式化
this.addParamBrokenRule(OrderRuleRegistry.INVALID_QUANTITY,
        new Object[]{quantity}, true);
```

- `getBrokenRules()`：返回已收集的违反列表（只读）。
- `throwBrokenRuleException()`：存在违反则抛**单条**异常（取第一条）。
- `throwBrokenRuleAggregateException()`：存在违反则抛**聚合异常**（含全部违反）。
- `clearBrokenRules()`：清空已收集的违反。

### 4.4 版本号与 CAS 乐观锁

聚合根内置乐观锁版本控制，防止并发更新丢数据：

```java
@Getter
private long oldVersion = 1;   // 上一次持久化的版本
private long newVersion = 0;   // 本次工作单元递增后的版本
```

- `getOldVersion()`：仓储加载后由 `findById` 回填（`oldVersion`）。
- `getNewVersion()`：**幂等**地返回递增后的新版本号。首次调用时 `oldVersion + 1`，之后多次调用返回同一值。
- 持久化语义：`UPDATE ... WHERE version = oldVersion`，影响行数为 0 时说明并发冲突。
- 常用做法：聚合根每次修改时调用 `getNewVersion()`，把新版本号随 UPDATE 一起提交。

```java
public void modify() {
    // 业务修改
    this.getNewVersion();  // 触发版本递增，落库时携带
}
```

### 4.5 新建标记

`markNew()` 把聚合根标记为"新建"，`isNew()` 判断。仓储的 `save()` 据此自动路由到 `insert` 还是 `update`：

```java
public void save(Order order) {
    if (order.isNew()) {
        insert(order);
    } else {
        update(order);
    }
}
```

## 5. 消息码与注册表

### 5.1 `MessageCode`

`MessageCode` 是 Java 17 record，表示一条规则违反消息码，由**局部码 + 描述**组成，作为消息表 key 与异常 code：

```java
public record MessageCode(String localCode, String description) {
    public static MessageCode of(String localCode, String description);
    public static MessageCode of(String localCode);
    public String code();           // 返回局部码
}
```

关键语义：

- **相等性仅按 `localCode` 判定**（`equals/hashCode` 重写），便于作为 Map key 与去重。
- 全局唯一标识由 `localCode + description` 共同构成；但业务上以 `code()`（局部码）作为实际 key。

### 5.2 `BrokenRuleRegistry` 注册表

`BrokenRuleRegistry` 是规则消息注册表基类。它的特殊机制是：**构造时反射扫描子类声明的 `static MessageCode` 字段并自动注册**，子类只需声明常量即可。

```java
public class OrderRuleRegistry extends BrokenRuleRegistry {

    public static final MessageCode EMPTY_ITEMS = MessageCode.of("ORDER_EMPTY_ITEMS", "订单不能为空");
    public static final MessageCode INVALID_QUANTITY = MessageCode.of("ORDER_INVALID_QUANTITY", "数量必须大于0，当前为 %s");

    private OrderRuleRegistry() {
    }
}
```

> ⚠️ **重要约束**：由于构造时 `io.pragmatic.ddd.base` 包内通过反射扫描子类的 `static MessageCode` 字段并调用 `field.get(null)`，**注册表子类必须是 `public`**。若注册表子类是包级私有（默认可见性），base 包无法访问其 public 字段，`field.get(null)` 会抛 `IllegalAccessException` 并被静默吞掉，导致所有消息码未注册、`getRuleDescription` 返回空串（只影响 description，不影响 code）。

`BrokenRuleRegistry` 还提供便利方法与内联工厂：

```java
// 内联构建，无需自定义子类
BrokenRuleRegistry registry = BrokenRuleRegistry.of(
        MessageCode.of("A", "msg A"),
        MessageCode.of("B", "msg B"));

String desc = registry.getRuleDescription("A");       // "msg A"，未注册返回空串
registry.createException("A");                        // 构造单条规则违反异常
registry.createExceptionWithParam("B", "arg");        // 构造参数格式化异常
```

## 6. 领域服务标记

`IDomainService` 是领域服务标记接口，用于标识承载领域逻辑、但不属于单个聚合的服务类：

```java
public class TransferService implements IDomainService {
    // 跨聚合的领域逻辑
}
```

它主要用于 AI 编码辅助对领域结构的识别，本身不承载契约方法。领域服务承担"不属于任何单个聚合"的领域逻辑，例如转账涉及两个账户的协调。

## 7. 异常体系

base 层定义了统一的异常继承体系，使用者可按需捕获：

```
RuntimeException
 └── PragmaticException         框架所有业务异常的抽象基类
      └── RuleException         业务规则校验异常基类
           └── BrokenRuleException          单条规则违反异常（code + message + source）
           └── BrokenRuleAggregateException 聚合规则违反异常（含全部违反）
```

- 通过 `catch (PragmaticException e)` 可统一兜底捕获所有框架异常。
- `BrokenRuleException` 携带 `code`（局部码）、`message`（描述）与 `source`（触发源，transient）。
- 最佳实践：在应用层边界捕获 `BrokenRuleException`，把 `code` 映射为对前端友好的错误码。详见 [异常处理策略](../best-practices/aggregate-design.md)。

## 8. 领域建模小结

到这里，你已经掌握了使用 pragmatic-ddd 建模领域模型的全部基础概念：

| 概念 | 使用方式 | 关键点 |
| --- | --- | --- |
| 实体 | 继承 `AbstractEntity<T>` | 基于 ID 的等同性、审计字段、软删 |
| 值对象 | 继承 `ValueObject` 或实现 `IValueObject` | 结构相等、不可变 |
| 枚举值对象 | 实现 `IEnumValue<T,K>` | 替代 Java enum，规避 MyBatis 痛点 |
| 聚合根 | 继承 `AggregateRoot<T>` | 规则校验 + 版本 + 事件 + 操作追踪 |
| 领域服务 | 实现 `IDomainService` | 跨聚合领域逻辑标记 |
| 消息码 | `MessageCode` + `BrokenRuleRegistry` | 注册表子类必须 `public` |

下一步建议阅读：

- [业务规则引擎](./business-rules.md)：聚合根上的规则校验能力
- [领域事件](./domain-events.md)：`collectEvent` 与事件发布
- [仓储](./repository.md)：聚合根的持久化与版本对账
