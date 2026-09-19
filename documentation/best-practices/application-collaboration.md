# 应用层落地模式

> 本文档介绍应用服务层的落地方式：WriteService 怎么搭、`execute()` 模板顺序、创建 / 修改 / 预校验三场景、规则装配与执行器选择。前置阅读：[聚合目录落地骨架](./aggregate-structure.md) · [聚合设计原则](./aggregate-design.md)。

## 1. 本质与定位

应用服务层（Application）是**编排领域逻辑**的层：接收 Input → 建聚合（`EntityFactory`）/ 改聚合（`EntityUpdater`）→ 规则校验 → 仓储持久化 → 领域事件发布，全部由 `execute()` 模板统一编排，置于同一事务边界。

- 职责：**编排**（把 Factory / Updater / Rule / Repository / EventManager 串起来）。
- 不做：不写业务逻辑（业务逻辑在聚合根）、不操作持久化细节（仓储承担）、不做协议转换（UI 层承担）。
- 核心形态：应用服务分**命令（写）**与**查询（读）**两类——写服务 `{Agg}WriteService` 继承 `AbstractApplicationService`，每个用例一个公开方法、内部走 `execute()` / `tryExecute()`；读服务 `{Agg}ReadService` 无框架基类，注入领域层源端口、`implements IQueryApplicationService`，只读不写（见 §4.8）。

## 2. 命名与包结构

### 2.1 包结构

```text
application/{agg}/
├── {Agg}WriteService.java      # 命令应用服务（外层）
├── {Agg}ReadService.java       # 查询应用服务（外层）
├── input/                      # {Action}Input 业务语义入参
├── factory/                    # EntityFactory 聚合工厂（创建场景）
├── updater/                    # EntityUpdater 修改器（修改场景）
├── resolver/                   # Command → 领域输入适配
├── rule/                       # 规则装配（如 OrderRuleConfig）
├── service/                    # 领域服务实现
└── subscriber/                 # 事件订阅登记
```

### 2.2 命名规范

| 类型 | 命名 | 示例 |
| --- | --- | --- |
| 命令应用服务 | `{Agg}WriteService` | `OrderWriteService` |
| 查询应用服务 | `{Agg}ReadService` | `OrderReadService` |
| 入参 | `{Action}Input` | `PayOrderInput` |
| 工厂 | `{Agg}Factory` | `OrderFactory` |
| 修改器 | `{Agg}{Action}Updater` | `OrderPayUpdater` |
| 规则装配 | `{Agg}RuleConfig` | `OrderRuleConfig` |

## 3. 数据 / 职责承载

| 承载 | 不承载 |
| --- | --- |
| 编排依赖（Factory / Updater / Rule / Repository / EventManager） | 业务规则判断（聚合根 / 规则容器） |
| 事务边界与事件发布（委托执行器） | 持久化 SQL / 技术细节（仓储） |
| Input → 领域对象的组装与转换（Factory / Updater / Resolver） | 协议 Request / Response（UI 层） |

## 4. 落地方式（核心）

### 4.1 WriteService 骨架

```java
@Service
public class OrderWriteService extends AbstractApplicationService
        implements ICommandApplicationService {

    private final OrderFactory orderFactory;
    private final OrderRule orderRule;
    private final OrderRepository orderRepository;
    private final OrderPayUpdater orderPayUpdater;
    // ... 其余 Updater

    public OrderWriteService(IEventManager eventManager,
                             OutboxCommandExecutor commandExecutor,
                             Supplier<IUnitOfWork> unitOfWorkFactory,
                             OrderFactory orderFactory,
                             OrderRule orderRule,
                             OrderRepository orderRepository,
                             OrderPayUpdater orderPayUpdater) {
        // 执行器与工厂由组合根（OutboxConfig）以成品 Bean 注入，服务内不再手动 new
        super(eventManager, commandExecutor, unitOfWorkFactory);
        this.orderFactory = orderFactory;
        this.orderRule = orderRule;
        this.orderRepository = orderRepository;
        this.orderPayUpdater = orderPayUpdater;
    }

    /** 支付：加载聚合 → Updater 改聚合 → 统一校验 + 持久化 + 事件发布。 */
    public Order payOrder(Long orderId, PayOrderInput input) {
        Order order = orderRepository.findById(orderId);
        if (order == null) {
            return null;
        }
        return super.execute(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input));
    }
}
```

要点：

