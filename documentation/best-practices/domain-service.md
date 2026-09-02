# 领域服务落地模式

> 本文档介绍 Pragmatic DDD 中领域服务（`IDomainService`）的落地方式：四类分类怎么判、领域层契约怎么写、应用层实现怎么落，以及每类与现有落地文档的关系。前置阅读：[聚合设计原则](./aggregate-design.md)。

## 1. 本质与定位

领域服务是**领域层声明、应用层实现的契约接口**（端口）：领域层通过 `extends IDomainService` 声明"需要什么能力"，应用层 `implements` 提供具体实现。它承载无法归入单一聚合根的逻辑——事件订阅、跨聚合校验、属性计算与领域原语供给。

与经典 DDD 的认知差异：

| 维度 | 经典 DDD 领域服务 | 本框架领域服务 |
| --- | --- | --- |
| 定义形态 | 直接编写实现类，承载业务逻辑 | 领域层定义接口，应用层实现 |
| 分类 | 单一笼统概念 | 四类明确分类（事件订阅 / 校验规则 / 属性计算 / 能力供给） |
| 跨聚合协作 | 鼓励在一个服务中同步编排多个聚合 | 通过领域事件驱动，多个订阅者分别响应 |
| 依赖方向 | 实现可直接依赖基础设施 | 领域层接口零基础设施依赖 |

> **不要套用"银行转账式"跨聚合编排范式**：跨聚合协作走领域事件，不在单个领域服务内同步编排多个聚合。

`@DomainService` 注解与依赖体系的 `@ExternalDependency` 对称：一个声明"我需要什么能力"，一个声明"我依赖什么外部聚合"。

## 2. 命名与包结构

### 2.1 包结构

```text
domain/order/
├── model/         聚合根、实体、值对象
├── event/         领域事件定义
├── calculator/    属性计算契约（如 IOrderTotalAmountCalculator）
├── service/       事件订阅 / 校验规则 / 能力供给契约
└── rule/          规则容器 + 规则注册表

application/order/
├── service/       领域服务实现（@Service / @Component）
├── resolver/      Command DTO → 领域输入的适配解析器
└── factory/       聚合工厂（先算后赋）
```

### 2.2 命名规范

| 分类 | 领域层接口（`I` 开头） | 应用层实现（去 `I`） | 示例 |
| --- | --- | --- | --- |
| 事件订阅 | `I{事件}{业务意图}Handle/Sender` | `{事件}{业务意图}Handler` | `IOrderDataSyncEsProjectionHandle` |
| 校验规则 | `I{业务对象}{规则意图}Service` | 接口名去 `I` | `IOrderCustomerPermissionService` |
| 属性计算 | `I{结果}Calculator` | 接口名去 `I` | `IOrderTotalAmountCalculator` |
| 能力供给 | `I{产物}Generator/Provider` | 接口名去 `I` | `IOrderIdGenerator` |

> 接口名必须体现业务意图，不得用泛化占位词 `Handler` / `Processor` 作为接口名；接口名本身即领域文档。

## 3. 数据 / 职责承载

| 承载 | 不承载 |
| --- | --- |
| 领域层契约（接口方法签名，声明"做什么"） | 实现逻辑（应用层提供） |
| `@DomainService` 元信息（category / description / targetName） | 基础设施依赖（领域层接口零依赖） |
| 业务意图描述（targetName 按分类语义解释） | 跨聚合同步编排（走领域事件） |

## 4. 四类判定

任一 `extends IDomainService` 的契约，按"方法形态 + 业务语义"唯一归类（自上而下，命中即止）：

| 判定序 | 检查项 | 归类 | 枚举值 |
| --- | --- | --- | --- |
| 1 | 是否 `extends IHandle<T>`（响应领域事件） | 事件订阅 | `EVENT_SUBSCRIBER` |
| 2 | 方法是否返回 `RuleCheckResult`（校验给出通过/拒绝） | 业务规则 | `BUSINESS_RULE` |
| 3 | 方法是否"由领域输入推导领域输出"（`calculate(...)`） | 属性计算 | `ATTRIBUTE_CALCULATOR` |
| 4 | 方法是否"无/少输入却新生产领域原语/对象"（`generate()` / `nextId()`） | 能力供给 | `CAPABILITY_PROVIDER` |

