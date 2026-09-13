# 投影读模型代码落地指南

> 本文档说明投影读模型代码**按本次落地的方式怎么写**，是可复用的通用指导原则：从包结构与命名，到每个组件的手写规则，再到事件物化、对账补偿与读侧检索的衔接。下文以订单投影（`Order`）为示例贯穿全文；其他模块做类似的投影设计时，套用本文档的结构与规则，把 `Order` 换成目标聚合、`Es` 换成目标存储即可。
>
> ⚠️ **读侧入口是应用层读服务（`OrderReadService`），它注入的是领域层源接口（端口），不是框架基类、也不是注册中心**：读服务 `implements IQueryApplicationService`，按**查询族**（ById / One / List / Page）选择注入的源接口（`IOrderRedisSource` / `IOrderESSource`）直接调用，**调用方只传目标投影类型、不感知源**。框架**不提供**查询编排基类——「用哪个源」由「该源 implements 了哪些查询族」在**编译期**决定。

## 1. 投影读模型的本质

投影读模型把聚合（写模型）映射为异构存储中的文档（读模型），为「面向查询的聚合检索」场景提供**独立读视图**，与写模型的聚合根装配完全解耦。

核心特征：

- **中立读视图**：投影 DTO 只声明字段、对齐存储 Mapping，不含存储与查询逻辑。
- **纯映射**：投影器只做字段取值 / 裁剪 / 派生，不接触存储客户端。
- **源承载写读对账**：一份物理副本的写（`materialize` / `purge`）、读（查询族实现）、对账（`readVersion` / `rebuild`）收敛在同一个源对象内。
- **检索在源内**：条件翻译与检索实现是源的一部分，不再拆成独立的 Searcher 类。
- **裁剪纯内存**：裁剪器只做「索引级全量投影 → 业务子投影」的字段裁剪 / 层级重排，不接触存储。
- **版本对账**：存储中的副本版本 V' 与写模型版本 V 比较，落后（STALE）重建、残留（ORPHAN）清理。

**写模型零改动**：聚合根不持有任何存储依赖，只在仓储落库前经 `triggerDataSyncHook()` 发出数据同步事件；投影的装配与存储细节全部落在领域契约与基础设施实现中。

### 1.1 读侧四个角色

读模型一侧由四个角色协作，职责边界不可越界：

| 角色 | 方向 | 职责 | 不负责 |
| --- | --- | --- | --- |
| 投影器 `Projector` | 聚合 → 索引级全量投影 | 字段取值 / 裁剪 / 派生 | 存储读写、版本控制 |
| 源 `Source` | 聚合 → 存储（写）、存储 → 全量投影（读）、副本自维护 | materialize / purge、实现查询族检索、持有裁剪器列表、**自身即副本**（readVersion / rebuild / purgeOrphan） | 字段派生（由投影器负责） |
| 裁剪器 `Reducer` | 索引级全量投影 → 业务子投影 | 字段裁剪 / 层级重排 / 派生（Java 内存） | 存储访问、条件翻译、分页 |
| 源接口 `I{Agg}{Store}Source`（领域层端口） | — | 声明某副本**支持哪些查询族** + 暴露 `getReducer(Class<X>)` | 任何实现逻辑（纯接口） |
| 读服务 `OrderReadService` | 读侧入口（应用层） | 注入源接口、按族分派、查全量 + 裁剪两跳 | 直接持有存储客户端、把源或 `ProjectionSource` 暴露给调用方 |

> 原设计中「检索器 `Searcher`」是独立构件、由框架基类 `AbstractProjectionQuery` 做「选源 → 查全量 → 裁剪」三跳编排。现设计下**检索能力收进源、编排能力收进应用服务**，框架只提供查询族 SPI 与分页值对象。

## 2. 包结构与命名规范

### 2.1 分层：领域定义契约，基础设施提供实现，应用层组装编排

```text
domain/order/projection/                       领域：读模型视图 + 条件族 + 源端口（无存储/Spring 依赖）
  ├── IOrderProjection                         extends IAggregateProjection（投影 sealed 体系基类）
  ├── IOrderESSource                           源端口：extends 四个查询族 + getReducer（全能力）
  ├── IOrderRedisSource                        源端口：extends IProjectionByIdSearcher + getReducer（仅主键）
  ├── OrderEsProjection                        @Data 数据容器，implements IOrderProjection
  ├── OrderSummaryProjection                   @Data 数据容器，implements IOrderProjection
  ├── OrderCacheProjection                     @Data 数据容器，implements IOrderProjection
  ├── OrderEsTargets                           ORDER_INDEX_NAME / REPLICA_ID / REPLICA_KEY
  ├── OrderCacheTargets                        ORDER_CACHE_KEY_PREFIX / REPLICA_ID / REPLICA_KEY
  └── query/                                   领域：三族查询条件（sealed interface + record）
      ├── OrderOneQuery                        extends OneQueryCriteria
      ├── OrderListQuery                       extends ListQueryCriteria
      └── OrderPageQuery                       extends PageQueryCriteria

infrastructure/persistent/order/projection/projector/  基础设施：聚合 → 视图 纯映射
  ├── OrderEsProjector                         extends AbstractAggregateProjector<Order, OrderEsProjection>
  └── OrderCacheProjector                      extends AbstractAggregateProjector<Order, OrderCacheProjection>
infrastructure/persistent/order/projection/searcher/   基础设施：条件族 → 存储查询的纯函数构建器
  └── OrderEsConditionFactory                  public final，条件族 → ES Query（filter / must 分离）
infrastructure/persistent/order/projection/reducer/    基础设施：索引级全量投影 → 业务子投影（Java 内存）
  ├── OrderSummaryReducer                      implements IReducer<OrderEsProjection, OrderSummaryProjection>
  └── OrderCacheSummaryReducer                 implements IReducer<OrderCacheProjection, OrderSummaryProjection>
infrastructure/persistent/order/projection/replica/    基础设施：写读对账一体的源（源自身即副本）
  ├── OrderEsSource                            extends AbstractProjectionSource<Order, Long, OrderEsProjection>
  │                                            implements IOrderESSource
  └── OrderRedisSource                         extends AbstractProjectionSource<Order, Long, OrderCacheProjection>
                                               implements IOrderRedisSource
infrastructure/config/order/                    Spring 装配
  └── OrderReconciliationConfig                产出 ReconciliationContribution，登记聚合仓储
application/order/                             应用层：读/写应用服务（注入源端口，业务编排门面）
  ├── OrderWriteService                        extends AbstractApplicationService implements ICommandApplicationService（写）
  ├── OrderReadService                         implements IQueryApplicationService（读，见 §4.7）
  └── service/                                 应用层编排（findById → 源.sync）
      ├── OrderDataSyncEsProjectionHandle      → esSource.sync(order)
      ├── OrderRedisCacheHandle                → redisSource.sync(order)
      └── OrderReconcileHandle                 → reconciliationManager.reconcile(...)
application/order/subscriber/                  事件订阅绑定
  └── OrderEventSubscriberRegistry
```

依赖方向单向内聚：`Infrastructure → Domain → Framework`。

### 2.2 命名规范

```java
// ✅ 推荐：领域契约 I 开头，以聚合前缀窄化框架通用接口
public interface IOrderProjection extends IAggregateProjection { }

// ✅ 推荐：源端口在领域层，按需 extends 查询族——「支持什么族」即「能被怎么查」
public interface IOrderESSource
        extends IProjectionByIdSearcher<OrderEsProjection>,
                IOneQuerySearcher<OrderEsProjection, OrderOneQuery>,
                IListQuerySearcher<OrderEsProjection, OrderListQuery>,
                IPagedQuerySearcher<OrderEsProjection, OrderPageQuery> {
    <X extends IAggregateProjection> IReducer<OrderEsProjection, X> getReducer(Class<X> target);
}

// ✅ 推荐：缓存副本只支撑主键直取，就只 extends ById 一族
public interface IOrderRedisSource extends IProjectionByIdSearcher<OrderCacheProjection> {
    <X extends IAggregateProjection> IReducer<OrderCacheProjection, X> getReducer(Class<X> target);
}

// ✅ 推荐：读侧入口是「应用层读服务」——注入领域源接口，
//    implements IQueryApplicationService 标记读侧应用服务；不继承任何框架基类
@Service
public class OrderReadService implements IQueryApplicationService {

    public OrderReadService(IOrderRedisSource redisSource, IOrderESSource esSource) {
        this.redisSource = redisSource;
        this.esSource = esSource;
    }
}

// ✅ 推荐：视图载体与实现以 OrderEs* 标明聚合与存储；写读对账一体落在源
public class OrderEsProjector extends AbstractAggregateProjector<Order, OrderEsProjection> { }
public class OrderEsSource extends AbstractProjectionSource<Order, Long, OrderEsProjection>
        implements IOrderESSource { }

// ❌ 反模式：基础设施直接实现框架通用接口、领域层无源端口——应用层被迫依赖基础设施类
```

> **命名约定**：领域契约接口一律 `I` 开头，以聚合前缀区分框架通用接口（`IOrderProjection` / `IOrderESSource` / `IOrderRepository` 等，不含存储标记）；**应用服务不用 `I` 前缀**，读/写服务用 `OrderReadService` / `OrderWriteService`；实现类不用 `Impl` 后缀，用 `OrderEs*`（`OrderEsProjector` / `OrderEsSource`）与 `OrderRedis*`（`OrderRedisSource`）标明聚合与存储；条件族以 `Order{One|List|Page}Query` 命名、族内场景为 `record`；裁剪器以 `Order{Target}Reducer` 命名（如 `OrderSummaryReducer`）；源以 `Order{Store}Source` 命名；**源自身即副本**，不另设版本解析器 / 补同步器实现类；副本标识常量集中在 `OrderEsTargets` / `OrderCacheTargets`（`REPLICA_ID` / `REPLICA_KEY`）。
>
> **没有独立的 `*Searcher` 类了**：检索实现是源的方法，不再单建 `Order{ById|One|List|Page}Searcher`。唯一保留在 `searcher/` 包的是**无状态的条件翻译工厂**（`OrderEsConditionFactory`）。
>
> ⚠️ **索引级全量投影的命名要体现「存储文档形状」而非「业务用途」**：它对齐的是物理索引 Mapping，本质上是存储契约在 Java 侧的镜像。`OrderEsProjection`（索引 `order_index` 的全量文档）是好名字；`OrderProjection` 这类不带存储标记的名字会与业务投影混淆，无法区分「哪个是索引级、哪个是裁剪产物」。

## 3. 投影承载的数据

投影字段对齐存储 Mapping，既含聚合原始字段，也含查询辅助的派生字段。**字段取舍由「读侧要查什么」决定，而不是「写模型有什么」**。

字段设计规则：

