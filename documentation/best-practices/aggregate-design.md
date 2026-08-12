# 聚合设计原则

> 本文档介绍使用 Pragmatic DDD 进行聚合设计的最佳实践与常见反模式，并结合聚合根实体编码规范给出可直接落地的代码示例。

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

## 2. 聚合根编码规范

聚合根继承 `AggregateRoot<T>`，通过业务构造函数初始化，业务方法内聚状态变更；规则注册表与操作注册表由领域层定义，领域事件由聚合根收集，应用层负责发布与清理。

- **聚合根**：DDD 聚合的唯一对外入口，充血模型，承载状态变更、Operation 记录、事件收集
- **注册表**：领域层定义 `{聚合}OperationRegistry` 与 `{聚合}BrokenRuleRegistry`，反射自动注册
- **领域事件**：表达已发生的领域事实，由聚合根 `collectEvent` 收集
- **应用层**：在合适时机触发规则校验，发布事件后调用 `clearWorkUnitState()` 清理

### 2.1 继承基类与两个抽象方法

聚合根继承 `io.pragmatic.ddd.base.AggregateRoot<T>`，必须实现两个 `protected abstract` 方法：

```java
/** 提供规则注册表，不能为 null。 */
protected abstract BrokenRuleRegistry brokenRuleRegistry();

/** 提供操作注册表；返回 null 表示不启用操作体系。 */
protected abstract OperationRegistry operationRegistry();
```

实现推荐以单例 `INSTANCE` 返回，避免每次调用重复 new：

```java
@Override
protected BrokenRuleRegistry brokenRuleRegistry() {
    return PersonBrokenRuleRegistry.INSTANCE;
}

@Override
protected OperationRegistry operationRegistry() {
    return PersonOperationRegistry.INSTANCE;
}
```

### 2.2 构造函数与无参构造

聚合根存在两类构造路径，职责不同，**切勿混用**：

- **业务构造函数（`public` 全参）**：唯一承载初始化逻辑，负责属性赋值、记录 `CREATE` 操作、收集初始事件、调用 `markCreated()`。此处不做规则校验，仅表达「新建这个聚合的事实」。
- **无参构造函数（`protected`）**：仅供持久化框架（MyBatis 反射、JSON 反序列化）重建对象，不触发任何业务逻辑。

```java
public Person(PersonInitData data) {
    this.name = data.getName();
    this.age = data.getAge();
    this.recordOperation(PersonOperationRegistry.CREATE);
    this.markCreated();
    this.collectEvent(() -> PersonCreatedEvent.buildEvent(this));
}

protected Person() {
    // 持久化重建专用，空实现
}
```

> 重建出的对象没有「新建」标记，依赖 `isNew()` 即可区分。

### 2.3 充血模型业务方法

业务变更逻辑内聚在聚合根内部，一个标准的业务方法只包含三步：修改属性、更新审计时间、记录 Operation 并收集事件。

```java
public void update(PersonUpdateData data) {
    this.name = data.getName();
    this.age = data.getAge();
    this.markModified();
    this.recordOperation(PersonOperationRegistry.UPDATE);
    this.collectEvent(PersonUpdatedEvent.buildEvent(this));
}
```

**关键认知**：规则校验不是业务方法的责任。业务方法只负责「执行变更」，校验由应用层或统一校验入口在合适时机触发。这样业务方法保持纯粹、可预测，也避免了校验逻辑散落各处。

### 2.4 协作组件：注册表 / 事件 / 值对象

**注册表（必须 `public`）**：基类在 `io.pragmatic.ddd.base` 包内通过反射扫描子类的 `static` 字段并 `field.get(null)`。若子类是包级私有，`IllegalAccessException` 会被静默吞掉，导致操作码/校验码未注册。推荐单例 `INSTANCE`，无需手动 `register`，`localCode` 字符串必须与常量字段名完全相同。

```java
public class PersonOperationRegistry extends OperationRegistry {
    public static final EntityOperation CREATE = EntityOperation.of("CREATE", "创建人员");
    public static final EntityOperation UPDATE = EntityOperation.of("UPDATE", "更新人员");
    private PersonOperationRegistry() {}
    public static final PersonOperationRegistry INSTANCE = new PersonOperationRegistry();
}

public class PersonBrokenRuleRegistry extends BrokenRuleRegistry {
    public static final MessageCode NAME_EMPTY = MessageCode.of("NAME_EMPTY", "姓名不能为空");
    private PersonBrokenRuleRegistry() {}
    public static final PersonBrokenRuleRegistry INSTANCE = new PersonBrokenRuleRegistry();
}
```

