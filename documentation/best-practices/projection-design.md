# 投影读模型代码落地指南

> 本文档说明投影读模型代码**按本次落地的方式怎么写**，是可复用的通用指导原则：从包结构与命名，到每个组件的手写规则，再到事件物化与对账补偿的衔接。下文以订单 ES 投影（`Order`）为示例贯穿全文；其他模块做类似的投影设计时，套用本文档的结构与规则，把 `Order` 换成目标聚合、`Es` 换成目标存储即可。

## 1. 投影读模型的本质

投影读模型把聚合（写模型）映射为异构存储中的文档（读模型），为「面向查询的聚合检索」场景提供**独立读视图**，与写模型的聚合根装配完全解耦。

核心特征：

- **中立读视图**：投影 DTO 只声明字段、对齐存储 Mapping，不含存储与查询逻辑。
- **纯映射**：投影器只做字段取值 / 裁剪 / 派生，不接触存储客户端。
- **物化分离**：物化器只负责存储读写与版本控制，不知道字段如何派生。
- **版本对账**：存储中的副本版本 V' 与写模型版本 V 比较，落后（STALE）重建、残留（ORPHAN）清理。

**写模型零改动**：聚合根不持有任何存储依赖，只在仓储落库前经 `triggerDataSyncHook()` 发出数据同步事件；投影的装配与存储细节全部落在领域契约与基础设施实现中。

## 2. 包结构与命名规范

### 2.1 分层：领域定义契约，基础设施提供实现，应用层组装编排

```text
domain/order/projection/                       领域：读模型视图 + 寻址（无存储/Spring 依赖）
  ├── IOrderProjection                         extends IAggregateProjection
  ├── OrderEsProjection                        @Data 数据容器，implements IOrderProjection
  └── OrderEsTargets                           ORDER_INDEX_NAME / TARGET_ES_ORDERS 常量
domain/order/projection/materializer/          领域：物化/版本/补偿 专属契约（窄化框架接口）
  ├── IOrderProjectionMaterializer             extends IProjectionMaterializer<OrderEsProjection>
  ├── IOrderReadModelVersionResolver           extends IReadModelVersionResolver<Long>
  └── IOrderReadModelResynchronizer            extends IReadModelResynchronizer<Long>

infrastructure/order/projection/               基础设施：聚合 → 视图 纯映射
  └── OrderEsProjector                         extends AbstractAggregateProjector<Order, OrderEsProjection>
infrastructure/order/projection/materializer/  基础设施：异构存储写入 + 对账
  ├── OrderEsMaterializer                      implements IOrderProjectionMaterializer
  ├── OrderEsVersionResolver                   implements IOrderReadModelVersionResolver
  └── OrderEsResynchronizer                    implements IOrderReadModelResynchronizer
infrastructure/order/config/                   Spring 装配（登记 registry、产出 Bean）
  └── OrderProjectionConfig
application/order/service/                     应用层编排（findById → project → materialize）
  └── OrderDataSyncEsProjectionHandle
application/order/subscriber/                  事件订阅绑定
  └── OrderEventSubscriberRegistry
```

依赖方向单向内聚：`Infrastructure → Domain → Framework`。

### 2.2 命名规范

```java
// ✅ 推荐：契约 I 开头，以聚合前缀窄化框架通用接口
public interface IOrderProjection extends IAggregateProjection { }
public interface IOrderProjectionMaterializer extends IProjectionMaterializer<OrderEsProjection> { }

// ✅ 推荐：视图载体与实现以 OrderEs* 标明聚合与存储
public class OrderEsProjector extends AbstractAggregateProjector<Order, OrderEsProjection> { }
public class OrderEsMaterializer implements IOrderProjectionMaterializer { }

// ❌ 反模式：基础设施直接实现框架通用接口，领域层无专属契约、替换存储需改基础设施与框架的直接契约
public class OrderEsMaterializer implements IProjectionMaterializer<OrderEsProjection> { }
```

> **命名约定**：接口一律 `I` 开头；领域专属接口以 `Order` 前缀区分框架通用接口（`IOrderProjection` 等，不含存储标记）；实现类不用 `Impl` 后缀，用 `OrderEs*` 标明聚合与存储（`OrderEsProjector` / `OrderEsMaterializer` / `OrderEsVersionResolver` / `OrderEsResynchronizer`）；对账目标常量集中在 `OrderEsTargets`。

## 3. 投影承载的数据

