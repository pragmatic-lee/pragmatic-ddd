# 仓储设计原则

> 本文档介绍使用 Pragmatic DDD 进行仓储设计的最佳实践与常见反模式：先明确仓储的定位与边界，再说明框架已经提供的仓储契约与抽象基类，最后落到命令侧仓储实现、MyBatis 落地与测试编写规范。

## 1. 仓储设计原则

### 1.1 仓储的定位：领域层接口，基础设施层实现

仓储（Repository）是**聚合根的持久化出入口**，负责在聚合根与持久化存储之间架桥。接口定义在**领域层**（domain），实现落在**基础设施层**（infrastructure），领域层依赖接口、不感知任何持久化细节。

在 order-example 中表现为两层结构：

```text
domain.order.repository.AbstractOrderRepository    // 领域层抽象类（继承框架 AbstractRepository）
  └─ infrastructure.order.repository.OrderRepository // 基础设施层实现（注入 SqlSessionTemplate）
```

```java
// 领域层：只声明"这个聚合怎么存取"，不出现任何 MyBatis / Spring 依赖
public abstract class AbstractOrderRepository extends AbstractRepository<Long, Order> {
}

// 基础设施层：实现真实落库，注入具体持久化组件
@Repository
public class OrderRepository extends AbstractOrderRepository {
    private final SqlSessionTemplate sqlSessionTemplate;
    // doInsert / doUpdate / doRemove / findById ...
}
```

**设计含义**：聚合根的调用方（应用层）只依赖领域层的 `AbstractOrderRepository`，即便把 MyBatis 换成 JPA / JdbcTemplate，应用层代码零改动。

### 1.2 写读分离：命令侧仓储与查询侧契约

仓储分两个方向，职责不同，**不要混在一个类里**：

- **命令侧（写模型，C 侧）**：`IRepository<ID, T>`——聚合的增删改，入参/出参都是**聚合根对象**。
- **查询侧（读模型，Q 侧）**：`io.pragmatic.ddd.repository.query` 子包——聚合级查询契约（`IQueryById` / `IQueryByIds` / `IQueryList` / `IQueryPage` / `IQueryOne` / `IQueryScroll`）与读模型投影（`IAggregateProjector`）。

命令侧操作的是"要落库的聚合"，查询侧投影出"页面要展示的读模型"。聚合根写仓储不做复杂查询，复杂查询交给查询侧。

### 1.3 只收聚合根对象，不收字段

仓储的 `insert` / `update` / `remove` 入参**必须是聚合根对象**，不允许把聚合根的各个字段拆散逐个传入，也不允许直接操作聚合根内部子实体的裸列表。

```java
// ✅ 推荐：整存整取聚合根
repo.insert(order);

// ❌ 反模式：向仓储传入散装字段
repo.insert(orderId, customer, status, totalAmount);
repo.updateOrderItems(orderId, items); // 绕过聚合根直接改子表
```

理由：聚合根是唯一一致性边界，仓储从聚合根整体出发落库，才能保证主表与子表、聚合字段与版本号在**同一个事务内**保持一致。

### 1.4 一个聚合根对应一个仓储

仓储以聚合根为单位划分，**一个聚合根一个仓储**。子实体（如 `OrderItem`）不是独立的持久化入口，它随聚合根一起存取，不能单独为子实体建仓储。

```text
✅  OrderRepository     → Order 聚合（含 OrderItem 子实体）
❌  OrderItemRepository → 子实体不应有独立仓储
```

### 1.5 事务边界由应用层负责

仓储**不管理事务**。`insert` / `update` / `remove` 只是发出落库动作，事务边界由调用方（应用层 `@Transactional`）负责。这也是为什么 `AbstractRepository` 的模板方法里没有 `@Transactional`——保持仓储纯粹，事务归属应用层编排。

> 注意：聚合根子集合为 MyBatis 懒加载时，访问子集合必须在**事务内**，否则懒加载代理取不到连接会抛异常。order-example 的测试正是靠 `@SpringBootTest` 默认的事务包装来保证事务内访问。

---

## 2. 框架已提供的能力

框架通过 `IRepository` 接口与 `AbstractRepository` 基类托管了仓储的**通用骨架**。实现类只需关注"这个聚合特有的落库细节"，不要重复造通用轮子。

### 2.1 IRepository 契约

`IRepository<ID, T>`（`T extends AggregateRoot<ID>`）定义了写模型持久化契约：

