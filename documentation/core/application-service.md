# 应用服务（Application Service）

> 本文档说明 `io.pragmatic.ddd.application` 包提供的应用服务编排能力：命令执行器、工作单元、应用服务基类、实体工厂/更新器/属性解析器，以及 Outbox 事务性发件箱。前置阅读：[领域建模](./domain-modeling.md) · [业务规则引擎](./business-rules.md) · [领域事件](./domain-events.md)。

## 1. 概述

### 1.1 核心定位

应用服务层负责编排"领域逻辑 → 规则校验 → 持久化 → 事件分发"的标准流程，并对外部调用屏蔽模板细节。框架提供两类编排入口与一个便捷基类：

- **单聚合根命令**：一次操作只涉及一个聚合根，使用 `CommandExecutor`。
- **跨聚合根工作单元**：一次操作涉及多个聚合根、需统一提交，使用 `UnitOfWork`。
- **应用服务基类**：`AbstractApplicationService` 内聚二者，提供语义化方法。

此外，Outbox 模式（`application.outbox`）提供事务性发件箱，保证事件与聚合根同事务原子落库。

两者均提供零副作用的试跑（Dry-run）入口，用于预览校验结论而不落库、不发事件。

### 1.2 概念层级与依赖关系

```text
ICommandExecutor ──AbstractCommandExecutor── CommandExecutor（默认）
IUnitOfWork    ──AbstractUnitOfWork──── UnitOfWork（默认）/ OutboxUnitOfWork

AbstractApplicationService  聚合 ICommandExecutor + IUnitOfWork 工厂

EntityFactory<T,C>          创建新聚合根
EntityUpdater<T,C>          修改已有聚合根（apply）
IEntityPropertyResolver<C,E,R> / EntityPropertyResolvers  属性解析适配

application.outbox
 ├─ OutboxCommandExecutor / OutboxUnitOfWork   事务性发件箱执行器
 ├─ EagerOutboxPublisher                       提交后主动推送
 ├─ OutboxRelay + OutboxRelayConfig            兜底轮询补偿
 └─ OutboxStatus / OutboxMessage / OutboxEntry 状态机与载体
```

| 类型 | 包路径 | 用途 |
| --- | --- | --- |
| `ICommandExecutor` / `AbstractCommandExecutor` / `CommandExecutor` | `io.pragmatic.ddd.application` | 单聚合根命令（默认：先 save 再逐条 publish） |
| `IUnitOfWork` / `AbstractUnitOfWork` / `UnitOfWork` | `io.pragmatic.ddd.application` | 跨聚合根工作单元（默认：统一 save 后 publishList） |
| `AbstractApplicationService` | `io.pragmatic.ddd.application` | 应用服务便捷基类 |
| `DryRunResult` | `io.pragmatic.ddd.application` | 试跑（Dry-run）结构化结果（record） |
| `EntityFactory` / `EntityUpdater` | `io.pragmatic.ddd.application` | 实体创建 / 修改契约 |
| `IEntityPropertyResolver` / `EntityPropertyResolvers` | `io.pragmatic.ddd.application` | 实体属性解析契约与适配器工厂 |
| `OutboxCommandExecutor` / `OutboxUnitOfWork` | `io.pragmatic.ddd.application.outbox` | 事务性发件箱执行器 |
| `EagerOutboxPublisher` / `OutboxRelay` / `OutboxRelayConfig` | `io.pragmatic.ddd.application.outbox` | 主动推送 / 兜底轮询 / 运行配置 |
| `OutboxStatus` / `OutboxMessage` / `OutboxEntry` | `io.pragmatic.ddd.application.outbox` | 发件箱状态机与载体 |

## 2. 核心概念详解

### 2.1 单聚合根命令：`ICommandExecutor` / `CommandExecutor`

`AbstractCommandExecutor` 固定五步模板，`CommandExecutor`（默认）在 `persistAndDispatch` 钩子中先 `save`、再逐条 `publish`：

