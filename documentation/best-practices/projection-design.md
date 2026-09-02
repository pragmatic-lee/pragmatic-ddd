# 投影读模型代码落地指南

> 本文档说明投影读模型代码**按本次落地的方式怎么写**，是可复用的通用指导原则：从包结构与命名，到每个组件的手写规则，再到事件物化、对账补偿与读侧检索的衔接。下文以订单 ES 投影（`Order`）为示例贯穿全文；其他模块做类似的投影设计时，套用本文档的结构与规则，把 `Order` 换成目标聚合、`Es` 换成目标存储即可。

## 1. 投影读模型的本质

投影读模型把聚合（写模型）映射为异构存储中的文档（读模型），为「面向查询的聚合检索」场景提供**独立读视图**，与写模型的聚合根装配完全解耦。

核心特征：

- **中立读视图**：投影 DTO 只声明字段、对齐存储 Mapping，不含存储与查询逻辑。
- **纯映射**：投影器只做字段取值 / 裁剪 / 派生，不接触存储客户端。
- **物化分离**：物化器只负责「投影 → 存储」的写入与版本控制，不知道字段如何派生。
- **检索对称**：检索器只负责「存储 → 投影」的读回，与物化器方向相反、互不重叠。
- **版本对账**：存储中的副本版本 V' 与写模型版本 V 比较，落后（STALE）重建、残留（ORPHAN）清理。

**写模型零改动**：聚合根不持有任何存储依赖，只在仓储落库前经 `triggerDataSyncHook()` 发出数据同步事件；投影的装配与存储细节全部落在领域契约与基础设施实现中。

### 1.1 读侧四个角色

读模型一侧由四个角色协作，职责边界不可越界：

| 角色 | 方向 | 职责 | 不负责 |
| --- | --- | --- | --- |
| 投影器 `Projector` | 聚合 → 投影 | 字段取值 / 裁剪 / 派生（含按需的单位换算） | 存储读写、版本控制 |
| 源 `Source` | 投影 → 存储（写）、存储 → 投影（读） | 写读一体：materialize / purge、绑定检索器与裁剪器、external 版本控制 | 字段派生（由投影器负责）、条件翻译（由检索器负责） |
| 检索器 `Searcher` | 存储 → 索引级全量投影 | 条件翻译、查询、分页 / 滚动、游标 | 字段裁剪、层级重排、派生 |
| 裁剪器 `Reducer` | 索引级全量投影 → 业务子投影 | 字段裁剪 / 层级重排 / 派生（Java 内存） | 存储访问、条件翻译、分页 |
| 门面 `IOrderQuery` | 读侧入口 | 选路 + 查全量 + 裁剪 三跳编排 | 直接持有存储客户端 |

## 2. 包结构与命名规范

### 2.1 分层：领域定义契约，基础设施提供实现，应用层组装编排

```text
domain/order/projection/                       领域：读模型视图 + 条件族 + 寻址（无存储/Spring 依赖）
  ├── IOrderProjection                         extends IAggregateProjection（投影 sealed 体系基类）
  ├── IOrderQuery                              extends IAggregateQuery<...>（读侧能力组合）
  ├── OrderEsProjection                        @Data 数据容器，implements IOrderProjection
  ├── OrderSummaryProjection                   @Data 数据容器，implements IOrderProjection
  ├── OrderEsTargets                           ORDER_INDEX_NAME / TARGET_ES_ORDERS 常量
  ├── query/                                   领域：三族查询条件（sealed interface + record）
  │   ├── OrderOneQuery                        extends OneQueryCriteria
  │   ├── OrderListQuery                       extends ListQueryCriteria
  │   └── OrderPageQuery                       extends PageQueryCriteria
  ├── reducer/                                 领域：裁剪专属契约（窄化框架接口）
  │   └── IOrderSummaryReducer                 extends IProjectionReducer<OrderEsProjection, OrderSummaryProjection>
  └── materializer/                            领域：版本/补偿 专属契约（窄化框架接口）
      ├── IOrderReadModelVersionResolver       extends IReadModelVersionResolver<Long>
      └── IOrderReadModelResynchronizer        extends IReadModelResynchronizer<Long>

infrastructure/order/projection/               基础设施：聚合 → 视图 纯映射 + 读侧检索
  ├── OrderEsProjector                         extends AbstractAggregateProjector<Order, OrderEsProjection>
  ├── OrderQuery                               extends AbstractProjectionQuery（基类承载选源 + 查全量 + 裁剪 三跳）
  ├── OrderByIdSearcher                        implements IProjectionByIdSearcher<OrderEsProjection>
  ├── OrderOneSearcher                         implements IProjectionSearcher<OrderOneQuery, OrderEsProjection>
  ├── OrderListSearcher                        implements IProjectionSearcher<OrderListQuery, OrderEsProjection>
  └── OrderPageSearcher                        implements IProjectionPagedSearcher<OrderPageQuery, OrderEsProjection>
infrastructure/order/projection/reducer/       基础设施：索引级全量投影 → 业务子投影（Java 内存）
  └── OrderSummaryReducer                      implements IOrderSummaryReducer（领域契约）
infrastructure/order/projection/materializer/  基础设施：写读一体的源（继承框架基类）
  ├── OrderEsSource                           extends AbstractProjectionSource<Order, OrderEsProjection>
  ├── OrderRedisSource                        extends AbstractProjectionSource<Order, OrderCacheProjection>
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
public interface IOrderQuery extends IAggregateQuery<
        Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery> { }

// ✅ 推荐：视图载体与实现以 OrderEs* 标明聚合与存储；写读一体落在源（继承框架基类，不再定义领域专属 materializer 接口）
public class OrderEsProjector extends AbstractAggregateProjector<Order, OrderEsProjection> { }
public class OrderEsSource extends AbstractProjectionSource<Order, OrderEsProjection> { }

// ❌ 反模式：基础设施直接实现框架通用接口，领域层无专属契约、替换存储需改基础设施与框架的直接契约
// （旧版曾定义 IOrderProjectionMaterializer extends IProjectionMaterializer<...>，现源已直接继承框架基类，无需领域层 materializer 接口）
```

> **命名约定**：接口一律 `I` 开头；领域专属接口以 `Order` 前缀区分框架通用接口（`IOrderProjection` / `IOrderQuery` 等，不含存储标记）；实现类不用 `Impl` 后缀，用 `OrderEs*`（`OrderEsProjector` / `OrderEsSource` / `OrderEsVersionResolver` / `OrderEsResynchronizer`）与 `OrderRedis*`（`OrderRedisSource`）标明聚合与存储；条件族以 `Order{One|List|Page}Query` 命名、族内场景为 `record`；检索器以 `Order{ById|One|List|Page}Searcher` 命名；裁剪器以 `Order{Target}Reducer` 命名（如 `OrderSummaryReducer`）；源以 `Order{Store}Source` 命名；对账目标常量集中在 `OrderEsTargets` / `OrderCacheTargets`。
>
> ⚠️ **索引级全量投影的命名要体现「存储文档形状」而非「业务用途」**：它对齐的是物理索引 Mapping，本质上是存储契约在 Java 侧的镜像。`OrderEsProjection`（索引 `order_index` 的全量文档）是好名字；`OrderProjection` 这类不带存储标记的名字会与业务投影混淆，无法区分「哪个是索引级、哪个是裁剪产物」。

## 3. 投影承载的数据

投影字段对齐存储 Mapping，既含聚合原始字段，也含查询辅助的派生字段。**字段取舍由「读侧要查什么」决定，而不是「写模型有什么」**。

字段设计规则：