> ⚠️ **重要约束**：机器可读的分类**只来自 `@DomainService` 注解**，方法形态只是编写时的语义判断辅助。基类接口（`ICheckRuleService` 等）都是空标记，不承载任何方法。契约必须标注 `@DomainService(category = ...)`；未标注则 `category()` 返回 `UNKNOWN`，丢失分类元信息（不影响方法调用，但依赖分类的扫描/校验逻辑无法识别）。

## 5. 落地方式（核心）

### 5.1 通用三步

1. **领域层**定义接口 + 标注 `@DomainService(category, description, targetName)`。
2. **应用层** `@Service` / `@Component` 实现，可依赖基础设施。
3. 按分类接入运行机制（事件订阅注册 / 校验规则注入规则容器 / 属性计算进工厂 / 能力供给进构造）。

### 5.2 业务规则（BUSINESS_RULE）——见 [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)

外部校验契约（如 `IOrderCustomerPermissionService`）属于本类：接口方法返回 `RuleCheckResult`，标注 `@DomainService(category = BUSINESS_RULE)`，实现放应用/基础设施层，经构造器注入规则容器 `OrderRule`。完整的「契约注入 → 规则容器 → 触发」落地见 [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)。

### 5.3 事件订阅（EVENT_SUBSCRIBER）——见 [事件订阅领域服务落地模式](./event-subscriber-pattern.md)

`IOrderDataSyncEsProjectionHandle extends IDomainService, IHandle<OrderDataSyncEvent>` 即本类：继承 `IHandle<T>` 声明关注事件与 `handleEvent`，标注 `@DomainService(category = EVENT_SUBSCRIBER)`。

契约声明、三类订阅者实现形态（外部系统联动 / 读模型副本物化）、`OrderEventSubscriberRegistry` 显式注册绑定与全部避坑点见 [事件订阅领域服务落地模式](./event-subscriber-pattern.md)。读模型副本的投影 / 物化 / 对账构件落地见 [投影读模型代码落地指南](./projection-design.md)。

### 5.4 属性计算（ATTRIBUTE_CALCULATOR）——完整落地

**① 领域层契约接口**（`calculator` 子包）：

```java
@DomainService(
        category = DomainServiceCategory.ATTRIBUTE_CALCULATOR,
        targetName = "Order/OrderItem",
        description = "汇总各订单项得到订单总额"
)
public interface IOrderTotalAmountCalculator
        extends IEntityPropertyCalculator<List<OrderItem>, Order, Money> {
}
```

`IEntityPropertyCalculator<T, E, R>` 是纯函数式契约：`R calculate(T source, E entity)`，`entity` 创建场景为 `null`，实现需兼容。

**② 应用层实现**（`@Service`，可注入外部依赖）：

```java
@Service
public class OrderTotalAmountCalculator implements IOrderTotalAmountCalculator {

    private final IUserDependency userDependency;   // 外部聚合依赖声明

    public OrderTotalAmountCalculator(IUserDependency userDependency) {
        this.userDependency = userDependency;
    }

    @Override
    public Money calculate(List<OrderItem> items, Order entity) {
        int level = userDependency.getUserLevel(String.valueOf(entity.getCustomer().getCustomerId()));
        BigDecimal rate = discountRateOf(level);
        Money sum = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(new Money(BigDecimal.ZERO, "CNY"), Money::add);
        return sum.multiply(rate);
    }
}
```

**③ 场景适配 resolver**（把 Command DTO 适配到领域输入，经 `EntityPropertyResolvers.of` 一处定义多处复用）：

```java
@Component
public class OrderTotalAmountResolver implements IEntityPropertyResolver<CreateOrderInput, Order, Money> {

    private final IOrderTotalAmountCalculator calculator;

    public OrderTotalAmountResolver(IOrderTotalAmountCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public Money resolve(CreateOrderInput command, Order entity) {
        IEntityPropertyResolver<CreateOrderInput, Order, Money> delegate =
                EntityPropertyResolvers.of(calculator, this::toOrderItems);   // calculator + 取数函数
        return delegate.resolve(command, entity);
    }
}
```

**④ 工厂"先算后赋"**（构造聚合前算出派生属性再装配）：