| 规则 | 说明 |
| --- | --- |
| 标识字段 | 聚合标识，作为文档 `_id` / 缓存键后缀 |
| 枚举双写 | code（`getValue()`）与文案（`getName()`）同时落库，避免查询侧枚举反查 |
| 金额 / 数值**原样承载** | 用 `BigDecimal`，与聚合字段保持同一单位与精度，**不做任何隐式单位换算** |
| 嵌套对象 | 有强归属的子结构扁平化嵌入（如无跨字段约束，不引入 `nested`） |
| 派生字段 | 冗余查询辅助字段（聚合列表 / 拼接文本等），由投影器计算 |

:::: tip 金额单位：与聚合同单位，不做换算
投影字段的金额**与聚合字段同单位、同精度**（聚合是 `Money`/`BigDecimal` 就照原值承载），框架不要求、也不应强制转成「分」。

存储 Mapping 若确实要求整数最小单位（如 `scaled_float` 存分），则：

| 情况 | 做法 |
| --- | --- |
| 存储字段与聚合字段单位一致（本示例） | **原样承载**，投影器只做 `Money → BigDecimal` 取值 |
| 存储 Mapping 用整数最小单位 | 在投影器内显式换算，并集中在具名方法内注明原因 |

换算一旦发生，必须在投影器与裁剪器之间**保持一致**：投影写什么单位，裁剪就读什么单位，不要在裁剪器里二次换算。
::::

> 示例：订单投影有三个形态——`OrderEsProjection`（索引级详情投影，对齐 ES 索引 `order_index` Mapping，含 `status` / `paymentStatus` / `shipmentStatus` / `paymentMethod` 四组枚举双写、`totalAmount` / `platformDiscount` / `actualAmount`（`BigDecimal`，单位元）、`customer` / `shippingAddress` / `logisticsInfo` / `orderItems` 扁平对象、`itemProductNames` / `itemProductNamesText` 商品名检索派生字段）、`OrderCacheProjection`（Redis 缓存副本投影，`OrderEsProjection` 剔除 ES 检索派生字段后**再加 `version` 字段**承载副本版本）与 `OrderSummaryProjection`（业务概要投影，仅含列表展示所需字段）。

### 3.1 同一聚合的多种投影形态

一个聚合可以有多个投影形态，全部实现同一个领域投影接口，由调用方用 `Class<X>` 显式选择：

```java
// ✅ 推荐：一个聚合多种投影，共用同一个领域投影接口，按 projectionType 选择
public interface IOrderProjection extends IAggregateProjection { }        // sealed 体系基类
public class OrderEsProjection implements IOrderProjection { }            // ES 索引级详情投影
public class OrderCacheProjection implements IOrderProjection { }         // Redis 索引级缓存投影
public class OrderSummaryProjection implements IOrderProjection { }       // 业务概要投影
```

> ⚠️ **核心约束：源只承载一种「索引级全量投影」，且由泛型在编译期钉死**。
>
> `AbstractProjectionSource<T, ID, P>` 的第三个泛型 `P` 就是该源唯一能产出 / 检索的投影类型。
> 实现的查询族接口（`IProjectionByIdSearcher<P>` 等）也以同一个 `P` 为泛型实参——
> **「这个源查出来是什么形状」在 `implements` 那一行就定死了**，不存在运行期按 `Class` 键查表，
> 也就不存在「登记键与查询键不一致」这类运行期 miss。
>
> 调用方想要的业务子投影（如 `OrderSummaryProjection`）由裁剪器在 Java 内存中产出（见 §4.11），
> 裁剪器经 `源.getReducer(目标类型)` 定位，未注册返回 `null`。
>
> 同一聚合若有**多套物理索引**（如详情索引 A、概要索引 B），则应有**两个源**（`I{Agg}IndexASource` / `I{Agg}IndexBSource`），
> 各自 `extends` 各自要支撑的查询族——两个源互不冲突，因为它们是不同的对象、有不同的 `REPLICA_ID`。

### 3.2 派生字段在投影器内计算

派生字段（枚举文案、聚合列表、拼接文本等）全部在投影器 `project` 内由聚合计算，**不依赖存储内部脚本**（如 ES script）。

单位换算属于**可选的**派生处理：默认原样承载，仅当存储 Mapping 要求不同单位时才做，且应集中在一个具名方法内并注明原因，不要散落在字段赋值语句中。

### 3.3 事件只携带聚合标识，不携带业务快照

数据同步事件只携带聚合标识（`version` 由框架在 `collectEvent` 时回填为 `getNewVersion()`），**不携带业务快照**。订阅方处理时**重新 `findById` 加载最新聚合**再投影物化——读模型反映的是事件处理时刻的最新状态，而不是事件产生时刻的旧状态。

```java
// ✅ 推荐：事件只携带聚合标识，处理时反查聚合根取权威状态
@Override
public void handleEvent(OrderDataSyncEvent event) {
    Order order = orderRepository.findById(Long.valueOf(event.getEntityId()));
    if (order == null) {
        return;
    }
    esSource.sync(order);   // 内部 project → materialize(projection, aggregate.getOldVersion())
}

// ❌ 反模式：事件携带整份业务快照，延迟处理后会用旧数据覆盖新副本
```

:::: tip 可以带少量路由 ID，但不要带快照
事件可以携带聚合标识与少量路由 / 上下文 ID（供订阅者定位聚合、路由分支），但**不要携带整份业务快照**——快照会过期、会随业务字段增长而膨胀，权威数据始终以聚合根为准。
::::

## 4. 组件的落地方式

按以下顺序逐个落地组件，即可得到可运行的投影读模型代码。

### 4.1 领域契约：投影接口 + 源端口

在领域层定义聚合专属接口，**只 `extends` 框架通用接口，不写任何实现逻辑**：

```java
// domain/order/projection/IOrderProjection.java
public interface IOrderProjection extends IAggregateProjection { }

// domain/order/projection/IOrderESSource.java —— ES 副本：四族全能力
public interface IOrderESSource
        extends IProjectionByIdSearcher<OrderEsProjection>,
                IOneQuerySearcher<OrderEsProjection, OrderOneQuery>,
                IListQuerySearcher<OrderEsProjection, OrderListQuery>,
                IPagedQuerySearcher<OrderEsProjection, OrderPageQuery> {

    /** 按目标子投影类型取裁剪器（由源基类实现，声明在此供应用层调用）。 */
    <X extends IAggregateProjection> IReducer<OrderEsProjection, X> getReducer(Class<X> target);
}

// domain/order/projection/IOrderRedisSource.java —— Redis 副本：仅主键直取
public interface IOrderRedisSource extends IProjectionByIdSearcher<OrderCacheProjection> {
    <X extends IAggregateProjection> IReducer<OrderCacheProjection, X> getReducer(Class<X> target);
}
```

框架四个查询族 SPI（均位于 `io.pragmatic.ddd.repository.query.projection`）：

| 接口 | 方法 | 语义 |
| --- | --- | --- |
| `IProjectionByIdSearcher<P>` | `P getById(Object id)` / `List<P> getByIds(List<Object> ids)` | 按主键直取；单条未命中返回 `null`，批量返回列表（不返回 `null`） |
| `IOneQuerySearcher<P, C extends OneQueryCriteria>` | `List<P> search(C criteria)` | 按精确条件取**列表**（由调用方取首条） |
| `IListQuerySearcher<P, C extends ListQueryCriteria>` | `List<P> search(C criteria)` | 按精确条件取列表（含 TOP N） |
| `IPagedQuerySearcher<P, C extends PageQueryCriteria>` | `PageResult<P> searchPage(C, PageRequest)` / `ScrollResult<P> searchScroll(C, ScrollPosition, int)` | 分页 / 滚动 |

> **为什么把源端口放到领域层**：源端口声明的是「这份副本能被怎么查」，是**业务消费方（应用层）真实需要的契约**，属于依赖倒置的正规用法；而查询族接口的泛型实参 `OrderEsProjection` 是存储形状，它被限定在这一行 `extends` 上，不会扩散——应用层拿到的永远是「`IOrderESSource` + 目标业务投影类型」。
>
> **能力不对称是合法的**：`IOrderRedisSource` 只 `extends` ById 一族，因此对它调用 `search(...)` / `searchPage(...)` **编译期即失败**。这正是「缓存副本只支撑主键直取」的机器保证，不需要运行期判断、也不需要文档约定。
>
> ⚠️ **接口里没有 `criteriaType()` / `projectionType()` 这类元数据方法**：条件类型与投影类型由 `implements` 时的泛型实参钉死。写检索实现时不需要、也无法在运行时自报类型。

### 4.2 投影 DTO：`OrderEsProjection`

纯数据容器，用 Lombok `@Data` 简化（含嵌套 `@Data` 静态类）。只声明字段、对齐 Mapping，**不含计算逻辑**：

```java
@Data
public class OrderEsProjection implements IOrderProjection {
    private Long orderId;                 // 聚合标识，文档 _id

    private int status;                   // 基础类型，不使用枚举
    private String statusName;
    private int paymentStatus;
    private String paymentStatusName;
    private int shipmentStatus;
    private String shipmentStatusName;
    private int paymentMethod;
    private String paymentMethodName;

    private String currency;
    private String remark;
    private String cancelReason;
    private String paymentSerialNo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;

    private BigDecimal totalAmount;       // 单位：元，与聚合 Money 同单位同精度
    private BigDecimal platformDiscount;
    private BigDecimal actualAmount;

    private CustomerProjection customer;
    private AddressProjection shippingAddress;
    private LogisticsProjection logisticsInfo;
    private List<OrderItemProjection> orderItems;

    private List<String> itemProductNames;      // 派生：明细项商品名列表
    private String itemProductNamesText;        // 派生：商品名拼接文本，供分词检索

    @Data
    public static class CustomerProjection {
        private Long customerId;
        private String customerName;
    }

    @Data
    public static class AddressProjection {
        private String province;
        private String city;
        private String district;
        private String detail;
        private String receiverName;
        private String receiverPhone;
    }

    @Data
    public static class LogisticsProjection {
        private String trackingNo;
        private String companyCode;
        private String companyName;
        private LocalDateTime shippedAt;
    }

    @Data
    public static class OrderItemProjection {
        private Long itemId;
        private Long productId;
        private String productName;
        private String spec;
        private BigDecimal price;
        private int quantity;
        private BigDecimal subtotal;
    }
}
```

