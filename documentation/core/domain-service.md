# 领域服务

> 本文档属于 pragmatic-ddd 使用文档 `core` 系列，定义领域服务（`io.pragmatic.ddd.service.IDomainService`）的分类、契约与分层约束。
> 阅读前建议先完成 [领域建模](./domain-modeling.md)。本系列相关文档：[业务规则引擎](./business-rules.md) · [领域事件](./domain-events.md) · [应用服务](./application-service.md)。

## 1. 概述

### 1.1 核心定位

领域服务是**领域层声明、应用层实现的契约接口**：领域层通过 `extends IDomainService` 定义"需要什么能力"，应用层提供具体实现。该契约用于承载无法归入单一聚合根的逻辑——事件订阅、跨聚合校验、属性计算与领域原语供给。框架不提供领域服务运行时容器，仅通过标记接口 + 注解承载可反射读取的分类元信息，实现在应用层注册与装配。

### 1.2 概念层级与依赖关系

类型继承关系：

```text
IDomainService (io.pragmatic.ddd.service)           标记接口，提供 category()
├── IEventSubscriberService<T>  (service)            extends IDomainService, IHandle<T>
├── IRuleValidatorService       (service)            extends IDomainService
├── ICapabilityProviderService  (service)            extends IDomainService
└── IEntityPropertyCalculator<T,E,R> (base)          extends IDomainService （第三类属性计算的实际承载）

IHandle<T> (io.pragmatic.ddd.event.spi)              事件处理端口，声明 void handleEvent(T)
```

模块/分层依赖约束：

| 层 | 职责 | 编译期依赖边界 |
| --- | --- | --- |
| 领域层 | 定义接口（契约），声明"做什么" | 仅依赖 `io.pragmatic.ddd.service` / `io.pragmatic.ddd.base` 与领域类型；**不依赖基础设施**（数据库、HTTP 客户端、消息中间件） |
| 应用层 | `implements` 提供实现 | 可依赖基础设施，并将实现注册/装配到框架运行时（Spring 容器或事件总线） |

### 1.3 与经典 DDD 的认知差异

经典 DDD 将领域服务定义为"当某业务逻辑不属于任何一个实体或值对象时，就把它放入领域服务"的兜底概念，典型场景为跨聚合操作（如银行转账）、实体自身无法处理的复杂计算、需多聚合协作完成的流程。该定义的核心问题是"不属于实体"依赖主观判断，缺乏明确边界：同一段逻辑，不同开发者可能归入聚合根，也可能归入领域服务，归属结论不一致。

本框架的领域服务与经典 DDD 理论中的领域服务在定位上有本质区别。经典 DDD 将领域服务定义为"当逻辑不属于任何实体或值对象时的兜底容器"，本框架将其重定义为**领域层声明、应用层实现的契约（端口）**。两者差异如下：

| 维度 | 经典 DDD 领域服务 | 本框架领域服务 |
| --- | --- | --- |
| 定义形态 | 直接编写实现类，承载业务逻辑 | 领域层定义接口（`extends IDomainService`），应用层 `implements` 提供实现 |
| 分类 | 单一笼统概念，无内置分类 | 四类明确分类（事件订阅 / 校验规则 / 属性计算 / 能力供给），由方法签名判定 |
| 跨聚合协作 | 鼓励在领域服务中同步编排多个聚合 | 跨聚合协作通过领域事件驱动，由多个订阅者分别响应，不在单个服务内同步编排 |
| 依赖方向 | 实现可直接依赖数据库、外部 API 等基础设施 | 领域层接口不依赖基础设施；仅应用层实现可依赖基础设施 |
| 归类判定 | 由开发者主观判断"是否属于实体" | 由契约的方法签名与语义客观判定（见 §2.5 判定流程） |

> 经典 DDD 常用"银行转账"作为领域服务范例。该范例在本框架中不适用：真实转账多为跨系统调用（属应用/基础设施层职责），即便同系统内部转账也更宜通过事件驱动异步处理，而非在一个领域服务中同步编排两个账户聚合。定义本框架的领域服务时，**不要套用"银行转账式"跨聚合编排范式**。

## 2. 四类领域服务的严格定义

任一 `extends IDomainService` 的契约，按其"方法形态 + 业务语义"唯一落入以下一类（互斥且完备）。

### 2.1 第一类：事件订阅领域服务（EVENT_SUBSCRIBER）