```java
@Component
public class OrderFactory implements EntityFactory<Order, CreateOrderInput> {

    private final IOrderIdGenerator idGenerator;
    private final OrderTotalAmountResolver totalAmountResolver;

    @Override
    public Order create(CreateOrderInput input) {
        Long orderId = idGenerator.nextId();
        List<OrderItem> items = totalAmountResolver.toOrderItems(input, orderId);
        Customer customer = new Customer(input.getCustomerId(), input.getCustomerName());
        Order probeOrder = new Order(probeData(customer), orderId);      // 临时探测 Order
        Money total = totalAmountResolver.resolve(input, probeOrder);    // 先算
        // ... 组装 OrderInitData 并 setTotalAmount(total)，再 new Order(data, orderId) // 后赋
    }
}
```

> 派生属性（订单总额 = Σ 单价 × 数量 × 折扣）计算职责**外移到领域服务**，聚合根不内嵌计算逻辑；同一属性一处定义、N 个场景复用（创建 / 修改 / 展示）。

### 5.5 能力供给（CAPABILITY_PROVIDER）——完整落地

**① 领域层契约接口**（`service` 子包）：

```java
@DomainService(
        category = DomainServiceCategory.CAPABILITY_PROVIDER,
        targetName = "OrderId",
        description = "生成订单唯一标识"
)
public interface IOrderIdGenerator extends IDomainService {

    Long nextId();
}
```

**② 应用层实现**（包装框架能力，具体算法由应用层决定）：

```java
@Service
public class OrderIdGenerator implements IOrderIdGenerator {

    private static final String ORDER_BIZ_KEY = "order";

    private final IdGeneratorRegistry idGeneratorRegistry;

    public OrderIdGenerator(IdGeneratorRegistry idGeneratorRegistry) {
        this.idGeneratorRegistry = idGeneratorRegistry;
    }

    @Override
    public Long nextId() {
        return idGeneratorRegistry.nextId(ORDER_BIZ_KEY);
    }
}
```

> 声明"我需要能产生 X 的能力"，具体算法（雪花 / 数据库序列 / UUID）由应用层实现决定；通常无法用纯领域逻辑替代。

## 6. 关键机制与避坑

- **`category()` 的注解查找链**：`IDomainService.category()` 沿 实现类 → 所实现接口 → 父类 递归查找 `@DomainService`。注解标在实现类上，子类接口同样能读到；未标注返回 `UNKNOWN`。
- **契约参数必须是领域类型**：方法入参只能是领域对象 / 值对象 / 领域上下文，**不得**用应用层 `Command` / `HttpRequest` / DTO——否则破坏领域层零基础设施依赖，契约无法在领域层独立单测。若输入来自应用层入参，用 resolver 先转领域类型再进契约。
- **事件订阅的注册路径**：框架**不扫描** `@Component` 的 `IHandle` 实现，Spring 与非 Spring 环境都必须在 `{聚合}EventSubscriberRegistry` 里显式 `IEventRegistry.registerSubscriber`，否则事件发布后不触发且无任何提示。
- **分类必须与接口语义一致**：标注的 `category` 须与 §4 判定结果一致；一个契约只属一类，若同时命中多项说明接口抽象错误，应拆分。

## 7. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 接口名用 `Handler` / `Processor` 占位词 | 丢失业务意图，接口名不成领域文档 | 用 `I{事件}{业务意图}` / `I{结果}Calculator` 等具名接口 |
| 契约方法入参用 Command / DTO | 破坏领域层零基础设施依赖，无法单测 | 参数用领域类型，Command 经 resolver 适配 |
| 不标 `@DomainService` 或 category 标错 | `category()` 返回 `UNKNOWN`，丢失分类元信息 | 标注正确的 `category`，与接口语义一致 |
| 一个接口同时命中多类判定 | 接口职责混杂 | 拆分为多个单类契约 |
| 用领域服务同步编排跨聚合（银行转账式） | 同步跨聚合事务、锁竞争 | 领域事件驱动，订阅者各自响应 |
| 属性计算直接内嵌在聚合根方法里 | 派生规则散落、不可复用 | 外移为 ATTRIBUTE_CALCULATOR，工厂先算后赋 |

## 8. 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根编码规范与派生属性的边界
- [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)：业务规则领域服务（BUSINESS_RULE）的落地
- [事件订阅领域服务落地模式](./event-subscriber-pattern.md)：事件订阅领域服务（EVENT_SUBSCRIBER）的落地
- [投影读模型代码落地指南](./projection-design.md)：读模型副本的投影 / 物化 / 对账构件
- [核心：领域服务](../core/domain-service.md)：四类契约与注解机制详解
- [核心：领域建模](../core/domain-modeling.md)：`IEntityPropertyCalculator` 与值对象