> ⚠️ **投影用 `@Data`，聚合根禁用 `@Data`**：投影等同性由 Lombok 生成、纯属容器便利；聚合根等同性由 `AbstractEntity` 托管。嵌套子投影（`CustomerProjection` 等）不实现 `IOrderProjection`，只有聚合拓扑级投影实现。
>
> ⚠️ **投影中枚举降级为基础类型**：投影字段不使用枚举类型，`status` 用 `int`、`statusName` 用 `String` 双写承载。投影是跨进程传输的存储文档，绑定枚举类型会让文档反序列化与枚举演进互相牵制。
>
> ⚠️ **缓存副本投影需要自带版本字段**：Redis 这类不提供外部版本号的存储，副本版本 V' 要由投影自己承载——`OrderCacheProjection.version`（`Long`）由投影器写入 `aggregate.getOldVersion()`，`readVersion` 解析它。ES 有 `_version`，故 `OrderEsProjection` 不需要该字段。

### 4.3 投影器：`OrderEsProjector`

继承 `AbstractAggregateProjector<Order, OrderEsProjection>`，构造传入投影类型，只实现 `project`（`projectionType()` 由基类预置且 `final`）：

```java
@Component
public class OrderEsProjector extends AbstractAggregateProjector<Order, OrderEsProjection> {

    public OrderEsProjector() {
        super(OrderEsProjection.class);
    }

    @Override
    public OrderEsProjection project(Order order) {
        OrderEsProjection projection = new OrderEsProjection();
        projection.setOrderId(order.getEntityId());
        projection.setStatus(order.getStatus().getValue());
        projection.setStatusName(order.getStatus().getName());
        projection.setPaymentStatus(order.getPaymentStatus().getValue());
        projection.setPaymentStatusName(order.getPaymentStatus().getName());
        projection.setShipmentStatus(order.getShipmentStatus().getValue());
        projection.setShipmentStatusName(order.getShipmentStatus().getName());
        projection.setPaymentMethod(order.getPaymentMethod().getValue());
        projection.setPaymentMethodName(order.getPaymentMethod().name());
        projection.setCurrency(order.getCurrency());
        projection.setRemark(order.getRemark());
        projection.setCancelReason(order.getCancelReason());
        projection.setPaymentSerialNo(order.getPaymentSerialNo());
        projection.setCreatedAt(order.getCreatedAt());
        projection.setUpdatedAt(order.getUpdatedAt());
        projection.setPaidAt(order.getPaidAt());

        projection.setTotalAmount(amountOf(order.getTotalAmount()));
        projection.setPlatformDiscount(amountOf(order.getPlatformDiscount()));
        projection.setActualAmount(amountOf(order.getActualAmount()));

        Optional.ofNullable(order.getCustomer())
                .ifPresent(customer -> {
                    OrderEsProjection.CustomerProjection cp = new OrderEsProjection.CustomerProjection();
                    cp.setCustomerId(customer.getCustomerId());
                    cp.setCustomerName(customer.getCustomerName());
                    projection.setCustomer(cp);
                });
        // shippingAddress / logisticsInfo 同构，均用 Optional.ofNullable(...).ifPresent(...)

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

    private OrderEsProjection.OrderItemProjection toItemProjection(OrderItem item) {
        OrderEsProjection.OrderItemProjection ip = new OrderEsProjection.OrderItemProjection();
        ip.setItemId(item.getEntityId());
        ip.setProductId(item.getProductId());
        ip.setProductName(item.getProductName());
        ip.setSpec(item.getSpec());
        ip.setPrice(amountOf(item.getPrice()));
        ip.setQuantity(item.getQuantity());
        ip.setSubtotal(amountOf(item.getSubtotal()));
        return ip;
    }

    /**
     * 取金额值（单位：元），金额为空时返回 0。
     *
     * @param money 金额值对象，可为 null
     * @return 金额数值，永不为 null
     */
    private BigDecimal amountOf(Money money) {
        return Optional.ofNullable(money)
                .map(Money::getAmount)
                .orElse(BigDecimal.ZERO);
    }
}
```

编写规则：

- **字段映射全部手写**：框架不提供反射式默认映射。
- **金额原样承载**：与聚合字段同单位、同精度，不做换算。只有存储 Mapping 明确要求不同单位时，才引入具名换算方法并注明原因。
- **派生字段在 `project` 内计算**：如 `itemProductNames = items.stream().map(...).toList()`、`itemProductNamesText = String.join(" ", itemProductNames)`。
- **可空嵌套值用 Optional**：`customer` / `shippingAddress` / `logisticsInfo` 用 `Optional.ofNullable(...).ifPresent(...)`，符合项目「控制流判断优先 Optional」约定。
- **不返回 `null`**：本聚合始终满足投影条件；确实不满足时返回 `null`，`源.sync` 会静默跳过物化。
- **纯映射、无状态单例**：可独立单测，不持有存储客户端。
- **一份副本一个投影器**：`OrderCacheProjector` 与 `OrderEsProjector` 平级，各自独立映射、不互相复用——缓存投影额外写入 `version`，且不含 ES 检索派生字段。

> ⚠️ **不要在裁剪器里二次换算金额**。单位换算只发生在「聚合 → 索引级全量投影」这一次；裁剪器读的是投影字段，应与投影保持同一单位。两处都换算会导致重复进位。
>
> ⚠️ **`project` 不含任何存储细节**：存储读写只存在于源；投影器不知道「写入哪个索引、如何控制版本、如何检索」。

### 4.4 源：`OrderEsSource`

写读对账一体落在**源**（`AbstractProjectionSource` 子类）：注入投影器、裁剪器、存储客户端与仓储（供 `rebuild`），并 `implements` 领域源端口以提供查询族实现：

```java
@Component
public class OrderEsSource extends AbstractProjectionSource<Order, Long, OrderEsProjection>
        implements IOrderESSource {

    private static final Logger log = LoggerFactory.getLogger(OrderEsSource.class);

    private final ElasticsearchClient elasticsearchClient;
    private final OrderRepository orderRepository;

    public OrderEsSource(
            OrderEsProjector projector,
            OrderSummaryReducer summaryReducer,
            ElasticsearchClient elasticsearchClient,
            OrderRepository orderRepository) {
        super(ProjectionSource.of(OrderEsTargets.REPLICA_ID),
                Order.class, OrderEsProjection.class, projector, List.of(summaryReducer));
        this.elasticsearchClient = elasticsearchClient;
        this.orderRepository = orderRepository;
    }

    // ---------- 写 ----------

    @Override
    public void materialize(IAggregateProjection projection, long version) {
        OrderEsProjection es = (OrderEsProjection) projection;   // 基类签名退化为接口，需强转
        try {
            elasticsearchClient.index(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .id(es.getOrderId().toString())
                    .versionType(VersionType.External)
                    .version(version)
                    .document(es));
        } catch (ResponseException ex) {
            // external 版本不前进（迟到/重复事件）时 ES 返回 409，按乐观锁语义静默丢弃
            log.debug("订单 ES 投影物化被版本冲突忽略，orderId={}, version={}", es.getOrderId(), version);
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

    // ---------- 对账（源自身即副本） ----------

    @Override
    @SneakyThrows
    public long readVersion(Long aggregateId) {
        GetResponse<Map> response = elasticsearchClient.get(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .id(aggregateId.toString()), Map.class);
        if (!response.found()) {
            return 0L;                       // 副本缺失 → 判 STALE，由 rebuild 回填
        }
        return Optional.ofNullable(response.version()).orElse(0L);
    }

    @Override
    public void rebuild(Long aggregateId) {
        Order order = orderRepository.findById(aggregateId);
        if (order == null) {
            return;
        }
        sync(order);
    }

    // ---------- 读（查询族实现） ----------

    @Override
    public OrderEsProjection getById(Object id) {
        return ProjectionExceptions.retrieve(() -> doGetById(id.toString()), "getById");
    }

    @Override
    public List<OrderEsProjection> getByIds(List<Object> ids) {
        return ProjectionExceptions.retrieve(() -> doGetByIds(ids), "getByIds");
    }

    @Override
    public List<OrderEsProjection> search(OrderOneQuery criteria) {
        return ProjectionExceptions.retrieve(() -> {
            if (criteria instanceof OrderOneQuery.LatestByCustomer c) {
                return searchLatestByCustomer(c);
            }
            return List.<OrderEsProjection>of();
        }, "search");
    }

    @Override
    public List<OrderEsProjection> search(OrderListQuery criteria) { /* 族内 instanceof 分发 */ }

    @Override
    public PageResult<OrderEsProjection> searchPage(OrderPageQuery criteria, PageRequest pageRequest) {
        return ProjectionExceptions.retrieve(() -> doSearchPage(criteria, pageRequest), "searchPage");
    }

    @Override
    public ScrollResult<OrderEsProjection> searchScroll(
            OrderPageQuery criteria, ScrollPosition cursor, int pageSize) {
        return ProjectionExceptions.retrieve(() -> doSearchScroll(criteria, cursor, pageSize), "searchScroll");
    }
    // doGetById / doGetByIds / searchXxx / doSearchPage / doSearchScroll 为私有实现，见 §4.9
}
```

基类提供了什么（`AbstractProjectionSource<T, ID, P>`）：

| 成员 | 说明 |
| --- | --- |
| `source` / `aggregateType` / `projectionType` / `projector` / `reducers` | 构造注入，`@Getter` 暴露 |
| `sync(T aggregate)` | `projector.project(aggregate)` → `materialize(projection, aggregate.getOldVersion())`；投影为 `null` 静默跳过 |
| `getReducer(Class<X> target)` | 从 `reducers` 中按 `target.isAssignableFrom(reducer.projectionType())` 取首个；未注册返回 `null` |
| `readVersion(ID)` / `rebuild(ID)` | **抽象方法，子类必须实现**——「每个源都必须可对账」是框架不变式 |
| `purgeOrphan(ID)` | 默认委托 `purge(aggregateId)`；语义不同时可覆写 |
| `replicaId()` | 返回 `source.id()`，写读对账共用同一身份 |
| `key()` | `ReplicaKey(aggregateType, replicaId)`，对账注册表入账用 |

编写规则：

- `super(ProjectionSource.of(XxxTargets.REPLICA_ID), Order.class, Order{Store}Projection.class, projector, List.of(reducer...))`：源标识即副本标识（读寻址与对账寻址的同一身份）；泛型形参 `<Order, Long, OrderEsProjection>` 中的 `Long` 为聚合标识类型，使 `readVersion` / `rebuild` 直接接收 `Long` 而无需手工转换。
- **版本控制用 External 版本**：写模型版本 V 落到副本版本元数据（ES 为 `_version`），副本落后时存储拒绝写入并抛 409 版本冲突。
- **区分失败**：409 版本冲突（迟到/重复事件）静默丢弃，仅 `log.debug`；真正的写失败（连接/映射错误）以 `IOException` 上抛，交给事件重试 / 对账 `rebuild` 兜底。
- `purge` 删除文档，文档不存在时静默忽略（清理幂等）。
- **对账能力收敛于源**：覆写 `readVersion` 与 `rebuild`；Redis 这类无外部版本号的存储，版本内嵌在投影 JSON 里由 `readVersion` 解析。
- **注入仓储仅供 `rebuild`**：`findById` → `sync(order)`，从写模型当前快照重建。