- 继承 `AbstractApplicationService`，**必须显式注入命令执行器与工作单元工厂**（不再提供默认构造器）。
- `OutboxCommandExecutor` 与 `Supplier<IUnitOfWork>`（outbox 工厂）由 `OutboxConfig` 以成品 Bean 提供，执行器与工厂语义须一致。
- 每个用例一个公开方法：加载 / 创建聚合 → `execute(...)`。
- `execute` 四参：聚合根、规则容器、仓储、领域逻辑（`Consumer<T>`）。

### 4.2 execute() 模板顺序

`AbstractCommandExecutor.execute()` 固定五步，子类只管"怎么落库 + 分发"：

```text
1. domainLogic.accept(aggregateRoot)   执行领域逻辑（Factory 建 / Updater 改）
2. satisfiesRule(rule)                 规则校验，失败抛 BrokenRuleException
3. persistAndDispatch                  落库（repository.save）+ 事件发布（eventManager.publish）
4. clearWorkUnitState()                事件 / 操作清空（防止跨请求串味）
```

> 事件发布后的清理由模板内置，继承 `AbstractApplicationService` 无需手动调用；仅在自研编排时需要。

### 4.3 创建场景：EntityFactory（先算后赋）

```java
@Component
public class OrderFactory implements EntityFactory<Order, CreateOrderInput> {

    @Override
    public Order create(CreateOrderInput input) {
        Long orderId = idGenerator.nextId();
        List<OrderItem> items = totalAmountResolver.toOrderItems(input, orderId);
        Customer customer = new Customer(input.getCustomerId(), input.getCustomerName());
        Order probeOrder = new Order(probeData(customer), orderId);    // 临时探测 Order
        Money total = totalAmountResolver.resolve(input, probeOrder);  // 先算派生属性
        // ... 组装 OrderInitData 并 setTotalAmount(total)，再 new Order(data, orderId) // 后赋
    }
}
```

`EntityFactory<T, C>` 契约：`T create(C command)`，从 Command DTO 构建聚合，遵循「先算后赋」。WriteService 下单用例 `orderFactory.create(input)` 后交给 `execute` 落库。

### 4.4 修改场景：加载聚合 + EntityUpdater

```java
@Component
public class OrderPayUpdater implements EntityUpdater<Order, PayOrderInput> {

    @Override
    public void apply(Order aggregateRoot, PayOrderInput command) {
        PaymentInfo paymentInfo = new PaymentInfo(
                command.getPaymentSerialNo(),
                new Money(command.getPlatformDiscountAmount(), command.getCurrency()),
                new Money(command.getAmount(), command.getCurrency()));
        aggregateRoot.pay(paymentInfo);   // 调聚合充血方法
    }
}
```

`EntityUpdater<T, C>` 契约：`void apply(T aggregateRoot, C command)`。职责 = Input → 领域对象转换 + 调充血方法；**不做校验、不持久化、不发事件**（由 `execute` 模板统一）。

### 4.5 预校验：tryExecute → DryRunResult

需要"先试跑不落库"的场景（如表单预校验 / 下单前检查）：

```java
public DryRunResult tryPayOrder(Long orderId, PayOrderInput input) {
    Order order = orderRepository.findById(orderId);
    if (order == null) {
        return null;
    }
    return super.tryExecute(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input));
}
```

`tryExecute` 返回 `DryRunResult`（`passed()` / `brokenRules()`），不落库、不发事件，供预校验反馈。

### 4.6 规则装配：OrderRuleConfig

规则容器 `OrderRule` 的构造依赖（领域服务契约、仓储）由装配配置显式声明，领域层保持零 Spring 依赖：

```java
@Configuration
public class OrderRuleConfig {

    @Bean
    public OrderRule orderRule(IOrderCustomerPermissionService permissionService,
                               IOrderRepository orderRepository) {
        return new OrderRule(permissionService, orderRepository);
    }
}
```

> 规则容器的构造与触发完整落地见 [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)。

### 4.7 执行器选择

| 执行器 | 语义 | 场景 |
| --- | --- | --- |
| `CommandExecutor` | 落库后立即发布事件 | 不需要 Outbox 的事务一致性兜底 |
| `OutboxCommandExecutor` | 聚合写 + outbox 行同事务，异步投递 | 跨模块可靠投递 / 崩溃兜底（见 [Outbox 链路装配](./outbox-config.md)） |

