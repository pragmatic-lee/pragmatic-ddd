# 变更追踪

> 本文档说明 `io.pragmatic.ddd.track` 包提供的变更追踪能力：把一对多 / 键值集合的状态拆为基线 / 新增 / 删除三桶，使持久化只做增量 `INSERT` / `DELETE` / `UPDATE`，而非"全删全插"。相关文档：[领域建模](./domain-modeling.md) · [仓储](./repository.md) · [MyBatis 集成](../integration/mybatis.md)。

## 1. 概述

### 1.1 核心定位

变更追踪容器用于管理聚合内部**有独立 DB 行的集合元素**（子实体或独立表值对象）的生命周期差量。容器在内存中维护三桶状态，应用层按领域语义调用 `append` / `remove` / `update` / `put`，持久化阶段只消费差量发增量 SQL。

```text
initCollection / initMap   基线（DB 加载时已有，不变）
appendList / putMap        新增 / 待 PUT 桶 → 持久化发 INSERT
removeList / removeKeys    删除桶 → 持久化发 DELETE
```

| 容器 | 行标识来源 | 适用场景 |
| --- | --- | --- |
| `TrackedList<T, ID>` | `T implements ITrackable<ID>` → `T.id()` | 有序子项集合，子项有独立行标识 |
| `TrackedMap<K, V>` | `key` 即行标识 | 键值映射，key 提供原地 `UPDATE` 能力 |

### 1.2 适用对象与边界

适用对象：**有独立 DB 行的对象类型**（实体或独立表值对象），不处理基础类型（`String`/`Integer`）或无身份的内嵌值对象。内嵌值对象的集合整体随父表替换，无需本容器。

### 1.3 概念层级

```text
ITrackable<ID>                  行标识契约（TrackedList 元素必须实现）
  ↑
TrackedList<T, ID>             一对多集合追踪（基线 / append / remove 三桶）
TrackedMap<K, V>               键值追踪（基线 / put / removeKeys 三桶，支持物理 UPDATE）
```

## 2. 核心概念详解

### 2.1 `ITrackable<ID>` 行标识契约

```java
public interface ITrackable<ID> {
    ID id();  // 持久化行标识（用于 DELETE/UPDATE 定位行记录）
}
```

所有被 `TrackedList` 追踪的元素必须实现此接口。`ITrackable.id()` 返回的是持久化层的行标识，与 `IEntity.getEntityId()`（领域身份）属于不同层次，不可混用：实体通常返回领域 ID，独立表值对象返回业务键。

:::: tip 与 IEntity 的区别
`ITrackable.id()` 是持久化层的行标识，与 `IEntity.getEntityId()`（领域身份）属于不同层次，不可混用。实体通常返回领域 ID，独立表值对象返回业务键。
::::

### 2.2 `TrackedList<T, ID>` 一对多集合追踪

#### 2.2.1 三桶设计

```text
initCollection（基线）    ← DB 加载时已有的子项，不变
appendList（新增桶）      ← 本次新增的子项 → 持久化发 INSERT
removeList（删除桶）      ← 本次移除的子项 → 持久化发 DELETE
```

构造器有两种形态：无参构造（`initCollection = 空`）用于新建聚合；带 `List<T>` 构造用于 DB 加载，且**直接赋引用、不遍历、不拷贝**。

#### 2.2.2 基本用法

```java
public class Order extends AggregateRoot<Long> {

    private TrackedList<OrderItem, Long> items;

    public Order() {
        this.items = new TrackedList<>();  // 空基线（新建订单）
    }

    // 从 DB 加载时（通常由 MyBatis 反射填充内部 initCollection，无需此 setter）
    public void setItems(List<OrderItem> loadedItems) {
        this.items = new TrackedList<>(loadedItems);  // 带基线
    }

    // 新增子项
    public void addItem(OrderItem item) {
        this.items.append(item);
    }

    // 持久化时获取增量
    public List<OrderItem> getItemsToInsert() {
        return this.items.getAppendedItems();
    }

    public List<OrderItem> getItemsToDelete() {
        return this.items.getRemovedItems();
    }
}
```

#### 2.2.3 更新子项

"更新"= 用新对象替换旧对象 = `remove(旧) + append(新)`，旧项移入删除桶（发 DELETE），新项进入新增桶（发 INSERT）：

```java
public void updateItem(OrderItem oldItem, OrderItem newItem) {
    this.items.update(oldItem, newItem);
}
```

#### 2.2.4 条件移除

```java
public void removeOutOfStockItems() {
    List<OrderItem> removed = this.items.removeItems(item -> item.isOutOfStock());
    // 符合条件的子项从基线/新增桶移入删除桶
}
```

#### 2.2.5 全量替换