> ⚠️ **`materialize` 的形参是 `IAggregateProjection`，不是具体投影类型**。基类为支持桥方法把签名退化到接口，实现方第一行需强转为本源的 `P`（`OrderEsProjection es = (OrderEsProjection) projection;`）。这是唯一一处需要强转的地方，强转安全由「只有本源的 projector 产出 `P`」保证。
>
> ⚠️ **`readVersion` 的缺省值语义由实现自定，本示例统一返回 `0`**：`0` = 副本缺失、需重建（`Reconciliation.of` 判 **STALE**，触发 `rebuild`）；`-1` = 未追踪（判 **UNTRACKED**，**不触发重建**）。误用 `-1` 会让副本永久落后且静默无告警。ES 场景读 `_version`、文档不存在返回 `0`；Redis 场景解析投影 JSON 的 `version`、键缺失或 JSON 损坏返回 `0`。
>
> ⚠️ **源自身即副本**：`replicaId()` 即源 `id`，写侧 `源.sync(aggregate)` 与对账 `rebuild` 共享同一身份，由类型而非字符串约定保证；业务方应引用 `OrderEsTargets.REPLICA_ID`，不要自行 `new ProjectionSource("es:orders")`，否则 key 不一致导致寻址失败。

### 4.5 副本常量：`OrderEsTargets` / `OrderCacheTargets`

索引名、键前缀与副本标识收口到一处，写入 / 读取 / 对账全部引用同一常量：

```java
public final class OrderEsTargets {
    /** 物理索引名，写入与读取必须命中同一物理索引。 */
    public static final String ORDER_INDEX_NAME = "order_index";
    /** 副本标识（＝源标识），写入 / 读取 / 对账共用。 */
    public static final String REPLICA_ID = "es:orders";
    /** 对账寻址键（聚合类型 + 副本标识）。 */
    public static final ReplicaKey REPLICA_KEY = new ReplicaKey(Order.class, REPLICA_ID);
    private OrderEsTargets() { }
}

public final class OrderCacheTargets {
    /** Redis 缓存键前缀。 */
    public static final String ORDER_CACHE_KEY_PREFIX = "order:agg:";
    public static final String REPLICA_ID = "redis:orders";
    public static final ReplicaKey REPLICA_KEY = new ReplicaKey(Order.class, REPLICA_ID);
    private OrderCacheTargets() { }
}
```

> ⚠️ **物化与检索必须命中同一物理索引**：`materialize` / `purge` / `readVersion` / 各查询族实现全部引用 `ORDER_INDEX_NAME`（或 `ORDER_CACHE_KEY_PREFIX`）。两侧各写字面量会在索引改名时只改一半。

### 4.6 查询条件族：三个 sealed interface

读侧条件按**族**划分，每族一个 `sealed interface` + 若干 `record` 场景，族间在编译期隔离：

```java
// domain/order/projection/query/OrderOneQuery.java —— 精确规约，字段全必填
public sealed interface OrderOneQuery extends OneQueryCriteria
        permits OrderOneQuery.LatestByCustomer {
    record LatestByCustomer(Long customerId) implements OrderOneQuery { }
}

// domain/order/projection/query/OrderListQuery.java —— 精确规约 + TOP N
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
            Optional<Integer> status,
            Optional<Integer> paymentStatus,
            Optional<Integer> shipmentStatus,
            Optional<Long> customerId,
            Optional<String> trackingNo,
            Optional<String> remark,
            Optional<BigDecimal> minAmount,
            Optional<BigDecimal> maxAmount,
            Optional<LocalDateTime> paidFrom,
            Optional<LocalDateTime> paidTo,
            Optional<LocalDateTime> createdFrom,
            Optional<LocalDateTime> createdTo,
            Optional<String> productName) implements OrderPageQuery { }
}
```

三族语义对照：

| 族 | 父类 | 字段语义 | 对应查询族接口 | 典型场景 |
| --- | --- | --- | --- | --- |
| `OrderOneQuery` | `OneQueryCriteria` | 全必填、精确匹配 | `IOneQuerySearcher` | 按客户取最新一单 |
| `OrderListQuery` | `ListQueryCriteria` | 全必填、精确匹配 + TOP N | `IListQuerySearcher` | 金额 TOP N、最近 TOP N |
| `OrderPageQuery` | `PageQueryCriteria` | 全 `Optional`、按需过滤 | `IPagedQuerySearcher` | 后台多条件分页 / 滚动 |

编写规则：

- **族内扩展只加 `record` 并在 `permits` 登记**：新增场景后，所有 `instanceof` 分发点在编译期暴露未覆盖分支。
- **Page / Scroll 共用同一条件族**：二者语义同属「按需过滤」，不各自建族。
- **条件中枚举一律降级为基础类型**：`status` / `paymentStatus` 用 `Integer`，不用枚举类型。
- **条件不含分页参数**：分页由 `PageRequest` / `ScrollPosition` 单独传入，不塞进条件 `record`。
- **条件不含存储方言**：条件只表达业务意图（"商品名分词匹配"），翻译成 `match` 还是 `wildcard` 由源决定。

> ⚠️ **重要约束：跨族传参在编译期报错**。三族父类（`OneQueryCriteria` / `ListQueryCriteria` / `PageQueryCriteria`）互不继承，只有共同的空标记父接口 `QueryCriteria`。把 `OrderListQuery` 传给 `searchPage` 无法编译——这是刻意的隔离设计，用于防止「精确规约」与「按需过滤」语义混用。
>
> ⚠️ **`IOneQuerySearcher.search` 返回的是 `List<P>`，不是单条**。「取首条」由调用方（应用服务）决定。本示例的 `OrderReadService.queryOne` 也返回 `List<X>`——命名里的 `One` 指的是「条件精确命中一条」，不是方法返回值形态。

### 4.7 读侧入口：应用层 `OrderReadService`

读侧入口是**应用层读服务**。它 `implements IQueryApplicationService`，**注入领域层源接口**，按查询族分派并完成「查全量 → 裁剪」两跳；**不继承框架基类、不注入注册中心、不持有存储客户端**：

```java
// application/order/OrderReadService.java —— 应用层读服务（注入领域层源接口）
@Service
public class OrderReadService implements IQueryApplicationService {

    private final IOrderRedisSource redisSource;
    private final IOrderESSource esSource;

    public OrderReadService(IOrderRedisSource redisSource, IOrderESSource esSource) {
        this.redisSource = redisSource;
        this.esSource = esSource;
    }

    /** 按主键取投影（走 Redis 缓存副本）；缓存未命中返回 null。 */
    public <X extends IOrderProjection> X queryById(Long id, Class<X> projectionType) {
        var full = redisSource.getById(id);
        if (full == null) {
            return null;
        }
        return reduceWith(redisSource.getReducer(projectionType), full, projectionType);
    }

    /** 按主键批量取投影（走 Redis 缓存副本）。 */
    public <X extends IOrderProjection> List<X> queryByIds(List<Long> ids, Class<X> projectionType) {
        var reducer = redisSource.getReducer(projectionType);
        return redisSource.getByIds(List.copyOf(ids)).stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
    }

    /** 单条条件查询（走 ES 索引）；IOneQuerySearcher.search 返回列表。 */
    public <X extends IOrderProjection> List<X> queryOne(OrderOneQuery criteria, Class<X> projectionType) {
        var reducer = esSource.getReducer(projectionType);
        return esSource.search(criteria).stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
    }

    /** 列表条件查询（走 ES 索引）。 */
    public <X extends IOrderProjection> List<X> queryList(OrderListQuery criteria, Class<X> projectionType) {
        var reducer = esSource.getReducer(projectionType);
        return esSource.search(criteria).stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
    }

    /** 分页查询（走 ES 索引）；totalCount 取自裁剪前。 */
    public <X extends IOrderProjection> PageResult<X> queryPage(
            OrderPageQuery criteria, PageRequest pageRequest, Class<X> projectionType) {
        var reducer = esSource.getReducer(projectionType);
        PageResult<OrderEsProjection> result = esSource.searchPage(criteria, pageRequest);
        List<X> reduced = result.data().stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
        return PageResult.of(reduced, result.totalCount(), pageRequest);
    }

    private <S extends IOrderProjection, X extends IOrderProjection> X reduceWith(
            IReducer<S, X> reducer, S full, Class<X> projectionType) {
        if (projectionType.isInstance(full)) {
            return projectionType.cast(full);          // 目标即索引级全量投影 → 短路
        }
        if (reducer == null) {
            throw new ProjectionReducerNotFoundException("源未注册裁剪器: " + projectionType.getName());
        }
        return reducer.reduce(full);
    }
}
```

两跳模型：

| 跳 | 动作 | 承担者 |
| --- | --- | --- |
| 第 1 跳 查全量 | 调用源端口的查询族方法，拿回**索引级全量投影** | 源（基础设施） |
| 第 2 跳 裁剪 | `源.getReducer(目标类型)` → 逐条 `reduce` | 裁剪器（基础设施），由读服务编排 |

编写规则：

- **读服务只依赖领域源接口**：构造注入 `IOrderRedisSource` / `IOrderESSource`，不注入 `OrderEsSource` 具体类、不注入任何登记中心、不注入存储客户端。
- **每个方法内显式指定源**：本项目 ById 族走 Redis，One / List / Page 族走 ES，直接写在方法体里（见 §4.8）。
- **目标即索引级全量投影时短路**：`reduceWith` 里先 `projectionType.isInstance(full)`，命中直接 `cast`，不查 reducer、不新建对象。
- **`getReducer` 返回 `null` 时必须显式抛 `ProjectionReducerNotFoundException`**：基类不抛异常（返回 `null`），判断责任在读服务。
- **分页不重算总数**：`PageResult.of(reduced, result.totalCount(), pageRequest)` 的 `totalCount` 取自裁剪前。

> ⚠️ **重要约束：`totalCount` 与 `nextCursor` 必须取自裁剪前的全量结果**。分页 / 滚动在源内完成，裁剪只做逐条 `.map`、不改变集合规模。若误在裁剪后重新计算总数或游标，会得到错误的页边界与游标。
>
> ⚠️ **本示例未暴露 `queryScroll`**：`IPagedQuerySearcher` 提供 `searchScroll`，但读服务暂未包装。需要深翻页 / 全量导出时，按 `queryPage` 同构加一个方法即可（`nextCursor` 同样取自裁剪前）。

### 4.8 选源：由「源支持哪些查询族」在编译期决定

**没有运行期回源链，也没有「按投影类型选源」的注册中心查询**。「用哪个源」由两件事共同决定，且都在编译期：