| 规则 | 说明 |
| --- | --- |
| 标识字段 | 聚合标识，作为文档 `_id` |
| 枚举双写 | code（`getValue()`）与文案（`name()`）同时落库，避免查询侧枚举反查 |
| 金额 / 数值**默认原样承载** | 与聚合字段保持同一单位与精度，**不做隐式单位换算**；仅当存储 Mapping 有明确要求时才按需转换 |
| 嵌套对象 | 有强归属的子结构扁平化嵌入（如无跨字段约束，不引入 `nested`） |
| 派生字段 | 冗余查询辅助字段（聚合列表 / 拼接文本等），由投影器计算 |

::: tip 金额单位：默认原样，不强制转「分」
投影字段的金额**默认与聚合字段同单位、同精度**（聚合是 `Money`/`BigDecimal` 就照原值承载），框架不要求、也不应强制转成「分」。

是否换算**只取决于存储 Mapping 的实际情况**：

| 情况 | 做法 |
| --- | --- |
| 存储字段与聚合字段单位一致（常见） | **原样承载**，不做任何换算 |
| 存储 Mapping 用整数最小单位（如 `scaled_float` 存分） | 在投影器内显式换算，并注明原因 |

本示例属于后者——`order_index` 的 Mapping 把金额存为分，故投影器内有 `toFen`；这不是框架约束，换成别的存储或别的 Mapping 就不必如此。

换算一旦发生，必须在投影器与裁剪器之间**保持一致**：投影写什么单位，裁剪就读什么单位，不要在裁剪器里二次换算。
:::

> 示例：订单投影有两个形态——`OrderEsProjection`（详情投影，含 `status` / `statusName` 枚举双写、`totalAmount`（因 Mapping 存分故换算为 `long`，见上）、`customer` / `shippingAddress` / `logisticsInfo` / `orderItems` 扁平对象、`itemProductNames` / `itemProductNamesText` 商品名检索派生字段，对齐 ES 索引 `order_index` Mapping）与 `OrderSummaryProjection`（概要投影，仅含列表展示所需字段）。

### 3.1 同一聚合的多种投影形态

一个聚合可以有多个投影形态，全部实现同一个领域投影接口，由调用方用 `Class<X>` 显式选择：

```java
// ✅ 推荐：一个聚合多种投影，共用同一个领域投影接口，按 projectionType 选择
public interface IOrderProjection extends IAggregateProjection { }        // sealed 体系基类
public class OrderEsProjection implements IOrderProjection { }            // 详情投影
public class OrderSummaryProjection implements IOrderProjection { }       // 概要投影
```

> ⚠️ **核心约束：检索器绑定「索引级全量投影」，不是业务投影，也不是投影体系接口**。
>
> `projectionType()` 返回的是**对齐某个物理存储索引文档形状的具体类**（示例为 `OrderEsProjection.class`，对应 `order_index`），它描述的是「存储里长什么样」，而不是「调用方想要什么」。调用方想要的业务子投影由裁剪器在 Java 内存中产出（见 §4.9）。
>
> Registry 以 `Class` **精确键匹配**（`Map.get`），**不做 `isAssignableFrom` 向上查找**。因此：
>
> | 登记侧 | 查询侧 | 结果 |
> | --- | --- | --- |
> | `OrderEsProjection.class` | `OrderEsProjection.class` | ✅ 命中 |
> | `IOrderProjection.class`（接口） | `OrderEsProjection.class`（子类型） | ❌ 抛 `ProjectionSearcherNotFoundException` |
>
> 这是最容易踩的坑：登记时用投影体系接口「图省事」，运行时必然 miss，且编译期完全看不出来。
>
> 同一聚合若有**多套物理索引**（如详情索引 A、概要索引 B），则同一条件族下应有**两个检索器**，分别绑定各自索引的全量投影——键的第二维不同，互不冲突。

### 3.2 派生字段在投影器内计算

派生字段（枚举文案、聚合列表、拼接文本等）全部在投影器 `project` 内由聚合计算，**不依赖存储内部脚本**（如 ES script）。

单位换算属于**可选的**派生处理：默认原样承载，仅当存储 Mapping 要求不同单位时才做，且应集中在一个具名方法内并注明原因，不要散落在字段赋值语句中。

### 3.3 事件只携带聚合标识，不携带业务快照

数据同步事件只携带聚合标识（`version` 由框架在 `collectEvent` 时回填为 `getNewVersion()`），**不携带业务快照**。订阅方处理时**重新 `findById` 加载最新聚合**再投影物化——读模型反映的是事件处理时刻的最新状态，而不是事件产生时刻的旧状态。

```java
// ✅ 推荐：事件只携带聚合标识，处理时反查聚合根取权威状态
public void handleEvent(OrderDataSyncEvent event) {
    Order order = orderRepository.findById(Long.valueOf(event.getEntityId()));
    if (order == null) {
        return;
    }
    // aggregateProjectorSupport.sync(order, OrderEsTargets.TARGET_ES_ORDERS)  // 内部 resolveProjector → project → 源.materialize(projection, event.getVersion())
}

// ❌ 反模式：事件携带整份业务快照，延迟处理后会用旧数据覆盖新副本
```

::: tip 可以带少量路由 ID，但不要带快照
事件可以携带聚合标识与少量路由 / 上下文 ID（供订阅者定位聚合、路由分支），但**不要携带整份业务快照**——快照会过期、会随业务字段增长而膨胀，权威数据始终以聚合根为准。
:::

## 4. 组件的落地方式

按以下顺序逐个落地组件，即可得到可运行的投影读模型代码。

### 4.1 领域契约：投影接口 + 物化窄化接口

在领域层定义聚合专属接口，**只 `extends` 框架通用接口，不写任何实现逻辑**：

```java
// domain/order/projection/IOrderProjection.java
public interface IOrderProjection extends IAggregateProjection { }

// domain/order/projection/materializer/IOrderReadModelVersionResolver.java
public interface IOrderReadModelVersionResolver extends IReadModelVersionResolver<Long> { }

// domain/order/projection/materializer/IOrderReadModelResynchronizer.java
public interface IOrderReadModelResynchronizer extends IReadModelResynchronizer<Long> { }
```

> **为什么**：领域层清晰声明聚合读模型的版本 / 补偿契约边界；写读一体的「源」（`AbstractProjectionSource` 子类）落在基础设施层，直接继承框架基类、不再定义领域专属 materializer 接口。基础设施只依赖领域专属接口与框架基类，存储替换时影响面收敛到基础设施层。

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
    private List<String> itemProductNames;      // 派生：明细项商品名列表
    private String itemProductNamesText;        // 派生：商品名拼接文本，供模糊检索

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
>
> ⚠️ **投影中枚举降级为基础类型**：投影字段不使用枚举类型，`status` 用 `int`、`statusName` 用 `String` 双写承载。投影是跨进程传输的存储文档，绑定枚举类型会让文档反序列化与枚举演进互相牵制。

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
        // 仅因本示例 order_index 的 Mapping 把金额存为分才换算；默认应原样承载
        projection.setTotalAmount(toFen(order.getTotalAmount()));
        Optional.ofNullable(order.getCustomer()).ifPresent(customer -> {
            OrderEsProjection.CustomerProjection cp = new OrderEsProjection.CustomerProjection();
            cp.setCustomerId(customer.getCustomerId());
            cp.setCustomerName(customer.getCustomerName());
            projection.setCustomer(cp);
        });
        // 明细项映射后派生检索辅助字段
        List<OrderEsProjection.OrderItemProjection> items = order.getOrderItems().getAllItems().stream()
                .map(this::toItemProjection)
                .toList();
        projection.setOrderItems(items);
        List<String> productNames = items.stream()
                .map(OrderEsProjection.OrderItemProjection::getProductName)
                .toList();
        projection.setItemProductNames(productNames);
        projection.setItemProductNamesText(String.join(" ", productNames));
        return projection;
    }

    /**
     * 金额换算为分。
     *
     * <p>非必需：仅当存储 Mapping 以整数最小单位承载金额时才需要。
     * 若投影字段与聚合字段同单位，直接原样取值即可，不要引入本方法。</p>
     */
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
- **金额默认原样承载**：与聚合字段同单位、同精度，不做换算。只有存储 Mapping 明确要求不同单位时，才在投影器内显式换算并注明原因（如上例 `toFen`）。
- **派生字段在 `project` 内计算**：如 `itemProductNames = items.stream().map(...).toList()`、`itemProductNamesText = String.join(" ", itemProductNames)`。
- **可空嵌套值用 Optional**：`customer` / `shippingAddress` / `logisticsInfo` 用 `Optional.ofNullable(...).ifPresent(...)`，符合项目「控制流判断优先 Optional」约定。
- **不返回 `null`**：本聚合始终满足投影条件。
- **纯映射、无状态单例**：可独立单测，不持有存储客户端。