领域层声明"在某领域事件发生后，执行某业务动作"的契约。形态标志：**继承 `IHandle<T>`**，由此声明所关注事件类型 `T` 与处理方法 `handleEvent(T)`。

| 项 | 说明 |
| --- | --- |
| 基类接口 | `IEventSubscriberService<T extends IDomainEvent> extends IDomainService, IHandle<T>` |
| 方法 | `void handleEvent(T event)`（来自 `IHandle<T>`，领域层不实现） |
| 触发 | 事件总线在 `T` 发布后路由调用（Spring 环境下框架自动扫描 `IHandle` 实现；非 Spring 需 `IEventRegistry.registerSubscriber` 注册） |
| 语义边界 | 仅响应已发生事件做后续动作；不主动编排跨聚合写操作链路 |

### 2.2 第二类：校验规则领域服务（RULE_VALIDATOR）

领域层声明"对某业务对象执行一条可复用校验，给出通过/拒绝结论"的契约。形态标志：**方法返回 `RuleCheckResult`**。

| 项 | 说明 |
| --- | --- |
| 基类接口 | `IRuleValidatorService extends IDomainService` |
| 方法 | 自定义校验方法，返回 `RuleCheckResult`（通常命名为 `check(...)`） |
| 参数 | 领域对象、值对象或领域参数（**不得**是应用层 `Command` / `HttpRequest`） |
| 返回值 | `RuleCheckResult.pass()` / `RuleCheckResult.fail(Object[])` |
| 语义边界 | 只判断、不改状态、不写库 |

与聚合根内部 `IRule<T>` / `ICheckRule<T>` 细粒度不变量校验互补：跨聚合、入参前置、复杂组合业务规则用本类。详见 [校验规则领域服务最佳实践](../best-practices/rule-validation.md)。

### 2.3 第三类：属性计算（类型转换）领域服务（ATTRIBUTE_CALCULATOR）

领域层声明"基于一个或多个领域输入，推导出某领域属性值 / 值对象"的契约。形态标志：**存在"由输入推导输出"的计算方法**，输入输出均为领域类型。

| 项 | 说明 |
| --- | --- |
| 基类接口 | 无专属标记子接口；直接 `extends IDomainService`，或复用 `IEntityPropertyCalculator<T,E,R> extends IDomainService` |
| 方法 | `R calculate(...)`（自由计算）或 `R calculate(T source, E entity)`（实体属性计算） |
| 参数 | 领域对象 / 值对象 / 上下文（`IEntityPropertyCalculator` 中 `entity` 在创建场景为 `null`） |
| 语义边界 | 输出是由输入"推导"出的属性值或视图对象，通常可替换为纯领域计算 |

### 2.4 第四类：领域工厂 / 能力供给领域服务（CAPABILITY_PROVIDER）

领域层声明"需由应用层落地、且通常依赖基础设施才能提供的领域能力（典型为生成某领域原语/对象）"的契约。形态标志：**方法无（或仅简单领域参数）输入，却新生产出领域原语/对象**。

| 项 | 说明 |
| --- | --- |
| 基类接口 | `ICapabilityProviderService extends IDomainService` |
| 方法 | 产出方法，通常 `T generate()` / `T nextId()` / `T createXxx(...)` |
| 返回值 | 领域原语（如 `long` ID）或领域对象 |
| 语义边界 | 声明"我需要能产生 X 的能力"，具体算法（雪花 / 数据库序列 / UUID）由应用层决定；通常无法用纯领域逻辑替代 |

### 2.5 四类的互斥与判定流程

任一 `extends IDomainService` 的契约，按下表唯一归类（自上而下，命中即止）：

| 判定序 | 检查项 | 命中条件 | 归类 | 枚举值 |
| --- | --- | --- | --- | --- |
| 1 | 是否 `extends IHandle<T>` | 继承 `IHandle`，响应领域事件 | 事件订阅 | `EVENT_SUBSCRIBER` |
| 2 | 方法是否返回 `RuleCheckResult` | 校验方法返回 `RuleCheckResult` | 校验规则 | `RULE_VALIDATOR` |
| 3 | 方法是否"由领域输入推导领域输出" | 有 `calculate(...)`，输入输出均为领域类型 | 属性计算 | `ATTRIBUTE_CALCULATOR` |
| 4 | 方法是否"无/少输入却新生产领域原语/对象" | 有 `generate()` / `nextId()` 等产出方法 | 能力供给 | `CAPABILITY_PROVIDER` |

