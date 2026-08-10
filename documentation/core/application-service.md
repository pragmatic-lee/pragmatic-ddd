# 应用服务

> 本文档介绍应用服务层（`io.pragmatic.ddd.application`）的命令执行器、工作单元与 Outbox 模式。
> 前置阅读：[领域建模](./domain-modeling.md) · [业务规则引擎](./business-rules.md) · [领域事件](./domain-events.md)。

## 1. 概述

应用服务层负责编排"领域逻辑 → 规则校验 → 持久化 → 事件分发"的标准流程。框架提供两种编排方式：

| 方式 | 适用场景 | 类 |
| --- | --- | --- |
| **单聚合根命令** | 一次操作只涉及一个聚合根 | `CommandExecutor` |
| **跨聚合根工作单元** | 一次操作涉及多个聚合根，需统一提交 | `UnitOfWork` |

此外，Outbox 模式提供事务性发件箱，保证事件与聚合根同事务原子性。

## 2. 单聚合根命令 `CommandExecutor`

### 2.1 模板流程

`AbstractCommandExecutor` 固定了五步模板：

```
1. 执行领域逻辑    domainLogic.accept(aggregateRoot)
2. 规则校验        aggregateRoot.satisfiesRule(rule)  →  未通过则 throwBrokenRuleException
3. 持久化          repository.save(aggregateRoot)     ←  钩子：persistAndDispatch
4. 发布事件        eventManager.publish(events)       ←  钩子：persistAndDispatch
5. 清空状态        aggregateRoot.clearWorkUnitState()
```

`CommandExecutor`（默认实现）在 `persistAndDispatch` 中：先 `save` 再逐条 `publish`。

### 2.2 使用方式

```java
IEventManager eventManager = new ThreadPoolEventManager(LocalEventManagerConfig.defaultConfig());
eventManager.start();

CommandExecutor executor = new CommandExecutor(eventManager);
OrderRepository repository = new OrderRepository();
OrderRule rule = new OrderRule();

Order order = new Order(1L, "张三", 100);
Order result = executor.execute(order, rule, repository, Order::cancel);
```

### 2.3 试跑 Dry-run

```java
DryRunResult result = executor.tryExecute(order, rule, repository, Order::cancel);

if (result.passed()) {
    // 校验通过
} else {
    List<BrokenRule> brokenRules = result.brokenRules();
    // 处理校验失败
}
```

::: warning Dry-run 后不可重用
试跑后的聚合根状态已变更（领域逻辑已执行），不可再用于真实 `execute`。试跑应使用专用实例。
:::

## 3. 跨聚合根工作单元 `UnitOfWork`

### 3.1 模板流程

`AbstractUnitOfWork` 固定了多聚合根的统一提交流程：

```
1. 逐条执行领域逻辑    每个注册条目执行 domainLogic
2. 逐条规则校验        每个条目 satisfiesRule → 未通过则 throwBrokenRuleException
3. 逐条持久化          每个条目 repository.save
4. 收集全部事件        汇总所有聚合根的 getDomainEvents
5. 统一发布            eventManager.publishList(allEvents)
6. 逐条清空            每个条目 clearWorkUnitState
```

与 `CommandExecutor` 的区别：**先全部校验，再统一落库，最后统一发布事件**，适合需要事务一致性的多聚合根操作。

### 3.2 使用方式

```java
UnitOfWork unitOfWork = new UnitOfWork(eventManager);

unitOfWork.register(order, orderRule, orderRepository, Order::cancel)
          .register(inventory, inventoryRule, inventoryRepository, Inventory::deduct)
          .commit();   // 统一校验 → 落库 → 发布事件

// 或用 try-with-resources
try (IUnitOfWork uow = new UnitOfWork(eventManager)) {
    uow.register(order, orderRule, orderRepository, Order::cancel)
       .register(inventory, inventoryRule, inventoryRepository, Inventory::deduct)
       .commit();
}
```

::: tip 自动清理
`UnitOfWork` 实现了 `AutoCloseable`，未提交时 `close()` 会自动清理事件，防止内存泄漏。
:::

### 3.3 试跑 `tryCommit`

```java
DryRunResult result = unitOfWork.tryCommit();
// 逐条执行领域逻辑与规则校验，不落库、不发事件
```

