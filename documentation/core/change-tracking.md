# 变更追踪

> 本文档介绍一对多集合的变更追踪容器（`io.pragmatic.ddd.track`）。
> 前置阅读：[领域建模](./domain-modeling.md)。

## 1. 概述

变更追踪容器把一对多集合的状态拆为**基线 / 新增 / 删除**三桶，使持久化只做增量 `INSERT` / `DELETE`，而非"全删全插"。

适用对象：**有独立 DB 行的对象类型**（实体或独立表值对象），不处理基础类型（`String`/`Integer`）或无身份的内嵌值对象。

## 2. `ITrackable<ID>` 行标识契约

```java
public interface ITrackable<ID> {
    ID id();  // 持久化行标识（用于 DELETE/UPDATE 定位行记录）
}
```

所有被追踪的元素必须实现此接口：

```java
public class OrderItem extends AbstractEntity<Long> implements ITrackable<Long> {

    @Override
    public Long id() {
        return this.getEntityId();  // 返回持久化行键
    }
}
```

::: tip 与 IEntity 的区别
`ITrackable.id()` 是持久化层的行标识，与 `IEntity.getEntityId()`（领域身份）属于不同层次，不可混用。实体通常返回领域 ID，独立表值对象返回业务键。
:::

## 3. `TrackedList<T, ID>` 一对多集合追踪

### 3.1 三桶设计

```
initCollection（基线）    ← DB 加载时已有的子项，不变
appendList（新增桶）      ← 本次新增的子项 → 持久化发 INSERT
removeList（删除桶）      ← 本次移除的子项 → 持久化发 DELETE
```

### 3.2 基本用法

```java
public class Order extends AggregateRoot<Long> {

    private TrackedList<OrderItem, Long> items;

    public Order() {
        this.items = new TrackedList<>();  // 空基线（新建订单）
    }

    // 从 DB 加载时
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

### 3.3 更新子项

"更新"= 用新对象替换旧对象 = `remove(旧) + append(新)`：

```java
public void updateItem(OrderItem oldItem, OrderItem newItem) {
    this.items.update(oldItem, newItem);
    // oldItem 从基线移入删除桶（发 DELETE）
    // newItem 进入新增桶（发 INSERT）
}
```

### 3.4 条件移除

```java
public void removeOutOfStockItems() {
    List<OrderItem> removed = this.items.removeItems(item -> item.isOutOfStock());
    // 符合条件的子项从基线/新增桶移入删除桶
}
```

### 3.5 全量替换

```java
public void replaceAllItems(List<OrderItem> newItems) {
    this.items.clearAndAppend(newItems);
    // 当前所有基线移入删除桶（发 DELETE）
    // 新列表进入新增桶（发 INSERT）
}
```

### 3.6 读取逻辑视图

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

## 4. `TrackedMap<K, T, ID>` Map 追踪

`TrackedMap` 与 `TrackedList` 设计一致，以 Key 索引：

```java
TrackedMap<String, OrderItem, Long> itemMap = new TrackedMap<>();

itemMap.append("SKU-001", item1);     // 新增
itemMap.remove("SKU-002");            // 删除
OrderItem item = itemMap.get("SKU-001"); // 读取
```

## 5. 持久化策略

```
持久化时只需处理两桶：

INSERT INTO order_item (...) VALUES (appendedItems)
DELETE FROM order_item WHERE id IN (removedItems.id)

基线不改动（不发 SQL）
```

::: tip 惰性索引
`TrackedList` 构造时直接赋引用（不遍历、不拷贝），传入 MyBatis 懒加载代理时不会触发子查询。首次调用 `update` 或 `removeItems` 时才遍历基线建索引。
:::

---

下一步：

- [仓储](./repository.md)：仓储如何利用变更追踪做增量持久化
- [MyBatis 集成](../integration/mybatis.md)：集合 TypeHandler 的使用