| 方法 | 类型 | 职责 |
| --- | --- | --- |
| `insert(T)` | 抽象 | 插入聚合根 |
| `update(T)` | 抽象 | 更新聚合根 |
| `save(T)` | 默认 | 按 `aggregateRoot.isNew()` 路由 insert / update |
| `findById(ID)` | 抽象 | 按主键查询，未命中返回 `null` |
| `remove(T)` | 抽象 | 按主键删除聚合，**必须提供真实删除逻辑** |
| `existsById(ID)` | 默认 | `findById(id) != null` |
| `currentVersion(ID)` | 默认 | 返回写模型当前版本，聚合不存在返回 `-1`（供 ORPHAN 判定） |

要点：

- **`save`**：默认实现根据 `isNew()` 路由——新建聚合走 insert，重建聚合走 update。聚合根在业务构造时调用 `markNew()` 打上新建标记。
- **`remove`**：接口层**没有默认空实现**，实现方必须提供真实删除（物理删或软删由实现决定），避免误以为删了其实什么都没做。
- **`existsById` / `currentVersion`**：默认实现都基于 `findById`。高频对账场景可覆写为"只查主键列 / 只查版本列"，避免加载整个聚合。

### 2.2 AbstractRepository 模板方法

`AbstractRepository<ID, T>` 实现了 `IRepository`，用**模板方法模式**接管通用流程：

```java
@Override
public final void insert(T aggregateRoot) {
    aggregateRoot.triggerDataSyncHook(); // 落库前触发聚合根数据同步钩子（收集异构事件）
    this.doInsert(aggregateRoot);        // 子类实现真实落库
}
```

`insert` / `update` / `remove` 三个 `final` 方法统一做了两件事：

1. 先调用聚合根的 `triggerDataSyncHook()`——落库前让聚合根有机会发异构事件；
2. 再委托子类实现的 `doInsert` / `doUpdate` / `doRemove` 完成真实持久化。

**子类只需实现三个 `protected abstract` 方法**，不能覆写 `final` 的 insert / update / remove——通用钩子逻辑由框架保证不被绕过。

---

## 3. 仓储实现规范

### 3.1 覆写模板方法，不直接实现接口

写仓储时，**继承 `AbstractRepository` 并覆写 `doInsert` / `doUpdate` / `doRemove`**，而不是直接实现 `IRepository` 的 `insert` / `update` / `remove`。这样 `triggerDataSyncHook()` 被框架统一调用，事件机制不会因为子类覆写而失效。

```java
@Repository
public class OrderRepository extends AbstractOrderRepository {

    @Override
    protected void doInsert(Order aggregateRoot) {
        sqlSessionTemplate.insert("OrderMapper.insert", aggregateRoot);
        // 级联落库子实体（整存）
    }

    @Override
    protected void doUpdate(Order aggregateRoot) {
        // CAS 更新 + 子集合差量同步
    }

    @Override
    protected void doRemove(Order aggregateRoot) {
        // 先删子表再删主表，避免孤儿行
    }
}
```

`findById` 是查询方法，不受 `final` 限制，按需直接覆写实现。

### 3.2 乐观锁 CAS：以版本列做条件更新

聚合根有 `oldVersion`（上次持久化版本）与 `getNewVersion()`（递增后的版本）。`doUpdate` 必须以 `version = oldVersion` 作为 WHERE 条件做 CAS 更新，影响行数为 0 即视为并发冲突：

```java
@Override
protected void doUpdate(Order aggregateRoot) {
    int affected = sqlSessionTemplate.update("OrderMapper.update", aggregateRoot);
    if (affected == 0) {
        throw new OptimisticLockingFailureException(
                "订单 [" + aggregateRoot.getEntityId() + "] 乐观锁冲突，期望版本 ["
                        + aggregateRoot.getOldVersion() + "]");
    }
    syncTrackedList(aggregateRoot);
}
```

对应 XML 中 UPDATE 语句的条件部分：

```xml
WHERE order_id = #{entityId}
  AND version = #{oldVersion}
```

**设计含义**：`oldVersion` 由 `findById` 从 DB 版本列回填；更新时用内存中的 `oldVersion` 比对 DB，命中才写 `newVersion`。仓储实现不要自建 version 字段，也不要绕过 CAS 直接 UPDATE。

### 3.3 子集合差量同步：TrackedList 三桶

一对多子集合（如 `OrderItem`）用 `TrackedList<OrderItem, Long>` 承载。它把集合拆成**基线 / 新增（append）/ 删除（remove）**三桶，仓储只需消费 append 桶发 INSERT、消费 remove 桶发 DELETE，**不必全删全插**。

```java
private void syncTrackedList(Order aggregateRoot) {
    TrackedList<OrderItem, Long> orderItems = aggregateRoot.getOrderItems();
    List<OrderItem> removedItems = orderItems.getRemovedItems();
    if (!removedItems.isEmpty()) {
        List<Long> removedIds = removedItems.stream().map(OrderItem::id).toList();
        sqlSessionTemplate.delete("OrderMapper.deleteOrderItemsByIds", removedIds);
    }
    List<OrderItem> appendedItems = orderItems.getAppendedItems();
    if (!appendedItems.isEmpty()) {
        this.batchInsertOrderItems(aggregateRoot.getEntityId(), appendedItems);
    }
}
```