投影字段对齐存储 Mapping，既含聚合原始字段，也含查询辅助的派生字段。**字段取舍由「读侧要查什么」决定，而不是「写模型有什么」**。

字段设计规则：

| 规则 | 说明 |
| --- | --- |
| 标识字段 | 聚合标识，作为文档 `_id` |
| 枚举双写 | code（`getValue()`）与文案（`name()`）同时落库，避免查询侧枚举反查 |
| 数值以最小单位整数承载 | 金额以「分」等整数单位存储（如 `scaled_float` 语义），聚合内保留业务值对象 |
| 嵌套对象 | 有强归属的子结构扁平化嵌入（如无跨字段约束，不引入 `nested`） |
| 派生字段 | 冗余查询辅助字段（聚合列表 / 拼接文本等），由投影器计算 |

> 示例：订单投影 `OrderEsProjection` 含 `status` / `statusName`（枚举双写）、`totalAmount`（分）、`customer` / `orderItems`（扁平对象）、`itemProductNames` / `itemProductNamesText`（商品名检索派生字段），对齐 ES 索引 `order_index` 的 Mapping。

### 3.1 派生字段在投影器内计算

派生字段（枚举文案、聚合列表、拼接文本等）全部在投影器 `project` 内由聚合计算，**不依赖存储内部脚本**（如 ES script）。

### 3.2 事件只携带聚合标识，不携带业务快照

数据同步事件只携带聚合标识（`version` 由框架在 `collectEvent` 时回填为 `getNewVersion()`），**不携带业务快照**。订阅方处理时**重新 `findById` 加载最新聚合**再投影物化——读模型反映的是事件处理时刻的最新状态，而不是事件产生时刻的旧状态。

```java
// ✅ 推荐：事件只携带聚合标识，处理时反查聚合根取权威状态
public void handleEvent(OrderDataSyncEvent event) {
    Order order = orderRepository.findById(Long.valueOf(event.getEntityId()));
    if (order == null) {
        return;
    }
    // resolveProjector → project → resolveMaterializer → materialize(projection, event.getVersion())
}

// ❌ 反模式：事件携带整份业务快照，延迟处理后会用旧数据覆盖新副本
```

::: tip 可以带少量路由 ID，但不要带快照
事件可以携带聚合标识与少量路由 / 上下文 ID（供订阅者定位聚合、路由分支），但**不要携带整份业务快照**——快照会过期、会随业务字段增长而膨胀，权威数据始终以聚合根为准。
:::

## 4. 组件的落地方式

按以下顺序逐个落地组件，即可得到可运行的投影读模型代码。

### 4.1 领域契约：4 个窄化接口

在领域层定义聚合专属接口，**只 `extends` 框架通用接口，不写任何实现逻辑**：

```java
// domain/order/projection/IOrderProjection.java
public interface IOrderProjection extends IAggregateProjection { }

// domain/order/projection/materializer/IOrderProjectionMaterializer.java
public interface IOrderProjectionMaterializer extends IProjectionMaterializer<OrderEsProjection> { }

// domain/order/projection/materializer/IOrderReadModelVersionResolver.java
public interface IOrderReadModelVersionResolver extends IReadModelVersionResolver<Long> { }

// domain/order/projection/materializer/IOrderReadModelResynchronizer.java
public interface IOrderReadModelResynchronizer extends IReadModelResynchronizer<Long> { }
```

> **为什么**：领域层清晰声明聚合读模型的物化 / 版本 / 补偿契约边界；基础设施只依赖领域专属接口，框架升级或存储替换时影响面收敛到基础设施层。

### 4.2 投影 DTO：`OrderEsProjection`

纯数据容器，用 Lombok `@Data` 简化（含嵌套 `@Data` 静态类）。只声明字段、对齐 Mapping，**不含计算逻辑**：

```java
@Data
public class OrderEsProjection implements IOrderProjection {
    private Long orderId;                 // 聚合标识，文档 _id
    private int status;
    private String statusName;
    private long totalAmount;
    private CustomerProjection customer;
    private List<OrderItemProjection> orderItems;

    @Data
    public static class CustomerProjection {
        private Long customerId;
        private String customerName;
    }

    @Data
    public static class OrderItemProjection {
        private Long itemId;
        private Long productId;
        private String productName;
        private long price;
        private int quantity;
        private long subtotal;
    }
}
```

> ⚠️ **投影用 `@Data`，聚合根禁用 `@Data`**：投影等同性由 Lombok 生成、纯属容器便利；聚合根等同性由 `AbstractEntity` 托管。嵌套子投影（`CustomerProjection` 等）不实现 `IOrderProjection`，只有聚合拓扑级投影实现。

