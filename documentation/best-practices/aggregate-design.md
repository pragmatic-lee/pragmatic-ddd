# 聚合设计原则

> 本文档介绍使用 Pragmatic DDD 进行聚合设计的最佳实践与常见反模式：先明确聚合本身的边界与粒度原则，再说明框架父类已经托管的字段与能力，最后落到聚合根编码规范。

## 1. 聚合设计原则

### 1.1 小聚合原则

聚合根应尽量小，只包含**必须保证一致性**的子实体。一个聚合根包含的字段和子实体越多，锁竞争越激烈，并发性能越差。

```java
// ✅ 推荐：Order 聚合只持有 OrderItem 的引用
public class Order extends AggregateRoot<Long> {
    private TrackedList<OrderItem, Long> items;
    private String status;
    private long amount;
}

// ❌ 反模式：Order 聚合包含 Customer 和 Product 的完整数据
public class Order extends AggregateRoot<Long> {
    private Customer customer;    // 应改为 customerId
    private List<Product> products; // 应改为 productIds
}
```

### 1.2 ID 引用而非对象引用

跨聚合引用时，只持有对方的 ID，不持有对象引用：

```java
// ✅ 推荐
public class Order extends AggregateRoot<Long> {
    private Long customerId;  // 只持有 ID
    private TrackedList<OrderItem, Long> items;
}

// ❌ 反模式
public class Order extends AggregateRoot<Long> {
    private Customer customer;  // 持有完整对象引用
}
```

### 1.3 事务边界 = 单聚合根

一次事务只修改一个聚合根。需要跨聚合根操作时：

- 单聚合根命令：`CommandExecutor`
- 跨聚合根事务：`UnitOfWork`（谨慎使用，影响并发）
- 更推荐：通过领域事件解耦，最终一致

```java
// ✅ 推荐：通过事件解耦
order.cancel();  // 只改 Order 聚合
// OrderCancelledEvent → 触发库存释放（异步）

// ⚠️ 谨慎：跨聚合根事务
unitOfWork.register(order, orderRule, orderRepo, Order::cancel)
          .register(inventory, inventoryRule, inventoryRepo, Inventory::release)
          .commit();
```

---

## 2. 框架父类已提供的能力

聚合根继承 `AggregateRoot<T>` 后，身份标识、审计字段、软删标记、版本号、规则校验、领域事件与操作追踪全部由父类托管。**聚合根只需声明业务字段与业务方法，不要重复造基础设施的轮子。**

### 2.1 继承体系

```text
IEntity<T>                    实体标识契约（暴露 getEntityId）
  └─ AbstractEntity<T>       实体基类：ID / 软删 / 审计 / 等同性 / 时间戳
       └─ AggregateRoot<T>   聚合根基类：规则 / 版本 / 事件 / 操作 / 清理
```

### 2.2 已托管的字段

| 字段 | 继承自 | 说明 |
| --- | --- | --- |
| `entityId` | `AbstractEntity` | 身份标识，经 `setEntityId(T)` 赋值；持久化重建时由仓储回填 |
| `entityDelete` | `AbstractEntity` | 软删标记 |
| `createdAt` / `updatedAt` | `AbstractEntity` | 审计时间戳（`LocalDateTime`） |
| `createdBy` / `updatedBy` | `AbstractEntity` | 审计操作人 |
| `oldVersion` | `AggregateRoot` | 上一次持久化版本，默认 `1`，仓储 `findById` 回填 |
| `isNew` | `AggregateRoot` | 新建标记，仓储 `save()` 据此路由 insert / update |

**设计含义**：聚合根类里只出现业务字段。ID、审计、软删、版本都是父类字段，不要自定义同名属性；审计时间戳只能经 `markCreated()` / `markModified()` 写入，禁止直接操作。

### 2.3 已提供的能力

**等同性（由 `entityId` 托管）**