要点：

- `getRemovedItems()` 待 DELETE，`getAppendedItems()` 待 INSERT，`getAllItems()` 是当前逻辑视图。
- 仓储层**只读这三桶做增量持久化**，不负责自己 diff——diff 已由 `TrackedList` 在聚合根业务方法里完成。
- 子项字段修改在 `TrackedList` 语义里是 `remove(旧) + append(新)`，仓储层无须发"原地 UPDATE 子表"的语句。

> 详见 [聚合设计原则](./aggregate-design.md) 中 `TrackedList` 的相关约定。

### 3.4 删除：先子后主，避免孤儿行

`doRemove` 删除聚合时，若子表无外键约束，必须先删除子表再删主表，否则会留下孤儿行。

```java
@Override
protected void doRemove(Order aggregateRoot) {
    sqlSessionTemplate.delete("OrderMapper.deleteOrderItemsByOrderId", aggregateRoot.getEntityId());
    sqlSessionTemplate.delete("OrderMapper.deleteById", aggregateRoot.getEntityId());
}
```

### 3.5 纯持久化，无业务逻辑

仓储实现**只做数据存取**，不做业务判断、不做数据组装、不触发领域事件（事件由聚合根经 `triggerDataSyncHook` 统一处理）。任何"要不要删""金额对不对"的判断都属于应用层或领域层，不进仓储。

---

## 4. MyBatis 实现专题

### 4.1 纯 XML Mapper，无 Java 接口

order-example 的 Mapper 是**纯 XML**（`OrderMapper.xml`），没有对应的 Java 接口类。`namespace` 作为语句命名空间标识，由 `mybatis-config.xml` 的 `<mappers>` 统一加载，调用方通过 `sqlSession.insert/update/select/delete("OrderMapper.xxx", param)` 使用。

```xml
<mapper namespace="OrderMapper">
    <insert id="insert" parameterType="...Order">...</insert>
    <update id="update" parameterType="...Order">...</update>
    <delete id="deleteById" ...>...</delete>
    <select id="selectById" resultMap="orderResultMap">...</select>
</mapper>
```

**优势**：语句与领域模型解耦，SQL 集中可审阅，不因接口层多一层而增加维护成本。

### 4.2 显式 resultMap，关闭自动映射

所有查询列都应在 `resultMap` 中**显式映射**，`autoMapping="false"`。这样字段缺失、拼写错误能在开发期暴露，且能精确控制乐观锁基线列、子集合懒加载代理的写入。

```xml
<resultMap id="orderResultMap" type="...Order" autoMapping="false">
    <id property="entityId" column="order_id"/>
    <!-- ... 全字段显式映射 ... -->
    <!-- 乐观锁基线：DB version 列读入 oldVersion -->
    <result property="oldVersion" column="version"/>
    <!-- 子集合委托 TrackedList resultMap，懒加载 -->
    <association property="orderItems" resultMap="orderItemTrackedListResultMap"/>
</resultMap>
```

### 4.3 TrackedList 懒加载：不触发子查询的实例化

子集合 `orderItems` 是 `TrackedList`，用嵌套 resultMap 实例化，并把 MyBatis 懒加载代理通过 `<collection property="initCollection">` 写入 `TrackedList.initCollection`（字段反射填充）。`fetchType="lazy"` 保证访问子集合时才触发子查询。

```xml
<resultMap id="orderItemTrackedListResultMap"
           type="io.pragmatic.ddd.track.TrackedList"
           autoMapping="false">
    <collection property="initCollection"
                ofType="...OrderItem"
                column="order_id"
                select="OrderMapper.selectOrderItemsByOrderId"
                fetchType="lazy"/>
</resultMap>
```

> `TrackedList` 构造器把基线列表**直接赋引用、不遍历**，所以传入懒加载代理不会提前触发子查询；首次访问时才物化为内部可变 `ArrayList`。

### 4.4 乐观锁版本列

INSERT / UPDATE 都写 `AggregateRoot.getNewVersion()`，UPDATE 以 `version = oldVersion` 做 CAS 比对。查询时 DB 版本列读入 `oldVersion` 作为乐观锁基线。

```xml
<!-- INSERT 写 newVersion -->
#{newVersion}

<!-- UPDATE 写 newVersion，CAS 命中旧版本 -->
version = #{newVersion}
WHERE order_id = #{entityId}
  AND version = #{oldVersion}
```