### 4.3 投影器：`OrderEsProjector`

继承 `AbstractAggregateProjector<Order, OrderEsProjection>`，构造传入投影类型，只实现 `project`：

```java
public class OrderEsProjector extends AbstractAggregateProjector<Order, OrderEsProjection> {

    public OrderEsProjector(Class<OrderEsProjection> projectionType) {
        super(projectionType);
    }

    @Override
    public OrderEsProjection project(Order order) {
        OrderEsProjection projection = new OrderEsProjection();
        projection.setOrderId(order.getEntityId());
        projection.setStatus(order.getStatus().getValue());
        projection.setStatusName(order.getStatus().name());
        projection.setTotalAmount(toFen(order.getTotalAmount()));
        Optional.ofNullable(order.getCustomer()).ifPresent(customer -> {
            OrderEsProjection.CustomerProjection cp = new OrderEsProjection.CustomerProjection();
            cp.setCustomerId(customer.getCustomerId());
            cp.setCustomerName(customer.getCustomerName());
            projection.setCustomer(cp);
        });
        return projection;
    }

    private long toFen(Money money) {
        return Optional.ofNullable(money)
                .map(Money::getAmount)
                .map(value -> value.multiply(java.math.BigDecimal.valueOf(100)).longValue())
                .orElse(0L);
    }
}
```

编写规则：

- **字段映射全部手写**：框架不提供反射式默认映射。
- **派生字段在 `project` 内计算**：如 `itemProductNames = items.stream().map(...).toList()`、`itemProductNamesText = String.join(" ", itemProductNames)`。
- **单位转换在投影器完成**：`Money.amount` → 分（`long`）。
- **可空嵌套值用 Optional**：`customer` / `shippingAddress` / `logisticsInfo` 用 `Optional.ofNullable(...).ifPresent(...)`，符合项目「控制流判断优先 Optional」约定。
- **不返回 `null`**：本聚合始终满足投影条件。
- **纯映射、无状态单例**：可独立单测，不持有存储客户端。

> ⚠️ **`project` 不含任何存储细节**：存储读写只存在于物化器；投影器不知道「写入哪个索引、如何控制版本」。

### 4.4 物化器：`OrderEsMaterializer`

实现 `IOrderProjectionMaterializer`，注入既有存储客户端（ES 场景为 `ElasticsearchClient`）：

```java
@Component
public class OrderEsMaterializer implements IOrderProjectionMaterializer {

    private final ElasticsearchClient elasticsearchClient;

    public OrderEsMaterializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public void materialize(OrderEsProjection projection, long version) {
        elasticsearchClient.index(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                .id(projection.getOrderId().toString())
                .versionType(VersionType.External)
                .version(version)
                .document(projection));
    }

    @Override
    public void purge(Object aggregateId) {
        elasticsearchClient.delete(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                .id(aggregateId.toString()));
    }
}
```

编写规则：

- `projectionType()` 返回 `OrderEsProjection.class`；`target()` 返回 `OrderEsTargets.TARGET_ES_ORDERS`。
- **版本控制用 External 版本**：写模型版本 V 落到副本版本元数据（ES 为 `_version`），副本落后时存储拒绝写入并抛版本冲突异常。
- **异常不吞掉**：用 `@SneakyThrows` 上抛 checked 异常，版本冲突交给对账 `resync` 兜底。
- `purge` 删除文档，文档不存在时静默成功。

> ⚠️ **`target()` 是 `ReconciliationTarget` 的唯一权威来源**：Registry 按 `materializer.target()` 登记；业务方不要自行 `new ReconciliationTarget`，应引用 `OrderEsTargets.TARGET_ES_ORDERS`，否则 Registry 中 key 不一致导致寻址失败。

### 4.5 目标常量：`OrderEsTargets`

索引名与对账目标收口到一处，写入 / 读取 / 对账全部引用同一常量：

```java
public final class OrderEsTargets {
    public static final String ORDER_INDEX_NAME = "order_index";
    public static final ReconciliationTarget TARGET_ES_ORDERS =
            new ReconciliationTarget(Order.class, "es:orders");
    private OrderEsTargets() { }
}
```

### 4.6 装配：`OrderProjectionConfig`

`@Configuration` 在**构造期登记**全部构件并产出门面 Bean（保证 Bean 一旦注入即已完成登记）：

