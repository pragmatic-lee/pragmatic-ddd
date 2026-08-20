# 仓储写模型（Repository / Write Model）

> 本文档说明 `io.pragmatic.ddd.repository` 包提供的写模型能力。相关文档：[投影读模型](./projection-read.md) · [领域事件](./domain-events.md) · [领域建模](./domain-modeling.md)。

## 1. 概述

### 1.1 核心定位

写模型负责聚合根（Aggregate Root）的持久化与生命周期管理，是命令侧（C 侧）的单一持久化入口。框架在 `io.pragmatic.ddd.repository` 定义写侧契约与抽象基类，在 `io.pragmatic.ddd.base.AggregateRoot` 定义聚合根基类，开发者无需手写落库前事件收集与乐观锁样板。

### 1.2 概念层级与依赖关系

```text
repository
  IRepository<ID, T>                 聚合持久化契约（insert/update/save/findById/remove/existsById/currentVersion）
    └─ AbstractRepository<ID, T>    抽象基类（final 落库方法 + doInsert/doUpdate/doRemove 抽象）
  （无独立异常基类，复用 IllegalArgumentException / 持久化层异常）

base
  AggregateRoot<ID>                  聚合根基类，提供 oldVersion/newVersion 与 triggerDataSyncHook
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `IRepository<ID, T>` | `io.pragmatic.ddd.repository` | 聚合持久化契约 |
| `AbstractRepository<ID, T>` | `io.pragmatic.ddd.repository` | 落库流程抽象基类 |
| `AggregateRoot<ID>` | `io.pragmatic.ddd.base` | 聚合根基类（版本与钩子） |

写模型不感知任何异构读存储（ES / Redis / 独立读表）的存在；异构副本的物化与对账完全在 `query` / `reconciliation` 子包内完成。`reconciliation` 子包反向依赖 `IRepository` 取权威版本 V。

## 2. 核心概念详解

### 2.1 聚合持久化契约：`IRepository<ID, T>`

#### 契约 / 接口

写侧顶层接口，定义聚合根的持久化契约。方法如下：

| 方法 | 签名 | 语义 |
| --- | --- | --- |
| `insert` | `void insert(T aggregateRoot)` | 新增聚合；空 ID 由框架/实现分配 |
| `update` | `void update(T aggregateRoot)` | 更新聚合；ID 必填 |
| `save` | `default void save(T aggregateRoot)` | 按 ID 是否为空路由到 `insert` 或 `update` |
| `findById` | `Optional<T> findById(ID id)` | 按 ID 查询；未命中返回 `Optional.empty()` |
| `remove` | `void remove(ID id)` | 按 ID 删除聚合 |
| `existsById` | `boolean existsById(ID id)` | 聚合是否存在 |
| `currentVersion` | `long currentVersion(ID aggregateId)` | 写模型当前版本 V；无版本返回 `-1` |

#### 关键约束

> ⚠️ **重要约束**：`currentVersion` 的返回值是读模型对账的权威版本 V。当写库不追踪版本（如未建版本列）时返回 `-1`，语义为「写模型无此聚合」，对账据此判定 ORPHAN。返回 `-1` 不是异常，写模型本身不应把它当作错误抛出。

#### 示例代码

```java
public interface IRepository<ID, T extends AggregateRoot<ID>> {

    void insert(T aggregateRoot);

    void update(T aggregateRoot);

    default void save(T aggregateRoot) {
        if (aggregateRoot.getId() == null) {
            insert(aggregateRoot);
        } else {
            update(aggregateRoot);
        }
    }

    Optional<T> findById(ID id);

    void remove(ID id);

    boolean existsById(ID id);