> 四类互斥：一个契约只会命中其中一项。若同时命中多项，说明接口抽象错误，应拆分。未标注 `@DomainService` 时 `category()` 返回 `UNKNOWN`，不在上述四类之内。

## 3. 分类标记：`@DomainService` 与 `category()`

`IDomainService` 提供 `category()` 默认方法，配合 `@DomainService` 注解承载可反射读取的业务元信息（对称于依赖体系的 `@ExternalDependency`）。

### 3.1 顶层接口契约

```java
package io.pragmatic.ddd.service;

public interface IDomainService {

    /** 返回领域服务分类，默认读取注解，未标注则返回 DomainServiceCategory.UNKNOWN。 */
    default DomainServiceCategory category() {
        DomainService annotation = findAnnotation(this.getClass());
        return annotation == null ? DomainServiceCategory.UNKNOWN : annotation.category();
    }
}
```

`category()` 的注解查找规则（`findAnnotation`）：
- 先查实现类上的 `@DomainService`；
- 未命中则沿**所实现接口**向上递归查找；
- 仍未命中则沿**父类**向上递归查找；
- 到达 `Object` 仍无则返回 `null`，`category()` 返回 `UNKNOWN`。

### 3.2 注解字段说明

```java
package io.pragmatic.ddd.service;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DomainService {
    DomainServiceCategory category();   // 服务分类，必填，须与 IDomainService.category() 反映的分类一致
    String description() default "";    // 业务描述：这个领域服务是干什么的
    String targetName() default "";     // 关联对象名（按 category 语义解释，见下表）
}
```

| `category` | `targetName` 语义 |
| --- | --- |
| `EVENT_SUBSCRIBER` | 处理的事件名（如 `OrderPaidEvent`） |
| `RULE_VALIDATOR` | 作用的领域对象（如 `Order`） |
| `ATTRIBUTE_CALCULATOR` | 作用的领域对象（如 `Order/OrderItem`） |
| `CAPABILITY_PROVIDER` | 产出的领域原语/对象（如 `OrderId`） |

### 3.3 分类枚举

```java
package io.pragmatic.ddd.service;

public enum DomainServiceCategory {
    EVENT_SUBSCRIBER,     // 事件订阅
    RULE_VALIDATOR,       // 校验规则
    ATTRIBUTE_CALCULATOR, // 属性计算（类型转换）
    CAPABILITY_PROVIDER,  // 领域工厂 / 能力供给
    UNKNOWN               // 未分类（向后兼容）
}
```

### 3.4 基类接口清单与注解声明

四类契约继承对应基类接口，并在接口上标注 `@DomainService` 作为主声明；`category()` 统一由注解读取。

```java
// 第一类：事件订阅（service 包）
public interface IEventSubscriberService<T extends IDomainEvent>
        extends IDomainService, IHandle<T> { }

// 第二类：校验规则（service 包）
public interface IRuleValidatorService extends IDomainService { }

// 第四类：能力供给（service 包）
public interface ICapabilityProviderService extends IDomainService { }

// 第三类：属性计算（base 包，无专属标记子接口，直接复用泛型契约）
public interface IEntityPropertyCalculator<T, E, R> extends IDomainService {
    R calculate(T source, E entity);   // entity 在创建场景为 null
}
```

::: tip 注解用法示例
```java
@DomainService(category = DomainServiceCategory.EVENT_SUBSCRIBER,
        targetName = "OrderPaidEvent",
        description = "订单支付成功后扣减库存")
public interface IOrderPaidInventoryDeducer
        extends IDomainService, IHandle<OrderPaidEvent> { }
```
:::

## 4. 定义方式（领域层）

四类契约均只在领域层声明接口，不含实现。接口名必须**体现业务意图**，不得使用泛化占位词 `Handler` / `Processor` / `Rule` 作为接口名。

### 4.1 事件订阅契约

```java
public interface IOrderPaidNotificationSender
        extends IDomainService, IHandle<OrderPaidEvent> {
    // 继承 IHandle<T> 即声明 handleEvent(OrderPaidEvent)，无需重复声明
}
```

### 4.2 校验规则契约

```java
@FunctionalInterface
public interface IOrderAmountLimitRule extends IDomainService {
    RuleCheckResult check(BigDecimal amount);
}
```

### 4.3 属性计算契约

自由计算场景，直接 `extends IDomainService`：