```text
1. 执行领域逻辑    domainLogic.accept(aggregateRoot)
2. 规则校验        aggregateRoot.satisfiesRule(rule) → 未通过则 throwBrokenRuleException
3. 持久化          repository.save(aggregateRoot)            ← persistAndDispatch 钩子
4. 发布事件        aggregateRoot.getDomainEvents().forEach(eventManager::publish)
5. 清空状态        aggregateRoot.clearWorkUnitState()
```

```java
IEventManager eventManager = new ThreadPoolEventManager(LocalEventManagerConfig.defaultConfig());
eventManager.start();

CommandExecutor executor = new CommandExecutor(eventManager);
OrderRepository repository = new OrderRepository();
OrderRule rule = new OrderRule();

Order order = new Order(1L, "张三", 100);
Order result = executor.execute(order, rule, repository, Order::cancel);
```

#### 试跑 Dry-run

`tryExecute` 执行与 `execute` 完全相同的领域逻辑与规则校验，但跳过持久化与事件分发，以 `DryRunResult` 结构化返回；`rule` 为 `null` 时视为无规则约束；仅规则类异常被转译为未通过，其余异常照常上抛。

```java
DryRunResult result = executor.tryExecute(order, rule, repository, Order::cancel);
if (result.passed()) {
    // 校验通过
} else {
    List<BrokenRule> brokenRules = result.brokenRules();   // 规则违反明细
}
```

> ⚠️ **重要约束**：试跑后的聚合根状态已变更（领域逻辑已执行），且 `tryExecute` 内部已清空暂存事件；该实例**不可再用于真实 `execute`**，应使用专用实例试跑。

### 2.2 跨聚合根工作单元：`IUnitOfWork` / `UnitOfWork`

`AbstractUnitOfWork` 固定多聚合根统一提交流程，`UnitOfWork`（默认）通过 `persistAndCollect` 钩子逐条 save 并收集事件、`dispatchEvents` 钩子统一 `publishList`：

```text
1. 逐条执行领域逻辑
2. 逐条规则校验     未通过则 throwBrokenRuleException（中断提交）
3. 逐条持久化       repository.save
4. 收集全部事件     汇总所有聚合根 getDomainEvents
5. 统一发布         eventManager.publishList(allEvents)
6. 逐条清空         clearWorkUnitState
```

与 `CommandExecutor` 的区别：**先全部校验，再统一落库，最后统一发布事件**，适合需要事务一致性的多聚合根操作。

```java
try (IUnitOfWork uow = new UnitOfWork(eventManager)) {
    uow.register(order, orderRule, orderRepository, Order::cancel)
       .register(inventory, inventoryRule, inventoryRepository, Inventory::deduct)
       .commit();   // 统一校验 → 落库 → 发布事件
}
```

`UnitOfWork` 实现 `AutoCloseable`：未提交时 `close()` 自动清空各条目事件，防止内存泄漏；`commit()` 与 `tryCommit()` 均幂等保护，重复调用抛 `IllegalStateException`。

#### 试跑 `tryCommit`

`tryCommit` 逐条执行领域逻辑与规则校验，跳过持久化与事件分发；任一条目未通过时收集明细、不中断其余条目；试跑会消费工作单元，之后不可再 `commit`/`tryCommit`。

```java
DryRunResult result = unitOfWork.tryCommit();   // 零副作用，返回聚合全部条目校验结论
```

### 2.3 应用服务基类：`AbstractApplicationService`

便捷基类，内聚 `ICommandExecutor` 与 `IUnitOfWork` 工厂，子类以语义方法暴露能力，无需直接持有编排器。