校验码命名：不带聚合/领域前缀、全大写下划线、跨聚合允许重名（各自独立注册）、不收敛为有限枚举词表；`description` 支持 `{}` 占位符。

**领域事件**：表达「已发生且不可变」的领域事实，必须有 `buildEvent(聚合类型)` 静态工厂（入参是当前聚合对象，不是零散原始值），`operationCode` 与 `version` 由框架回填。

```java
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public static class PersonCreatedEvent extends BaseDomainEvent {
    private String name;
    private int age;

    protected PersonCreatedEvent(String entityId) {
        super(entityId);
    }

    public static PersonCreatedEvent buildEvent(Person person) {
        PersonCreatedEvent event = new PersonCreatedEvent(String.valueOf(person.getEntityId()));
        event.setName(person.getName());
        event.setAge(person.getAge());
        return event;
    }
}
```

**值对象与枚举**：值对象（继承 `ValueObject` / 实现 `IValueObject`）是由属性组合判等的可嵌入数据结构（如地址），不可变；枚举（实现 `IEnumValue<T, 自身>`）是固定离散常量集合（如状态机）。经验法则：能用有限个常量表达的分类用枚举；需要由多个字段组合且按结构判等的数据用值对象。复杂值对象通过 MyBatis JSON TypeHandler 整体读写数据库 JSON 列——**必须实现 `IValueObject` 标记接口**才会被自动登记。构造入参超过 5 个时，用实现 `IParamObject` 的数据容器（如 `AddressInitData`）收敛入参。

### 2.5 Lombok 统一约定

聚合根与值对象的字段一律 `@Getter` + `@Setter(AccessLevel.PROTECTED)`（对外只读、对内/重建可写），重建构造用 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`。两者均**禁用 `@Data` / `@EqualsAndHashCode` / `@Builder`**（聚合根等同性由 `AbstractEntity` 托管、值对象判等由 `ValueObject` 基类托管）；聚合根全参业务构造必须手写（含副作用）。

```java
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 取代手写 protected Person(){}
public class Person extends AggregateRoot<Long> {
    private String name;
    private int age;

    // 全参业务构造手写，含副作用
    public Person(PersonInitData data) {
        this.name = data.getName();
        this.age = data.getAge();
        this.recordOperation(PersonOperationRegistry.CREATE);
        this.markCreated();
        this.collectEvent(() -> PersonCreatedEvent.buildEvent(this));
    }
}
```

---

## 3. 应用层协作

### 3.1 操作与事件的顺序

每次业务行为必须**先 `recordOperation`，后 `collectEvent`**。框架的事件会自动回填 `operationCode`（取最近一次操作）与 `version`。如果先收集事件再记录操作，事件因缺少成因而抛 `OperationException`。

```java
this.recordOperation(PersonOperationRegistry.UPDATE); // 先
this.collectEvent(PersonUpdatedEvent.buildEvent(this)); // 后
```

若希望事件成因与「最近操作」解耦，使用 `collectEvent(event, triggerOperation)` 显式指定。

### 3.2 延迟事件：ID 后生成必用

当聚合根 ID 由持久化后生成（自增主键、仓储回填雪花 ID），**构造期 `getEntityId()` 还是 `null`**。若用立即事件，事件会定格错误的 `entityId`，且无法补救。

框架提供延迟事件重载 `collectEvent(Supplier<IDomainEvent>)`：`Supplier` 在事件真正发布时才执行，届时读到真实 ID。

```java
// ID 构造期未知 → 强制延迟事件
this.collectEvent(() -> PersonCreatedEvent.buildEvent(this));
```

判定原则：**构造期拿不到确定 ID，一律用延迟事件**；ID 由业务传入（UUID / 雪花 ID）时，可用立即事件 `collectEvent(PersonCreatedEvent.buildEvent(this))`。

### 3.3 事件发布后的清理

应用层在事件分发完成后，必须调用 `clearWorkUnitState()` 清空已收集的事件、操作与因果指针，避免同一工作单元被重复处理或跨请求串味。

---

## 4. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 大聚合根 | 锁竞争、性能差 | 拆分小聚合，ID 引用 |
| 跨聚合引用对象 | 加载整个对象图 | 只持有 ID |
| 聚合根之间直接调用 | 耦合、事务边界模糊 | 通过领域事件解耦 |
| 领域逻辑泄漏到应用层 | 贫血模型 | 领域逻辑内聚到聚合根 |
| 业务方法内做规则校验 | 校验散落、方法不可测 | 校验交由应用层 `satisfiesRule` 触发 |
| 注册表写成包级私有 | 反射注册失败、码未注册 | 注册表子类必须为 `public` |
| 聚合根用 `@Data`/`@Builder` | 破坏等同性、构造副作用丢失 | 用 `@Getter`+`@Setter(PROTECTED)`，手写业务构造 |
| 仓储返回 DTO | 混淆读写模型 | 写走 `IRepository`，读走 `IAggregateProjection` |

---

## 5. 异常处理策略

```
PragmaticException             框架所有业务异常的抽象基类
 └── RuleException             业务规则校验异常基类
      └── BrokenRuleException          单条规则违反（code + message + source）
      └── BrokenRuleAggregateException 聚合规则违反（含全部违反）
