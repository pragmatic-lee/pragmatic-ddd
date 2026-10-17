# BP 卡：应用层落地模式（application-collaboration）

> 源：`documentation/best-practices/application-collaboration.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 包结构

```text
application/{agg}/
├── {Agg}WriteService.java      # extends AbstractApplicationService implements ICommandApplicationService
├── {Agg}ReadService.java       # implements IQueryApplicationService（无基类）
├── input/{Action}Input.java
├── factory/{Agg}Factory.java
├── updater/{Agg}{Action}Updater.java
├── resolver/Command → 领域输入适配
├── rule/{Agg}RuleConfig.java
├── service/领域服务实现
└── subscriber/事件订阅登记
```

## 2. 命名规范

| 类型 | 命名 | 示例 |
| --- | --- | --- |
| 命令应用服务 | `{Agg}WriteService` | `OrderWriteService` |
| 查询应用服务 | `{Agg}ReadService` | `OrderReadService` |
| 入参 | `{Action}Input` | `PayOrderInput` |
| 工厂 | `{Agg}Factory` | `OrderFactory` |
| 修改器 | `{Agg}{Action}Updater` | `OrderPayUpdater` |
| 规则装配 | `{Agg}RuleConfig` | `OrderRuleConfig` |

**Input 注释**：类注释写三要素（业务语义 / 与协议及领域值对象的边界 / 由哪个 Factory 或 Updater 转换）；字段注释写金额币种、时间语义、编码来源、组合字段关系，不复述类型。详见 `code-comment-specification.md`。

## 3. WriteService 骨架

```java
@Service
public class OrderWriteService extends AbstractApplicationService
        implements ICommandApplicationService {
    private final OrderFactory orderFactory;
    private final OrderRule orderRule;
    private final OrderRepository orderRepository;
    private final OrderPayUpdater orderPayUpdater;

    public OrderWriteService(IEventManager eventManager,
                             OutboxCommandExecutor commandExecutor,
                             Supplier<IUnitOfWork> unitOfWorkFactory,
                             OrderFactory orderFactory, OrderRule orderRule,
                             OrderRepository orderRepository, OrderPayUpdater orderPayUpdater) {
        super(eventManager, commandExecutor, unitOfWorkFactory);
        this.orderFactory = orderFactory;
        this.orderRule = orderRule;
        this.orderRepository = orderRepository;
        this.orderPayUpdater = orderPayUpdater;
    }

    public Order payOrder(Long orderId, PayOrderInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.execute(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input));
    }
}
```

## 4. 关键约定

- 职责：编排（串 Factory / Updater / Rule / Repository / EventManager）；不做业务逻辑、不做持久化细节、不做协议转换。
- 必须显式注入执行器（`CommandExecutor`/`OutboxCommandExecutor`）+ `Supplier<IUnitOfWork>`，无默认构造器。
- `execute(agg, rule, repo, Consumer<T>)` 固定顺序：domainLogic → satisfiesRule → persistAndDispatch → clearWorkUnitState。
- 每个用例一个公开方法；`execute` 四参：聚合、规则容器、仓储、领域逻辑。
- `tryExecute(...)` 返回 `DryRunResult`（`passed()`/`brokenRules()`），不落库不发事件（预校验）。
- 业务方法内"先 `recordOperation` 后 `collectEvent`"：事件 `operationCode` 自动取最近一次操作；顺序颠倒抛 `OperationException`。
- 延迟事件：`entityId` 为 `null` 时用 `() -> XxxEvent.buildEvent(this)`，发布时才读真实 ID。
- 事务边界：`@Transactional` 由调用方（WriteService 方法 / 执行器）负责，仓储不管理事务。

## 5. 执行器与单元工厂配对

| 执行器 | 工作单元工厂 | 场景 |
| --- | --- | --- |
| `CommandExecutor` | `UnitOfWork` 工厂 | 不需 Outbox 事务一致性 |
| `OutboxCommandExecutor` | `OutboxUnitOfWork` 工厂 | 跨模块可靠投递 / 崩溃兜底 |

⚠️ 禁止混用：同一服务内不得出现 `CommandExecutor` + `OutboxUnitOfWork` 或反之。

## 6. Factory / Updater

- `EntityFactory<T,C>`：`T create(C command)`，先算后赋。
- `EntityUpdater<T,C>`：`void apply(T, C)`；Input→领域对象转换 + 调充血方法，**不校验/不持久化/不发事件**（硬约束 11）。

## 7. 读侧

- 读服务 `implements IQueryApplicationService`，**不继承** `AbstractApplicationService`（框架已无 `AbstractProjectionQuery` 查询基类）。
- 注入领域层源端口（`IOrderRedisSource`/`IOrderESSource`），选源写死方法体内，不外泄给调用方（无 `ProjectorRegistry` 可按 id 反查源）。
- 两跳编排：查全量（`getById`/`search`）→ `reduceWith(getReducer(projectionType), full, projectionType)` 裁剪为目标投影。
- 读侧不发布领域事件、不持有写仓储。

## 8. 多聚合根：工作单元写法

单聚合根用 `execute()`；一次用例操作多个聚合根且需同一事务时，用 `beginUnitOfWork()`（基类提供，底层 `unitOfWorkFactory.get()`，`IUnitOfWork` 实现 `AutoCloseable`）：

```java
public void payWithDeduction(Long orderId, Long inventoryId, PayOrderInput input) {
    Order order = orderRepository.findById(orderId);
    Inventory inventory = inventoryRepository.findById(inventoryId);
    if (order == null || inventory == null) {
        return;
    }
    try (IUnitOfWork uow = beginUnitOfWork()) {
        uow.register(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input))
           .register(inventory, inventoryRule, inventoryRepository, Inventory::deduct)
           .commit();
    }
}
```

- `register(agg, rule, repo, Consumer<T>)` 链式，四元组与 `execute()` 一一对应；换 `OutboxUnitOfWork` 工厂业务代码零改动。
- **一律 try-with-resources**：未提交时 `close()` 自动 `clearWorkUnitState`，防事件泄漏 / 串味。
- 工作单元有状态、单次消费，注入 `Supplier<IUnitOfWork>` 原型工厂，不得直接暴露单例。

## 9. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 应用层写业务规则 | 业务逻辑内聚聚合根 |
| 绕过 `execute` 手动编排 | 统一走 `execute`/`tryExecute` |
| Updater 内校验/持久化 | Updater 只转换 + 调充血方法 |
| 每个方法 new 执行器 | 构造器注入一次复用 |
| 复杂查询塞进写仓储 | 交查询侧 |
| 多聚合根逐个 `execute()` | 用 `beginUnitOfWork()` 注册多条目统一 `commit` |
| `UnitOfWork` 当单例 Bean 注入 | 注入 `Supplier<IUnitOfWork>` 工厂 |
| 工作单元不用 try-with-resources | 一律 `try (IUnitOfWork uow = beginUnitOfWork())` |
| 执行器与工作单元语义混用 | 成对匹配：`CommandExecutor`+`UnitOfWork`、`OutboxCommandExecutor`+`OutboxUnitOfWork` |

## 10. 被读于

S4 `ddd-aggregate-create`（步骤 4 应用层）、S7 `ddd-code-review`。