```java
public class OrderApplicationService extends AbstractApplicationService {

    private final OrderRepository orderRepository;
    private final OrderRule orderRule;

    public OrderApplicationService(IEventManager eventManager,
                                   OrderRepository orderRepository,
                                   OrderRule orderRule) {
        super(eventManager);  // 默认 CommandExecutor + UnitOfWork
        this.orderRepository = orderRepository;
        this.orderRule = orderRule;
    }

    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId);
        execute(order, orderRule, orderRepository, Order::cancel);
    }

    public DryRunResult tryCancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId);
        return tryExecute(order, orderRule, orderRepository, Order::cancel);
    }

    public void cancelWithInventory(Order order, Inventory inventory, ...) {
        try (IUnitOfWork uow = beginUnitOfWork()) {
            uow.register(order, orderRule, orderRepository, Order::cancel)
               .register(inventory, inventoryRule, inventoryRepository, Inventory::deduct)
               .commit();
        }
    }
}
```

三个受保护构造器：

| 构造器 | 用途 |
| --- | --- |
| `AbstractApplicationService(IEventManager)` | 默认 `CommandExecutor` + 默认 `UnitOfWork` |
| `AbstractApplicationService(IEventManager, ICommandExecutor)` | 注入自定义 `ICommandExecutor` |
| `AbstractApplicationService(IEventManager, ICommandExecutor, Supplier<IUnitOfWork>)` | 全自定义 + `IUnitOfWork` 工厂 |

> **重要约束**：`AbstractApplicationService` 仅为便捷基类，不强制继承；也可直接组合 `ICommandExecutor` / `IUnitOfWork` 使用。

### 2.4 实体工厂、更新器与属性解析器

框架提供创建与修改聚合根的契约分离，以及属性解析的适配器工厂。

#### `EntityFactory<T, C>`：创建新实体

```java
public interface EntityFactory<T extends AggregateRoot<?>, C> {
    T create(C command);   // 从 Command DTO 构建新聚合根，遵循"先算后赋"
}
```

#### `EntityUpdater<T, C>`：修改已有实体

```java
public interface EntityUpdater<T extends AggregateRoot<?>, C> {
    void apply(T aggregateRoot, C command);   // 从 Command 计算变更并调用实体业务方法完成修改
}
```

#### `IEntityPropertyResolver<C, E, R>` / `EntityPropertyResolvers`：属性解析适配

`IEntityPropertyResolver` 在具体场景下从 Command DTO 与实体现状解析出实体属性值；`EntityPropertyResolvers` 适配器工厂将同一个实体属性计算领域服务（`IEntityPropertyCalculator`）适配到不同场景的 Command DTO，一处定义多处复用。

```java
// 取数仅依赖 Command
IEntityPropertyResolver<CreateCmd, Order, BigDecimal> r1 =
        EntityPropertyResolvers.of(calculator, CreateCmd::getAmount);

// 取数需同时依赖 Command 与实体现状
IEntityPropertyResolver<UpdateCmd, Order, BigDecimal> r2 =
        EntityPropertyResolvers.of(calculator, (cmd, entity) -> cmd.extract(entity));
```

> ⚠️ **重要约束**：`EntityUpdater` 的方法是 `apply`（非 `update`）；`IEntityPropertyResolver` 的方法是 `resolve(C, E)`，创建场景下可调用 `resolve(C)`（entity 置 `null`）。

### 2.5 应用服务分层标记

```java
public interface ICommandApplicationService { }   // 命令服务（写操作）
public interface IQueryApplicationService { }      // 查询服务（读操作）
```

读写分离标记，配合仓储的 `IRepository`（写）与 `IAggregateProjection`（读）实现读写分离：

```java
public class OrderCommandService extends AbstractApplicationService
        implements ICommandApplicationService { /* 写操作 */ }

public class OrderQueryService implements IQueryApplicationService { /* 读操作 */ }
```

## 3. 关键机制与避坑指南

### 3.1 Outbox 事务性发件箱

`OutboxCommandExecutor` / `OutboxUnitOfWork` 与默认执行器并存、零侵入，保证事件与聚合根同事务原子落库：