1. **源端口 `extends` 了哪些查询族** —— `IOrderRedisSource` 只 extends ById 一族，故它只能被 `getById` / `getByIds` 调用；`IOrderESSource` extends 四族，全部可调用。
2. **读服务的哪个方法被声明为用哪个源** —— 写在方法体里。

```java
// 读服务内：ById 走缓存副本，条件/分页族走 ES 索引——显式、可读、无魔法
public <X extends IOrderProjection> X queryById(Long id, Class<X> projectionType) {
    var full = redisSource.getById(id);      // ← 缓存副本
    ...
}

public <X extends IOrderProjection> List<X> queryList(OrderListQuery criteria, Class<X> projectionType) {
    var reducer = esSource.getReducer(projectionType);
    return esSource.search(criteria)...      // ← ES 索引
}
```

调用方只看得到「要什么形状」：

```java
OrderSummaryProjection summary = orderReadService.queryById(orderId, OrderSummaryProjection.class);
PageResult<OrderEsProjection> page = orderReadService.queryPage(criteria, pageRequest, OrderEsProjection.class);
```

| 查询 | 本示例实际去向 | 依据 |
| --- | --- | --- |
| `queryById` / `queryByIds` | Redis 缓存副本 | 方法内注入的是 `IOrderRedisSource` |
| `queryOne` / `queryList`（条件族） | ES 索引 | `IOrderRedisSource` 未 extends 条件族，编译期无法调用 |
| `queryPage`（分页族） | ES 索引 | 同上 |

编写规则：

- **选源写在读服务方法体内，不外泄给调用方**：读服务对外不暴露 `ProjectionSource` 入参、也不暴露源接口本身。
- **缓存未命中不自动回退**：`queryById` 在 Redis 未命中时返回 `null`，**不会**回退 ES。这区别于旧的「`fallbackChain()` 回源链」设计。
  - 若业务需要「缓存未命中回源 ES」，就在读服务里显式写（`if (full == null) { return queryByIdFromEs(...); }`），把策略放在明处而不是框架里的隐式规则。
  - 若需要「缓存未命中即重建」，走对账路径（见 §5.3），而不是读路径。
- **同一个子投影可以有多个来源裁剪器**：`OrderSummaryProjection` 由 `OrderSummaryReducer`（源 ES）与 `OrderCacheSummaryReducer`（源 Redis）各自产出，`getReducer` 在**各自源内**独立定位，互不冲突（不像旧设计那样全局唯一）。
- **不存在「按源 id 反查源」的动作**：框架已删除源登记中心 `ProjectorRegistry`（见 §4.12），读服务注入领域源接口即可，没有任何反查环节。

> ⚠️ **为什么不再有框架级回源链**：回源链要求框架在运行时判断「这个源有没有挂对应条件族的检索器」，而判断依据（源实现了哪些族）**编译期就已确定**。把它搬到运行期既多一层间接、又把「源不支持某族」从编译错误降级成运行期异常。现设计直接让「不支持」无法编译。
>
> ⚠️ **读服务不含任何存储逻辑**：不注入 `ElasticsearchClient`，不拼查询 DSL，不写 `resolveSourceType` 之类的样板。

### 4.9 查询族实现：检索逻辑落在源内 + 条件工厂

源的查询族实现是**唯一接触存储客户端的读侧代码**，只产出**索引级全量投影**。

> ⚠️ **查询族方法的返回类型是索引级全量投影，不是业务子投影**。下面所有 `OrderEsProjection` 都不能写成 `IOrderProjection` 或 `OrderSummaryProjection`。

#### 4.9.1 按主键检索

```java
@Override
public OrderEsProjection getById(Object id) {
    return ProjectionExceptions.retrieve(() -> doGetById(id.toString()), "getById");
}

@Override
public List<OrderEsProjection> getByIds(List<Object> ids) {
    return ProjectionExceptions.retrieve(() -> doGetByIds(ids), "getByIds");
}

@SneakyThrows
private OrderEsProjection doGetById(String id) {
    return elasticsearchClient.get(req -> req
            .index(OrderEsTargets.ORDER_INDEX_NAME)
            .id(id), OrderEsProjection.class).source();
}

@SneakyThrows
private List<OrderEsProjection> doGetByIds(List<Object> ids) {
    List<String> docIds = ids.stream().map(Object::toString).toList();
    IdsQuery idsQuery = IdsQuery.of(q -> q.values(docIds));
    Query query = Query.of(q -> q.ids(idsQuery));
    return elasticsearchClient.search(req -> req
            .index(OrderEsTargets.ORDER_INDEX_NAME)
            .query(query)
            .size(docIds.size()), OrderEsProjection.class).hits().hits().stream()
            .map(Hit::source)
            .toList();
}
```

#### 4.9.2 按条件检索（族内 `instanceof` 分发）

```java
@Override
public List<OrderEsProjection> search(OrderOneQuery criteria) {
    return ProjectionExceptions.retrieve(() -> {
        if (criteria instanceof OrderOneQuery.LatestByCustomer c) {
            return searchLatestByCustomer(c);
        }
        return List.<OrderEsProjection>of();
    }, "search");
}

@Override
public List<OrderEsProjection> search(OrderListQuery criteria) {
    return ProjectionExceptions.retrieve(() -> {
        if (criteria instanceof OrderListQuery.TopByAmount c) {
            return searchTopByAmount(c);
        }
        if (criteria instanceof OrderListQuery.TopRecent c) {
            return searchTopRecent(c);
        }
        return List.<OrderEsProjection>of();
    }, "search");
}

@SneakyThrows
private List<OrderEsProjection> searchTopByAmount(OrderListQuery.TopByAmount condition) {
    Query query = buildCustomerStatusQuery(condition.customerId(), condition.status());
    return elasticsearchClient.search(req -> req
                    .index(OrderEsTargets.ORDER_INDEX_NAME)
                    .query(query)
                    .sort(sort -> sort.field(f -> f.field("totalAmount").order(SortOrder.Desc)))
                    .size(condition.top()), OrderEsProjection.class).hits().hits().stream()
            .map(Hit::source)
            .toList();
}
```

#### 4.9.3 分页 / 滚动（共用同一条件翻译）

```java
@Override
public PageResult<OrderEsProjection> searchPage(OrderPageQuery criteria, PageRequest pageRequest) {
    return ProjectionExceptions.retrieve(() -> doSearchPage(criteria, pageRequest), "searchPage");
}

@Override
public ScrollResult<OrderEsProjection> searchScroll(
        OrderPageQuery criteria, ScrollPosition cursor, int pageSize) {
    return ProjectionExceptions.retrieve(() -> doSearchScroll(criteria, cursor, pageSize), "searchScroll");
}

@SneakyThrows
private PageResult<OrderEsProjection> doSearchPage(OrderPageQuery condition, PageRequest pageRequest) {
    Query query = buildConditionQuery(condition);
    SearchResponse<OrderEsProjection> response = elasticsearchClient.search(req -> req
            .index(OrderEsTargets.ORDER_INDEX_NAME)
            .query(query)
            .sort(defaultSort())
            .from(pageRequest.offset())          // PageRequest 已算好 (pageNumber-1)*pageSize
            .size(pageRequest.pageSize())
            .trackTotalHits(t -> t.enabled(true)), OrderEsProjection.class);
    List<OrderEsProjection> data = response.hits().hits().stream().map(Hit::source).toList();
    Long total = Optional.of(response)
            .map(SearchResponse::hits)
            .map(HitsMetadata::total)
            .map(TotalHits::value)
            .orElse(0L);
    return PageResult.of(data, total, pageRequest);
}

@SneakyThrows
private ScrollResult<OrderEsProjection> doSearchScroll(
        OrderPageQuery condition, ScrollPosition cursor, int pageSize) {
    Query query = buildConditionQuery(condition);
    SearchResponse<OrderEsProjection> response = elasticsearchClient.search(req -> {
        var b = req.index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .sort(defaultSort())
                .size(pageSize);
        if (!cursor.isInitial()) {              // 首次查询不设 searchAfter
            b.searchAfter(cursor.cursor());
        }
        return b;
    }, OrderEsProjection.class);
    List<Hit<OrderEsProjection>> hits = response.hits().hits();
    List<OrderEsProjection> data = hits.stream().map(Hit::source).toList();
    String nextCursor = hits.isEmpty() ? null : hits.get(hits.size() - 1).id();
    return ScrollResult.of(data, nextCursor);
}

private Query buildConditionQuery(OrderPageQuery condition) {
    if (condition instanceof OrderPageQuery.ByConditions c) {
        return OrderEsConditionFactory.build(c);
    }
    return Query.of(q -> q.bool(BoolQuery.of(b -> b)));   // 空 bool
}
```

#### 4.9.4 条件工厂：`OrderEsConditionFactory`

条件翻译抽成**无状态的纯函数工厂**，不持有 `ElasticsearchClient`，便于单测直接断言 `Query` 结构：

```java
public final class OrderEsConditionFactory {

    private OrderEsConditionFactory() { }

    /** 精确 / 范围条件进 filter（不参与评分、可被缓存），文本分词条件进 must。 */
    public static Query build(OrderPageQuery.ByConditions condition) {
        List<Query> filter = Stream.of(
                        termLong("orderId", condition.orderId()),
                        termInt("status", condition.status()),
                        termInt("paymentStatus", condition.paymentStatus()),
                        termInt("shipmentStatus", condition.shipmentStatus()),
                        termLong("customer.customerId", condition.customerId()),
                        termKeyword("logisticsInfo.trackingNo", condition.trackingNo()),
                        decimalRange("totalAmount", condition.minAmount(), condition.maxAmount()),
                        dateRange("paidAt", condition.paidFrom(), condition.paidTo()),
                        dateRange("createdAt", condition.createdFrom(), condition.createdTo()))
                .flatMap(Optional::stream)
                .toList();
        List<Query> must = Stream.of(
                        match("remark", condition.remark()),
                        match("itemProductNamesText", condition.productName()))
                .flatMap(Optional::stream)
                .toList();
        return Query.of(q -> q.bool(BoolQuery.of(b -> b.must(must).filter(filter))));
    }
    // termLong / termInt / termKeyword / match / decimalRange / dateRange 为包级私有纯函数
}
```

编写规则：

| 规则 | 说明 |
| --- | --- |
| 一个源服务一种「索引级全量投影」 | 源内多个条件族各自一个 `search` / `searchPage` 方法；同一条件族若有多个物理索引，则一个索引一个源 |
| 只产出索引级全量投影 | 不产出业务子投影，也不按投影分支——业务子投影由裁剪器产出 |
| 条件翻译在源内（可外置为纯函数工厂） | 条件 → 存储查询 DSL 是存储方言，只在基础设施层出现 |
| `Optional` 字段用 `Optional::stream` 扁平化 | 分页条件全 `Optional`，未传即不追加该 clause |
| 精确 / 范围进 `filter`，文本分词进 `must` | 精确与范围条件不参与评分且可被缓存 |
| 禁止空 `bool` | 全字段未传时 `must` / `filter` 均为空列表，需确认存储对空 `bool` 的行为，必要时补 `matchAll` |
| 返回值不返回 `null` | 列表检索未命中返回 `List.of()`；按主键单条未命中返回 `null`（与 `findById` 一致） |
| 分页 / 滚动在源内完成 | 裁剪只做逐条转换；`totalCount` 与 `nextCursor` 由源产出 |
| 用 `ProjectionExceptions` 包裹 | 见 §4.10 |