## 4. 应用服务基类 `AbstractApplicationService`

`AbstractApplicationService` 是便捷基类，内聚 `ICommandExecutor` 与 `IUnitOfWork`：

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
}
```

三个构造器：

```java
// 1. 默认：CommandExecutor + UnitOfWork
protected AbstractApplicationService(IEventManager eventManager)

// 2. 注入自定义 CommandExecutor
protected AbstractApplicationService(IEventManager eventManager, ICommandExecutor commandExecutor)

// 3. 全自定义：CommandExecutor + UnitOfWork 工厂
protected AbstractApplicationService(IEventManager eventManager,
                                     ICommandExecutor commandExecutor,
                                     Supplier<IUnitOfWork> unitOfWorkFactory)
```

::: tip 不强制继承
`AbstractApplicationService` 只是便捷基类，你也可以直接组合使用 `ICommandExecutor` / `IUnitOfWork`。
:::

## 5. Outbox 模式

### 5.1 `OutboxCommandExecutor`

`OutboxCommandExecutor` 是可选的命令执行器，实现事务性发件箱：

```java
OutboxCommandExecutor outboxExecutor = new OutboxCommandExecutor(
        outboxStore,          // IOutboxStore 实现（如 MybatisOutboxStore）
        transactionOperations, // TransactionOperations（事务模板）
        eventSerializer,      // IEventSerializer
        eagerPublisher);      // EagerOutboxPublisher（事务提交后主动推送）

// 使用方式与 CommandExecutor 完全一致
outboxExecutor.execute(order, rule, repository, Order::cancel);
```

内部流程：

```
① 同事务：聚合落库 + outbox 落库(PENDING)
② 事务提交后 → 触发主动推送（EagerOutboxPublisher）
③ 若主动推送失败 → OutboxRelay 兜底轮询补偿
```

### 5.2 `OutboxRelay` 兜底轮询

```java
OutboxRelay relay = new OutboxRelay(
        outboxStore,
        eventManager,
        eventSerializer,
        scheduledExecutor,
        new OutboxRelayConfig(
                Duration.ofSeconds(5),  // pollInterval
                100,                     // batchSize
                Duration.ofSeconds(30), // grace（超过此时间的 PENDING 才认领）
                5));                     // maxAttempts

relay.start();  // 启动周期性轮询
```

状态机：

```
PENDING ──claim──→ PROCESSING ──publish成功──→ SENT
                      │
                      └──publish失败──→ release → PENDING
                                        ↓
                                   attempts > max?
                                      ├── 是 → FAILED（死信）
                                      └── 否 → 继续 PENDING
```

### 5.3 何时用 Outbox 替代 CommandExecutor

| 场景 | 推荐 |
| --- | --- |
| 单体应用，事件即时发布即可 | `CommandExecutor` |
| 需要保证事件与聚合根同事务原子性 | `OutboxCommandExecutor` |
| 多实例部署，需防重复投递 | `OutboxCommandExecutor`（claim_token 机制） |
| 事件投递可容忍少量延迟 | `OutboxCommandExecutor` |

## 6. 实体工厂与更新器

框架提供了创建与修改实体的契约分离：

```java
// 实体工厂：创建新实体
public interface EntityFactory<T, C> {
    T create(C command);
}

// 实体更新器：修改已有实体
public interface EntityUpdater<T, C> {
    void update(T entity, C command);
}
```

## 7. 应用服务分层标记

```java
// 命令服务（修改操作）
public interface ICommandApplicationService { }

// 查询服务（读取操作）
public interface IQueryApplicationService { }

// 示例
public class OrderCommandService extends AbstractApplicationService
        implements ICommandApplicationService {
    // 写操作
}

public class OrderQueryService implements IQueryApplicationService {
    // 读操作
}
```

读写分离标记，配合仓储的 `IRepository`（写）与 `IAggregateProjection`（读）实现读写分离。

---

下一步：

- [仓储](./repository.md)：聚合根的持久化与版本对账
- [Outbox 最佳实践](../best-practices/transactional-outbox.md)
- [MyBatis 集成](../integration/mybatis.md)：`MybatisOutboxStore` 的使用