```java
public interface IOrderTotalCalculator extends IDomainService {
    OrderTotal calculate(List<OrderItem> items);
}
```

绑定具体实体属性的场景，复用 base 包泛型基契约：

```java
public interface IOrderTotalPriceCalculator
        extends IEntityPropertyCalculator<TotalPriceContext, Order, BigDecimal> {
}
```

### 4.4 能力供给契约

```java
public interface IOrderIdGenerator extends IDomainService {
    long generate();
}
```

## 5. 实现方式（应用层）

应用层 `implements` 契约提供实现，可访问基础设施资源。实现类与领域层契约**语义镜像对应**。

```java
@Component
public class OrderPaidInventoryDeductionHandler
        implements IOrderPaidInventoryDeducer {

    private final IRepository<String, Inventory> inventoryRepository;

    public OrderPaidInventoryDeductionHandler(
            IRepository<String, Inventory> inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public void handleEvent(OrderPaidEvent event) {
        Inventory inventory = inventoryRepository.findById(event.getOrderId());
        inventory.deduct();
        inventoryRepository.save(inventory);
    }
}
```

```java
@Component
public class OrderAmountLimitRule implements IOrderAmountLimitRule {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("50000");

    @Override
    public RuleCheckResult check(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return RuleCheckResult.fail(new Object[]{amount});
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            return RuleCheckResult.fail(new Object[]{amount, MAX_AMOUNT});
        }
        return RuleCheckResult.pass();
    }
}
```

事件订阅实现在 Spring 环境下由框架自动扫描 `IHandle` 实现并注册；非 Spring 环境需手动注册到事件总线：

```java
eventManager.registerSubscriber(
    "order-paid-inventory-deduction",   // 订阅者别名
    OrderPaidEvent.class,                // 监听的事件类型
    orderPaidInventoryDeductionHandler   // IHandle<OrderPaidEvent> 实现
);
```

## 6. 与属性计算（类型转换）的边界

第三类与第四类都属"领域层要一个产出、应用层给实现"，易混淆，区分准则：

| 维度 | 属性计算（ATTRIBUTE_CALCULATOR） | 能力供给（CAPABILITY_PROVIDER） |
| --- | --- | --- |
| **输入** | 至少一个领域对象/值对象/上下文 | 往往无输入，或仅简单领域参数 |
| **产出性质** | 由输入"推导"出的属性值/视图对象 | 新"产生"的标识符或领域对象（非由输入推导） |
| **典型方法** | `calculate(T, E)` / `calculate(List<OrderItem>)` | `generate()` / `nextId()` / `createXxx(...)` |
| **可替换为纯领域逻辑吗** | 往往可以（纯计算） | 通常不行（依赖基础设施才能落地） |

> 区分准则：输出是"算出来的"归第三类，"凭空造出来的（或需外部设施才能造出来的）"归第四类。

## 7. 命名规范速查

### 7.1 领域层（接口，以 `I` 开头）

| 类型 | 命名格式 | 示例 |
| --- | --- | --- |
| 事件订阅 | `I{事件}{业务动作意图}` | `IOrderPaidNotificationSender` |
| 校验规则 | `I{业务对象}{具体规则意图}Rule` | `IOrderAmountLimitRule` |
| 属性计算 | `I{输入}To{输出}Converter` / `I{结果}Calculator` | `IOrderTotalCalculator` |
| 能力供给 | `I{产物}Generator` / `I{产物}Provider` | `IOrderIdGenerator` |

### 7.2 应用层（实现，镜像接口名去 `I`）

| 类型 | 命名格式 | 示例 |
| --- | --- | --- |
| 事件订阅 | `{事件}{业务意图}Handler` | `OrderPaidInventoryDeductionHandler` |
| 校验规则 | 与接口同名（去 `I`） | `OrderAmountLimitRule` |
| 属性计算 | 与接口同名（去 `I`） | `OrderTotalCalculator` |
| 能力供给 | 与接口同名（去 `I`） | `OrderIdGenerator` |

> 命名中的"业务意图"指领域层承诺的具体领域动作（发通知 / 扣库存 / 金额上限 / 生成 ID），而非技术占位词。接口名本身即成为领域文档。

## 8. 包结构建议