`equals` / `hashCode` / `toString` 由 `AbstractEntity` 基于身份标识实现：两方 `entityId` 均非空且相等即为同一实体。**不要覆盖 `equals` / `hashCode`**——聚合根等同性与业务字段无关，覆盖会破坏集合去重与对账逻辑。

**审计时间戳**

- `markCreated()`：置 `createdAt` 与 `updatedAt` 为当前时间，业务构造函数末尾调用一次。
- `markModified()`：刷新 `updatedAt`，业务方法修改状态后调用。

**规则校验（委托 `BrokenRuleObject`）**

聚合根组合持有规则违反收集器，无需自建校验设施：`addBrokenRule(MessageCode)` / `addParamBrokenRule(...)` 追加违反；`getBrokenRules()` 只读查询；`throwBrokenRuleException()`（单条）/ `throwBrokenRuleAggregateException()`（全量）抛出；`satisfiesRule(IRule<?>)` 执行规则。校验触发时机由应用层决定，业务方法内不自行校验。

**版本号与乐观锁**

`oldVersion` 由仓储回填；`getNewVersion()` 幂等返回递增后的版本号（首次调用 `oldVersion + 1` 并缓存）。持久化层以 `version = oldVersion` 作为乐观锁条件，影响行数为 0 即冲突。**不要自建 version 字段。**

**新建标记**

`markNew()` 置 `isNew = true`，仓储 `save()` 据此路由 insert / update；重建对象没有新建标记，靠 `isNew()` 区分。

**领域事件收集（`collectEvent` 系列为 `protected`，仅供聚合根内部）**

| 方法 | 说明 |
| --- | --- |
| `collectEvent(BaseDomainEvent)` | 收集立即事件，自动回填 `operationCode`（最近一次 `recordOperation`）与 `version` |
| `collectEvent(BaseDomainEvent, EntityOperation)` | 显式指定成因操作（优先级最高） |
| `collectEvent(Supplier<IDomainEvent>)` | 延迟事件，发布时才构造并回填，ID 后生成场景必用 |
| `getDomainEvents()` | `public`，应用层读取本工作单元已收集事件 |
| `triggerDataSyncHook()` | `public`，仓储落库前调用，子类覆写以发异构事件 |

> 事件成因约束：使用无显式操作参数的 `collectEvent(BaseDomainEvent)` 前，必须先 `recordOperation(...)`，否则启用了操作体系时抛 `OperationException`。操作与事件的顺序详见 [应用层落地模式](./application-collaboration.md)。

**操作追踪**

`recordOperation(EntityOperation)`（`protected`）记录操作；`hasOperation` / `hasAllOperations` / `hasAnyOperation`（`public`）供应用层判断已触发操作。

**工作单元清理**

`clearWorkUnitState()` 清空已收集事件、已触发操作与因果指针，由应用层在事件分发完成后调用。

### 2.4 父类要求聚合根提供的东西

`AggregateRoot` 声明两个 `protected abstract` 方法，聚合根必须实现：

```java
protected abstract BrokenRuleRegistry brokenRuleRegistry();   // 规则注册表，不能为 null
protected abstract OperationRegistry operationRegistry();     // 操作注册表；返回 null = 不启用操作体系
```

---

## 3. 聚合根编码规范

聚合根继承 `AggregateRoot<T>`，通过业务构造函数初始化，业务方法内聚状态变更；规则注册表与操作注册表由领域层定义，领域事件由聚合根收集，应用层负责发布与清理。

### 3.1 两个抽象方法的实现

实现推荐以单例 `INSTANCE` 返回，避免每次调用重复 new：

```java
@Override
protected BrokenRuleRegistry brokenRuleRegistry() {
    return OrderBrokenRuleRegistry.INSTANCE;
}

@Override
protected OperationRegistry operationRegistry() {
    return OrderOperationRegistry.INSTANCE;
}
```