> ⚠️ **不要在裁剪器里二次换算金额**。单位换算只发生在「聚合 → 索引级全量投影」这一次；裁剪器读的是投影字段，应与投影保持同一单位。两处都换算会导致重复进位。

> ⚠️ **`project` 不含任何存储细节**：存储读写只存在于物化器与检索器；投影器不知道「写入哪个索引、如何控制版本、如何检索」。

### 4.4 源：`OrderEsSource`

写读一体收敛到**源**（`AbstractProjectionSource` 子类），注入投影器、检索器、裁剪器与存储客户端（ES 场景为 `ElasticsearchClient`）。`super` 第 5 参注入了 byId 检索器，其余检索器与裁剪器以 `bind` 挂载：

```java
@Component
public class OrderEsSource extends AbstractProjectionSource<Order, OrderEsProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderEsSource(
            OrderEsProjector projector,
            OrderByIdSearcher byIdSearcher,
            OrderOneSearcher oneSearcher,
            OrderListSearcher listSearcher,
            OrderPageSearcher pageSearcher,
            OrderSummaryReducer summaryReducer,
            ElasticsearchClient elasticsearchClient) {
        super(OrderEsTargets.TARGET_ES_ORDERS, Order.class, OrderEsProjection.class, projector, byIdSearcher);
        bind(oneSearcher);
        bind(listSearcher);
        bind(pageSearcher);
        bind(summaryReducer);
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public void materialize(OrderEsProjection projection, long version) {
        try {
            elasticsearchClient.index(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .id(projection.getOrderId().toString())
                    .versionType(VersionType.External)
                    .version(version)
                    .document(projection));
        } catch (ResponseException ex) {
            // external 版本不前进（迟到/重复事件）时 ES 返回 409，按乐观锁语义静默丢弃
            log.debug("订单 ES 投影物化被版本冲突忽略，orderId={}, version={}", projection.getOrderId(), version);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void purge(Object aggregateId) {
        try {
            elasticsearchClient.delete(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .id(aggregateId.toString()));
        } catch (ResponseException ignored) {
            // 文档可能不存在，清理时忽略删除异常
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
```

编写规则：

- `super(OrderEsTargets.TARGET_ES_ORDERS, Order.class, OrderEsProjection.class, projector, byIdSearcher)`：源标识即 `ProjectionSource` 串（同时也是读寻址与对账 target 的同名身份）；byId 检索器经构造器第 5 参注入（查询门面 `queryById` 需按源取 byId searcher）。
- 其他检索器与裁剪器以 `bind(...)` 挂载，源持有它们的引用。
- **版本控制用 External 版本**：写模型版本 V 落到副本版本元数据（ES 为 `_version`），副本落后时存储拒绝写入并抛 409 版本冲突。
- **区分失败**：409 版本冲突（迟到/重复事件）静默丢弃，仅 `log.debug`；真正的写失败（连接/映射错误）以 `IOException` 上抛，交给事件重试 / 对账 resync 兜底。
- `purge` 删除文档，文档不存在时静默忽略（清理幂等）。

> ⚠️ **源标识 `source()` 即 `ReconciliationTarget.storeId()` 的同一身份**：写侧 `AggregateProjectorSupport.sync(aggregate, source)` 与对账 resync 共享同源标识；业务方应引用 `OrderEsTargets.TARGET_ES_ORDERS`，不要自行 `new ProjectionSource("es:orders")`，否则 key 不一致导致寻址失败。

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

> ⚠️ **物化与检索必须命中同一物理索引**：写入侧引用 `ORDER_INDEX_NAME`，检索侧（`OrderByIdSearcher` / `OrderOneSearcher` 等）也引用同一常量。两侧各写字面量会在索引改名时只改一半。

### 4.6 查询条件族：三个 sealed interface

读侧条件按**族**划分，每族一个 `sealed interface` + 若干 `record` 场景，族间在编译期隔离：

```java
// domain/order/projection/query/OrderOneQuery.java —— 精确规约，字段全必填
public sealed interface OrderOneQuery extends OneQueryCriteria
        permits OrderOneQuery.LatestByCustomer {
    record LatestByCustomer(Long customerId) implements OrderOneQuery { }
}

// domain/order/projection/query/OrderListQuery.java —— 精确规约，字段全必填
public sealed interface OrderListQuery extends ListQueryCriteria
        permits OrderListQuery.TopByAmount,
                OrderListQuery.TopRecent {
    record TopByAmount(int top, Integer status, Long customerId) implements OrderListQuery { }
    record TopRecent(Long customerId, Integer status, int top) implements OrderListQuery { }
}

// domain/order/projection/query/OrderPageQuery.java —— 按需过滤，字段全 Optional
public sealed interface OrderPageQuery extends PageQueryCriteria
        permits OrderPageQuery.ByConditions {
    record ByConditions(
            Optional<Long> orderId,
            Optional<Integer> payStatus,
            Optional<Long> totalAmount,
            Optional<String> productName,
            Optional<Long> customerId) implements OrderPageQuery { }
}
```

三族语义对照：

| 族 | 父类 | 字段语义 | 对应方法 | 典型场景 |
| --- | --- | --- | --- | --- |
| `OrderOneQuery` | `OneQueryCriteria` | 全必填、精确匹配 | `queryOne` | 按客户取最新一单 |
| `OrderListQuery` | `ListQueryCriteria` | 全必填、精确匹配 + TOP N | `queryList` | 金额 TOP N、最近 TOP N |
| `OrderPageQuery` | `PageQueryCriteria` | 全 `Optional`、按需过滤 | `queryPage` / `queryScroll` | 后台多条件分页 |

编写规则：

- **族内扩展只加 `record` 并在 `permits` 登记**：新增场景后，所有 `instanceof` 分发点在编译期暴露未覆盖分支。
- **Page / Scroll 共用同一条件族**：二者语义同属「按需过滤」，不各自建族。
- **条件中枚举一律降级为基础类型**：`status` / `payStatus` 用 `Integer`，不用枚举类型。
- **条件不含分页参数**：分页由 `PageRequest` / `ScrollPosition` 单独传入，不塞进条件 `record`。
- **条件不含存储方言**：条件只表达业务意图（"商品名模糊匹配"），翻译成 `match` 还是 `wildcard` 由检索器决定。

> ⚠️ **重要约束：跨族传参在编译期报错**。三族父类（`OneQueryCriteria` / `ListQueryCriteria` / `PageQueryCriteria`）互不继承，只有共同的空标记父接口 `QueryCriteria`。把 `OrderListQuery` 传给 `queryPage` 无法编译——这是刻意的隔离设计，用于防止「精确规约」与「按需过滤」语义混用。

### 4.7 读侧能力组合：`IOrderQuery`