    long currentVersion(ID aggregateId);
}
```

### 2.2 落库抽象基类：`AbstractRepository<ID, T>`

#### 基类能力

抽象基类固定落库流程，把「持久化前准备」与「真正持久化」分离：

| 成员 | 说明 |
| --- | --- |
| `insert` / `update` / `remove` | `final`，子类不可覆写；落库前统一触发 `triggerDataSyncHook` 后委托 `doXxx` |
| `save` | `default`，按 ID 是否空路由到 `insert` 或 `update` |
| `doInsert` / `doUpdate` / `doRemove` | `protected abstract`，由具体存储实现（如 MyBatis 仓储） |
| `triggerDataSyncHook` 调用 | 定义在 `AggregateRoot` 基类；`AbstractRepository` 自身不持有事件发布器，事件分发由聚合根钩子内部完成 |

#### 关键约束

> ⚠️ **重要约束**：`insert / update / remove` 为 `final`，子类只实现 `doInsert / doUpdate / doRemove`，不得覆写 `final` 落库方法，否则会绕过 `triggerDataSyncHook` 事件收集，造成异构副本遗漏。

> ⚠️ **重要约束**：`remove(ID)` 会先 `findById` 加载聚合再触发钩子后删除；聚合不存在时抛 `IllegalArgumentException`。需要「硬删且不强依赖聚合加载」的场景，应由子类在 `doRemove` 直接处理，但钩子路径不可省略事件收集。

#### 示例代码

```java
public abstract class AbstractRepository<ID, T extends AggregateRoot<ID>>
        implements IRepository<ID, T> {

    @Override
    public final void insert(T aggregateRoot) {
        aggregateRoot.triggerDataSyncHook();
        this.doInsert(aggregateRoot);
    }

    @Override
    public final void update(T aggregateRoot) {
        aggregateRoot.triggerDataSyncHook();
        this.doUpdate(aggregateRoot);
    }

    @Override
    public final void remove(ID id) {
        T aggregateRoot = this.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Aggregate not found for removal: " + id));
        aggregateRoot.triggerDataSyncHook();
        this.doRemove(aggregateRoot);
    }

    protected abstract void doInsert(T aggregateRoot);

    protected abstract void doUpdate(T aggregateRoot);

    protected abstract void doRemove(T aggregateRoot);
}
```

### 2.3 版本与乐观锁

#### 基类能力：`AggregateRoot<ID>` 版本字段

| 字段/方法 | 含义 | 说明 |
| --- | --- | --- |
| `getOldVersion()` | 本次变更前的快照版本 | 持久化 `WHERE version = oldVersion` 的乐观锁基准 |
| `getNewVersion()` | 本次变更后的目标版本 | 持久化后写入版本列；领域事件 `version` 取此值 |

`AggregateRoot` 在装配/重建时维护 `oldVersion / newVersion`；领域事件与数据同步快照统一取 `getNewVersion()` 作为版本标记。

#### 关键约束

> ⚠️ **重要约束**：`update` 以 `WHERE version = oldVersion` 完成乐观锁更新。命中 0 行表示并发写冲突（数据集已变更），框架视为冲突；命中 1 行写入 `newVersion`。调用方应捕获异常并重试（重新 `findById` 拉取最新快照后重放命令），框架不内置重试。

### 2.4 写模型与读模型对账的衔接点

#### 契约 / 衔接

写模型在对账链路中只扮演**权威版本来源**角色：读模型对账通过 `IRepository.currentVersion(aggregateId)` 取得 V。当 V = -1 时，对账判定为 ORPHAN，由读模型侧执行 `purge` 清理残留副本。其余对账组件（目标、resolver、resyncer、manager）均定义在 `reconciliation` 子包，属于[投影读模型](./projection-read.md)范畴。

#### 关键约束

> ⚠️ **重要约束**：写模型不依赖 `reconciliation` 子包的任何类型，`reconciliation` 反向依赖 `IRepository`。对账触发与补救编排由读模型侧负责，写模型只暴露 `currentVersion`。

## 3. 关键机制与避坑指南

### 3.1 落库流程不可被绕过

`insert / update / remove` 为 `final`。子类若需自定义落库前逻辑，必须在聚合根内通过 `triggerDataSyncHook` 的钩子扩展，而不能覆写这三个方法，否则会绕过事件收集，造成异构副本遗漏。

> ⚠️ **重要约束**：事件收集依赖 `AbstractRepository.final` 方法在每次写操作前统一触发 `triggerDataSyncHook`。任何绕过 `final` 方法的持久化路径（如在子类直接调用 `doXxx`）都将导致事件未收集、读模型副本陈旧。

### 3.2 乐观锁冲突必须显式处理

`update` 的 `WHERE version = oldVersion` 未命中时，框架视为并发冲突。

> ⚠️ **重要约束**：框架不内置重试。调用方需捕获冲突异常并重新 `findById` 拉取最新快照后重放命令，否则并发写会静默丢失。

### 3.3 `currentVersion` 返回 -1 的语义

`-1` 表示写模型无此聚合（或写库未追踪版本）。

> ⚠️ **重要约束**：返回 `-1` 不是异常，而是对账的输入信号，写模型本身不应把 `-1` 当作错误抛出；读模型对账据此判定 ORPHAN 并执行 `purge`。

### 3.4 `remove` 的触发路径

`remove(ID)` 先 `findById` 加载聚合再触发钩子后删除；聚合不存在抛 `IllegalArgumentException`。

> ⚠️ **重要约束**：需要「硬删且不强依赖聚合加载」的场景，应由子类在 `doRemove` 直接处理，但钩子路径（`triggerDataSyncHook`）不可省略，否则该聚合产生的异构事件不会被收集。

## 4. 异常与错误处理体系

### 4.1 继承关系

写模型未定义独立的异常基类，复用 `java.lang.IllegalArgumentException` 表达契约违例，乐观锁冲突由持久化层（子类 `doUpdate`）抛运行时异常。

```text
RuntimeException
 ├─ IllegalArgumentException   契约违例（删除不存在聚合、ID 缺失等）
 └─ （乐观锁 0 行命中由子类持久化层抛运行时异常，无统一基类）