```

推荐处理方式：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BrokenRuleException.class)
    public ResponseEntity<ErrorResponse> handleBrokenRule(BrokenRuleException e) {
        // 把 code 映射为前端友好的错误码
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(BrokenRuleAggregateException.class)
    public ResponseEntity<ErrorResponse> handleAggregate(BrokenRuleAggregateException e) {
        // 返回全部违反信息
        List<ErrorResponse.FieldError> errors = e.getBrokenRules().stream()
                .map(r -> new ErrorResponse.FieldError(r.getName(), r.getDescription()))
                .toList();
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("AGGREGATE_VIOLATION", "校验失败", errors));
    }

    @ExceptionHandler(PragmaticException.class)
    public ResponseEntity<ErrorResponse> handlePragmatic(PragmaticException e) {
        // 兜底捕获
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("INTERNAL", e.getMessage()));
    }
}
```

---

## 6. 项目分包建议

```
com.example.order/
├── domain/                 # 领域层
│   ├── model/              # 聚合根、实体、值对象
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── Address.java
│   ├── rule/              # 规则
│   │   ├── OrderRule.java
│   │   └── OrderRuleRegistry.java
│   ├── event/             # 领域事件
│   │   └── OrderCancelledEvent.java
│   ├── operation/         # 操作注册表
│   │   └── OrderOperationRegistry.java
│   └── service/           # 领域服务
│       └── TransferService.java
├── application/           # 应用层
│   ├── command/           # 命令服务
│   │   └── OrderCommandService.java
│   ├── query/             # 查询服务
│   │   └── OrderQueryService.java
│   ├── factory/           # 实体工厂
│   └── updater/           # 实体更新器
├── infrastructure/        # 基础设施层
│   ├── persistence/       # 仓储实现
│   │   ├── OrderRepositoryImpl.java
│   │   └── OrderMapper.java
│   ├── event/             # 事件管理器
│   │   └── EventManagerConfig.java
│   └── outbox/            # Outbox 实现
│       └── OutboxConfig.java
└── interfaces/            # 接口层
    ├── rest/              # REST Controller
    │   └── OrderController.java
    └── rpc/               # RPC 入口
```

---

## 7. 命名规范速查

| 层 | 类型 | 命名格式 | 示例 |
|----|------|---------|------|
| 领域层 | 聚合根 | `{业务对象}` extends `AggregateRoot<T>` | `Person` |
| 领域层 | 操作注册表 | `{聚合}OperationRegistry` | `PersonOperationRegistry` |
| 领域层 | 规则注册表 | `{聚合}BrokenRuleRegistry` | `PersonBrokenRuleRegistry` |
| 领域层 | 操作常量 | `EntityOperation.of("CODE", "描述")` | `CREATE` |
| 领域层 | 校验码常量 | `MessageCode.of("NAME_EMPTY", "描述")` | `NAME_EMPTY` |
| 领域层 | 领域事件 | `{聚合}{动作}Event` extends `BaseDomainEvent` | `PersonCreatedEvent` |
| 领域层 | 事件工厂 | `buildEvent({聚合})` | `PersonCreatedEvent.buildEvent(person)` |
| 领域层 | 值对象 | 继承 `ValueObject` / 实现 `IValueObject` | `Address` |
| 领域层 | 枚举 | 实现 `IEnumValue<T, 自身>` | `Status` |
| 应用层 | 入参容器 | `{聚合}{动作}Data` implements `IParamObject` | `PersonUpdateData` |

---

下一步：

- [事件建模指南](./event-modeling.md)
- [事务性发件箱](./transactional-outbox.md)
- [聚合根实现详解](../core/domain-modeling.md)
- [领域事件体系](../core/domain-events.md)