```java
public void replaceAllItems(List<OrderItem> newItems) {
    this.items.clearAndAppend(newItems);
    // 当前所有基线移入删除桶（发 DELETE）
    // 新列表进入新增桶（发 INSERT）
}
```

#### 2.2.6 读取逻辑视图

```java
// 当前逻辑视图 = 基线 + 新增（不含已移除）
List<OrderItem> currentItems = this.items.getAllItems();

// 仅基线
List<OrderItem> initItems = this.items.getInitItems();

// 仅新增
List<OrderItem> appendedItems = this.items.getAppendedItems();

// 仅删除
List<OrderItem> removedItems = this.items.getRemovedItems();
```

### 2.3 `TrackedMap<K, V>` Map 追踪

`TrackedMap` 与 `TrackedList` 对称设计：构造不遍历、不可变读 API、去回调。Map 的 `key` 是内置行标识，因此**不需要** `ITrackable` 约束；`key` 同时提供"原地定位"能力——同一 `key` 上 `put` 替换 value 语义上即"对 key 这一行发一条 UPDATE"，无需删旧插新。

| 操作 | 落库语义 | 消费 API |
| --- | --- | --- |
| `put(k, v)` 且 k 不在基线 | INSERT | `getInsertedEntries()` |
| `put(k, v)` 且 k 已在基线 | UPDATE 同一行 | `getUpdatedEntries()` |
| `remove(k)` 且 k 在基线 | DELETE | `getRemovedEntries()` |

```java
TrackedMap<String, OrderItem, Long> itemMap = new TrackedMap<>();

itemMap.put("SKU-001", item1);     // 新增（INSERT 候选）
itemMap.put("SKU-001", item2);     // 同 key 替换（UPDATE 候选）
itemMap.remove("SKU-002");         // 删除（DELETE 候选）
OrderItem item = itemMap.get("SKU-001"); // 读取
```

若需"删旧插新"语义，可对同一 key 先 `remove` 再 `put`：该 key 从 `getUpdatedEntries()` 移到 `getInsertedEntries()`（净重新新增），持久化发 INSERT 新行。

## 3. 与 MyBatis 集成

`pragmatic-ddd-mybatis` 与变更追踪容器是"零侵入"配合：实体层不写任何 MyBatis 代码，容器通过 MyBatis 的 `<association>` 嵌套 `<collection>` 直接把子项填进内部 `initCollection` / `initMap` 字段。

### 3.1 前置条件

| 项 | 要求 |
| --- | --- |
| TrackedList 子项 | 必须实现 `ITrackable<ID>`，`id()` 返回持久化行标识 |
| TrackedMap 子项 | 无需 `ITrackable`，key 即行标识 |
| 延迟加载 | `lazyLoadingEnabled=true`、`aggressiveLazyLoading=false`（非强制，但推荐以享懒加载） |

```xml
<!-- mybatis-config.xml -->
<settings>
    <setting name="lazyLoadingEnabled" value="true"/>
    <setting name="aggressiveLazyLoading" value="false"/>
</settings>
```

:::: warning 字段非 final 约束
MyBatis 通过反射直接设置容器内部的 `initCollection` / `initMap` 字段，因此这两个字段**不能是 `final`**。当前 `TrackedList` / `TrackedMap` 已用非 final 字段，无需额外配置。
::::

### 3.2 领域实体（零 MyBatis 代码）

```java
public class Order {

    private Long id;
    private String orderNo;
    private TrackedList<OrderItem, Long> items;  // MyBatis 直接映射内部 initCollection

    public void addItem(OrderItem item) {
        this.items.append(item);
    }

    public void removeItem(Long itemId) {
        this.items.removeItems(i -> i.id().equals(itemId));
    }

    public List<OrderItem> getItems() {
        return this.items.getAllItems();
    }

    // 暴露容器给仓储消费差量
    public TrackedList<OrderItem, Long> itemsTrace() {
        return this.items;
    }
}
```

子项实现 `ITrackable`：

```java
public class OrderItem implements ITrackable<Long> {

    private Long id;
    private String sku;
    private int qty;

    @Override
    public Long id() {
        return this.id;
    }
}
```

### 3.3 ResultMap 映射

核心是把 `<collection>` 直接指向容器内部字段 `initCollection`：

```xml
<mapper namespace="io.pragmatic.ddd.example.OrderMapper">

    <!-- TrackedList 的嵌套 resultMap：映射到内部 initCollection 字段 -->
    <resultMap id="TrackedItemsMap" type="io.pragmatic.ddd.track.TrackedList">
        <collection property="initCollection" ofType="OrderItem"
                    select="selectItemsByOrderId" column="id" fetchType="lazy"/>
    </resultMap>

    <!-- 主 resultMap -->
    <resultMap id="OrderMap" type="Order">
        <id property="id" column="id"/>
        <result property="orderNo" column="order_no"/>
        <association property="items" resultMap="TrackedItemsMap"/>
    </resultMap>

    <select id="selectById" resultMap="OrderMap">
        SELECT id, order_no FROM t_order WHERE id = #{id}
    </select>

    <select id="selectItemsByOrderId" resultType="OrderItem">
        SELECT id, sku, qty FROM t_order_item WHERE order_id = #{id}
    </select>
</mapper>
```