```text
① 同事务：聚合落库 + outbox 落库（PENDING）
② 事务提交后 → EagerOutboxPublisher.publishAfterCommit 主动推送（post-commit，规避"提交前误发"）
③ 若主动推送失败/崩溃（未 markSent）→ OutboxRelay 兜底轮询补偿
```

`EagerOutboxPublisher` 在事务外发送原始事件，成功后 `markSent`（PENDING→SENT，带状态守卫、幂等）；失败/崩溃不标记，保留 PENDING 交由 Relay 补偿。

```java
OutboxCommandExecutor outboxExecutor = new OutboxCommandExecutor(
        outboxStore,            // IOutboxStore 实现（如 MybatisOutboxStore）
        transactionOperations,  // TransactionOperations（事务模板）
        eventSerializer,        // IEventSerializer
        eagerPublisher);        // EagerOutboxPublisher

outboxExecutor.execute(order, rule, repository, Order::cancel);   // 用法与 CommandExecutor 完全一致
```

### 3.2 Outbox 状态机与兜底轮询

`OutboxStatus` 四态：`PENDING` / `PROCESSING` / `SENT` / `FAILED`。

```text
PENDING ──claim──→ PROCESSING ──publish成功──→ SENT
                      │
                      └──publish失败──→ release → PENDING
                                        ↓
                                   attempts > max?
                                      ├── 是 → FAILED（死信）
                                      └── 否 → 继续 PENDING
```

`OutboxRelay` 通过 `claimPending`（原子认领超时 PENDING 批次，`WHERE status=PENDING AND created_at < now-grace LIMIT ?`）补偿重发；`OutboxRelayConfig.defaultConfig()` 默认值：**pollInterval=5min、grace=30s、batchSize=200、maxAttempts=10**，亦可用 `OutboxRelayConfig.bind(source)` 按 `outbox` 前缀从配置源绑定。

```java
OutboxRelay relay = new OutboxRelay(
        outboxStore, eventManager, eventSerializer, scheduledExecutor,
        OutboxRelayConfig.defaultConfig());
relay.start();   // 启动周期性轮询（scheduleAtFixedRate）
```

> ⚠️ **重要约束**：Outbox 执行器的事件推送发生在**事务提交之后**（post-commit）；`markSent` 为独立短事务且带状态守卫，保证幂等。文档早期示例将 `pollInterval`/`batchSize`/`maxAttempts` 写为 5s/100/5 系旧值，正确默认值为 5min/200/10。

### 3.3 何时用 Outbox 替代默认执行器

| 场景 | 推荐 |
| --- | --- |
| 单体应用，事件即时发布即可 | `CommandExecutor` / `UnitOfWork` |
| 需保证事件与聚合根同事务原子性 | `OutboxCommandExecutor` / `OutboxUnitOfWork` |
| 多实例部署，需防重复投递（claim_token 机制） | Outbox（`OutboxRelay.claimPending` 原子认领） |
| 事件投递可容忍少量延迟（兜底补偿） | Outbox |

## 4. 异常与错误处理体系

应用服务层主要复用框架既有异常，编排器自身不定义专属异常类型：

| 触发点 | 异常 | 说明 |
| --- | --- | --- |
| 规则校验未通过 | `RuleException`（经 `throwBrokenRuleException`） | `CommandExecutor`/`UnitOfWork` 提交时中断 |
| 试跑中规则失败 | 转译为 `DryRunResult.reject`，**不抛异常** | 仅 `ICommandExecutor.tryExecute` / `IUnitOfWork.tryCommit` |
| 工作单元重复提交/试跑 | `IllegalStateException` | `commit()` / `tryCommit()` 幂等保护 |
| Outbox 兜底失败超限 | `OutboxRelay` 标记 `FAILED` | 死信，不抛异常，需独立监控 |

> **重要约束**：试跑（Dry-run）**仅将规则类异常**转译为未通过结论；领域逻辑中抛出的非规则异常（如空指针、基础设施异常）仍照常上抛，不会被吞掉。