### 4.10 检索异常处理：`ProjectionExceptions`

源内用 `ProjectionExceptions` 收敛样板，避免异常被重复嵌套：

```java
// 检索执行阶段（通信 / 反序列化失败）→ ProjectionRetrieveException，可重试
ProjectionExceptions.retrieve(() -> doSearchPage(...), "searchPage");

// 条件翻译阶段（条件非法 / 不支持）→ ProjectionConditionException，不可重试
ProjectionExceptions.translate(() -> buildConditionQuery(condition), "buildConditionQuery");
```

异常体系（位于 `io.pragmatic.ddd.repository.query.exception`，全部继承 `ProjectionException` → `PragmaticException`）：

| 异常 | 语义 | 可重试 | 触发场景 |
| --- | --- | --- | --- |
| `ProjectionException` | 读侧检索域抽象基类 | — | 可 `catch (PragmaticException)` 统一兜底 |
| `ProjectionRetrieveException` | 存储通信 / 远程错误 / 反序列化失败 | 是 | ES 超时、连接失败 |
| `ProjectionConditionException` | 条件无法翻译为该存储的检索请求 | 否 | 条件字段无对应索引 |
| `ProjectionSearcherNotFoundException` | 无对应检索器 | 否 | 按源取用检索器时缺失 |
| `ProjectionReducerNotFoundException` | 无对应裁剪器 | 否 | 源未注册目标子投影的裁剪器 |
| `ProjectionSourceConflictException` | 同一源 id 重复登记不同实例 | 否 | 使用方自建装配期源注册表时的冲突（框架内已无此入口） |
| `ProjectionSourceNotFoundException` | 按源 id 取源未登记 | 否 | 使用方自建按 id 取源的场合（框架内已无此入口） |
| `ProjectionSourceAmbiguousException` | 多源且无默认源 | 否 | 需要唯一定源的场景 |

> ⚠️ **不要把 `ProjectionSearcherNotFoundException` / `ProjectionReducerNotFoundException` 当「业务上查不到」处理**。它们表示装配缺失，属于接线 bug。**注意 `getReducer` 未注册时返回 `null` 而不抛异常**（见 §4.11），是否抛 `ProjectionReducerNotFoundException` 由读服务决定。
>
> `retrieve` 对已抛出的 `ProjectionException` 原样传递、不二次包装，因此调用方能准确区分「存储不可达」与「条件不支持」。

### 4.11 裁剪器：`OrderSummaryReducer` / `OrderCacheSummaryReducer`

裁剪器**直接实现框架接口 `IReducer<源投影类型, 子投影类型>`**，随源构造注入，由 `源.getReducer(Class<X>)` 按目标类型定位：

```java
// infrastructure/persistent/order/projection/reducer/OrderSummaryReducer.java
@Component
public class OrderSummaryReducer
        implements IReducer<OrderEsProjection, OrderSummaryProjection> {

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
        summary.setPaymentStatus(source.getPaymentStatus());
        summary.setPaymentStatusName(source.getPaymentStatusName());
        summary.setShipmentStatus(source.getShipmentStatus());
        summary.setShipmentStatusName(source.getShipmentStatusName());
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

- **直接实现框架接口**：`IReducer<OrderEsProjection, OrderSummaryProjection>`，泛型参数即「源形状 → 业务形状」。新增副本只需新增裁剪器实现类并注入该源。
- **源的类型参数是存储形状镜像**：这正是裁剪器留在基础设施层的原因。领域层只持有业务消费形状（如 `OrderSummaryProjection`）。
- **`reduce` 是纯函数**：无状态、无存储访问、无远程调用，可独立单测。
- **源为 `null` 返回 `null`**：由读服务过滤，不在裁剪器内抛异常。
- **不改变集合规模**：一次只转换一条；分页 / 滚动在源内完成。
- **可空嵌套值用 Optional**：与投影器保持一致的取值风格。
- **一个裁剪器服务一个 (源, 子) 组合**：新增子投影就新增裁剪器类，不在裁剪器内按目标类型分支。
- **同一子投影可由多个源各自产出**：`OrderSummaryReducer`（ES 源）与 `OrderCacheSummaryReducer`（Redis 源）产出同一个 `OrderSummaryProjection`，二者平级、互不引用，分别注入各自的源——`getReducer` 在源内定位，不会跨源冲突。

> ⚠️ **为什么必须有裁剪器，而不是直接查子投影**：存储侧 `_source` 过滤只能**裁剪字段路径**，不能**改变字段层级**。示例中 `OrderSummaryProjection.customerName` 是顶层字段，而 ES 文档中该值位于 `customer.customerName` 嵌套路径——直接反序列化成概要投影会拿到 `null`。层级重排只能在 Java 内存中完成，这正是裁剪器存在的核心价值。
>
> ⚠️ **裁剪器不能复用 `IAggregateProjector`**：`IAggregateProjector<T, P>` 要求 `T extends AggregateRoot<?>`，而索引级全量投影是 `@Data` 数据容器、并非聚合根。二者是平级且互不替代的抽象——投影器是「聚合根 → 投影」，裁剪器是「全量投影 → 子投影」。

### 4.12 装配：源零登记，只需 `OrderReconciliationConfig`

投影侧**没有任何装配代码**：源标注 `@Component` 即为 Bean，检索是源的方法、裁剪器随源注入，都不需要登记到任何中心。写侧订阅者与读侧读服务直接注入使用：

```java
@Component
public class OrderEsSource extends AbstractProjectionSource<Order, Long, OrderEsProjection>
        implements IOrderESSource {
    // 投影器与裁剪器列表在构造时注入；sync 由写侧订阅者调用
}
```

唯一的装配代码属于对账侧——贡献「聚合类型 → 仓储」这一条框架无法推导的接线：

```java
@Configuration
public class OrderReconciliationConfig {

    @Bean
    public ReconciliationContribution orderReconciliationContribution(OrderRepository orderRepository) {
        return registry -> registry.registerRepository(Order.class, orderRepository);
    }
}
```

副本（即源）由通用 `ReconciliationConfig` 注入 `List<IReadModelReplica<?>>` 统一 `registerReplicas`——**新增副本无需改任何装配代码**。

> ⚠️ **装配核心只有「贡献仓储」一条**。源无需登记：读服务注入的是 Spring Bean，写侧订阅者注入的也是同一个 Bean，缺少 `@Component` 会在启动期直接失败而非静默降级；漏登记仓储则会让对账取不到写模型版本。
>
> ⚠️ **裁剪器注入源构造器，参数声明为具体实现类**（如 `OrderSummaryReducer`），与源同处基础设施层；裁剪器不上升到领域层，替换实现只影响该源的装配。
>
> ⚠️ **每个源必须有唯一 `REPLICA_ID`**：两个源共用同一 id 会在对账注册表 `registerReplica` 时抛 `ReconcileDuplicateReplicaException`。

### 4.13 扩展模式：新增一个存储副本 / 索引

上文以 ES（`order_index`）为主线、Redis（缓存副本）为对照讲完了各组件的写法。它们的并存不是偶然，而是一个**可扩展的框架机制**。本节把它抽象成可复用的模式：**当你需要"再加一种存储 / 再加一个索引 / 再加一个副本"时，应该怎么改、改哪里**。

#### 模式本质：投影基础设施以「源」为扩展单位

- **一个 `源`（`AbstractProjectionSource` 子类）= 一份物理副本**。源在结构上绑定：`(副本标识, 聚合类型, 全量投影类型)` + 投影器 + 裁剪器列表 + 写读对账（materialize / purge / readVersion / rebuild / purgeOrphan）。**源自身即副本**（实现 `IReadModelReplica`），无需另建版本解析器 / 补同步器。
- **领域层为每份副本定义一个源端口**，按需 `extends` 查询族——这份副本「能被怎么查」在此声明。
- **新增副本 = 新增一个源对象 + 一个源端口**，无需登记到任何中心（原 `ProjectorRegistry` 已删除）；对账侧由 `List<IReadModelReplica<?>>` 集合注入自动登记，**对账装配代码无需改动**。

#### 什么时候才需要加副本（先判断，再动手）

| 诉求 | 是否应新增副本 | 依据 |
| --- | --- | --- |
| 某聚合详情页查得慢，想加 Redis 热缓存，按主键直取 | ✅ 加一个 Redis 源（源端口只 extends ById 族） | 读写热路径分离，Redis 只承担主键直取 |
| 同一聚合有两个物理索引（详情索引 A / 概要索引 B）承载不同查询 | ✅ 加一个 ES 索引源（各自 extends 各自要支撑的族） | 两个源互不冲突，`REPLICA_ID` 不同 |
| 只是想在既有 ES 上加字段 / 加一个查询场景 | ❌ 不是新副本，是改既有投影 DTO + 源内加 `search` 分发分支 / 加条件族 `record` | 结构内扩展，不新增源 |

> 判断核心：**副本是"同一份读模型的另一种物化 / 另一份物理拷贝"，不是"给既有副本加能力"**。前者新增源；后者在既有源内扩展。

#### 新增副本的落地清单（四步）

以「给已有 ES 订单副本，再加一份 Redis 主键直取缓存」为例：

**① 领域层——新投影 DTO + 常量 + 源端口**

```java
// domain/order/projection/OrderCacheProjection.java —— Redis 键存储的文档形状（含 version）
// domain/order/projection/OrderCacheTargets.java
public final class OrderCacheTargets {
    public static final String ORDER_CACHE_KEY_PREFIX = "order:agg:";
    public static final String REPLICA_ID = "redis:orders";          // 即源标识与副本标识
    public static final ReplicaKey REPLICA_KEY = new ReplicaKey(Order.class, REPLICA_ID);
}