领域层用一行把 6 类查询能力组合成聚合专属契约：

```java
public interface IOrderQuery extends IAggregateQuery<
        Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery> {
}
```

泛型参数含义：

| 位置 | 参数 | 含义 |
| --- | --- | --- |
| 1 | `ID` | 聚合 ID 类型（`queryById` / `queryByIds`） |
| 2 | `P` | 投影基类，必须是 `IAggregateProjection` 子类型 |
| 3 | `ONE_QUERY` | `OneQueryCriteria` 子类型 |
| 4 | `LIST_QUERY` | `ListQueryCriteria` 子类型 |
| 5 | `PAGE_QUERY` | `PageQueryCriteria` 子类型，`queryPage` 与 `queryScroll` 共享 |

组合后得到的方法族（每个方法都额外接收 `Class<X> projectionType`）：

| 方法 | 来源 trait | 未命中行为 |
| --- | --- | --- |
| `queryById(id, type)` | `IQueryById` | 返回 `null` |
| `queryByIds(ids, type)` | `IQueryByIds` | 返回空列表 |
| `queryOne(query, type)` | `IQueryOne` | 返回 `null` |
| `queryList(query, type)` | `IQueryList` | 返回空列表 |
| `queryPage(query, pageRequest, type)` | `IQueryPage` | 返回空数据页 |
| `queryScroll(query, cursor, pageSize, type)` | `IQueryScroll` | `nextCursor == null` |

::: tip 只用到部分查询能力时
不需要全量组合，可以只 `extends` 需要的 trait（如只保留 `IQueryById` + `IQueryPage`），避免实现层被迫写空方法。
:::

### 4.8 读侧门面实现：`OrderQuery`

基础设施层继承 `AbstractProjectionQuery`，**三跳取数（选源 → 查全量 → 裁剪）由基类统一承载**，业务门面只声明聚合类型、投影顶层接口与三个条件族类型，不持有存储客户端、不写分流样板：

```java
@Service
public class OrderQuery extends AbstractProjectionQuery<Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery> {

    public OrderQuery(ProjectorRegistry registry) {
        super(registry, OrderOneQuery.class, OrderListQuery.class, OrderPageQuery.class);
    }
}
```

基类从 registry 取全量投影、按源定位检索器与裁剪器，自动完成三跳；调用方通过返回的源视图指定数据源：

```java
// 默认源（由 registerDefaultSource 决定）按主键取一个概要投影
OrderSummary summary = orderQuery.queryById(orderId, OrderSummary.class);

// 指定单源：直接打 Redis 源（仅支持 byId，调 queryList 抛 ProjectionSearcherNotFoundException）
OrderCacheProjection cache = orderQuery.source(OrderCacheTargets.TARGET_REDIS_ORDERS)
        .queryById(orderId, OrderCacheProjection.class);

// 回源链：先 Redis 未命中则推进 ES
OrderProjection full = orderQuery.fallbackChain(List.of(OrderCacheTargets.TARGET_REDIS_ORDERS, OrderEsTargets.TARGET_ES_ORDERS))
        .queryById(orderId, OrderEsProjection.class);
```

编写规则：

- **门面不含任何存储逻辑**：不注入 `ElasticsearchClient`，不拼查询 DSL，不手写 `reduceOne` / `resolveSourceType`。
- **构造器 `super(registry, oneType, listType, pageType)`**：三个条件族类型对应 `IQueryOne` / `IQueryList` / `IQueryPage`；`queryById` / `queryByIds` 复用源的 byId searcher（构造器第 5 参注入源），`queryScroll` 复用 page 族检索器。
- **条件族类型用族父类传入**：`OrderListQuery.class` 传的是**族 sealed 接口**，检索器按族登记、族内自行分发。
- **`source(X)` 指定单源**：若该源不挂对应条件族检索器，抛 `ProjectionSearcherNotFoundException`（信息含该源支持的条件族）。
- **`fallbackChain(List)` 回源链**：前源未命中（byId 返回 null / list 返回空）推进下一源；分页 / 滚动不回源，取链上第一个支持该条件族的原。
- **短路路径由基类处理**：目标即索引级全量投影时不重建 `PageResult` / `ScrollResult`、不重新拷贝列表，既省开销也保持对象同一性。

> ⚠️ **重要约束：`totalCount` 与 `nextCursor` 必须取自裁剪前的全量结果**。分页 / 滚动在检索器侧完成，裁剪只做逐条 `.map`、不改变集合规模。若误在裁剪后重新计算总数或游标，会得到错误的页边界与游标。

> ⚠️ **重要约束：`getSearcher` / `getPagedSearcher` / `getByIdSearcher` 未登记时抛异常，不是返回 `null`**。这与 `resolveProjector` / `resolveSource`（未登记返回 `null`）行为不同：检索器缺失属于「接线/配置缺失」，必须暴露。因此装配时必须 `bind` 全部检索器，否则首次查询即抛 `ProjectionSearcherNotFoundException`。

### 4.9 检索器：四个 ES Searcher

检索器是唯一接触存储客户端的读侧组件，**只产出索引级全量投影**（示例为 `OrderEsProjection`）。四个检索器按**注册键维度**分为两类。

> ⚠️ **检索器接口签名中的投影泛型是索引级全量投影，不是业务子投影**。下面所有 `OrderEsProjection` 都不能写成 `IOrderProjection`。

#### 4.9.1 按主键检索：`OrderByIdSearcher`

注册键为**一维** `(索引级投影类型)`，覆盖 `queryById` / `queryByIds`：

```java
@Component
public class OrderByIdSearcher implements IProjectionByIdSearcher<OrderEsProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderByIdSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<OrderEsProjection> projectionType() {
        return OrderEsProjection.class;
    }

    @Override
    public OrderEsProjection getById(Object id, Class<OrderEsProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> doGetById(id.toString(), projectionType), "getById");
    }

    @Override
    public List<OrderEsProjection> getByIds(List<Object> ids, Class<OrderEsProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> doGetByIds(ids, projectionType), "getByIds");
    }

    @SneakyThrows
    private OrderEsProjection doGetById(String id, Class<OrderEsProjection> projectionType) {
        return elasticsearchClient.get(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .id(id), projectionType).source();
    }

    @SneakyThrows
    private List<OrderEsProjection> doGetByIds(List<Object> ids, Class<OrderEsProjection> projectionType) {
        List<String> docIds = ids.stream().map(Object::toString).toList();
        Query query = Query.of(q -> q.ids(IdsQuery.of(i -> i.values(docIds))));
        return elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .size(docIds.size()), projectionType).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }
}
```

#### 4.9.2 按条件检索：`OrderOneSearcher` / `OrderListSearcher`

注册键为**二维** `(条件类型, 索引级投影类型)`，族内按 `instanceof` 分发：

```java
@Component
public class OrderListSearcher implements IProjectionSearcher<OrderListQuery, OrderEsProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderListSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<OrderListQuery> criteriaType() {
        return OrderListQuery.class;
    }

    @Override
    public Class<OrderEsProjection> projectionType() {
        return OrderEsProjection.class;
    }

    @Override
    public List<OrderEsProjection> search(OrderListQuery condition, Class<OrderEsProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> {
            if (condition instanceof OrderListQuery.TopByAmount c) {
                return searchTopByAmount(c, projectionType);
            }
            if (condition instanceof OrderListQuery.TopRecent c) {
                return searchTopRecent(c, projectionType);
            }
            return List.<OrderEsProjection>of();
        }, "search");
    }

    @SneakyThrows
    private List<OrderEsProjection> searchTopByAmount(
            OrderListQuery.TopByAmount condition, Class<OrderEsProjection> projectionType) {
        Query query = buildCustomerStatusQuery(condition.customerId(), condition.status());
        return elasticsearchClient.search(req -> req
                        .index(OrderEsTargets.ORDER_INDEX_NAME)
                        .query(query)
                        .sort(sort -> sort.field(f -> f.field("totalAmount").order(SortOrder.Desc)))
                        .size(condition.top()), projectionType).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }
    // searchTopRecent / buildCustomerStatusQuery 同构
}
```