> 注册表类的编写规范（`public` 类、单例 `INSTANCE`、`code` 命名约定）见 [规则注册表设计](./registry-design.md) 与 [操作注册表设计](./operation-registry-design.md)。

### 3.2 构造函数与无参构造

聚合根存在两类构造路径，职责不同，**切勿混用**：

- **业务构造函数（`public` 全参）**：唯一承载初始化逻辑，负责属性赋值、记录 `CREATE` 操作、收集初始事件、调用 `markCreated()`。此处不做规则校验，仅表达「新建这个聚合的事实」。
- **无参构造函数（`protected`）**：仅供持久化框架（MyBatis 反射、JSON 反序列化）重建对象，不触发任何业务逻辑。

```java
public Order(OrderInitData data) {
    this.name = data.getName();
    this.age = data.getAge();
    this.recordOperation(OrderOperationRegistry.CREATE);
    this.markCreated();
    this.collectEvent(() -> OrderCreatedEvent.buildEvent(this));
}

protected Order() {
    // 持久化重建专用，空实现
}
```

> 重建出的对象没有「新建」标记，依赖 `isNew()` 即可区分。

### 3.3 充血模型业务方法

业务方法内聚聚合根的状态变更，本质是**纯粹的赋值**：把「准备好的」入参赋给自身字段，再收尾（更新审计时间、记录 Operation、收集事件）。它**不需要任何守卫**，也不携带任何其他职责。

```java
public void update(OrderUpdateData data) {
    this.name = data.getName();
    this.age = data.getAge();
    this.markModified();
    this.recordOperation(OrderOperationRegistry.UPDATE);
    this.collectEvent(OrderUpdatedEvent.buildEvent(this));
}
```

业务方法保持纯粹、可预测，**它就是赋值**。围绕它有两条设计原则：

**① 不做规则校验**

规则校验不是业务方法的责任，业务方法内**不写任何 `if + throw` 守卫**。校验（含前置状态不变性）由应用层或统一校验入口在合适时机触发（`satisfiesRule` / `EntityRule`），校验逻辑集中可审计，业务方法不被守卫塞满、保持可测。

**② 不做数据的组装与转换**

业务方法不负责把入参组装成领域对象，也不做数据格式转换（字符串转日期、code 转枚举、DTO 转领域结构等）。凡是业务方法要用的值对象、已转换的字段，由**调用方 / 工厂**组装好再传入；业务方法只做字段赋值与状态变更。数据组装与转换集中在应用层（`EntityFactory` / `EntityUpdater`）或调用方完成。

```java
// ❌ 反模式：业务方法内加守卫、做数据组装与转换
public void update(OrderUpdateData data) {
    if (data.getAge() < 0) {
        throw new IllegalArgumentException("年龄不能为负");                     // 守卫
    }
    this.address = new Address(data.getProvince(), data.getCity(), data.getDetail()); // 组装值对象
    this.birthday = LocalDate.parse(data.getBirthday(), DateTimeFormatter.ISO_DATE);  // 字符串转日期
    this.status = Status.of(data.getStatusCode());                                    // code 转枚举
    // ... 业务方法被守卫与组装转换逻辑塞满
}

// ✅ 推荐：入参已组装转换好，业务方法无守卫、纯赋值（见上例）
```

配合 §3.4 参数对象（`IParamObject` 收敛入参）与 `EntityFactory` / `EntityUpdater`（命令 → 领域结构的组装与转换），业务方法只收「准备好的」入参、只改自己的状态。

### 3.4 参数对象：用 `IParamObject` 收敛入参

构造函数或业务方法的入参过多（一般超过 5 个，或参数明显成组出现）时，不要逐个列参，而是封装成一个参数对象整体传入，并让该对象**实现 `IParamObject` 标记接口**。这样既避免了超长参数列表难以阅读、容易传错顺序，也让入参结构可复用、可演进——新增字段只改参数对象，不改方法签名。

