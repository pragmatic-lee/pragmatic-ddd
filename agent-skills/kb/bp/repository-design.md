# BP 卡：仓储设计原则（repository-design）

> 源：`documentation/best-practices/repository-design.md` · 派生时间：2026-10-28 · 框架版本：2.1.0-SNAPSHOT

## 1. 定位与边界

- 仓储是聚合根的持久化出入口：接口在**领域层**（`Abstract{Agg}Repository extends AbstractRepository`），实现在**基础设施层**（`{Agg}Repository implements 抽象类`），应用层只依赖领域层接口。
- 写读分离：命令侧 `IRepository<ID, T>`（入参/出参聚合根）；查询侧 `io.pragmatic.ddd.repository.query` 四查询族 SPI（见 `projection-design.md`）。
- 只收聚合根对象，不收散装字段、不绕过聚合根直接改子表。
- **一个聚合根一个仓储**；子实体（如 `OrderItem`）随聚合根存取，不独立建仓储。
- **事务边界由应用层负责**：仓储不管理事务。

## 2. 框架能力

### 2.1 IRepository<ID, T>（T extends AggregateRoot<ID>）

| 方法 | 类型 | 职责 |
| --- | --- | --- |
| `insert(T)` | 抽象 | 插入聚合根 |
| `update(T)` | 抽象 | 更新聚合根 |
| `save(T)` | 默认 | 按 `isNew()` 路由 insert/update |
| `findById(ID)` | 抽象 | 按主键查，未命中返回 `null` |
| `remove(T)` | 抽象 | **必须提供真实删除**（接口无默认空实现） |
| `existsById(ID)` | 默认 | `findById(id) != null` |
| `currentVersion(ID)` | 默认 | 写模型当前版本，聚合不存在返回 `-1`（供 ORPHAN 判定） |

> `remove` 无默认空实现——实现方必须提供真实删除（物理/软删自定），避免"以为删了其实没做"。

### 2.2 AbstractRepository 模板方法

`insert`/`update`/`remove` 统一：①先 `triggerDataSyncHook()` ②再委托子类 `doInsert`/`doUpdate`/`doRemove`。三个方法**不是 `final`**（子类理论上可覆写），但**不应覆写**——通用钩子逻辑（`triggerDataSyncHook()`）由父类统一调用，覆写会绕过异构事件收集，导致事件机制失效。子类**只实现三个 `protected abstract`** `doInsert`/`doUpdate`/`doRemove`。

## 3. 实现规范

- 继承 `AbstractRepository` 覆写 `doInsert`/`doUpdate`/`doRemove`；`findById` 是父类查询方法（抽象方法），按需直接实现。
- **乐观锁 CAS**：`doUpdate` 以 `version = oldVersion` 做 WHERE，`affected==0` 抛 `OptimisticLockingFailureException`；`oldVersion` 由 `findById` 从 DB 版列回填，不自建 version 字段。
- **子集合差量**：一对多用 `TrackedList<OrderItem, Long>`，消费 `getRemovedItems()` 发 DELETE、`getAppendedItems()` 发 INSERT；子项字段修改语义是 `remove(旧)+append(新)`，不原地 UPDATE。
- **删除先子后主**，避免孤儿行。
- **删除必须走 `remove(aggregateRoot)`**（不直连 SQL）：钩子未触发则 `PURGE` 不产生，ES / Redis 副本长期残留，只能靠对账 `ORPHAN` 兜底。
- **纯持久化无业务逻辑**：不判断、不组装、不触发事件（事件由 `triggerDataSyncHook` 统一处理）。

### 3.1 MyBatis 专题

- 纯 XML Mapper（无 Java 接口），`namespace` 标识，`sqlSession.insert("OrderMapper.xxx", param)` 调用。
- 显式 `resultMap` + `autoMapping="false"`，字段缺失/拼写错开发期暴露；乐观锁基线列读入 `oldVersion`。
- `TrackedList` 懒加载：嵌套 resultMap 实例化，`<collection property="initCollection" fetchType="lazy">` 写入 `TrackedList.initCollection`（构造器直接赋引用不遍历，首次访问才物化）。
- INSERT/UPDATE 写 `getNewVersion()`，UPDATE 以 `version = oldVersion` CAS。
- 逻辑外键列（如 `order_id`）是过滤键不纳查询列，由批插入参数 `Map` 单独传，不映射进子实体。

## 4. 仓储测试（集成式单测）

- 只装配 MySQL 链路最小上下文（`@Import({MySqlConfig.class, OrderRepository.class})`，排除 `DataSourceAutoConfiguration`）。
- 默认 `@SpringBootTest` 事务包装保证事务内访问懒加载子集合；按需 `@Rollback(false)` 真实提交；`@AfterEach` 先删子表再删主表清理。
- 覆盖：整存整取、懒加载、未命中返回 null、existsById、currentVersion、字段更新+版本递增、乐观锁冲突、差量同步、删除无孤儿行。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 仓储做业务逻辑 | 只做数据存取 |
| 直接实现 IRepository.insert/update/remove | 继承 `AbstractRepository`，覆写 `doXxx` |
| 传散装字段/直改子表 | 整存整取聚合根 |
| 为子实体建仓储 | 一个聚合根一个仓储 |
| 全删全插子集合 | `TrackedList` 三桶增量 |
| 删除不清理子表 | 先子后主 |
| 删除直连 SQL / 不传聚合根 | 走 `remove(aggregateRoot)`，由钩子发 `PURGE` |
| 绕过版本列 UPDATE | `version=oldVersion` CAS |
| 查询列混逻辑外键不映射 | 显式 resultMap，逻辑外键不入查询列 |
| 仓储开事务注解 | 事务由执行器经 `TransactionOperations` 负责 |
| 复杂报表查询塞写仓储 | 交查询侧 `query` 子包 |

## 6. 被读于

S3 `ddd-infra-config`、S4 `ddd-aggregate-create`、S7 `ddd-code-review`。