```

### 4.2 错误行为字典

| 场景 | 异常 | 触发点 |
| --- | --- | --- |
| 删除不存在的聚合 | `IllegalArgumentException` | `AbstractRepository.remove` 调用 `findById` 为空 |
| ID 缺失却调用 `update` | 由 `doUpdate` 实现决定（通常持久化失败） | 子类持久化层 |
| 乐观锁 0 行命中 | 由 `doUpdate` 实现决定（通常抛运行时异常） | 子类持久化层 |
| `currentVersion` 入参为空 | 由实现决定 | 子类持久化层 |

### 4.3 捕获与处理规范

- `catch (IllegalArgumentException e)` 处理删除不存在聚合等契约违例。
- 乐观锁冲突捕获持久化层运行时异常后重试（重新 `findById` → 重放命令）。
- 读模型对账链路中的一致性相关异常（STALE / ORPHAN 补救失败）定义在[投影读模型](./projection-read.md)的 `reconciliation` 子包，不在写模型职责内。

## 5. 总结速查

### 核心概念速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 持久化契约 | 实现 `IRepository<ID, T>`，或继承 `AbstractRepository` | `currentVersion` 返回 V，`-1` 表示无此聚合、非异常 |
| 抽象基类 | 继承 `AbstractRepository`，实现 `doInsert/doUpdate/doRemove` | `insert/update/remove` 为 `final`，不得覆写 |
| 版本与乐观锁 | `AggregateRoot` 提供 `oldVersion/newVersion` | `update` 用 `WHERE version=oldVersion`，冲突不内置重试 |
| 对账衔接 | `reconciliation` 反向依赖 `IRepository.currentVersion` | 写模型只暴露 V，不感知读副本 |

### 命名规范

| 分层 | 类型 | 命名格式 | 示例 |
| --- | --- | --- | --- |
| 仓储接口 | 仓储契约 | `I{聚合名}Repository` | `IOrderRepository` |
| 仓储接口 | 聚合根 | 继承 `AggregateRoot<ID>` | `Order` |
| 仓储实现 | 仓储实现 | `{聚合名}{集成模块}Repository` | `OrderMybatisRepository` |
| 仓储实现 | 抽象持久化方法 | `do{Insert\|Update\|Remove}`（`protected abstract`） | `doInsert` / `doUpdate` / `doRemove` |
| 版本字段 | 乐观锁版本 | `oldVersion` / `newVersion`（基类提供，子类不重定义） | — |

> ⚠️ **重要约束**：`insert / update / remove` 在 `AbstractRepository` 中为 `final`，子类只实现 `doInsert / doUpdate / doRemove`；`currentVersion` 无版本返回 `-1`，语义为「写模型无此聚合」，不是异常。

**下一步阅读**

- [投影读模型](./projection-read.md)：读侧查询契约、投影、物化与读模型对账（`currentVersion` 的 V 如何被使用）
- [领域事件](./domain-events.md)：`triggerDataSyncHook` 收集的事件如何分发
- [领域建模](./domain-modeling.md)：`AggregateRoot` 与 `oldVersion / newVersion` 的维护机制