映射时序：

```text
t0  orderMapper.selectById(id)
      → MyBatis 创建 Order，<association> 创建 TrackedList（无参构造：initCollection = 空）
      → <collection property="initCollection" fetchType="lazy">
        → 设置 initCollection = 懒加载代理（未执行子查询）★

t1  首次调用 getItems() / getAllItems()
      → 遍历 initCollection → 触发 selectItemsByOrderId → 返回真实子项
```

若不需要懒加载，去掉 `fetchType="lazy"` 即可在加载时立即带齐子项。

### 3.4 仓储读取

```java
@Repository
public class OrderRepository {

    private final OrderMapper orderMapper;

    public Order load(Long id) {
        return orderMapper.selectById(id);  // 一行：Order + TrackedList + 懒加载代理全自动
    }
}
```

### 3.5 仓储增量写入

持久化消费两桶差量，只发增量 SQL：

```java
@Repository
public class OrderRepository {

    private final OrderMapper orderMapper;
    private final OrderItemMapper itemMapper;

    public void save(Order order) {
        orderMapper.update(order);

        TrackedList<OrderItem, Long> items = order.itemsTrace();

        if (!items.getRemovedItems().isEmpty()) {
            List<Long> ids = items.getRemovedItems().stream()
                    .map(OrderItem::id).toList();
            itemMapper.batchDeleteByIds(ids);
        }

        if (!items.getAppendedItems().isEmpty()) {
            itemMapper.batchInsert(order.getId(), items.getAppendedItems());
        }
    }
}
```

`TrackedMap` 多一态 `UPDATE`，消费方式如下：

```java
public void save(Product product) {
    productMapper.update(product);

    TrackedMap<String, Attribute> attrs = product.attributesTrace();

    if (!attrs.getInsertedEntries().isEmpty())
        attrMapper.batchInsert(product.getId(), attrs.getInsertedEntries());
    if (!attrs.getUpdatedEntries().isEmpty())
        attrMapper.batchUpdate(product.getId(), attrs.getUpdatedEntries());
    if (!attrs.getRemovedEntries().isEmpty())
        attrMapper.batchDelete(product.getId(), attrs.getRemovedEntries());
}
```

:::: tip 读 API 不触发子查询
`getAppendedItems()` / `getRemovedItems()` / `getInsertedEntries()` / `getUpdatedEntries()` / `getRemovedEntries()` 只读变更桶，**不访问基线**，因此仓储写入阶段不会触发懒加载子查询。只有 `getAllItems()` / `getInitItems()` / `update()` / `removeItems()` 才遍历基线。
::::

### 3.6 TrackedMap 的映射注意点

MyBatis 的 `<collection>` 只能映射到 `List`，不能直接映射到 `Map<K, V>`。两种方案：

**方案 A：仓储显式查询后构造（简单明确，放弃懒加载）**

```java
@Mapper
public interface AttributeMapper {
    @MapKey("name")
    @Select("SELECT name, val FROM t_product_attr WHERE product_id = #{id}")
    Map<String, Attribute> selectAttributesByProductId(Long id);
}
```

```java
public Product load(Long id) {
    Product product = productMapper.selectById(id);
    Map<String, Attribute> attrMap = attributeMapper.selectAttributesByProductId(id);
    product.setAttributes(attrMap);  // 内部 new TrackedMap<>(attrMap)
    return product;
}
```

**方案 B：实体用 List 中转（支持懒加载）**

```xml
<resultMap id="TrackedAttrsMap" type="io.pragmatic.ddd.track.TrackedMap">
    <collection property="initMap" ofType="Attribute"
                select="selectAttrs" column="id" fetchType="lazy"/>
</resultMap>
```

```java
public void setAttributes(List<Attribute> list) {
    Map<String, Attribute> map = new LinkedHashMap<>();
    for (Attribute attr : list) {
        map.put(attr.getName(), attr);  // 触发懒加载
    }
    this.attributes = new TrackedMap<>(map);
}
```

方案 A 最直接但放弃懒加载；方案 B 支持懒加载，但多一次 List→Map 转换。

## 4. 关键机制与避坑指南

### 4.1 惰性索引与懒加载

`TrackedList` 构造时直接赋引用（不遍历）。传入 MyBatis 懒加载代理时，代理原样保存在 `initCollection`，不触发子查询；首次调用 `update` 或 `removeItems` 时遍历基线建惰性 `initMap`（按 `id()` 精确索引），此时也确实需要数据。