#### 4.9.3 分页 / 滚动检索：`OrderPageSearcher`

同时实现 `searchPage` 与 `searchScroll`，**共用 `buildConditionQuery`**：

```java
@Component
public class OrderPageSearcher implements IProjectionPagedSearcher<OrderPageQuery, OrderEsProjection> {

    @Override
    public Class<OrderPageQuery> criteriaType() {
        return OrderPageQuery.class;
    }

    @Override
    public Class<OrderEsProjection> projectionType() {
        return OrderEsProjection.class;
    }

    @Override
    public PageResult<OrderEsProjection> searchPage(
            OrderPageQuery condition, PageRequest pageRequest, Class<OrderEsProjection> projectionType) {
        return ProjectionExceptions.retrieve(
                () -> doSearchPage(condition, pageRequest, projectionType), "searchPage");
    }

    @Override
    public ScrollResult<OrderEsProjection> searchScroll(
            OrderPageQuery condition, ScrollPosition cursor, int pageSize, Class<OrderEsProjection> projectionType) {
        return ProjectionExceptions.retrieve(
                () -> doSearchScroll(condition, cursor, pageSize, projectionType), "searchScroll");
    }

    @SneakyThrows
    private PageResult<OrderEsProjection> doSearchPage(
            OrderPageQuery condition, PageRequest pageRequest, Class<OrderEsProjection> projectionType) {
        Query query = buildConditionQuery(condition);
        var response = elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .sort(sort -> sort.field(f -> f.field("orderId").order(SortOrder.Desc)))
                .from(pageRequest.offset())          // PageRequest 已算好 (pageNumber-1)*pageSize
                .size(pageRequest.pageSize())
                .trackTotalHits(t -> t.enabled(true)), projectionType);
        List<OrderEsProjection> data = response.hits().hits().stream().map(Hit::source).toList();
        long total = Optional.of(response)
                .map(SearchResponse::hits)
                .map(HitsMetadata::total)
                .map(TotalHits::value)
                .orElse(0L);
        return PageResult.of(data, total, pageRequest);
    }

    @SneakyThrows
    private ScrollResult<OrderEsProjection> doSearchScroll(
            OrderPageQuery condition, ScrollPosition cursor, int pageSize, Class<OrderEsProjection> projectionType) {
        Query query = buildConditionQuery(condition);
        var response = elasticsearchClient.search(req -> {
            var b = req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .query(query)
                    .sort(sort -> sort.field(f -> f.field("orderId").order(SortOrder.Desc)))
                    .size(pageSize);
            if (!cursor.isInitial()) {              // 首次查询不设 searchAfter
                b.searchAfter(cursor.cursor());
            }
            return b;
        }, projectionType);
        List<Hit<OrderEsProjection>> hits = response.hits().hits();
        List<OrderEsProjection> data = hits.stream().map(Hit::source).toList();
        String nextCursor = hits.isEmpty() ? null : hits.get(hits.size() - 1).id();
        return ScrollResult.of(data, nextCursor);
    }

    private Query buildConditionQuery(OrderPageQuery condition) {
        List<Query> must = new ArrayList<>();
        if (condition instanceof OrderPageQuery.ByConditions c) {
            c.orderId().ifPresent(v -> must.add(term("orderId", v)));
            c.payStatus().ifPresent(v -> must.add(term("status", v)));
            c.totalAmount().ifPresent(v -> must.add(term("totalAmount", v)));
            c.customerId().ifPresent(v -> must.add(term("customer.customerId", v)));
            c.productName().ifPresent(v -> must.add(match("itemProductNamesText", v)));
        }
        return Query.of(q -> q.bool(BoolQuery.of(b -> b.must(must))));
    }
}
```

检索器编写规则：

| 规则 | 说明 |
| --- | --- |
| 一个检索器服务一个「条件族 × 索引级全量投影」 | 族内多个 `record` 场景在同一个检索器内分发；同一条件族若有多个物理索引，则一个索引一个检索器 |
| 只产出索引级全量投影 | 不产出业务子投影，也不按投影分支——业务子投影由裁剪器产出 |
| 条件翻译在检索器内 | 条件 → 存储查询 DSL 的翻译是存储方言，只在基础设施层出现 |
| `Optional` 字段用 `ifPresent` 追加 | 分页条件全 `Optional`，未传即不追加该 clause |
| 禁止空 `bool` | 全字段未传时 `must` 为空列表，需确认存储对空 `bool` 的行为，必要时补 `matchAll` |
| 返回值不返回 `null` | 列表检索未命中返回 `List.of()`；按主键单条未命中返回 `null`（与 `findById` 一致） |
| 分页 / 滚动在检索器内完成 | 裁剪只做逐条转换；`totalCount` 与 `nextCursor` 由检索器产出 |
| 用 `ProjectionExceptions` 包裹 | 见下节 |

### 4.10 检索异常处理：`ProjectionExceptions`

检索器内用 `ProjectionExceptions` 收敛样板，避免异常被重复嵌套：

```java
// 检索执行阶段（通信 / 反序列化失败）→ ProjectionRetrieveException，可重试
ProjectionExceptions.retrieve(() -> doSearchPage(...), "searchPage");

// 条件翻译阶段（条件非法 / 不支持）→ ProjectionConditionException，不可重试
ProjectionExceptions.translate(() -> buildConditionQuery(condition), "buildConditionQuery");
```

异常体系：

| 异常 | 语义 | 可重试 | 触发场景 |
| --- | --- | --- | --- |
| `ProjectionException` | 读侧检索域抽象基类，继承 `PragmaticException` | — | 可 `catch (PragmaticException)` 统一兜底 |
| `ProjectionRetrieveException` | 存储通信 / 远程错误 / 反序列化失败 | 是 | ES 超时、连接失败 |
| `ProjectionConditionException` | 条件无法翻译为该存储的检索请求 | 否 | 条件字段无对应索引、searcher 不支持该条件子类 |
| `ProjectionSearcherNotFoundException` | Registry 中无对应 searcher | 否 | 装配漏登记、登记键与查询键不一致 |
| `ProjectionReducerNotFoundException` | 无对应 reducer，或子投影未登记来源 | 否 | 装配漏登记裁剪器 |
| `ProjectionReducerConflictException` | 同一子投影存在多个来源 | 否 | 装配期登记冲突 |

> ⚠️ **重要约束：不要把 `ProjectionSearcherNotFoundException` / `ProjectionReducerNotFoundException` 当「业务上查不到」处理**。它们表示装配缺失，属于接线 bug，应当在启动自检中暴露，而不是被降级成空列表。`retrieve` 对已抛出的 `ProjectionException` 原样传递、不二次包装，因此调用方能准确区分「存储不可达」与「条件不支持」。

### 4.10.1 裁剪器契约与实现：`IOrderSummaryReducer` / `OrderSummaryReducer`

与物化器、版本解析器同构——**领域层定义专属契约，基础设施层实现领域契约**，不直接实现框架接口：

```java
// domain/order/projection/reducer/IOrderSummaryReducer.java —— 领域层专属契约
public interface IOrderSummaryReducer
        extends IProjectionReducer<OrderEsProjection, OrderSummaryProjection> {
}
```

索引级全量投影 → 业务子投影，在 Java 内存中完成字段裁剪、层级重排与派生计算：

