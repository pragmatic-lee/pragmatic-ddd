# BP 卡：仓储设计（repository-design）

> 源：`documentation/best-practices/repository-design.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 定位

仓储是聚合根持久化出入口：接口在领域层（`Abstract{Agg}Repository extends AbstractRepository`），实现在基础设施层（`{Agg}Repository` 注入 `SqlSessionTemplate`）。**一个聚合根一个仓储**，子实体随聚合一起存取（硬约束 14 之外）。

### 1.1 写读分离
- **命令侧（写模型）**：`IRepository<ID, T>`——聚合增删改，出入参都是聚合根。
- **查询侧（读模型）**：`io.pragmatic.ddd.repository.query` 四个查询族 SPI + 投影（`IAggregateProjector`）+ 裁剪器（`IReducer`）（见 [投影读模型代码落地指南](./projection-design.md)）。

### 1.2 只收聚合根对象
`insert`/`update`/`remove` 入参**必须是聚合根对象**，不收散装字段、不直接操作子表裸列表（聚合根是唯一一致性边界，主表/子表/版本同一事务）。

### 1.3 事务边界由应用层负责
仓储**不管理事务**。`insert`/`update`/`remove` 只发落库动作，事务由调用方（应用服务 → 命令执行器 / 工作单元）经框架事务抽象 `TransactionOperations` 负责。两个执行器都提供事务边界（`REQUIRED`），单聚合多表整体原子。**不使用 `@Transactional` 注解**，仓储须用 Spring 托管的 `SqlSessionTemplate` 自动加入当前事务（裸 `SqlSessionFactory#openSession()` 每次另开连接，事务失效）。

## 2. 框架契约

`IRepository<ID, T>`（`T extends AggregateRoot<ID>`）：

| 方法 | 类型 | 职责 |
| --- | --- | --- |
| `insert(T)` | 抽象 | 插入聚合根 |
| `update(T)` | 抽象 | 更新聚合根 |
| `save(T)` | 默认 | 按 `isNew()` 路由 insert/update |
| `findById(ID)` | 抽象 | 按主键查询，未命中返回 `null` |
| `remove(T)` | 抽象 | 按主键删除，**必须提供真实删除逻辑** |
| `existsById(ID)` | 默认 | `findById(id) != null` |
| `currentVersion(ID)` | 默认 | 返回写模型当前版本，聚合不存在返回 `-1`（供 ORPHAN 判定） |

- `save` 按 `isNew()` 路由；聚合业务构造时 `markNew()` 打新建标记。
- `remove` **无默认空实现**，实现方必须提供真实删除（物理/软删由实现决定）。
- `existsById`/`currentVersion` 默认基于 `findById`；高频对账场景可覆写为只查主键/版本列。

## 3. 骨架

```java
// 领域层
public abstract class AbstractOrderRepository extends AbstractRepository<Long, Order> {}

// 基础设施层
@Repository
public class OrderRepository extends AbstractOrderRepository {
    private final SqlSessionTemplate sqlSessionTemplate;

    @Override
    protected void doInsert(Order aggregateRoot) {
        sqlSessionTemplate.insert("OrderMapper.insert", aggregateRoot);
    }

    @Override
    protected void doUpdate(Order aggregateRoot) {
        int affected = sqlSessionTemplate.update("OrderMapper.update", aggregateRoot);
        if (affected == 0) {
            throw new OptimisticLockingFailureException("订单 [" + aggregateRoot.getEntityId() + "] 乐观锁冲突");
        }
        syncTrackedList(aggregateRoot);
    }

    @Override
    protected void doRemove(Order aggregateRoot) {
        sqlSessionTemplate.delete("OrderMapper.deleteOrderItemsByOrderId", aggregateRoot.getEntityId());
        sqlSessionTemplate.delete("OrderMapper.deleteById", aggregateRoot.getEntityId());
    }
}
```

## 4. 关键约定

- 继承 `AbstractRepository`，覆写 `doInsert`/`doUpdate`/`doRemove`/`findById`；**勿直接实现** `IRepository` 的 `final` insert/update/remove（`triggerDataSyncHook` 由框架统一调）。
- 乐观锁 CAS：`doUpdate` 以 `version = oldVersion` 为 WHERE，影响行 0 即抛 `OptimisticLockingFailureException`。`oldVersion` 由 `findById` 从 DB 版本列回填。
- 子集合差量：`TrackedList` 三桶（`getAppendedItems`/`getRemovedItems`/`getAllItems`），仓储只读三桶发增量 INSERT/DELETE，不全删全插。
- 删除先子后主，避免孤儿行。
- 纯持久化：只做数据存取，不做业务判断/组装/发事件（事件由聚合根经 `triggerDataSyncHook` 统一处理）。

## 5. MyBatis

- 纯 XML Mapper（`namespace` = `OrderMapper`），无 Java 接口。
- `resultMap` 显式映射 + `autoMapping="false"`；`oldVersion` 由版本列回填。
- `TrackedList` 懒加载：`fetchType="lazy"`，`<collection property="initCollection">` 写入。
- 逻辑外键列（如 `order_id` 之于 `OrderItem`）是查询过滤键，不纳入查询列。

## 6. 测试规范

- 集成式单测：只装配 MySQL 链路（MyBatis + 数据源 + 事务 + 仓储），不加载 ES/Redis/MQ。
- `@SpringBootTest(classes = TestConfig.class)` + `@Rollback(false)` 按需真实提交 + `@AfterEach` 清理（先子后主）。
- 覆盖：整存整取、懒加载、未命中返回 null、existsById、currentVersion、字段更新+版本递增、乐观锁冲突、差量同步、删除无孤儿。

## 7. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 仓储写业务逻辑 | 只做数据存取 |
| 直接实现 IRepository 的 final 方法 | 继承 AbstractRepository 覆 doXxx |
| 传散装字段 / 直接操作子表 | 整存整取聚合根 |
| 为子实体建独立仓储 | 一聚合一根仓储 |
| 全删全插子集合 | TrackedList 三桶增量 |
| 绕过版本列 UPDATE | version = oldVersion 做 CAS |
| 仓储开 `@Transactional`/事务注解 | 事务由执行器经 `TransactionOperations` 负责 |
| 复杂报表查询塞进写仓储 | 交查询侧 |

## 8. 被读于

S4 `ddd-aggregate-create`（步骤 4 仓储）、S3 `ddd-infra-config`、S7 `ddd-code-review`。