```java
@Configuration
public class OrderProjectionConfig {

    @Bean
    public OrderEsProjector orderEsProjector() {
        return new OrderEsProjector(OrderEsProjection.class);
    }

    @Bean
    public OrderEsMaterializer orderEsMaterializer(ElasticsearchClient elasticsearchClient) {
        return new OrderEsMaterializer(elasticsearchClient);
    }

    @Bean
    public ProjectorRegistry projectorRegistry(
            OrderEsProjector orderEsProjector,
            OrderEsMaterializer orderEsMaterializer) {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(Order.class, orderEsProjector);      // (聚合类型, 投影器)
        registry.register(orderEsMaterializer);                 // (物化器，自带 target)
        return registry;
    }

    @Bean
    public ReconciliationRegistry reconciliationRegistry(
            OrderRepository orderRepository,
            OrderEsVersionResolver orderEsVersionResolver,
            OrderEsResynchronizer orderEsResynchronizer) {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerRepository(Order.class, orderRepository);
        registry.registerResolver(OrderEsTargets.TARGET_ES_ORDERS, orderEsVersionResolver);
        registry.registerResynchronizer(OrderEsTargets.TARGET_ES_ORDERS, orderEsResynchronizer);
        return registry;
    }

    @Bean
    public AggregateProjectorSupport aggregateProjectorSupport(ProjectorRegistry projectorRegistry) {
        return new AggregateProjectorSupport(projectorRegistry);
    }
}
```

### 4.7 事件订阅：应用层编排 + 绑定

事件路径**绕过 `AggregateProjectorSupport.sync`**（其版本口径为 `getOldVersion()`），由应用层实现经 `ProjectorRegistry` 直连编排，以使用事件携带的 `event.getVersion()`（= `getNewVersion()`）：

```java
@Component
public class OrderDataSyncEsProjectionHandle implements IOrderDataSyncEsProjectionHandle {

    private final OrderRepository orderRepository;
    private final ProjectorRegistry projectorRegistry;

    public OrderDataSyncEsProjectionHandle(
            OrderRepository orderRepository,
            ProjectorRegistry projectorRegistry) {
        this.orderRepository = orderRepository;
        this.projectorRegistry = projectorRegistry;
    }

    @Override
    public void handleEvent(OrderDataSyncEvent event) {
        Order order = orderRepository.findById(Long.valueOf(event.getEntityId()));
        if (order == null) {
            return;
        }
        IAggregateProjector<Order, OrderEsProjection> projector =
                projectorRegistry.resolveProjector(Order.class, OrderEsProjection.class);
        if (projector == null) {
            return;
        }
        OrderEsProjection projection = projector.project(order);
        if (projection == null) {
            return;
        }
        IProjectionMaterializer<OrderEsProjection> materializer =
                projectorRegistry.resolveMaterializer(OrderEsProjection.class, OrderEsTargets.TARGET_ES_ORDERS);
        if (materializer == null) {
            return;
        }
        materializer.materialize(projection, event.getVersion());
    }
}
```

领域契约 `IOrderDataSyncEsProjectionHandle extends IDomainService, IHandle<OrderDataSyncEvent>`（标注 `@DomainService(category = EVENT_SUBSCRIBER)`）定义于 `domain/order/service/`，仅声明意图；应用层实现负责把领域事件与基础设施构件**组装编排**。

订阅绑定在 `OrderEventSubscriberRegistry`（非 Spring 事件总线环境必须显式注册）：

```java
@Configuration
public class OrderEventSubscriberRegistry {
    public OrderEventSubscriberRegistry(IEventRegistry evtManager,
                                        OrderDataSyncEsProjectionHandle orderDataSyncEsProjectionHandle) {
        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, orderDataSyncEsProjectionHandle);
    }
}
```

## 5. 事件物化 vs 对账补偿

投影副本经两条路径维护，**转换逻辑共用** `ProjectorRegistry`。

### 5.1 事件物化（正常路径）

```text
Order 业务方法 → markModified() / markCreated()
  └─ triggerDataSyncHook() → collectEvent(OrderDataSyncEvent.buildEvent(order))
       └─ IEventRegistry 订阅("es", OrderDataSyncEvent.class, OrderDataSyncEsProjectionHandle)
            └─ handleEvent(event)
                 ├─ orderRepository.findById(id)
                 ├─ projectorRegistry.resolveProjector(Order.class, OrderEsProjection.class) → project
                 ├─ projectorRegistry.resolveMaterializer(OrderEsProjection.class, TARGET_ES_ORDERS)
                 └─ materialize(projection, event.getVersion())   // version = getNewVersion()
```