```java
// infrastructure/order/projection/reducer/OrderSummaryReducer.java —— 实现领域契约
@Component
public class OrderSummaryReducer implements IOrderSummaryReducer {

    @Override
    public Class<OrderEsProjection> sourceType() {
        return OrderEsProjection.class;
    }

    @Override
    public Class<OrderSummaryProjection> projectionType() {
        return OrderSummaryProjection.class;
    }

    @Override
    public OrderSummaryProjection reduce(OrderEsProjection source) {
        if (source == null) {
            return null;
        }
        OrderSummaryProjection summary = new OrderSummaryProjection();
        summary.setOrderId(source.getOrderId());
        summary.setStatus(source.getStatus());
        summary.setStatusName(source.getStatusName());
        summary.setActualAmount(source.getActualAmount());
        summary.setCreatedAt(source.getCreatedAt());
        // 层级提升：ES 文档为 customer.customerName，概要投影为顶层字段
        Optional.ofNullable(source.getCustomer())
                .map(OrderEsProjection.CustomerProjection::getCustomerName)
                .ifPresent(summary::setCustomerName);
        return summary;
    }
}
```

编写规则：

- **领域层定义专属契约，基础设施层实现它**：`IOrderSummaryReducer extends IProjectionReducer<...>`，`OrderSummaryReducer implements IOrderSummaryReducer`。与 `IOrderReadModelVersionResolver` / `IOrderReadModelResynchronizer` 保持同构（领域层定契约、基础设施层实现），装配参数也声明为领域接口类型。
- **`reduce` 是纯函数**：无状态、无存储访问、无远程调用，可独立单测。
- **源为 `null` 返回 `null`**：由门面过滤，不在裁剪器内抛异常。
- **不改变集合规模**：一次只转换一条；分页 / 滚动在检索器侧完成。
- **可空嵌套值用 Optional**：与投影器保持一致的取值风格。
- **一个裁剪器服务一个 (源, 子) 组合**：新增子投影就新增裁剪器类，不在裁剪器内按目标类型分支。

> ⚠️ **为什么必须有裁剪器，而不是直接查子投影**：存储侧 `_source` 过滤只能**裁剪字段路径**，不能**改变字段层级**。示例中 `OrderSummaryProjection.customerName` 是顶层字段，而 ES 文档中该值位于 `customer.customerName` 嵌套路径——直接反序列化成概要投影会拿到 `null`。层级重排只能在 Java 内存中完成，这正是裁剪器存在的核心价值。

> ⚠️ **裁剪器不能复用 `IAggregateProjector`**：`IAggregateProjector<T, P>` 要求 `T extends AggregateRoot<?>`，而索引级全量投影是 `@Data` 数据容器、并非聚合根。二者是平级且互不替代的抽象——投影器是「聚合根 → 投影」，裁剪器是「全量投影 → 子投影」。

> ⚠️ **同一子投影只能有一个来源**：若同一子投影可从多个索引级投影裁剪而来，`register(reducer)` 在登记期即抛 `ProjectionReducerConflictException`。这不是限制，而是保护——多个来源会让门面无法确定该查哪个索引，必须在装配期就暴露。

### 4.11 装配：`OrderProjectionConfig`

`@Configuration` 在**构造期登记**全部构件并产出门面 Bean（保证 Bean 一旦注入即已完成登记）：

```java
@Configuration
public class OrderProjectionConfig {

    @Bean
    public OrderEsProjector orderEsProjector() {
        return new OrderEsProjector(OrderEsProjection.class);
    }

    @Bean
    public OrderEsSource orderEsSource(
            OrderEsProjector orderEsProjector,
            OrderByIdSearcher orderByIdSearcher,
            OrderOneSearcher orderOneSearcher,
            OrderListSearcher orderListSearcher,
            OrderPageSearcher orderPageSearcher,
            OrderSummaryReducer orderSummaryReducer,
            ElasticsearchClient elasticsearchClient) {
        return new OrderEsSource(orderEsProjector, orderByIdSearcher, orderOneSearcher,
                orderListSearcher, orderPageSearcher, orderSummaryReducer, elasticsearchClient);
    }

    @Bean
    public ProjectorRegistry projectorRegistry(OrderEsSource orderEsSource, OrderRedisSource orderRedisSource) {
        ProjectorRegistry registry = new ProjectorRegistry();

        // 写侧：projector 按 (聚合类型, 投影类型)；源按源标识登记（源内已 bind 检索器 / 裁剪器）
        registry.register(Order.class, orderEsSource.projector());
        registry.register(orderEsSource);
        registry.register(orderRedisSource);

        // 默认源：概要投影默认落在 ES 源（读默认源也走 ES）
        registry.registerDefaultSource(OrderSummaryProjection.class, orderEsSource.source());

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

`ProjectorRegistry` 登记键对照（源模型）：

| 构件 | 登记键 | 登记方法 | 解析方法 | 未登记 |
| --- | --- | --- | --- | --- |
| `IAggregateProjector` | `(聚合类型, 投影类型)` | `register(Class<T>, projector)` | `resolveProjector` | 返回 `null` |
| `AbstractProjectionSource` | `(源标识)` | `register(source)` | `resolveSource` / `sourceByProjection` | 返回 `null`（多源共存） |
| `IProjectionByIdSearcher` | `(索引级投影类型)` | 源构造器第 5 参 / `register(idSearcher)` | `getByIdSearcher` | 抛异常 |
| `IProjectionSearcher` | `(条件类型, 索引级投影类型)` | 源内 `bind(searcher)` / `register(searcher)` | `getSearcher` | 抛异常 |
| `IProjectionPagedSearcher` | `(条件类型, 索引级投影类型)` | 源内 `bind(pagedSearcher)` / `register(pagedSearcher)` | `getPagedSearcher` | 抛异常 |
| `IProjectionReducer` | `(索引级投影类型, 子投影类型)` | 源内 `bind(reducer)` / `register(reducer)` | `getReducer` | 抛异常 |

另有两类辅助登记，不按「构件」寻址：

| 调用 | 作用 | 未做时的影响 |
| --- | --- | --- |
| `register(源实例)` | 登记索引级全量投影源（写读一体，替代旧 `markSourceProjection`） | 门面无法选源，直查全量投影时抛 `ProjectionSourceNotFoundException` |
| `registerDefaultSource(子投影类, 源)` | 建立子投影 → 默认源的绑定 | 查该子投影未指定源且无歧义判断时无法选默认源 |

装配方法的裁剪器参数声明为**领域契约** `IOrderSummaryReducer`（而非实现类 `OrderSummaryReducer`），使配置层依赖领域层而非基础设施实现——替换实现时无需改动装配代码。

> ⚠️ **重要约束：`ProjectorRegistry` 是读写两侧的唯一同册**。同一个 `ProjectorRegistry` Bean 同时登记源（写读一体）与裁剪器（读侧），不要拆成多个 registry——「写入哪个索引」与「从哪个索引读回」必须同源于 `OrderEsTargets` / `OrderCacheTargets`。

> ⚠️ **重要约束：装配核心是登记源 + 默认源**。检索器与裁剪器在源构造器内 `bind`（或经构造器参数注入），无需在 registry 上逐个登记。漏登记检索器会在首次查询抛 `ProjectionSearcherNotFoundException`、漏登记 reducer 会让子投影选路失败抛 `ProjectionReducerNotFoundException`——建议在配置类里就近写注释或加启动自检。

> ⚠️ **同一全量投影可落到多个源**：`OrderEsProjection` 同时进 ES 源与 Redis 源是允许的（`sourceByProjection` 返回集合）；但两个源**不能共用同一 `source()` 串**，重复登记抛 `ProjectionSourceConflictException`。

### 4.12 事件订阅：应用层编排 + 绑定

事件路径经 `AggregateProjectorSupport.sync(aggregate, source)` 桥接——门面内部 project 后调源 `materialize`，以传入的 `event.getVersion()`（= `getNewVersion()`）作为 external 版本写入：

```java
@Component
public class OrderDataSyncEsProjectionHandle implements IOrderDataSyncEsProjectionHandle {