// domain/order/projection/IOrderRedisSource.java —— 只声明要支撑的族
public interface IOrderRedisSource extends IProjectionByIdSearcher<OrderCacheProjection> {
    <X extends IAggregateProjection> IReducer<OrderCacheProjection, X> getReducer(Class<X> target);
}
```

**② 基础设施层——投影器 + 裁剪器 + 源**

```java
@Component
public class OrderRedisSource extends AbstractProjectionSource<Order, Long, OrderCacheProjection>
        implements IOrderRedisSource {

    public OrderRedisSource(
            OrderCacheProjector projector,
            OrderCacheSummaryReducer summaryReducer,
            RedisCommands<String, String> redis,
            OrderRepository orderRepository,
            @Value("${order.cache.redis.ttl:0}") long ttlSeconds) {
        super(ProjectionSource.of(OrderCacheTargets.REPLICA_ID),
                Order.class, OrderCacheProjection.class, projector, List.of(summaryReducer));
        this.redis = redis;
        this.orderRepository = orderRepository;
        this.ttlSeconds = ttlSeconds;
    }
    // materialize：写缓存键，内含版本号比较（旧版本不覆盖）
    // purge：del 键
    // readVersion：解析投影 JSON 的 version 字段，键缺失 / 损坏返回 0
    // rebuild：findById → sync
    // getById / getByIds：读同一批键
}
```

> ⚠️ **若新副本是「同存储、第二个索引」**（如 ES 详情索引 + 概要索引），它和 Redis 源的区别仅在：仍用 `ElasticsearchClient`、全量投影是新的 `OrderXxxProjection`。写路径 / 版本 / 对账各自独立——**每个源都有自己的一份 `readVersion` 与 `rebuild`**，副本标识由 `REPLICA_ID` 保证唯一。

**③ 装配——无需任何登记**

```java
@Component                                          // 新增源只需加这一个注解，没有装配类要改
public class OrderXxxSource extends AbstractProjectionSource<Order, Long, OrderXxxProjection>
        implements IOrderXxxSource { ... }
// 对账侧无需改动：源实现 IReadModelReplica，由 List<IReadModelReplica<?>> 自动入账
```

**④ 应用层——读服务接线 + 事件订阅**

```java
// OrderReadService：把新源端口注入，并在对应方法内使用
public OrderReadService(IOrderRedisSource redisSource, IOrderESSource esSource) { ... }

// 新增一个事件处理器把新副本挂进写路径
@Component
public class OrderRedisCacheHandle implements IOrderRedisCacheHandle {
    @Override
    public void handleEvent(OrderDataSyncEvent event) {
        Order order = orderRepository.findById(Long.valueOf(event.getEntityId()));
        if (order == null) {
            return;
        }
        redisSource.sync(order);
    }
}
```

#### ⚠️ 扩展模式的关键约束（照着做才不会踩坑）

| 约束 | 说明 | 违反后果 |
| --- | --- | --- |
| **新副本 = 新源 + 新源端口，不改既有源** | 复用已有的 ES 源类去"兼写 Redis"会把两种存储方言耦合进一个类 | 替换存储要改源，失去可扩展性 |
| **索引级投影 DTO 各自独立** | 每个源的全量投影对齐各自物理存储文档形状；不共享"通用投影" | 源与存储 Mapping 错位，检索结果失真 |
| **副本标识唯一，不手拼** | 每个源 `REPLICA_ID` 全局唯一 | 重复标识抛 `ProjectionSourceConflictException` / `ReconcileDuplicateReplicaException` |
| **能力按需 extends，不强求全集** | 缓存副本只 extends 它要支撑的族 | 强行给只读缓存补全检索器，制造无意义实现 |
| **每副本自带对账能力** | `readVersion` / `rebuild` 由各源独立实现 | 多个副本共用一个 `REPLICA_ID`，对账互相覆盖 |
| **写路径也要接线** | 新副本需有自己的事件处理器调用 `源.sync(aggregate)` | 副本永远为空，读侧一直 miss |

#### 反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 往既有 `OrderEsSource` 里加 Redis 分支 | 一个源类耦合两种存储，替换 / 新增都牵一发动全身 | 每种副本一个 `AbstractProjectionSource` 子类 |
| 新增缓存副本却硬造全量检索器 | 为不支撑的族补无意义实现，违背"能力按需 extends" | 源端口只 extends 该副本真实要支撑的族 |
| 只写了源却忘了写事件处理器 | 新副本无写路径，永远为空 | 写路径与读路径各自独立接线 |
| 多个副本共用一个 `REPLICA_ID` | 副本标识与副本非一一对应，`rebuild` 串写、版本互相干扰 | 每副本独立 `REPLICA_ID`，源自身即副本 |

> **举一反三**：这套"加副本 = 加源 + 加源端口"的模式对任何模块通用——`{Agg}EsSource` 之外再加 `{Agg}RedisSource`（缓存）或 `{Agg}XxxIndexSource`（第二个索引），都走「领域层加投影 DTO + 常量 + 源端口 → 基础设施写源（含投影器 / 裁剪器）→ 装配 register → 应用层读服务接线 + 事件订阅」四步；ES 与 Redis 只是两种已落地的存储实例，不是扩展的天花板。

### 4.14 事件订阅：应用层编排 + 绑定

事件路径由 `源.sync(aggregate)` 完成——源内部 project 后调自身 `materialize`，以 `aggregate.getOldVersion()` 作为 external 版本写入：

```java
@Component
public class OrderDataSyncEsProjectionHandle implements IOrderDataSyncEsProjectionHandle {

    private final OrderRepository orderRepository;
    private final OrderEsSource esSource;

    public OrderDataSyncEsProjectionHandle(OrderRepository orderRepository, OrderEsSource esSource) {
        this.orderRepository = orderRepository;
        this.esSource = esSource;
    }

    @Override
    public void handleEvent(OrderDataSyncEvent event) {
        Order order = orderRepository.findById(Long.valueOf(event.getEntityId()));
        if (order == null) {
            return;
        }
        esSource.sync(order);
    }
}
```

领域契约 `IOrderDataSyncEsProjectionHandle extends IDomainService, IHandle<OrderDataSyncEvent>`（标注 `@DomainService(category = EVENT_SUBSCRIBER)`）定义于 `domain/order/service/`，仅声明意图；应用层实现负责把领域事件与目标 `源` **组装编排**。`sync` 由源自身完成 project→materialize，投影为 `null` 时静默跳过。

订阅绑定在 `OrderEventSubscriberRegistry`（非 Spring 事件总线环境必须显式注册）。**一份副本一个订阅者**，它们平级、互不引用：

```java
@Configuration
public class OrderEventSubscriberRegistry {
    public OrderEventSubscriberRegistry(IEventRegistry evtManager,
                                        OrderDataSyncEsProjectionHandle orderDataSyncEsProjectionHandle,
                                        OrderRedisCacheHandle orderRedisCacheHandle,
                                        OrderPaidSmsNotifyHandle orderPaidSmsNotifyHandle,
                                        OrderPaidPointsGrantHandle orderPaidPointsGrantHandle,
                                        OrderReconcileHandle orderReconcileHandle) {
        evtManager.registerSubscriber("es", OrderDataSyncEvent.class, orderDataSyncEsProjectionHandle);
        evtManager.registerSubscriber("redis-cache", OrderDataSyncEvent.class, orderRedisCacheHandle);
        evtManager.registerSubscriber("reconcile", OrderDataSyncEvent.class,
                orderReconcileHandle, DeliveryPolicy.DELAYED);
        evtManager.registerSubscriber("sms-notify-on-order-paid", OrderPaidEvent.class, orderPaidSmsNotifyHandle);
        evtManager.registerSubscriber("points-grant-on-order-paid", OrderPaidEvent.class, orderPaidPointsGrantHandle);
    }
}
```

> `reconcile` 订阅者以 `DeliveryPolicy.DELAYED` 声明，复用同一条数据同步事件做**写后延迟复核**（`reconciliationManager.reconcile(Order.class, id)`），用于规避"事件刚发布、副本尚未同步完"的竞态。core 的 `Reconciler` 是纯同步原语，不做延迟复核。

## 5. 三条路径：写入、读取、对账

投影副本经三条路径维护，**转换逻辑共用**投影器与源（`AbstractProjectionSource`）。

### 5.1 事件物化（写路径）

```text
Order 业务方法 → markModified() / markCreated()
  └─ triggerDataSyncHook() → collectEvent(OrderDataSyncEvent.buildEvent(order))
       └─ IEventRegistry 订阅("es", OrderDataSyncEvent.class, OrderDataSyncEsProjectionHandle)
            │           订阅("redis-cache", ..., OrderRedisCacheHandle)
            └─ handleEvent(event)
                 ├─ orderRepository.findById(id)
                 └─ esSource.sync(order) / redisSource.sync(order)
                      ├─ 源持有的 projector project(order) → 全量投影
                      └─ 源 materialize(projection, aggregate.getOldVersion())
```

> `源.sync(order)` 由源自身完成 project 后调其 `materialize`；投影为 `null` 静默跳过。避免事件处理器内手写 project→materialize 双份逻辑。

### 5.2 读侧检索（读路径）

```text
调用方 → orderReadService.queryPage(OrderPageQuery.ByConditions, PageRequest.of(1, 20), OrderSummaryProjection.class)
  └─ OrderReadService（两跳编排，源由方法内显式指定）
       ├─ 第 1 跳 查全量：esSource.searchPage(criteria, pageRequest)
       │    ├─ OrderEsConditionFactory.build(condition)   // Optional 字段 → filter / must
       │    ├─ ES search（from = pageRequest.offset(), size = pageRequest.pageSize(), trackTotalHits）
       │    └─ PageResult.of(fullData, total, pageRequest)
       └─ 第 2 跳 裁剪：esSource.getReducer(OrderSummaryProjection.class)
            └─ OrderSummaryReducer.reduce(full) 逐条转换
                 └─ PageResult.of(summaryData, total /* 取自裁剪前 */, pageRequest)
