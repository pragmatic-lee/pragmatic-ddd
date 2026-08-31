<div align="center">
  <img src="documentation/public/LOGO.svg" alt="Pragmatic DDD" width="260"/>
</div>

# Pragmatic DDD

> **务实可落地的领域驱动设计框架（Pragmatic Domain-Driven Design Framework）**
>
> 不追求 CQRS / Event Sourcing 的"全家桶"复杂度，聚焦于 DDD 核心战术模式的标准化表达：实体、值对象、聚合根、领域规则、领域事件。让团队用最小的学习成本，把 DDD 真正写进代码里。

<div align="center">

[![Java](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-pragmatic--ddd--core-blue.svg)](https://central.sonatype.com/artifact/io.pragmatic.ddd/pragmatic-ddd-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)

[完整示例 · Order Example](./examples/order-example/README.md) · [使用文档](./documentation/) · [最佳实践](./documentation/best-practices/)

</div>

---

## 框架特性

### 领域建模

| 能力 | 说明 |
|------|------|
| 实体与聚合根 | `AbstractEntity<T>` / `AggregateRoot<T>` 统一托管标识、软删标记、审计字段、乐观锁版本号与基于标识的等同性 |
| 值对象 | `ValueObject` 基于 `equalityComponents()` 提供结构相等性，`IValueObject` 作为语义标记 |
| 枚举值对象 | `IEnumValue<T, K>` 以 CODE 持久化替代 Java enum 序号，避免枚举重排导致的数据错乱 |
| 参数对象 | `IParamObject` 标记构造与业务方法入参对象，收敛多参数签名 |
| 消息码 | `MessageCode`（Java 17 record）+ `BrokenRuleRegistry` 集中声明校验消息，消灭魔法字符串，静态字段自动注册 |

### 业务规则引擎

| 能力 | 说明 |
|------|------|
| 无状态规则容器 | `EntityRule<T>` 规则容器 + `BaseRuleValidator` / `ICheckRule` 校验项，校验项接收「新模型 + 旧模型」双参数，可单例化、多线程安全共享 |
| 激活条件 | `IActiveRuleCondition<T>` 支持「规则码级开关」与「模型级条件」两级激活判定 |
| 运行时编排 | 支持 `appendRule` / `replaceRule` / `removeRule` 运行时增删改，以及 failFast 短路与新旧模型对比 |
| 领域服务分类 | `@DomainService` 划分四类原子能力：`CAPABILITY_PROVIDER` / `BUSINESS_RULE` / `ATTRIBUTE_CALCULATOR` / `EVENT_SUBSCRIBER` |

### 领域事件与一致性

| 能力 | 说明 |
|------|------|
| 事件建模 | `BaseDomainEvent` 不可变事件基类，聚合根 `collectEvent` 收集，支持即时与延迟两种投递 |
| 事件管理器 | `IEventManager` 组合发布、注册、生命周期三类端口；内置 `ThreadPoolEventManager` 本地实现 |
| 有序执行 | `ISubscriberOrderManager` 基于 DAG 编排订阅者执行顺序，支持前置依赖、条件执行与投递策略组合 |
| 操作追踪 | `OperationRegistry` / `recordOperation` 让事件自动归因到触发操作编码与聚合版本号，形成完整因果链 |
| 事务性 Outbox | `OutboxUnitOfWork` 同事务落库 + 落 outbox，`EagerOutboxPublisher` 提交后主动推送，`OutboxRelay` 兜底轮询补偿并转死信 |
| 对外广播 | `IBroadcastMessenger` + `AggregateMessageEnvelope` 统一信封，跨系统广播与领域事件分离 |

### 应用编排

| 能力 | 说明 |
|------|------|
| 命令执行器 | `ICommandExecutor` 封装「领域逻辑 → 规则校验 → 落库 → 发布事件 → 清理状态」固定模板 |
| 工作单元 | `IUnitOfWork` 多聚合同事务编排，支持 `tryCommit()` 零副作用试跑（DryRun） |
| 应用服务基类 | `AbstractApplicationService` + `ICommandApplicationService` / `IQueryApplicationService` 读写分离契约 |
| 工厂与更新器 | `EntityFactory` / `EntityUpdater` 分离创建与修改场景的 Input → 实体转换 |
| 属性解析 | `IEntityPropertyResolver` 单字段派生计算，配合 `EntityPropertyResolvers` 装配 |

### 持久化与读模型

| 能力 | 说明 |
|------|------|
| 写模型仓储 | `IRepository<ID, T>` + `AbstractRepository` 聚合级持久化契约，含落库前数据同步钩子 |
| 读模型投影 | `IAggregateProjection` / `IAggregateProjector` + `ProjectorRegistry` 寻址，把聚合映射为异构存储视图 |
| 查询端口族 | `IQueryById` / `IQueryByIds` / `IQueryOne` / `IQueryList` / `IQueryPage` / `IQueryScroll`，含分页与游标滚动值对象 |
| 物化与对账 | `IProjectionMaterializer` 写入异构存储；`Reconciler` / `ReconciliationManager` 提供补偿、去重与版本对账 |
| 变更追踪 | `TrackedList` / `TrackedMap` 把一对多集合拆为「新增 / 修改 / 删除」三桶，持久化只做增量而非全删全插 |
| 号段 ID | `base.id` 号段 ID 生成器体系（`IIdGenerator` / `IIdSegmentAllocator`），支持 Long 与 String 两种类型 |

### 集成与扩展

| 能力 | 说明 |
|------|------|
| 防腐层 | `AbstractQueryGateway` / `AbstractWriteGateway` / `AbstractIdempotentWriteGateway` 继承式套路，或 `ExternalCall` 组合式调用；`AclExceptions` 区分可重试与不可重试异常 |
| 外部依赖声明 | `@ExternalDependency` + `DependencyType` 声明式标注聚合依赖的外部系统端口 |
| 配置体系 | `IConfigurationSource` / `ConfigurationBinder` / `AbstractConfiguration` 三层配置，含 `IFeatureToggle` 特性开关与灰度策略 |
| MyBatis | `TypeHandlerContext` 一次性注册枚举、JSON、集合三类 TypeHandler；`MybatisOutboxStore`、`DbSegmentAllocator` 开箱即用 |
| RocketMQ | `RocketMqEventManager`（Remoting）与 `RocketMqGrpcEventManager`（gRPC 5.x Proxy）双通道事件管理器 |
| 零框架依赖 | 核心库只依赖 JDK 与 Lombok（provided），不依赖 Spring / MyBatis / MQ，领域层保持纯净可测 |

---

## 模块结构

```text
pragmatic-ddd/
├── pragmatic-ddd-parent        ← 统一父 POM（Java 17、插件、依赖版本）
├── pragmatic-ddd-bom           ← BOM，集中管理内部模块版本，供消费者一键引入
├── pragmatic-ddd-core          ← 核心库（实体、值对象、规则、事件、仓储、应用层、Outbox、追踪）
├── pragmatic-ddd-rocketmq      ← RocketMQ 领域事件基础设施（Remoting + gRPC 两种通道）
├── pragmatic-ddd-kafka         ← Kafka 领域事件基础设施（规划中）
├── pragmatic-ddd-spring-boot   ← Spring Boot Starter（规划中）
├── pragmatic-ddd-mybatis       ← MyBatis 辅助能力（类型处理器、Outbox 存储、号段 ID 分配）
└── examples/
    └── order-example           ← 电商订单完整示例（见下方「完整示例」）
```

> **模块状态说明**：`pragmatic-ddd-kafka` 与 `pragmatic-ddd-spring-boot` 当前为占位模块（仅有 `pom.xml`）。
> 示例模块默认不参与构建，如需连同示例一起编译请使用 `mvn install -Pexamples`。

---

## 快速开始

### 安装到本地仓库

> 当前版本尚未推送到 Maven 中央仓库，需先克隆仓库并执行 `install-local.sh`，把框架各模块安装到本地 `~/.m2/repository` 后再引入依赖。

```bash
# 1. 克隆仓库
git clone https://github.com/pragmatic-lee/pragmatic-ddd.git
cd pragmatic-ddd

# 2. 赋予脚本执行权限并安装到本地仓库（默认跳过单元测试执行）
chmod +x install-local.sh
./install-local.sh
```

常用参数：

| 参数 | 说明 |
|------|------|
| *（无参数）* | 全量安装整个 reactor 的 7 个框架模块（推荐，Maven 自动按依赖拓扑排序） |
| `<模块名>...` | 选择性安装指定模块，`-am` 自动带上其依赖。可用模块：`pragmatic-ddd-parent`、`pragmatic-ddd-bom`、`pragmatic-ddd-core`、`pragmatic-ddd-rocketmq`、`pragmatic-ddd-kafka`、`pragmatic-ddd-mybatis`、`pragmatic-ddd-spring-boot` |
| `--with-examples` | 全量安装并连 `examples` 一起构建（启用 `-Pexamples`） |
| `--run-tests` | 安装时执行单元测试（默认 `-DskipTests` 跳过测试执行、保留测试编译） |

```bash
# 示例：只安装核心库（自动带上 parent / bom 等依赖）
./install-local.sh pragmatic-ddd-core

# 示例：连同示例模块一起安装并执行测试
./install-local.sh --with-examples --run-tests
```

> 若不想使用脚本，等价命令为：`mvn -f pom.xml install -DskipTests`。

### 引入依赖

> 版本号需与本地安装的仓库版本保持一致（当前为 `2.0.0`，见根 `pom.xml` 的 `<version>`）。

核心库：

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-core</artifactId>
    <version>2.0.0</version>
</dependency>
```

如需 RocketMQ / MyBatis 集成，额外引入对应模块：

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-rocketmq</artifactId>
    <version>2.0.0</version>
</dependency>

<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-mybatis</artifactId>
    <version>2.0.0</version>
</dependency>
```

### 定义聚合根

聚合根继承 `AggregateRoot<T>`，实现 `brokenRuleRegistry()` 与 `operationRegistry()` 两个抽象方法，在业务方法内通过 `recordOperation` 记录操作、通过 `collectEvent` 收集领域事件：

```java
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.BrokenRuleRegistry;

public class Order extends AggregateRoot<Long> {

    private String pin;
    private java.math.BigDecimal totalPrice;
    private int status;

    /** 支付：记录操作并收集领域事件 */
    public void payment() {
        this.status = 1; // 已支付
        this.recordOperation(OrderOperation.PAY);
        this.collectEvent(new OrderPayedEvent(this));
    }

    @Override
    protected BrokenRuleRegistry brokenRuleRegistry() {
        return OrderBrokenRuleRegistry.INSTANCE;
    }

    @Override
    protected OperationRegistry operationRegistry() {
        return OrderOperation.REGISTRY;
    }
}
```

规则消息码注册表（基于 Java 17 `record`，通过静态字段自动注册）：

```java
import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.MessageCode;

public class OrderBrokenRuleRegistry extends BrokenRuleRegistry {

    public static final OrderBrokenRuleRegistry INSTANCE = new OrderBrokenRuleRegistry();

    public static final MessageCode PIN_IS_EMPTY =
            MessageCode.of("ORDER_PIN_IS_EMPTY", "用户标识不能为空");
    public static final MessageCode TOTAL_PRICE_ERROR =
            MessageCode.of("ORDER_TOTAL_PRICE_ERROR", "订单金额必须大于 0");
    public static final MessageCode AMOUNT_LIMIT_ERROR =
            MessageCode.of("ORDER_AMOUNT_LIMIT_ERROR", "订单金额不能超过 1000");
}
```

操作注册表（事件归因用，可选但推荐）：

```java
import io.pragmatic.ddd.operation.EntityOperation;
import io.pragmatic.ddd.operation.OperationRegistry;

public class OrderOperation extends OperationRegistry {

    public static final OrderOperation REGISTRY = new OrderOperation();

    public static final EntityOperation PAY = EntityOperation.of("PAY", "支付订单");
    public static final EntityOperation CANCEL = EntityOperation.of("CANCEL", "取消订单");
}
```

> 事件会自动归因到 `recordOperation` 记录的最近一次操作编码与聚合版本号（`version`）。

### 定义业务规则

`EntityRule` 是无状态规则容器，可通过校验器基类或 Lambda 校验项追加规则：

```java
import io.pragmatic.ddd.rules.RuleCheckResult;
import io.pragmatic.ddd.rules.ActiveStatus;
import io.pragmatic.ddd.rules.BaseRuleValidator;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.rules.IActiveRuleCondition;

public class OrderEntityRule extends EntityRule<Order> {
    public OrderEntityRule() {
        // 1) 继承 BaseRuleValidator 的校验器（推荐：可复用、可测试）
        this.addRule(new PinNotEmptyRule(), OrderBrokenRuleRegistry.PIN_IS_EMPTY);

        // 2) 或使用 ICheckRuleBuilder / Lambda 校验项（新模型 + 旧模型双参数）
        this.addRule((newModel, oldModel) ->
                        newModel.getTotalPrice() != null
                                && newModel.getTotalPrice().compareTo(java.math.BigDecimal.ZERO) > 0
                                ? RuleCheckResult.pass()
                                : RuleCheckResult.fail(),
                OrderBrokenRuleRegistry.TOTAL_PRICE_ERROR);

        // 3) 带激活条件的规则：仅当订单处于未支付状态（status == 0）时生效
        this.addRule((newModel, oldModel) ->
                        newModel.getTotalPrice() != null
                                && newModel.getTotalPrice().compareTo(new java.math.BigDecimal("1000")) <= 0
                                ? RuleCheckResult.pass()
                                : RuleCheckResult.fail(),
                OrderBrokenRuleRegistry.AMOUNT_LIMIT_ERROR,
                IActiveRuleCondition.of(model ->
                        model.getStatus() == 0 ? ActiveStatus.ACTIVE : ActiveStatus.INACTIVE));
    }

    /** 示例校验器：复用且可单测 */
    static class PinNotEmptyRule extends BaseRuleValidator<Order> {
        @Override
        protected boolean validate(Order newModel, Order oldModel) {
            return newModel.getPin() != null && !newModel.getPin().isBlank();
        }
    }
}
```

`EntityRule` 支持运行时增删改：`appendRule(...)`（指定前后位置）、`replaceRule(...)`、`removeRule(...)`，也支持新旧模型对比（覆写 `requireOldEntity()` 与 `supplyOldEntity()`）。

校验失败时，违规会写入聚合根，可通过 `getBrokenRules()` 获取或抛出异常：

```java
Order order = new Order();
if (!order.satisfiesRule(new OrderEntityRule())) {
    order.throwBrokenRuleException();            // 抛出首条违规异常
    // 或 order.throwBrokenRuleAggregateException(); 抛出聚合异常
}
```

### 定义领域事件

领域事件继承 `BaseDomainEvent`，保持不可变：

```java
import io.pragmatic.ddd.event.BaseDomainEvent;

public class OrderPayedEvent extends BaseDomainEvent {

    private final Long orderId;
    private final java.math.BigDecimal amount;

    public OrderPayedEvent(Order order) {
        super(order.getEntityId().toString());
        this.orderId = order.getEntityId();
        this.amount = order.getTotalPrice();
    }

    public Long getOrderId() {
        return orderId;
    }

    public java.math.BigDecimal getAmount() {
        return amount;
    }
}
```

### 发布与订阅事件

框架通过 `IEventManager`（组合发布、注册、生命周期三类能力）统一事件发布与订阅。本地场景可使用 `ThreadPoolEventManager`：

```java
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.IHandle;

// 1. 创建事件管理器
IEventManager eventManager = new ThreadPoolEventManager();
eventManager.init();
eventManager.start();

// 2. 注册订阅者（通过 IHandle 函数式接口承载处理逻辑）
eventManager.registerSubscriber("orderPayedLogger", OrderPayedEvent.class,
        (IHandle<OrderPayedEvent>) event ->
                log.info("订单 {} 已支付，金额：{}", event.getOrderId(), event.getAmount()));

// 3. 业务方法内收集事件，发布聚合根上全部领域事件
Order order = new Order();
order.payment();
eventManager.publishList(order.getDomainEvents());

// 4. 事件分发完成后清理聚合根工作单元临时状态
order.clearWorkUnitState();

// 5. 关闭
eventManager.shutdown();
```

订阅者支持条件执行（`IExecuteCondition`）、延迟/立即投递（`DeliveryPolicy`）与前置依赖订阅者（DAG 顺序编排）：

```java
import io.pragmatic.ddd.event.internal.model.DeliveryPolicy;

// 依赖顺序：subscriberB 在 subscriberA 之后执行
eventManager.registerSubscriber("subscriberA", OrderPayedEvent.class, handlerA);
eventManager.registerSubscriber("subscriberB", OrderPayedEvent.class, handlerB,
        null, "subscriberA", DeliveryPolicy.IMMEDIATE);
```

> 分布式场景可替换为 `RocketMqEventManager`（见下文 RocketMQ 集成），通过 Builder 配置，`start()` / `shutdown()` 受控管理生命周期。

### 事务性 Outbox

`application.outbox` 提供可靠事件投递，保证「本地事务落库」与「事件投递」的最终一致性：

- **`OutboxUnitOfWork`**：同一事务内逐条 `save` + 整批落 outbox（PENDING），事务提交后由 `EagerOutboxPublisher` 主动推送，失败保持 PENDING。
- **`OutboxRelay`**：兜底轮询器，周期性认领超时 PENDING 记录补偿重发，重试超限转死信（FAILED）。
- **`IOutboxStore`**：outbox 存储 SPI，由基础设施模块（如 `pragmatic-ddd-mybatis` 的 `MybatisOutboxStore`）实现。

```java
import io.pragmatic.ddd.application.IUnitOfWork;
import io.pragmatic.ddd.application.outbox.EagerOutboxPublisher;
import io.pragmatic.ddd.application.outbox.OutboxUnitOfWork;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor;
import io.pragmatic.ddd.mybatis.outbox.MybatisOutboxStore;
import io.pragmatic.ddd.mybatis.typehandler.json.Fastjson2JsonSerializer;

import java.util.concurrent.Executors;

// 组合装配（示意）
TransactionOperations txOps = ...;   // 由集成层实现：绑定"聚合写 + outbox 写"到同一 DB 事务
IOutboxStatementExecutor executor = ...; // 由集成层实现：注入 SqlSessionTemplate，按 statementKey 直调 SQL（传统纯 XML，无需 Mapper 接口）
IOutboxStore outboxStore = new MybatisOutboxStore(executor, txOps);
IEventManager eventManager = new ThreadPoolEventManager();
IUnitOfWork uow = new OutboxUnitOfWork(outboxStore, txOps,
        new Fastjson2JsonSerializer(),
        new EagerOutboxPublisher(outboxStore, eventManager, Executors.newCachedThreadPool()));

// 在应用服务中使用
Order order = new Order();
uow.register(order, new OrderEntityRule(), orderRepository, Order::payment);
uow.commit();   // 同事务落库 + 落 outbox，提交后推送事件
```

> 默认 `UnitOfWork` 与 `OutboxUnitOfWork` 并存：前者直接发布事件，后者走 outbox 可靠通道，按需选用。

### MyBatis 集成

`pragmatic-ddd-mybatis` 提供与 MyBatis 的衔接能力，让聚合根可借助框架基础设施持久化：

- **统一类型处理器装配**：`TypeHandlerContext` 集中持有枚举策略、VO 类型与共享组件，构建完 `SqlSessionFactory` 后调用一次 `registerInto(...)` 即可同时注册枚举、JSON、集合三类处理器，零 Spring 依赖。
  - `enums`：`UniversalEnumTypeHandler` + `EnumValueResolver`，按策略（CODE/ORDINAL/NAME）映射枚举。
  - `json`：`GenericJsonTypeHandler`，把值对象整体读写为数据库原生 JSON 列（PG `jsonb` / MySQL `JSON`）。
  - `list`：`ListTypeHandler`，单列 JSON 数组处理器，按列标签还原 `List<E>` 元素类型。
- **可靠事件 Outbox**：`MybatisOutboxStore` 实现 `IOutboxStore`，`store` 在调用方事务内执行，`claim`/`markSent` 等为独立短事务，`markSent` 带状态守卫保证幂等。
- **ID 号段分配**：`DbSegmentAllocator` 实现 `IIdSegmentAllocator`，基于数据库 `SELECT ... FOR UPDATE` 自管独立短事务分配号段，仅依赖 MyBatis 核心 API。

```java
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.base.id.IIdSegmentAllocator;
import io.pragmatic.ddd.mybatis.id.DbSegmentAllocator;
import io.pragmatic.ddd.mybatis.id.IIdSegmentStatementExecutor;
import io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor;
import io.pragmatic.ddd.mybatis.outbox.MybatisOutboxStore;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;

// 基于 MyBatis 的号段 ID 分配器（传统纯 XML 直调，执行器自管独立短事务）
IIdSegmentStatementExecutor idExecutor = ...; // 由集成层实现：注入 SqlSessionFactory，自管独立短事务
IIdSegmentAllocator allocator = new DbSegmentAllocator(idExecutor);
IdSegment segment = allocator.allocateNext("order"); // record IdSegment(current, max, step)
long currentId = segment.current();                  // 当前号段 [current, max]

// 基于 MyBatis 的事件箱存储（与聚合同事务，执行器按 statementKey 直调 SQL）
IOutboxStatementExecutor outboxExecutor = ...; // 由集成层实现：注入 SqlSessionTemplate
IOutboxStore outboxStore = new MybatisOutboxStore(outboxExecutor, transactionOperations);
outboxStore.store(outboxMessages); // 在调用方事务内批量落库

// 统一注册枚举 / JSON / 集合类型处理器（构建完 SqlSessionFactory 后调用一次）
TypeHandlerContext context = new TypeHandlerContext(resolver, serializer,
        new PgJdbcJsonValue(), enumRules, voTypes, collectionsConfig);
context.registerInto(sqlSessionFactory);
```

### RocketMQ 集成

`pragmatic-ddd-rocketmq` 提供两种 RocketMQ 通道的领域事件管理器：

- **`RocketMqEventManager`**（Remoting 协议）：基于 `rocketmq-client` 的 `DefaultMQProducer` / `DefaultMQPushConsumer`，兼容 RocketMQ 4.x / 5.x Broker。
- **`RocketMqGrpcEventManager`**（gRPC 协议）：基于 `rocketmq-client-java`（5.x Proxy，可选依赖）。

通过 `RocketMqConfig` 统一配置，`RocketMqEventManager.builder()` 构建，`start()` / `shutdown()` 受控管理生命周期：

```java
import io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.ITopicResolver;
import io.pragmatic.ddd.rocketmq.RocketMqConfig;
import io.pragmatic.ddd.rocketmq.RocketMqEventManager;

RocketMqConfig config = new RocketMqConfig()
        .setNameServer("127.0.0.1:9876")
        .setProducerGroup("ORDER_PRODUCER_GROUP")
        .setConsumerGroup("ORDER_CONSUMER_GROUP");

// 解析事件 → Topic；可自行实现 ITopicResolver，也可用内置的 ConfigurableTopicResolver 快速上手
ITopicResolver topicResolver = new ConfigurableTopicResolver.Builder()
        .globalDefaultTopic("order-event-topic")
        .eventTopic("OrderPayedEvent", "order-pay-topic")
        .build();

IEventManager eventManager = RocketMqEventManager.builder()
        .config(config)
        .topicResolver(topicResolver)
        .build();

eventManager.start();
// ... 注册订阅者 / 发布事件
eventManager.shutdown();
```

> `ConfigurableTopicResolver` 提供三层次解析（订阅者级 → 事件级 → 全局默认），位于 `io.pragmatic.ddd.event.internal.defaults`；如需更精细的路由，可实现自己的 `ITopicResolver`。

---

## 完整示例：Order Example

> 想要看框架在真实工程里怎么落地，直接看 **[examples/order-example](./examples/order-example/README.md)**。

`order-example` 是一个完整的电商订单服务，覆盖从聚合建模到异构存储查询的全链路：

| 环节 | 示例中的内容 |
|------|-------------|
| 领域建模 | `Order` 聚合根 + `OrderItem` 实体 + 值对象 + `IEnumValue` 枚举 |
| 业务规则 | `OrderRule` 规则容器 + `OrderRuleRegistry` 消息码 + 外部依赖校验注入 |
| 应用编排 | `OrderWriteService` / `OrderReadService` + Factory / Updater / Resolver |
| 持久化 | MyBatis 仓储 + 手写 `SqlSessionFactory` 与 TypeHandler 三通道装配 |
| 读模型 | ES 投影：Projector + Materializer + Query 门面 + 四个 Searcher + 读模型对账 |
| 事件与一致性 | RocketMQ 事件管理器 + Outbox 事务性发件箱完整装配 |
| 基础设施 | MySQL / Redis / Elasticsearch / RocketMQ 配置类 |

```bash
# 启动示例前需先安装框架到本地仓库
./install-local.sh
# 按 examples/order-example/README.md 中的说明准备依赖中间件并启动
```

---

## 文档

| 文档 | 说明 |
|------|------|
| [使用文档](./documentation/) | VitePress 文档站：入门指南、核心能力、集成模块、最佳实践 |
| [快速开始](./documentation/getting-started/quick-start.md) | 5 分钟跑通第一个聚合根 |
| [核心能力](./documentation/core/domain-modeling.md) | 领域建模、业务规则、领域事件、应用服务、读写模型 |
| [最佳实践模式库](./documentation/best-practices/) | 20 篇落地模式：原则 + 代码骨架 + 约束 + 反模式 |
| [推荐项目结构](./documentation/getting-started/project-structure.md) | 四层分包规范与层间边界 |
| [API 速查索引](./documentation/reference/api-index.md) | 全部 public 类型与接口清单 |
| [设计提案与重构计划](./docs/design/core/) | 各模块的设计提案、分析与重构计划 |

---

## 设计理念

> **Pragmatic DDD** —— 务实可落地的领域驱动设计框架。
>
> 不追求 CQRS / Event Sourcing 的全家桶复杂度，聚焦于 DDD 核心战术模式的标准化表达：实体、值对象、聚合根、领域规则、领域事件。让团队用最小的学习成本，把 DDD 真正写进代码里。

框架遵循以下原则：

- **核心战术模式优先**：以实体、聚合根、值对象、领域规则、领域事件为骨架，避免引入过度抽象的架构负担。
- **框架通用性**：作为基础库，设计上保持通用、零 Spring 强依赖，便于其他项目引用并快速集成。
- **现代 Java 特性**：基于 Java 17 开发，充分利用记录类（record）、密封类（sealed）、模式匹配、方法引用等特性。
- **规则无状态化**：校验项接收「新模型 + 旧模型」双参数，规则对象可单例化、多线程安全共享。
- **约束显式化**：把业务约束写进代码结构（注册表、操作归因、聚合边界），而非依赖约定与口头规范。

---

## 贡献

欢迎参与贡献！请在提交 Pull Request 前阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)，了解代码规范与提交流程。

## 许可证

本项目基于 Apache License 2.0 开源，详见 [LICENSE](./LICENSE)。