| 操作 | 触发子查询 | 说明 |
| --- | --- | --- |
| 无参 `new TrackedList<>()` | 否 | `initCollection = 空 ArrayList` |
| MyBatis 反射设 `initCollection = proxy` | 否 | 设引用，不读元素 |
| `getAllItems()` / `getInitItems()` | 是 | `List.copyOf` 遍历 |
| `getAppendedItems()` / `getRemovedItems()` | 否 | 只读 append/remove 桶 |
| `update(old, new)` | 是 | 触发 `initMap()` 建索引 |
| `removeItems(predicate)` | 是 | 同上 |
| `getInserted/Updated/RemovedEntries()` | 否 | 只读 putMap + removeKeys |

:::: tip 惰性索引
`TrackedList` 构造时直接赋引用（不遍历、不拷贝），传入 MyBatis 懒加载代理时不会触发子查询。首次调用 `update` 或 `removeItems` 时才遍历基线建索引。
::::

### 4.2 结构差量 vs 字段修改

本容器只负责集合的**结构差量**：哪些子行要 INSERT（append）/ DELETE（remove）。"更新集合中某项"= 用新对象替换旧对象 = `remove(旧) + append(新)`，旧项发 DELETE、新项发 INSERT。容器**不引入**"原地 UPDATE 字段"的第四态；子项字段修改的源头由调用方负责——外部产生新对象，调用 `update(old, new)`。`TrackedMap` 例外：`put` 同 key 即物理 UPDATE 同一行。

### 4.3 等同性依赖

`TrackedList` 物理移除时按 `equals()` 兜底定位（当 `initMap` 未命中时）。子项应保证 `equals` / `hashCode` 按 `id()` 等同，否则删除可能误删或漏删。`TrackedMap` 以 key 为唯一索引，无需子项等同性。

### 4.4 读 API 不可变快照

所有读 API 返回不可变快照（`List.copyOf` / `Map.copyOf` / `Set.copyOf`）。调用方拿到的是副本，对返回集合的修改不影响容器内部状态；如需变更集合内容，必须调用容器提供的变更方法（`append` / `removeItems` / `put` / `remove` 等）。

### 4.5 MyBatis 字段非 final

MyBatis 直接反射设置 `initCollection` / `initMap` 字段，二者必须为非 final。当前实现已满足，无需额外配置；若自行扩展容器切勿将内部基线字段声明为 `final`。

## 5. 异常与错误处理体系

### 5.1 `TrackedList.update` 行标识未命中

`TrackedList.update(oldItem, newItem)` 在 `oldItem.id()` 未命中基线时抛 `IllegalArgumentException`：

```java
public void update(T oldItem, T newItem) {
    T removed = initMap().remove(oldItem.id());
    if (removed == null) {
        throw new IllegalArgumentException(
                "oldItem.id()=[" + oldItem.id() + "] not found in init collection");
    }
    // ...
}
```

规避方式：调用 `update` 前确保 `oldItem.id()` 来自 `getInitItems()` / `getAllItems()` 中的真实基线项；新建尚未持久化（无 id）的子项应走 `append` 而非 `update`。

### 5.2 错误用法汇总

| 错误用法 | 后果 | 正确做法 |
| --- | --- | --- |
| 对无 id 的新子项调用 `update` | 抛 `IllegalArgumentException` | 用 `append` |
| 直接修改读 API 返回的快照集合 | 不影响容器，变更丢失 | 调用 `append` / `removeItems` / `put` |
| 子项 `equals/hashCode` 不按 id | 删除误删/漏删 | 按 `id()` 实现等同性 |
| `ITrackable.id()` 与 `getEntityId()` 混用 | 行定位错位 | 区分持久化行标识与领域身份 |

## 6. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| `ITrackable` | 子项实现 `id()` 返回行标识 | 与 `IEntity.getEntityId()` 不同层次，不可混用 |
| `TrackedList` | `append` / `removeItems` / `update` | 构造不遍历（懒加载友好）；`update` 未命中基线抛 `IllegalArgumentException` |
| `TrackedMap` | `put` / `remove` | key 即行标识；同 key `put` = 物理 UPDATE |
| 读 API | `getAppended/Removed/Init/AllItems`、`getInserted/Updated/RemovedEntries` | 均返回不可变快照 |
| MyBatis 集成 | `<association>` 嵌套 `<collection property="initCollection">` | 内部字段非 final；关闭 aggressive 懒加载 |

**下一步阅读**

- [仓储](./repository.md)：仓储如何利用变更追踪做增量持久化
- [MyBatis 集成](../integration/mybatis.md)：枚举 / JSON / 集合 TypeHandler 的装配
- [领域建模](./domain-modeling.md)：实体 / 值对象 / 聚合根基础