```
domain/
└── {bounded-context}/
    ├── model/       # 聚合根、实体、值对象
    ├── event/       # 领域事件定义
    └── service/     # 领域服务接口定义（仅接口！）
        ├── IOrderPaidNotificationSender.java  # 事件订阅契约
        ├── IOrderAmountLimitRule.java         # 校验规则契约
        ├── IOrderTotalCalculator.java          # 属性计算契约
        └── IOrderIdGenerator.java              # 能力供给契约

application/
└── {bounded-context}/
    ├── command/     # 命令应用服务
    ├── query/       # 查询应用服务
    └── service/     # 领域服务实现
        ├── OrderPaidInventoryDeductionHandler.java
        ├── OrderAmountLimitRule.java
        ├── OrderTotalCalculator.java
        └── OrderIdGenerator.java
```

## 9. 关键机制与避坑指南

### 9.1 `category()` 的注解查找链

`category()` 通过 `findAnnotation` 递归查找 `@DomainService`：实现类 → 所实现接口 → 父类。因此**注解标注在实现类上，子类接口同样能读到分类**；但若在领域层接口上标注、`category()` 由框架以接口类型调用时也能命中。

> **重要约束**：`category()` 仅读取 `@DomainService` 注解。若契约接口既未标注注解、实现类也未标注，则 `category()` 恒返回 `UNKNOWN`，该服务不会进入任何分类维度（不影响方法调用，但会丢失分类元信息，导致依赖分类的扫描/校验逻辑无法识别它）。

### 9.2 第三类无专属标记子接口

> **重要约束**：框架**未提供** `ITypeConverterService` 这类第三类标记子接口。第三类属性计算应直接 `extends IDomainService`，或在绑定实体属性时复用 `io.pragmatic.ddd.base.IEntityPropertyCalculator<T,E,R>`。误引入不存在的 `ITypeConverterService` 会导致编译失败。

### 9.3 参数类型边界

> **重要约束**：第二、三类契约的方法参数必须是领域类型（领域对象、值对象、领域上下文）。**不得**使用应用层 `Command` / `HttpRequest` / DTO 作为方法入参，否则会破坏领域层对基础设施的零依赖约束，并使契约无法在领域层独立单测。

### 9.4 事件订阅的注册路径

> **重要约束**：事件订阅实现（`IHandle<T>`）在 Spring 环境下由框架自动扫描注册；**非 Spring 环境必须显式调用 `IEventRegistry.registerSubscriber`**，否则事件发布后不会触发 `handleEvent`。不要假设非 Spring 场景下存在自动装配。

## 10. 向后兼容

- 现有实现若不标注 `@DomainService`，`category()` 默认返回 `UNKNOWN`，方法调用行为不受影响。
- 新契约继承对应基类接口并标注 `@DomainService`，即获得明确分类与描述元信息。
- `@DomainService` 为 `RUNTIME` 保留策略，仅增加元数据，不改方法契约，不影响运行期调用。

## 11. 总结速查

| 概念 | 基类接口（包） | 形态标志 | 方法形态 | 最关键的约束 |
| --- | --- | --- | --- | --- |
| 事件订阅 `EVENT_SUBSCRIBER` | `IEventSubscriberService<T>`（service） | `extends IHandle<T>` | `void handleEvent(T)` | 仅响应已发生事件；非 Spring 需手动注册 |
| 校验规则 `RULE_VALIDATOR` | `IRuleValidatorService`（service） | 返回 `RuleCheckResult` | `RuleCheckResult check(...)` | 入参须为领域类型；只判断不改状态不写库 |
| 属性计算 `ATTRIBUTE_CALCULATOR` | 无专属子接口；复用 `IEntityPropertyCalculator`（base） | `calculate(...)` | `R calculate(...)` / `R calculate(T,E)` | 无 `ITypeConverterService`；输出须由输入推导 |
| 能力供给 `CAPABILITY_PROVIDER` | `ICapabilityProviderService`（service） | 产出方法 | `T generate()` / `nextId()` | 通常依赖基础设施；输出非由输入推导 |
| 未分类 `UNKNOWN` | — | 未标注 `@DomainService` | 任意 | `category()` 恒返回 `UNKNOWN`，丢失分类元信息 |

下一步建议阅读：

- [业务规则引擎](./business-rules.md)：聚合根上的细粒度不变量校验
- [领域事件](./domain-events.md)：`IHandle` 与事件总线注册
- [应用服务](./application-service.md)：`execute()` 中集成校验规则领域服务
- [校验规则领域服务最佳实践](../best-practices/rule-validation.md)：校验规则领域服务的应用层落地