    private final OrderRepository orderRepository;
    private final AggregateProjectorSupport projectorSupport;

    public OrderDataSyncEsProjectionHandle(
            OrderRepository orderRepository,
            AggregateProjectorSupport projectorSupport) {
        this.orderRepository = orderRepository;
        this.projectorSupport = projectorSupport;
    }

    @Override
    public void handleEvent(OrderDataSyncEvent event) {
        Order order = orderRepository.findById(Long.valueOf(event.getEntityId()));
        if (order == null) {
            return;
        }
        projectorSupport.sync(order, OrderEsTargets.TARGET_ES_ORDERS);
    }
}
```

领域契约 `IOrderDataSyncEsProjectionHandle extends IDomainService, IHandle<OrderDataSyncEvent>`（标注 `@DomainService(category = EVENT_SUBSCRIBER)`）定义于 `domain/order/service/`，仅声明意图；应用层实现负责把领域事件与 `AggregateProjectorSupport` 门面**组装编排**。`sync` 内部按源标识取 `AbstractProjectionSource` 实例，缺失或投影为 `null` 时静默跳过。

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

## 5. 三条路径：写入、读取、对账

投影副本经三条路径维护，**转换逻辑共用** `ProjectorRegistry` 与源（`AbstractProjectionSource`）。

### 5.1 事件物化（写路径）

```text
Order 业务方法 → markModified() / markCreated()
  └─ triggerDataSyncHook() → collectEvent(OrderDataSyncEvent.buildEvent(order))
       └─ IEventRegistry 订阅("es", OrderDataSyncEvent.class, OrderDataSyncEsProjectionHandle)
            └─ handleEvent(event)
                 ├─ orderRepository.findById(id)
                 └─ aggregateProjectorSupport.sync(order, TARGET_ES_ORDERS)
                      ├─ registry.resolveProjector(Order.class, OrderEsProjection.class) → project
                      └─ 源 OrderEsSource.materialize(projection, event.getVersion())   // version = getNewVersion()
```

> `sync` 内部按 `TARGET_ES_ORDERS` 定位 `OrderEsSource` 实例，project 后调其 `materialize`；缺失或投影为 `null` 静默跳过。避免事件处理器内手写 project→materialize 双份逻辑。

### 5.2 读侧检索（读路径）

```text
调用方 → orderQuery.queryPage(OrderPageQuery.ByConditions, PageRequest.of(1, 20), OrderSummaryProjection.class)
  └─ AbstractProjectionQuery（三跳编排，第一维为源）
       ├─ 第 0 跳 选源：默认源（registerDefaultSource 决定）= OrderEsSource；或显式 source(X) / fallbackChain
       ├─ 第 1 跳 查全量：源上 getPagedSearcher(OrderPageQuery.class, OrderEsProjection.class)
       │    └─ OrderPageSearcher.searchPage(...)
       │         ├─ buildConditionQuery(condition)   // Optional 字段 → bool.must
       │         ├─ ES search（from = pageRequest.offset(), size = pageRequest.pageSize(), trackTotalHits）
       │         └─ PageResult.of(fullData, total, pageRequest)
       └─ 第 2 跳 裁剪：源上 getReducer(OrderEsProjection.class, OrderSummaryProjection.class)
            └─ OrderSummaryReducer.reduce(full) 逐条转换
                 └─ PageResult.of(summaryData, total /* 取自裁剪前 */, pageRequest)