### 4.5 查询列 vs 映射的边界

逻辑外键列（如 `order_id` 之于 `OrderItem`）是查询过滤键，不是领域属性，**不纳入查询列**，保证所有被查询列都被 resultMap 显式映射。子实体的 `order_id` 由批插入的参数 `Map` 单独传入，不映射进 `OrderItem`。

```java
Map<String, Object> param = Map.of("orderId", orderId, "items", items);
sqlSessionTemplate.insert("OrderMapper.batchInsertOrderItems", param);
```

---

## 5. 仓储测试规范

仓储测试是**集成式单元测试**：装配真实的持久化链路（MyBatis + 数据源 + 事务管理器 + 仓储），不加载无关中间件（ES / Redis / MQ），真实执行 SQL，验证映射与落库行为。

### 5.1 装配方式

只装配 MySQL 链路的最小 Spring 上下文，避免启动整个应用：

```java
@SpringBootTest(classes = OrderRepositoryTest.TestConfig.class)
class OrderRepositoryTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({MySqlConfig.class, OrderRepository.class})
    static class TestConfig {
        // 仅装配 MyBatis + 数据源 + 事务管理器 + OrderRepository
    }
}
```

### 5.2 事务与清理策略

- 默认 `@SpringBootTest` 提供事务包装，保证**事务内访问懒加载子集合**不报错。
- 用例按需 `@Rollback(false)` 真实提交，便于落库后核验。
- `@AfterEach` 后置清理本次写入数据（先删子表再删主表），避免污染库。

```java
@AfterEach
void cleanup() {
    for (Long orderId : createdOrderIds) {
        jdbcTemplate.update("DELETE FROM t_order_item WHERE order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM t_order WHERE order_id = ?", orderId);
    }
    createdOrderIds.clear();
}
```

### 5.3 用例覆盖点

仓储测试至少覆盖：

| 关注点 | 用例 |
| --- | --- |
| 整存整取 | insert 后主表 + 全部子实体落库 |
| 懒加载 | findById 返回完整聚合，事务内访问子集合触发懒加载子查询 |
| 未命中 | findById 不存在的 id 返回 null |
| 存在性 | existsById 对存在/不存在返回 true/false |
| 版本 | currentVersion 返回 DB 当前乐观锁版本 |
| 字段更新 | update 后字段变更、版本号递增 |
| 乐观锁冲突 | 并发改写 DB 版本后 update 抛 `OptimisticLockingFailureException` |
| 差量同步 | update 后新增项落库、被删基线项物理删除，子表与内存一致 |
| 删除 | remove 删除主表同时级联清理子表，无孤儿行 |

---

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 仓储做业务逻辑 | 领域逻辑泄漏到基础设施，难以复用与测试 | 仓储只做数据存取，业务判断在应用层/领域层 |
| 直接实现 IRepository 的 insert/update/remove | 绕过 `triggerDataSyncHook`，异构事件失效 | 继承 `AbstractRepository`，覆写 `doXxx` 模板方法 |
| 向仓储传散装字段 / 直接操作子表 | 破坏聚合一致性边界 | 整存整取聚合根，子集合随聚合一起落库 |
| 为子实体单独建仓储 | 破坏聚合边界，子实体游离管理 | 一个聚合根一个仓储 |
| 全删全插子集合 | 无效 IO、破坏外键引用 | 用 `TrackedList` 三桶做增量 INSERT/DELETE |
| 删除时不清理子表 | 产生孤儿行 | 先删子表再删主表 |
| 绕过版本列直接 UPDATE | 乐观锁失效，并发覆盖 | 以 `version = oldVersion` 做 CAS，冲突即抛异常 |
| 查询列混入逻辑外键且不显式映射 | 字段隐射错乱、维护困难 | 显式 resultMap + `autoMapping="false"`，逻辑外键不入查询列 |
| 仓储里开启事务注解 | 事务边界混乱、与应用层编排冲突 | 事务由应用层 `@Transactional` 统一负责 |
| 把复杂报表查询塞进写仓储 | 写仓储臃肿、读模型被拖累 | 复杂查询交给查询侧（`query` 子包契约） |

---

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根边界、`TrackedList` 子集合、版本号与乐观锁
- [普通实体设计](./entity-design.md)：聚合内子实体的设计
- [应用服务层协作](./application-collaboration.md)：事务边界、操作/事件顺序与工作单元清理
- [值对象最佳实践](./value-object.md)：值对象的持久化序列化往返
- [事务性发件箱](./transactional-outbox.md)：`triggerDataSyncHook` 与异构事件投递
- [聚合根实现详解](../core/domain-modeling.md)
- [领域事件体系](../core/domain-events.md)