> `AbstractApplicationService` 不再提供默认构造器，执行器与工作单元工厂必须由继承者**显式注入**（通常以组合根 `@Bean` 提供成品），且二者语义须一致：默认场景用 `CommandExecutor` + `UnitOfWork`，outbox 场景用 `OutboxCommandExecutor` + `OutboxUnitOfWork`，禁止混用。工作单元的装配（原型工厂）与多聚合根直接编排写法见 [§4.9](#49-工作单元unitofwork--outboxunitofwork落地写法)。

### 4.8 读侧 ReadService：不进 `execute()` 模板

应用层有两类服务，**写走命令（`{Agg}WriteService`）、读走查询（`{Agg}ReadService`）**，但读侧与写侧有本质差异：

| 维度 | WriteService（命令侧） | ReadService（查询侧） |
| --- | --- | --- |
| 承载基类 | `extends AbstractApplicationService`（可写） | **无基类**（只读，只 `implements` 标记接口） |
| 应用服务标记 | `implements ICommandApplicationService` | `implements IQueryApplicationService` |
| 是否走 `execute()` / `tryExecute()` | 走模板（校验 → 落库 → 发布事件） | **不走**：只查，不建/改聚合 |
| 是否产生领域事件 / 写库 | 是（同步落库 + 事件发布） | **否**：读不产生业务事件、不持有写仓储 |
| 依赖 | Factory / Updater / Rule / Repository / EventManager | 领域层源端口（`IOrderRedisSource` / `IOrderESSource`） |

**读服务为什么不继承 `AbstractApplicationService`**：读侧没有"改聚合 → 校验 → 落库 → 发事件"这一套写语义，不需要 `execute()` 模板。框架**也不再提供** `AbstractProjectionQuery` 查询基类——「用哪个源」由「源端口 extends 了哪些查询族」在编译期决定，读服务只需注入领域源端口、按族分派并完成「查全量 → 裁剪」两跳：

```java
@Service
public class OrderReadService implements IQueryApplicationService {

    private final IOrderRedisSource redisSource;
    private final IOrderESSource esSource;

    public OrderReadService(IOrderRedisSource redisSource, IOrderESSource esSource) {
        this.redisSource = redisSource;
        this.esSource = esSource;
    }

    // 选源写死在方法体内（本项目：主键族走 Redis，条件族 / 分页族走 ES）
    public <X extends IOrderProjection> X queryById(Long id, Class<X> projectionType) {
        var full = redisSource.getById(id);
        if (full == null) {
            return null;
        }
        return reduceWith(redisSource.getReducer(projectionType), full, projectionType);
    }

    public <X extends IOrderProjection> List<X> queryList(OrderListQuery criteria, Class<X> projectionType) {
        var reducer = esSource.getReducer(projectionType);
        return esSource.search(criteria).stream()
                .map(full -> reduceWith(reducer, full, projectionType))
                .toList();
    }
    // queryByIds / queryOne / queryPage 同构；reduceWith 为私有两跳编排
}
```

> **选源写在读服务方法体内，不外泄给调用方**：不要把 `ProjectionSource` 作为方法入参。读服务直接注入领域层源端口（框架已无 `ProjectorRegistry` 这一「源 id → 源实例」登记表，无从按 id 反查源），选源写死在方法体内。调用方只传条件与目标投影类型。

> 读服务的**角色定位**（为什么门面放应用层、选源写在这里）与**两跳 / 裁剪的完整落地**见 [投影读模型代码落地指南](./projection-design.md#_4-8-选源由源支持哪些查询族在编译期决定)。本小节只区分读写两侧的应用服务形态，不重复投影机制的细节。

> ⚠️ **读侧不发布领域事件、不持有写仓储**：读模型由写侧事件物化而来（见 [投影设计](./projection-design.md)），`ReadService` 只消费读模型副本，不反向触发业务事件，避免读路径污染写一致性。

### 4.9 工作单元（UnitOfWork / OutboxUnitOfWork）落地写法

单个聚合根的写用例走 `execute()` 即可（§4.1）；**当一次用例需要操作多个聚合根、并要求它们落在同一数据库事务内统一提交**时，才需要直接拿到工作单元——`AbstractApplicationService` 暴露 `beginUnitOfWork()` 产出由工厂（`Supplier<IUnitOfWork>`）创建的新实例（`AbstractApplicationService.java:60`）。

> 工作单元是「跨聚合根事务编排」的语义载体，不是 `execute()` 的替代品：单聚合根请继续用 `execute()`，多聚合根才用 `beginUnitOfWork()`。

#### 4.9.1 三阶段模板（事实依据）

`AbstractUnitOfWork` 固定三阶段，子类只实现 `persistAndCollect` 与 `dispatchEvents` 两个钩子（`AbstractUnitOfWork.java:170`、`:173`）：

```text
阶段一（事务外）validateAndCollect   AbstractUnitOfWork.java:62
  逐条执行领域逻辑 + 规则校验 + 汇总事件；任一违反即终止，事务根本不开
阶段二（事务内）persistAndCollect    AbstractUnitOfWork.java:68
  纯数据库写（save / 落 outbox），事务边界由基类统一提供
阶段三（事务外）dispatchEvents       AbstractUnitOfWork.java:74
  统一发布事件（publishList / publishAfterCommit）
```

- 默认 `UnitOfWork`：阶段二逐条 `repository.save`，阶段三 `eventManager.publishList`（`UnitOfWork.java:33`、`:51`）。
- `OutboxUnitOfWork`：阶段二「save + 整批落 outbox（PENDING）同事务」，阶段三 `publishAfterCommit` 提交后主动推送（`OutboxUnitOfWork.java:53`、`:79`）。

> ⚠️ **顺序不可颠倒**：阶段一在事务外完成，是为了让规则校验里的外部调用 / 旧快照查询**不占用数据库连接**；阶段三在事务提交之后，是为了规避「提交前误发」。手动编排工作单元时严禁自创顺序。

#### 4.9.2 装配：Supplier 原型语义与执行器配对

工作单元**有状态、单次消费**，绝不能直接暴露单例。组合根以 `Supplier<IUnitOfWork>` 承载「每次 `get()` 产出全新实例」的原型语义（`OutboxConfig.java:133`）：

```java
@Bean
public Supplier<IUnitOfWork> outboxUnitOfWorkFactory(IOutboxStore outboxStore,
                                                     TransactionOperations txOps,
                                                     IEventSerializer serializer,
                                                     EagerOutboxPublisher eagerPublisher) {
    // 每次 get() new 一个全新 OutboxUnitOfWork；工作单元有状态、单次消费，不得直接暴露单例
    return () -> new OutboxUnitOfWork(outboxStore, txOps, serializer, eagerPublisher);
}
```

执行器与工作单元工厂**必须语义一致**（§4.7 已述，此处强调）：

| 执行器 | 工作单元工厂 | 场景 |
| --- | --- | --- |
| `CommandExecutor` | `UnitOfWork` 工厂 | 不需 Outbox 事务一致性 |
| `OutboxCommandExecutor` | `OutboxUnitOfWork` 工厂 | 跨模块可靠投递 / 崩溃兜底 |

> ⚠️ **禁止混用**：同一服务内不得出现「`CommandExecutor` + `OutboxUnitOfWork`」或反之两套一致性语义。

#### 4.9.3 写法示例（多聚合根 + try-with-resources）

以下为「支付订单 + 扣减库存」两个聚合根同一事务落地的**示意写法**（演示 API 用法，非项目既有用例）。`IUnitOfWork` 实现 `AutoCloseable`，未提交时 `close()` 自动清空各条目暂存事件（`AbstractUnitOfWork.java:151`、`:159`），因此**一律用 try-with-resources**，防止跨请求事件串味：

```java
/** 支付订单并扣减库存：两个聚合根同事务统一提交。 */
public void payWithDeduction(Long orderId, Long inventoryId, PayOrderInput input) {
    Order order = orderRepository.findById(orderId);
    Inventory inventory = inventoryRepository.findById(inventoryId);
    if (order == null || inventory == null) {
        return;
    }
    try (IUnitOfWork uow = beginUnitOfWork()) {   // 工厂产出全新实例，prototype 语义
        uow.register(order, orderRule, orderRepository, t -> orderPayUpdater.apply(t, input))
           .register(inventory, inventoryRule, inventoryRepository, Inventory::deduct)
           .commit();   // 事务外校验 → 事务内统一落库 → 事务外发布事件
    }                  // 未提交时 close() 自动 clearWorkUnitState，防事件泄漏
}
```

要点：

- `beginUnitOfWork()` 由基类提供，底层即 `unitOfWorkFactory.get()`（`AbstractApplicationService.java:60`）；无需在服务内手动 `new`。
- `register(...)` 链式调用，每个条目带「聚合根 + 规则 + 仓储 + 领域逻辑（`Consumer<T>`）」四元组，与 `execute()` 四参一一对应。
- 即便走 `OutboxUnitOfWork` 工厂，上面的写法**零改动**——换工厂即可切换一致性语义，业务代码不感知。

#### 4.9.4 避坑

| 坑 | 事实依据 | 正确做法 |
| --- | --- | --- |
| 重复 `commit()` / `tryCommit()` | `commit()` 二次调用抛 `UnitOfWorkStateException`（`AbstractUnitOfWork.java:54`） | 单次消费；试跑（`tryCommit`）会消费工作单元，之后不可再 `commit` |
| 把 `UnitOfWork` 当单例 Bean 注入 | 有状态、单次消费，注释明确「不得直接暴露单例」（`OutboxConfig.java:122`） | 注入 `Supplier<IUnitOfWork>` 工厂，用 `beginUnitOfWork()` 取新实例 |
| 不用 try-with-resources | 未提交时 `close()` 才清事件（`AbstractUnitOfWork.java:151`） | 一律 `try (IUnitOfWork uow = beginUnitOfWork())` |
| 参与工作单元的聚合无乐观锁 | 阶段一校验到阶段二落库之间存在并发窗口 | 参与聚合须带版本，`doUpdate` 校验 `affected rows`，为 0 抛乐观锁冲突回滚 |
| 执行器与工作单元语义混用 | 同服务两套一致性语义（`AbstractApplicationService.java:13`） | 执行器与工厂成对匹配，禁止跨语义组合 |

> 工作单元底层机制、Outbox 状态机与兜底轮询的完整说明见 [核心：应用服务](../core/application-service.md#_2-2-跨聚合根工作单元iunitofwork--unitofwork)。

## 5. 关键机制与避坑

- **业务方法内"先 `recordOperation` 后 `collectEvent`"**：事件 `operationCode` 自动取最近一次操作；顺序颠倒抛 `OperationException`。详见 [操作注册表设计](./operation-registry-design.md)。
- **延迟事件（ID 后生成必用）**：构造期 `entityId` 为 `null`，用 `() -> XxxEvent.buildEvent(this)`，发布时才读真实 ID。详见 [事件建模指南](./event-modeling.md)。
- **事务边界**：`@Transactional` 由调用方（WriteService 方法 / 执行器）负责，仓储不管理事务。
- **异常响应映射**：`BrokenRuleException`（单条）/ `BrokenRuleAggregateException`（全量）/ `PragmaticException`（兜底），用 `@RestControllerAdvice` 统一映射：

```java
@ExceptionHandler(BrokenRuleException.class)
public ResponseEntity<ErrorResponse> handleBrokenRule(BrokenRuleException e) {
    return ResponseEntity.badRequest().body(new ErrorResponse(e.getCode(), e.getMessage()));
}
```

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 应用服务里写业务规则 / 状态判断 | 业务逻辑泄漏到应用层、不可复用 | 业务逻辑内聚聚合根，校验走规则容器 |
| 绕过 `execute()` 手动编排放事件 / 清状态 | 模板顺序丢失、事件清理遗漏 | 统一走 `execute()` / `tryExecute()` |
| Updater / Factory 里做校验或持久化 | 职责混杂、模板被打断 | Updater 只转换 + 调充血方法；校验 / 持久化交模板 |
| 每个方法 new 一个执行器 / 事件管理器 | 资源浪费、语义漂移 | 构造器注入一次，复用 `AbstractApplicationService` |
| WriteService 直接操作仓储细节 / 批量 SQL | 仓储职责泄漏 | 复杂查询交查询侧，写仓储只收聚合根 |
| 多聚合根场景仍逐个 `execute()` 提交 | 多个聚合根不在同一事务，部分成功部分失败无法整体回滚 | 用 `beginUnitOfWork()` 注册多条目统一 `commit`（见 §4.9） |
| 把 `UnitOfWork` 当单例 Bean 直接注入 | 有状态、单次消费的工作单元跨请求复用，事件 / 状态串味 | 注入 `Supplier<IUnitOfWork>` 工厂，用 `beginUnitOfWork()` 取新实例 |
| 工作单元不用 try-with-resources | 未提交时暂存事件未清理，内存泄漏 / 跨请求误发 | 一律 `try (IUnitOfWork uow = beginUnitOfWork())` |
| 执行器与工作单元语义混用 | 同一服务内两套一致性语义，Outbox 行与事件不同步 | 成对匹配：`CommandExecutor`+`UnitOfWork`、`OutboxCommandExecutor`+`OutboxUnitOfWork` |

## 7. 下一步

- [聚合目录落地骨架](./aggregate-structure.md)：应用层目录的落位
- [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)：规则容器的构造与触发
- [领域服务落地模式](./domain-service.md)：应用层实现领域服务
- [Outbox 链路装配](./outbox-config.md)：`OutboxCommandExecutor` 装配
- [核心：应用服务](../core/application-service.md)：`CommandExecutor` / `UnitOfWork` / Outbox 详解
- [投影读模型代码落地指南](./projection-design.md)：`ReadService` 的完整落地（三跳取数 + Redis→ES 多源编排）