### 5.2 对账补偿（兜底路径）

```text
ReconciliationManager.reconcile(Order.class, id)
  ├─ OrderEsVersionResolver.resolve(id)     读副本版本 → V'（不存在/未追踪返回 -1）
  ├─ IRepository.currentVersion(id)         → V
  ├─ Reconciliation.of(V', V)               判定 CONSISTENT / STALE / ORPHAN / UNTRACKED
  └─ OrderEsResynchronizer
       ├─ resync(id)（STALE）：findById → project → materialize(projection, getOldVersion())
       └─ purge(id)（ORPHAN）：materializer.purge(id)
```

### 5.3 对比

| 维度 | 事件物化 | 对账补偿 |
| --- | --- | --- |
| 触发 | 领域事件（正常更新） | 调度 / 延迟消息 / 手动 |
| 版本来源 | `event.getVersion()`（= `getNewVersion()`） | `order.getOldVersion()`（与 `currentVersion` 一致） |
| 转换逻辑 | `ProjectorRegistry` 解析 + 应用层编排 | `ProjectorRegistry` 解析 |
| 目的 | 更新副本 | 副本落后 / 残留时重建或清理 |

> ⚠️ **`resync` 必须从写模型当前快照重建**（`findById` → `project` → `materialize`），而非重放那条被漏消费的事件——丢失的事件已不在事件流里，重放单条事件无法补齐副本。

## 6. 版本语义

- **V（写模型权威版本）**：事件路径来自 `event.getVersion()`（`collectEvent` 回填的 `getNewVersion()`）；resync 路径来自 `order.getOldVersion()`。
- **V'（副本版本）**：存储中的副本版本元数据（ES 为 `_version`），由版本解析器读取。
- **判定纯函数**：`Reconciliation.of(V', V)` —— `V'<0` → `UNTRACKED`；`V<0` 且 `V'≥0` → `ORPHAN`；`V'≥V` → `CONSISTENT`；否则 `STALE`。

> ⚠️ **External 版本约束（ES 场景）**：启用后不可依赖 ES 内部自增 `_version`；首次写入 `_version` 需与聚合新建版本对齐。版本解析器对「文档不存在 / 版本缺失」返回 `-1`（`UNTRACKED`）；传输层异常经 `@SneakyThrows` 上抛，**不会**被转换为 `-1`——「副本缺失」与「存储不可达」语义不同。

## 7. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 领域包引入存储客户端 / Spring 依赖 | 契约层与存储耦合、无法替换存储 | 领域只定义契约与 DTO，存储实现在 infrastructure |
| 把聚合根当投影返回 | 泄露写模型内部结构、破坏读写边界 | 定义中立投影 DTO，投影器裁剪字段 |
| 投影器内做存储读写 / 版本控制 | 投影器无法单测、存储细节扩散 | 投影器纯映射；持久化只在物化器 |
| 事件携带业务快照 | 延迟处理用旧数据覆盖新副本 | 事件只带聚合标识，处理时重新 load 聚合 |
| 物化失败 `catch` 后静默吞掉 | 副本落后被掩盖、对账失效 | 异常上抛，交给 `resync` 补偿 |
| 业务方自行 `new ReconciliationTarget` | Registry 中 key 不一致、寻址失败 | 引用 `OrderEsTargets` 已定义的 target 常量 |
| 绕过 Registry 手写投影更新 | 事件路径与 resync 逻辑不一致 | 共用 `ProjectorRegistry` / `AggregateProjectorSupport` |
| resync 重放单条事件 | 丢失的事件无法补齐副本 | 从写模型当前快照重建（findById → project → materialize） |
| 投影用聚合根的 Lombok 约定 | 数据容器被 @Builder 等污染 | 投影 DTO 用 `@Data`；聚合根禁用 `@Data` |

---

## 下一步

- [投影读模型](../core/projection-read.md)：框架 `repository.query` / `repository.reconciliation` 通用能力详解
- [仓储写模型](../core/repository-write.md)：聚合持久化、`currentVersion` 权威版本 V
- [聚合设计原则](./aggregate-design.md)：聚合根 `triggerDataSyncHook` 与事件收集
- [Elasticsearch 配置设计原则](./elasticsearch-config.md)：`ElasticsearchClient` 三层客户端构建与投影物化配套
- [事件建模指南](./event-modeling.md)：事件只携带聚合标识的建模规范