```

若调用方传入的正是 `OrderEsProjection.class`（索引级全量投影），第 2 跳短路、直接复用检索结果。

> ⚠️ **重要约束：读路径不回源聚合根**。读模型是权威副本，读侧不允许为了「拿最新数据」而 `findById` 再投影——这会退化成同步阻塞调用，且绕过了副本。副本落后由对账路径修复，不由读路径补偿。

> ⚠️ **重要约束：分页在第 1 跳完成，第 2 跳只做逐条 `.map`**。`totalCount` 与 `nextCursor` 均取自裁剪前的全量结果；在裁剪后重新计算总数或游标会得到错误的页边界。

### 5.3 对账补偿（兜底路径）

```text
ReconciliationManager.reconcile(Order.class, id)
  ├─ OrderEsVersionResolver.resolve(id)     读副本版本 → V'（不存在/未追踪返回 -1）
  ├─ IRepository.currentVersion(id)         → V
  ├─ Reconciliation.of(V', V)               判定 CONSISTENT / STALE / ORPHAN / UNTRACKED
  └─ OrderEsResynchronizer（implements IOrderReadModelResynchronizer）
       ├─ resync(id)（STALE）：findById → aggregateProjectorSupport.sync(order, TARGET_ES_ORDERS)
       └─ purge(id)（ORPHAN）：aggregateProjectorSupport.purge(TARGET_ES_ORDERS, id)
```

### 5.4 对比

| 维度 | 事件物化 | 读侧检索 | 对账补偿 |
| --- | --- | --- | --- |
| 触发 | 领域事件（正常更新） | 调用方主动查询 | 调度 / 延迟消息 / 手动 |
| 方向 | 聚合 → 投影 → 存储 | 存储 → 投影 | 聚合 → 投影 → 存储 / 删除 |
| 版本来源 | `event.getVersion()`（= `getNewVersion()`） | 不参与版本 | `order.getOldVersion()`（与 `currentVersion` 一致） |
| 关键构件 | Projector + Source（materialize） | Searcher + Reducer（挂源上） | VersionResolver + Resynchronizer（桥接 sync/purge） |
| 目的 | 更新副本 | 取回副本 | 副本落后 / 残留时重建或清理 |

> ⚠️ **`resync` 必须从写模型当前快照重建**（`findById` → `sync`），而非重放那条被漏消费的事件——丢失的事件已不在事件流里，重放单条事件无法补齐副本。

## 6. 分页与滚动值对象

### 6.1 `PageRequest`

```java
PageRequest.of(1, 20)     // 页码 1-based，页大小限定 [1, 200]
    .pageNumber()         // 1
    .pageSize()           // 20
    .offset();            // (pageNumber - 1) * pageSize，直接供 SQL / ES from 使用
```

> ⚠️ **重要约束：`PageRequest.of` 对非法参数抛 `IllegalArgumentException`**——`pageNumber < 1` 或 `pageSize` 不在 `[1, 200]`。分页参数通常来自外部接口，必须在入口校验或捕获，不能依赖存储层兜底。上限 200 用于限制深分页与单次返回数据量。

### 6.2 `PageResult`

不可变值对象，`data` 为防御性拷贝的不可变列表：

| 成员 | 说明 |
| --- | --- |
| `data()` | 当页数据（`List.copyOf`，不可修改） |
| `totalCount()` | 总记录数，供前端计算总页数 |
| `request()` | 回带本次 `PageRequest`，便于前端回显与翻页 |

> ⚠️ **重要约束：`totalCount` 需要存储侧配合开启精确计数**（ES 场景为 `trackTotalHits(true)`）。不开启时 ES 默认只给近似值或截断值，会导致总页数错误。

### 6.3 `ScrollPosition` / `ScrollResult`

```java
// 首次
ScrollResult<OrderEsProjection> first =
        orderQuery.queryScroll(condition, ScrollPosition.initial(), 100, OrderEsProjection.class);

// 翻页：把上一页 nextCursor 原样回传，null 表示已到末页
Optional.ofNullable(first.nextCursor())
        .ifPresent(cursor -> orderQuery.queryScroll(
                condition, ScrollPosition.of(cursor), 100, OrderEsProjection.class));
```

| 类型 | 成员 | 说明 |
| --- | --- | --- |
| `ScrollPosition` | `of(cursor)` / `initial()` | 不透明游标；`isInitial()` 判断是否首次 |
| `ScrollResult` | `data()` / `nextCursor()` | `nextCursor == null` 表示无更多数据 |

> ⚠️ **重要约束：游标对调用方不透明**。游标由实现层编解码（示例为 ES 文档 `_id` + `searchAfter`），调用方只做「原样回传」，不得解析、拼接或构造。首次查询用 `ScrollPosition.initial()`，此时检索器不设 `searchAfter`。

::: tip 分页还是滚动
需要总数与跳页（后台管理列表）用 `queryPage`；深翻页 / 全量导出 / 大数据量流式处理用 `queryScroll`——滚动不受 `from + size` 深分页上限约束，但拿不到总数。
:::

## 7. 版本语义

- **V（写模型权威版本）**：事件路径来自 `event.getVersion()`（`collectEvent` 回填的 `getNewVersion()`）；resync 路径来自 `order.getOldVersion()`。
- **V'（副本版本）**：存储中的副本版本元数据（ES 为 `_version`），由版本解析器读取。
- **判定纯函数**：`Reconciliation.of(V', V)` —— `V'<0` → `UNTRACKED`；`V<0` 且 `V'≥0` → `ORPHAN`；`V'≥V` → `CONSISTENT`；否则 `STALE`。

> ⚠️ **External 版本约束（ES 场景）**：启用后不可依赖 ES 内部自增 `_version`；首次写入 `_version` 需与聚合新建版本对齐。版本解析器对「文档不存在 / 版本缺失」返回 `-1`（`UNTRACKED`）；传输层异常经 `@SneakyThrows` 上抛，**不会**被转换为 `-1`——「副本缺失」与「存储不可达」语义不同。

## 8. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 领域包引入存储客户端 / Spring 依赖 | 契约层与存储耦合、无法替换存储 | 领域只定义契约、投影 DTO 与条件族；存储实现在 infrastructure |
| 把聚合根当投影返回 | 泄露写模型内部结构、破坏读写边界 | 定义中立投影 DTO，投影器裁剪字段 |
| 投影器内做存储读写 / 版本控制 | 投影器无法单测、存储细节扩散 | 投影器纯映射；持久化只在源（`AbstractProjectionSource.materialize`），检索只在检索器 |
| 无理由地把金额统一转成「分」 | 单位换算成了默认约定，投影与聚合单位不一致、排查困难 | 默认原样承载；仅当存储 Mapping 明确要求时才换算并注明 |
| 投影器与裁剪器各换算一次金额 | 重复进位，金额翻倍 | 换算只发生在聚合 → 全量投影这一次，裁剪器同单位直取 |
| 单位换算散落在字段赋值语句中 | 换算规则无法统一审计、改 Mapping 时易漏改 | 集中在具名方法内（如 `toFen`），并注明换算原因 |
| 门面 `OrderQuery` 注入 `ElasticsearchClient` 拼 DSL | 读侧存储方言泄漏到编排层，替换存储要改门面 | 门面继承 `AbstractProjectionQuery`，不注入客户端，DSL 翻译下沉到 Searcher |
| 事件携带业务快照 | 延迟处理用旧数据覆盖新副本 | 事件只带聚合标识，处理时重新 load 聚合 |
| 真正的写失败（连接 / 映射错误）`catch` 后静默吞掉 | 副本真正落后被掩盖、对账失效 | 异常上抛，交给 `resync` 补偿 |
| External 版本冲突（409）上抛而非静默丢弃 | 旧事件迟到触发无谓 `resync`、反复重建已最新副本 | 捕获 `ResponseException` 仅 `log.debug` 静默丢弃 |
| 业务方自行 `new ReconciliationTarget` / `new ProjectionSource("es:orders")` | Registry 中 key 不一致、寻址失败 | 引用 `OrderEsTargets` / `OrderCacheTargets` 已定义的源常量 |
| 为每个查询场景建一个条件类 / 一个检索器 | 类爆炸、条件失去穷举约束 | 按族建 `sealed interface`，族内场景为 `record`，检索器按族登记 |
| 跨族复用条件（`ListQuery` 传给 `queryPage`） | 编译期报错，语义混用 | 分页场景建 `PageQueryCriteria` 子族，字段全 `Optional` |
| 分页参数塞进条件 `record` | 条件与分页语义耦合、无法复用条件做滚动 | 分页由 `PageRequest` / `ScrollPosition` 单独传入 |
| 条件中使用枚举类型 | 跨进程传输与枚举演进互相牵制 | 条件与投影中枚举一律降级为 `Integer` / `int` + 文案 `String` |
| `getSearcher` 未登记当空结果处理 | 接线 bug 被掩盖成「查不到」 | 启动时自检登记完整性；异常上抛不降级 |
| 读路径为拿最新数据回源聚合根 | 同步阻塞、绕过副本、副本落后无法暴露 | 读路径只读副本，落后由对账修复 |
| 绕过 Registry 手写投影更新 | 事件路径与 resync 逻辑不一致 | 共用 `ProjectorRegistry` / `AggregateProjectorSupport` |
| resync 重放单条事件 | 丢失的事件无法补齐副本 | 从写模型当前快照重建（findById → project → materialize） |
| 投影用聚合根的 Lombok 约定 | 数据容器被 @Builder 等污染 | 投影 DTO 用 `@Data`；聚合根禁用 `@Data` |
| 检索器 `projectionType()` 返回投影体系接口（如 `IOrderProjection.class`） | 与门面按索引级投影查询的键不一致，**运行期必然**抛 `ProjectionSearcherNotFoundException`，且编译期看不出来 | 检索器返回索引级全量投影具体类（如 `OrderEsProjection.class`） |
| 门面直接把调用方的 `Class<X>` 传给 `getSearcher` | 传的是业务子投影，与检索器登记的索引级投影键不匹配 | 先 `sourceTypeOf` 反查来源，用来源类型取检索器 |
| 装配只登记检索器、忘记 `register(源)` | 连直查索引级全量投影都选路失败 | 装配核心是登记源 + `registerDefaultSource`；检索器 / 裁剪器在源内 `bind`，子投影需登记 reducer 反查 |
| 裁剪后重新计算 `totalCount` / `nextCursor` | 页边界与游标错误 | 分页留在检索器侧，二者取裁剪前的全量结果 |
| 裁剪器内查库 / 调远程 | 破坏纯函数性，造成 N+1 | `reduce` 只做内存转换，所需数据由检索器一次取全 |
| 一个裁剪器内按目标类型 `instanceof` 分支 | 与「一个实例服务一个 (源, 子)」契约相悖、无法按型寻址 | 一个子投影一个裁剪器 |
| 为同一子投影登记多个来源裁剪器 | 门面无法确定该查哪个索引 | 合并为单一来源；登记期即抛 `ProjectionReducerConflictException` |

---

## 下一步

- [投影读模型](../core/projection-read.md)：框架 `repository.query` / `repository.reconciliation` 通用能力详解
- [仓储写模型](../core/repository-write.md)：聚合持久化、`currentVersion` 权威版本 V
- [聚合设计原则](./aggregate-design.md)：聚合根 `triggerDataSyncHook` 与事件收集
- [仓储设计原则](./repository-design.md)：写模型仓储定位与落地
- [Elasticsearch 配置设计原则](./elasticsearch-config.md)：`ElasticsearchClient` 三层客户端构建与投影物化配套
- [事件建模指南](./event-modeling.md)：事件只携带聚合标识的建模规范
