# 仓储

> 本文档介绍仓储层（`io.pragmatic.ddd.repository`）的契约与用法。
> 前置阅读：[领域建模](./domain-modeling.md)。

## 1. 概述

仓储层负责聚合根的持久化与查询。框架采用**读写分离**设计：

- **写模型**：`IRepository<ID, T>` 负责聚合根的增删改
- **读模型**：`IAggregateProjection` / `IAggregateQuery` 负责查询（返回 DTO/VO，非聚合根）

## 2. `IRepository<ID, T>` 写模型仓储

```java
public interface IRepository<ID, T extends AggregateRoot<ID>> {

    void insert(T aggregateRoot);
    void update(T aggregateRoot);

    default void save(T aggregateRoot) {
        if (aggregateRoot.isNew()) {
            insert(aggregateRoot);
        } else {
            update(aggregateRoot);
        }
    }

    T findById(ID id);
    void remove(T aggregateRoot);

    default boolean existsById(ID id) {
        return findById(id) != null;
    }

    default long currentVersion(ID id) {
        T agg = findById(id);
        return agg != null ? agg.getOldVersion() : -1L;
    }
}
```

### 2.1 save 自动路由

`save()` 默认实现根据 `isNew()` 自动路由：

```java
default void save(T aggregateRoot) {
    if (aggregateRoot.isNew()) {
        insert(aggregateRoot);   // 新建 → INSERT
    } else {
        update(aggregateRoot);   // 更新 → UPDATE
    }
}
```

聚合根通过 `markNew()` 标记为新建状态。

### 2.2 版本对账

`currentVersion(id)` 用于乐观锁对账：

```java
default long currentVersion(ID id) {
    T agg = findById(id);
    return agg != null ? agg.getOldVersion() : -1L;
}
```

- 默认实现基于 `findById` 取 `oldVersion`，适合低频对账
- 高频对账场景可覆写为**只查版本列**，避免加载整个聚合根

### 2.3 实现你的仓储

```java
public class OrderRepository implements IRepository<Long, Order> {

    @Override
    public void insert(Order order) {
        // INSERT INTO orders (...) VALUES (...)
        // 落库时携带 order.getNewVersion() 作为新版本
    }

    @Override
    public void update(Order order) {
        // UPDATE orders SET ... WHERE id = ? AND version = ?
        // 使用 order.getOldVersion() 作为 WHERE 条件
        // 使用 order.getNewVersion() 作为新版本值
        // 影响行数为 0 → 并发冲突
    }

    @Override
    public Order findById(Long id) {
        // SELECT * FROM orders WHERE id = ?
        // 回填 oldVersion 字段
        return null;
    }

    @Override
    public void remove(Order order) {
        // DELETE FROM orders WHERE id = ?
        // 或软删：UPDATE orders SET entity_delete = 1 WHERE id = ?
    }

    // 可选覆写：高频对账只查版本列
    @Override
    public long currentVersion(Long id) {
        // SELECT version FROM orders WHERE id = ?
        return jdbcTemplate.queryForObject(...);
    }
}
```

## 3. 查询端口

### 3.1 `IAggregateQuery`

`IAggregateQuery` 是查询端口标记，提供多种查询契约的默认组合：

```java
public interface IAggregateQuery {
    // 组合了 IQueryById, IQueryByIds, IQueryOne, IQueryList, IQueryPage, IQueryScroll
}
```

各查询契约：

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `IQueryById<R>` | `R queryById(ID id)` | 按主键查 |
| `IQueryByIds<R>` | `List<R> queryByIds(Collection<ID> ids)` | 批量按主键查 |
| `IQueryOne<R, C>` | `R queryOne(C condition)` | 按条件查单条 |
| `IQueryList<R, C>` | `List<R> queryList(C condition)` | 按条件查列表 |
| `IQueryPage<R, C>` | `PageResult<R> queryPage(C condition, PageRequest page)` | 分页查询 |
| `IQueryScroll<R, C>` | `ScrollResult<R> queryScroll(C condition, ScrollPosition position)` | 游标滚动查询 |

### 3.2 `IAggregateProjection`

`IAggregateProjection` 是聚合投影查询，返回**非聚合根的 DTO/VO**：

```java
public class OrderProjection implements IAggregateProjection {

    public OrderSummary querySummary(Long orderId) {
        // SELECT id, status, total_amount FROM orders WHERE id = ?
        // 返回 OrderSummary DTO，不加载完整聚合根
        return new OrderSummary(...);
    }
}
```

## 4. 读写分离

```
写操作                          读操作
Command Service                 Query Service
    │                               │
    ▼                               ▼
IRepository                     IAggregateProjection
(INSERT/UPDATE/DELETE)          (SELECT → DTO/VO)
    │                               │
    ▼                               ▼
聚合根（完整领域模型）             DTO/VO（查询专用视图）
```

设计原则：

- **写走仓储**：`IRepository.save()` 操作完整聚合根，保证不变量
- **读走投影**：`IAggregateProjection` 直接查表返回 DTO，不走聚合根装配
- **读写可异库**：写库为聚合根表，读库可为物化视图/宽表/Elasticsearch

---

下一步：

- [应用服务](./application-service.md)：仓储在命令执行器中的位置
- [MyBatis 集成](../integration/mybatis.md)：TypeHandler 与持久化