```

若调用方传入的正是 `OrderEsProjection.class`（索引级全量投影），第 2 跳在 `reduceWith` 里短路、直接 `cast`。

> ⚠️ **重要约束：读路径不回源聚合根**。读模型是权威副本，读侧不允许为了「拿最新数据」而 `findById` 再投影——这会退化成同步阻塞调用，且绕过了副本。副本落后由对账路径修复，不由读路径补偿。
>
> ⚠️ **重要约束：分页在第 1 跳完成，第 2 跳只做逐条 `.map`**。`totalCount` 与 `nextCursor` 均取自裁剪前的全量结果；在裁剪后重新计算总数或游标会得到错误的页边界。

### 5.3 对账补偿（兜底路径）

```text
ReconciliationManager.reconcile(Order.class, id)
  └─ 遍历 registry.replicaKeysOf(Order.class)（O(1) 前缀索引）
       └─ 每个 ReplicaKey → Reconciler.reconcileAndResync(replica, repo, id)
            ├─ replica.readVersion(id)                 → V'（副本缺失返回 0）
            ├─ repo.currentVersion(id)                 → V（聚合不存在返回 -1）
            ├─ Reconciliation.of(V', V)                判定 CONSISTENT / STALE / ORPHAN / UNTRACKED
            ├─ STALE  → replica.rebuild(id)            findById → 源.sync(order)
            └─ ORPHAN → replica.purgeOrphan(id)        默认委托 purge(aggregateId)
  返回 Map<ReplicaKey, Reconciliation>
```

源即副本，因此**对账不需要任何适配器**：`OrderEsSource` / `OrderRedisSource` 直接被 `List<IReadModelReplica<?>>` 收集入账。

### 5.4 对比

| 维度 | 事件物化 | 读侧检索 | 对账补偿 |
| --- | --- | --- | --- |
| 触发 | 领域事件（正常更新） | 调用方主动查询 | 调度 / 延迟消息 / 手动 |
| 方向 | 聚合 → 投影 → 存储 | 存储 → 投影 | 聚合 → 投影 → 存储 / 删除 |
| 版本来源 | `aggregate.getOldVersion()`（与 `currentVersion` 一致） | 不参与版本 | 同上 |
| 关键构件 | Projector + 源（`sync` / `materialize`） | 源（查询族实现）+ Reducer | 源自身（`readVersion` / `rebuild` / `purgeOrphan`） |
| 目的 | 更新副本 | 取回副本 | 副本落后 / 残留时重建或清理 |

> ⚠️ **`rebuild` 必须从写模型当前快照重建**（`findById` → `sync`），而非重放那条被漏消费的事件——丢失的事件已不在事件流里，重放单条事件无法补齐副本。

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
// 首次（需读服务自行包装 queryScroll，见 §4.7）
ScrollResult<OrderEsProjection> first =
        esSource.searchScroll(condition, ScrollPosition.initial(), 100);

// 翻页：把上一页 nextCursor 原样回传，null 表示已到末页
Optional.ofNullable(first.nextCursor())
        .ifPresent(cursor -> esSource.searchScroll(
                condition, ScrollPosition.of(cursor), 100));
```

| 类型 | 成员 | 说明 |
| --- | --- | --- |
| `ScrollPosition` | `of(cursor)` / `initial()` / `cursor()` / `isInitial()` | 不透明游标；`isInitial()` 判断是否首次 |
| `ScrollResult` | `data()` / `nextCursor()` | `nextCursor == null` 表示无更多数据 |

> ⚠️ **重要约束：游标对调用方不透明**。游标由实现层编解码（示例为 ES 文档 `_id` + `searchAfter`），调用方只做「原样回传」，不得解析、拼接或构造。首次查询用 `ScrollPosition.initial()`，此时源不设 `searchAfter`。

:::: tip 分页还是滚动
需要总数与跳页（后台管理列表）用 `searchPage`；深翻页 / 全量导出 / 大数据量流式处理用 `searchScroll`——滚动不受 `from + size` 深分页上限约束，但拿不到总数。
::::

## 7. 版本语义

- **V（写模型权威版本）**：`IRepository.currentVersion(id)` 取 `aggregate.getOldVersion()`，聚合不存在返回 `-1`（判 ORPHAN）。写路径 `源.sync(aggregate)` 用的也是 `getOldVersion()`。
- **V'（副本版本）**：存储中的副本版本元数据（ES 为 `_version`；Redis 为投影 JSON 内嵌的 `version` 字段），由源的 `readVersion` 读取。
- **判定纯函数**：`Reconciliation.of(V', V)` —— `V'<0` → `UNTRACKED`；`V<0` 且 `V'≥0` → `ORPHAN`；`V'≥V` → `CONSISTENT`；否则 `STALE`。

| `readVersion` 返回值 | 判定 | 行为 |
| --- | --- | --- |
| `0`（副本缺失 / 损坏） | `STALE`（当 `V ≥ 0`） | `rebuild` 重建 |
| `-1` | `UNTRACKED` | **不触发重建** |
| `≥ V` | `CONSISTENT` | 无动作 |

> ⚠️ **External 版本约束（ES 场景）**：启用后不可依赖 ES 内部自增 `_version`；首次写入 `_version` 需与聚合新建版本对齐。传输层异常经 `@SneakyThrows` 上抛，**不会**被转换为 `-1`——「副本缺失」与「存储不可达」语义不同。
>
> ⚠️ **不要把「副本缺失」写成 `-1`**：本示例两个源的 `readVersion` 在副本缺失时都返回 `0`（判 STALE → 自动 `rebuild`）。返回 `-1` 会让副本永久落后且静默无告警。

## 8. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 领域包引入存储客户端 / Spring 依赖 | 契约层与存储耦合、无法替换存储 | 领域只定义投影 DTO、条件族与源端口；存储实现在 infrastructure |
| 把聚合根当投影返回 | 泄露写模型内部结构、破坏读写边界 | 定义中立投影 DTO，投影器裁剪字段 |
| 投影器内做存储读写 / 版本控制 | 投影器无法单测、存储细节扩散 | 投影器纯映射；持久化与检索在源 |
| 无理由地把金额统一转成「分」 | 单位换算成了默认约定，投影与聚合单位不一致、排查困难 | 用 `BigDecimal` 原样承载；仅当存储 Mapping 明确要求时才换算并注明 |
| 投影器与裁剪器各换算一次金额 | 重复进位，金额翻倍 | 换算只发生在聚合 → 全量投影这一次，裁剪器同单位直取 |
| 单位换算散落在字段赋值语句中 | 换算规则无法统一审计、改 Mapping 时易漏改 | 集中在具名方法内（如 `amountOf` → 换算方法），并注明换算原因 |
| 源端口 `extends` 了它并不支撑的查询族 | 为不存在的检索能力补空实现，调用方拿到空列表 | 只 `extends` 该副本真实要支撑的族；未 extends 即编译期不可调用 |
| 在源外另建 `*Searcher` 类并由注册中心登记 | 检索能力脱离源，「源支持什么族」退化为运行期查表 | 检索实现是源的方法 |
| 读服务继承框架查询基类 / 注入登记中心选源 | 框架已不提供查询基类，也不再有任何源登记中心 | 读服务 `implements IQueryApplicationService`，构造注入领域源接口 |
| 读服务把 `ProjectionSource` 或源接口作为方法入参 | 调用方必须懂源与副本才知道调哪个方法 | 选源写在读服务方法体内，对外只暴露业务查询方法 |
| 期望缓存未命中自动回退 ES | 现设计无运行期回源链，`queryById` 未命中返回 `null` | 需要回退就在读服务里显式写；需要自愈走对账路径 |
| 读服务 `OrderReadService` 注入 `ElasticsearchClient` 拼 DSL | 读侧存储方言泄漏到编排层，替换存储要改读服务 | DSL 翻译下沉到源（或外置的条件工厂） |
| 事件携带业务快照 | 延迟处理用旧数据覆盖新副本 | 事件只带聚合标识，处理时重新 load 聚合 |
| 真正的写失败（连接 / 映射错误）`catch` 后静默吞掉 | 副本真正落后被掩盖、对账失效 | 异常上抛，交给 `rebuild` 补偿 |
| External 版本冲突（409）上抛而非静默丢弃 | 旧事件迟到触发无谓 `rebuild`、反复重建已最新副本 | 捕获 `ResponseException` 仅 `log.debug` 静默丢弃 |
| `readVersion` 在副本缺失时返回 `-1` | 判 UNTRACKED，副本永久落后且无告警 | 副本缺失返回 `0`（判 STALE → rebuild）；`-1` 仅用于"明确不追踪" |
| 业务方手拼 `new ProjectionSource("es:orders")` | Registry 中 key 不一致、寻址失败 | 引用 `OrderEsTargets` / `OrderCacheTargets` 已定义的 `REPLICA_ID` 常量 |
| 为每个查询场景建一个条件类 / 一个检索器 | 类爆炸、条件失去穷举约束 | 按族建 `sealed interface`，族内场景为 `record`，源按族实现 |
| 跨族复用条件（`ListQuery` 传给 `searchPage`） | 编译期报错，语义混用 | 分页场景建 `PageQueryCriteria` 子族，字段全 `Optional` |
| 分页参数塞进条件 `record` | 条件与分页语义耦合、无法复用条件做滚动 | 分页由 `PageRequest` / `ScrollPosition` 单独传入 |
| 条件中使用枚举类型 | 跨进程传输与枚举演进互相牵制 | 条件与投影中枚举一律降级为 `Integer` / `int` + 文案 `String` |
| 把 `ProjectionSearcherNotFoundException` / `ProjectionReducerNotFoundException` 当「业务上查不到」处理 | 接线 bug 被掩盖成「查不到」 | 异常上抛不降级；注意 `getReducer` 未注册返回 `null`，由读服务显式抛异常 |
| 读路径为拿最新数据回源聚合根 | 同步阻塞、绕过副本、副本落后无法暴露 | 读路径只读副本，落后由对账修复 |
| 绕过源手写投影更新 | 事件路径与 resync 逻辑不一致 | 共用 `AbstractProjectionSource.sync` |
| resync 重放单条事件 | 丢失的事件无法补齐副本 | 从写模型当前快照重建（findById → project → materialize） |
| 投影用聚合根的 Lombok 约定 | 数据容器被 @Builder 等污染 | 投影 DTO 用 `@Data`；聚合根禁用 `@Data` |
| 裁剪后重新计算 `totalCount` / `nextCursor` | 页边界与游标错误 | 分页留在源内，二者取裁剪前的全量结果 |
| 裁剪器内查库 / 调远程 | 破坏纯函数性，造成 N+1 | `reduce` 只做内存转换，所需数据由源一次取全 |
| 一个裁剪器内按目标类型 `instanceof` 分支 | 与「一个实例服务一个 (源, 子)」契约相悖 | 一个子投影一个裁剪器 |
| 忘记录入 `materialize` 的强转 | 编译期报错（形参是 `IAggregateProjection`） | 实现第一行强转为本源的 `P` |
| 新增副本却漏了事件订阅 | 副本永远为空，读侧一直 miss 且无报错 | 写路径与读路径各自独立接线 |
| 多个副本共用一个 `REPLICA_ID` | 登记期即冲突，对账互相覆盖 | 每副本独立 `REPLICA_ID`，源自身即副本 |

---

## 下一步

- [投影读模型](../core/projection-read.md)：框架 `repository.query` 通用能力详解
- [仓储写模型](../core/repository-write.md)：聚合持久化、`currentVersion` 权威版本 V
- [聚合设计原则](./aggregate-design.md)：聚合根 `triggerDataSyncHook` 与事件收集
- [仓储设计原则](./repository-design.md)：写模型仓储定位与落地
- [应用服务协作](./application-collaboration.md)：`ReadService` 与 `WriteService` 的形态差异
- [Elasticsearch 配置设计原则](./elasticsearch-config.md)：`ElasticsearchClient` 三层客户端构建与投影物化配套
- [事件建模指南](./event-modeling.md)：事件只携带聚合标识的建模规范