`IParamObject` 只是**数据容器**标记：类上加 Lombok 注解（如 `@Data`）即可，**不需要手写构造函数**来初始化，字段经 setter 或工厂赋值。

```java
@Data // 纯数据容器：加注解即可，无需手写构造函数
public class OrderInitData implements IParamObject {
    private String name;
    private int age;
    private String email;
}

@Data
public class OrderUpdateData implements IParamObject {
    private String name;
    private int age;
}

// 构造函数与业务方法都只收一个参数对象
public Order(OrderInitData data) { ... }

public void update(OrderUpdateData data) { ... }
```

**判定原则**：入参 ≤ 3 个且短小稳定时可直接列参；一旦超过 5 个，或参数经常成组出现、未来可能继续增加，就应封装为 `IParamObject` 数据容器。

**与领域对象不同**：参数对象是纯数据容器——只声明字段、加 `@Data` 即可，不放任何业务逻辑、不继承领域基类。这里的 `@Data` 是被鼓励的；而聚合根禁用 `@Data`（见 §3.5）、值对象禁用 `@Data`（见 [值对象最佳实践](./value-object.md)），注意区分。

> 与[值对象](./value-object.md)（`IValueObject`）的区别：参数对象是**入参**载体，不参与持久化，等同性由 Lombok `@Data` 生成、纯属容器便利，不承载领域判等语义；值对象是**领域内**的可嵌入数据结构，判等是其领域行为。两者不要混用。

### 3.5 Lombok 统一约定

聚合根字段一律 `@Getter` + `@Setter(AccessLevel.PROTECTED)`（对外只读、对内/重建可写），重建构造用 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`。**禁用 `@Data` / `@EqualsAndHashCode` / `@Builder`**（聚合根等同性由 `AbstractEntity` 托管）；聚合根全参业务构造必须手写（含副作用）。值对象的 Lombok 约定见 [值对象最佳实践](./value-object.md)。

```java
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 取代手写 protected Order(){}
public class Order extends AggregateRoot<Long> {
    private String name;
    private int age;

    // 全参业务构造手写，含副作用
    public Order(OrderInitData data) {
        this.name = data.getName();
        this.age = data.getAge();
        this.recordOperation(OrderOperationRegistry.CREATE);
        this.markCreated();
        this.collectEvent(() -> OrderCreatedEvent.buildEvent(this));
    }
}
```

---

## 4. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 大聚合根 | 锁竞争、性能差 | 拆分小聚合，ID 引用 |
| 跨聚合引用对象 | 加载整个对象图 | 只持有 ID |
| 聚合根之间直接调用 | 耦合、事务边界模糊 | 通过领域事件解耦 |
| 领域逻辑泄漏到应用层 | 贫血模型 | 领域逻辑内聚到聚合根 |
| 业务方法内做规则校验 | 校验散落、方法不可测 | 校验交由应用层 `satisfiesRule` 触发 |
| 业务方法内做数据组装与转换 | 方法职责混杂、不可测 | 组装/转换集中在应用层或调用方，业务方法只做状态变更 |
| 聚合根用 `@Data`/`@Builder` | 破坏等同性、构造副作用丢失 | 用 `@Getter`+`@Setter(PROTECTED)`，手写业务构造 |

---

## 下一步

- [普通实体设计](./entity-design.md)：聚合内子实体的设计
- [应用层落地模式](./application-collaboration.md)：WriteService 编排、`execute` 模板与异常响应
- [规则注册表设计](./registry-design.md)：校验消息码（`BrokenRuleRegistry`）的编写规范
- [操作注册表设计](./operation-registry-design.md)：领域操作（`OperationRegistry`）的编写规范
- [值对象最佳实践](./value-object.md)：值对象的取舍与编写规范
- [事件建模指南](./event-modeling.md)
- [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)
- [Outbox 链路装配](./outbox-config.md)
- [聚合根实现详解](../core/domain-modeling.md)
- [领域事件体系](../core/domain-events.md)