## 5. 总结速查

| 概念 | 使用方式 | 最关键约束 |
| --- | --- | --- |
| 单聚合根命令 | `CommandExecutor.execute(t, rule, repo, logic)` | 默认先 save 再逐条 publish |
| 跨聚合根工作单元 | `UnitOfWork.register(...).commit()`（try-with-resources） | 统一校验→落库→发布；`close()` 未提交自动清理 |
| 试跑 | `tryExecute` / `tryCommit` → `DryRunResult` | 零副作用；试跑实例不可复用；仅规则异常转译 |
| 应用服务基类 | 继承 `AbstractApplicationService`，`super(eventManager)` | 提供 `execute`/`tryExecute`/`beginUnitOfWork`；非强制 |
| 实体创建/修改 | `EntityFactory.create` / `EntityUpdater.apply` | `apply` 非 `update` |
| 属性解析 | `EntityPropertyResolvers.of(calc, extractor)` | 一处定义多处复用 |
| 分层标记 | `ICommandApplicationService` / `IQueryApplicationService` | 配合 `IRepository`/`IAggregateProjection` 读写分离 |
| Outbox 执行器 | `OutboxCommandExecutor` / `OutboxUnitOfWork` | 同事务落库；post-commit 推送 |
| Outbox 兜底 | `OutboxRelay.start()` + `OutboxRelayConfig` | 默认 5min/30s/200/10；四态状态机 |

## 6. 命名规范速查

结合框架事实约束（接口以 `I` 开头、执行器/工作单元语义命名、Outbox 类以 `Outbox` 前缀、方法名定论），约定如下：

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 命令执行器接口/类 | `ICommandExecutor` / `{语义}CommandExecutor` | `ICommandExecutor`、`CommandExecutor` |
| 工作单元接口/类 | `IUnitOfWork` / `{语义}UnitOfWork` | `IUnitOfWork`、`UnitOfWork`、`OutboxUnitOfWork` |
| 应用服务基类 | `Abstract{层}Service` | `AbstractApplicationService` |
| 命令服务类 | `{聚合}CommandService implements ICommandApplicationService` | `OrderCommandService` |
| 查询服务类 | `{聚合}QueryService implements IQueryApplicationService` | `OrderQueryService` |
| 语义方法 | `execute` / `tryExecute` / `beginUnitOfWork` / `commit` / `tryCommit` | 基类已定义，子类按需派生 `cancelOrder` |
| 实体工厂 | `{聚合}Factory implements EntityFactory<T,C>` | `OrderFactory` |
| 实体更新器 | `{聚合}Updater implements EntityUpdater<T,C>`（方法 `apply`） | `OrderUpdater` |
| 属性解析器 | `{语义}Resolver implements IEntityPropertyResolver<C,E,R>` | `DiscountResolver` |
| Outbox 类 | `Outbox{角色}` | `OutboxCommandExecutor`、`OutboxRelay`、`OutboxRelayConfig`、`EagerOutboxPublisher` |
| 试跑结果 | `DryRunResult`（`pass()` / `reject(...)`） | `DryRunResult` |

> ⚠️ **重要约束**：`EntityUpdater` 的方法名为 `apply`（非 `update`）；`IEntityPropertyResolver` 的创建场景便捷方法为 `resolve(C)`。命名误用将导致编译失败。Outbox 配置键统一以 `outbox` 为前缀（`outbox.poll-interval` / `outbox.grace` / `outbox.batch-size` / `outbox.max-attempts`），由 `OutboxRelayConfig.bind` 绑定。

**下一步阅读**

- [仓储](./repository.md)：聚合根的持久化与版本对账
- [Outbox 最佳实践](../best-practices/transactional-outbox.md)
- [MyBatis 集成](../integration/mybatis.md)：`MybatisOutboxStore` 的使用
