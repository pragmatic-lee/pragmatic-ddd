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
├── action/                     # 可补偿动作（ICompensableAction：正向 / 回写 / 补偿三元组）
├── compensation/               # 崩溃兜底执行器（ICompensationHandler，durable 模式用）
└── subscriber/                 # 事件订阅登记
```

> 框架侧的可补偿命令执行器、注册表、补偿状态机与兜底中继统一放在 `io.pragmatic.ddd.application.compensation`（含 `relay/` 与 `spi/`），应用层只消费成品 Bean，不感知其内部状态机。

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
3. persistAndDispatch                  事务内 repository.save（覆盖聚合全部表）
4. 事务提交后 publish                   事件发布严格晚于提交
5. clearWorkUnitState()                事件 / 操作清空（防止跨请求串味），置于 finally
```

> 事件发布后的清理由模板内置，继承 `AbstractApplicationService` 无需手动调用；仅在自研编排时需要。
>
> 第 3、4 步的**事务边界完全由子类的 `persistAndDispatch` 钩子决定**，模板不感知事务。`CommandExecutor` 与 `OutboxCommandExecutor` 都遵循"事务内只落库、提交后再发事件"：前者提交后直发 MQ（不保证事件不丢），后者提交后走 eager 推送 + Relay 兜底（保证事件不丢）。
>
> 仓储不管理事务（见 [仓储设计原则 §1.5](./repository-design.md)）：事务由执行器经 `TransactionOperations` 开启，仓储需用 Spring 托管的 `SqlSessionTemplate` 自动加入当前事务。

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
| `CommandExecutor` | 同事务落库，提交后直发事件 | 事件丢失可接受、不需要 Outbox 兜底 |
| `OutboxCommandExecutor` | 聚合写 + outbox 行同事务，异步投递 | 跨模块可靠投递 / 崩溃兜底（见 [Outbox 链路装配](./outbox-config.md)） |
| `CompensableCommandExecutor`（装饰器） | 装饰上述任一执行器，为声明外部需求的命令叠加逆序补偿 | 用例涉及跨本地事务的外部副作用（预占 / 扣减 / 发券）且失败需回滚（见 [§4.10](#410-可补偿命令分布式事务补偿落地)） |

> `AbstractApplicationService` 不再提供默认构造器，执行器与工作单元工厂必须由继承者**显式注入**（通常以组合根 `@Bean` 提供成品），且二者语义须一致：默认场景用 `CommandExecutor` + `UnitOfWork`，outbox 场景用 `OutboxCommandExecutor` + `OutboxUnitOfWork`，禁止混用。工作单元的装配（原型工厂）与多聚合根直接编排写法见 [§4.9](#49-工作单元unitofwork--outboxunitofwork落地写法)。
>
> `CompensableCommandExecutor` 是**装饰器而非第二套语义**：它包裹既有的 `CommandExecutor` / `OutboxCommandExecutor`，共享被装饰者的同一事务边界，仅对该用例里聚合通过 `requireExternal` 声明的外部需求做正向执行 + 逆向补偿。需要补偿的用例单独注入它，其余用例继续走基类 `execute`。

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

### 4.10 可补偿命令（分布式事务补偿）落地

当一次用例除了「落库 + 发事件」之外，还要调用**本地事务之外的外部系统**（库存预占、积分发放、第三方扣款），且外部动作失败时要把已做的外部副作用**回滚**时，用 `CompensableCommandExecutor` 替代基类 `execute`。它是 `ICommandExecutor` 的装饰器，把「正向执行外部动作 → 失败逆序补偿」固化到应用层编排里，复用既有事务语义（推荐装饰 `OutboxCommandExecutor`）。

落地三要素：**领域声明需求 → 应用实现动作 → 用例注入补偿执行器**。

#### 4.10.1 领域层：聚合声明外部需求

聚合在领域方法内通过 `requireExternal(requirement)` 声明「需要外部世界配合完成的一件事」（`IExternalRequirement` 实现为不可变 record，`code()` 是跨进程稳定的路由键，不得由类名推导）：

```java
package io.pragmatic.ddd.example.order.domain.order.requirement;

public record ReserveInventoryRequirement() implements IExternalRequirement {
    public static final ReserveInventoryRequirement INSTANCE = new ReserveInventoryRequirement();

    @Override
    public String code() {
        return "reserve-inventory";   // = compensation_log.handler 列，全局唯一稳定
    }
}
```

```java
// 聚合领域方法内声明需求（与 collectEvent 同构，只描述要什么）
public void place() {
    // ... 领域逻辑 ...
    requireExternal(ReserveInventoryRequirement.INSTANCE);
}
```

#### 4.10.2 应用层：实现可补偿动作

`ICompensableAction<A, REQ, T>` 是「正向调用外部 + 逆向回滚外部」的配对单元，绑定聚合类型与需求类型；必须无状态（作单例由容器管理）。正向 / 回写 / 补偿一律从聚合读取业务事实，不得从入参读取；`apply` 只调聚合充血方法、禁 setter 直改；`compensate` 必须幂等；`payload` 返回供 durable 崩溃兜底还原的依据（非空才能开 durable）：

```java
@Component
public class ReserveInventoryAction implements ICompensableAction<Order, ReserveInventoryRequirement, String> {

    private final IInventoryDependency inventoryDependency;   // 防腐适配器端口（@ExternalDependency）
    private final IEventSerializer serializer;

    @Override
    public Class<ReserveInventoryRequirement> requirementType() {
        return ReserveInventoryRequirement.class;
    }

    @Override
    public Class<Order> aggregateType() {
        return Order.class;
    }

    @Override
    public String execute(Order order, ReserveInventoryRequirement requirement) {
        return inventoryDependency.reserve(order.getEntityId(), order.getOrderItems().getAllItems());
    }

    @Override
    public void apply(Order order, String reserveNo) {
        order.bindReserveNo(reserveNo);   // 只调充血方法
    }

    @Override
    public void compensate(Order order, String reserveNo) {
        inventoryDependency.release(reserveNo);   // 幂等释放
    }

    @Override
    public String payload(Order order, ReserveInventoryRequirement requirement) {
        return serializer.serialize(new ReservePayload(order.getEntityId(), /* 明细 */));
    }
}
```

> ⚠️ **动作依赖外部调用请走防腐适配器端口**：`ReserveInventoryAction` 注入的是领域层 `IInventoryDependency` 端口（见 [外部依赖声明落地模式](./dependency-declaration.md)），不直接写 HTTP / RPC——保持领域 / 应用层不耦合远程细节。

#### 4.10.3 应用服务：仅补偿用例注入补偿执行器

补偿执行器由 `pragmatic-ddd-spring-boot` 的补偿自动装配以成品 Bean 注入（装饰 Outbox 执行器），服务内不再手动 new；**只在需要补偿的用例使用它**，其余用例继续走基类 `execute`：

```java
@Service
public class OrderWriteService extends AbstractApplicationService implements ICommandApplicationService {

    private final CompensableCommandExecutor compensableExecutor;
    // ... 其余依赖

    public OrderWriteService(IEventManager eventManager,
                             OutboxCommandExecutor commandExecutor,
                             Supplier<IUnitOfWork> unitOfWorkFactory,
                             CompensableCommandExecutor compensableExecutor,
                             /* ... */) {
        super(eventManager, commandExecutor, unitOfWorkFactory);
        this.compensableExecutor = compensableExecutor;
    }

    /** 下单：建聚合（聚合声明库存预占需求）→ 框架执行外部动作并兜底补偿 → 校验落库发事件。 */
    public Order placeOrder(CreateOrderInput input) {
        Order order = orderFactory.create(input);
        return compensableExecutor.execute(order, orderRule, orderRepository, t -> {});
    }
}
```

#### 4.10.4 durable 模式：补崩溃兜底执行器

内存模式（默认）进程崩溃即中断补偿；需崩溃后仍可回抢时，把 `pragmatic-ddd.compensation.mode` 设为 `durable`，并**为每个需兜底的动作各提供一个 `ICompensationHandler`**（按 `actionName()` 路由，必须等于需求 `code`）。中继 `CompensationRelay` 由 starter 自动挂载轮询：

```java
@Component
public class ReserveInventoryCompensationHandler implements ICompensationHandler {
    @Override
    public String actionName() {
        return "reserve-inventory";   // 必须等于 ReserveInventoryRequirement.code()
    }

    @Override
    public void compensate(String payload) {
        ReservePayload p = serializer.deserialize(payload, ReservePayload.class);
        inventoryDependency.releaseByOrder(p.orderId());   // 按业务标识幂等释放
    }
}
```

#### 4.10.5 关键约束与选型

- **段序固定**：段一领域逻辑（纯内存收集需求）→ 段二逐项正向 + 回写 → 段三校验落库发事件；段三失败触发已执行动作**逆序、不中断**补偿（单个失败不影响其余），整体由 `CompensationTemplate` 固化。
- **事务边界复用被装饰者**：装饰 `OutboxCommandExecutor` 即同时获得「事件同事务 + 外部副作用可补偿」；装饰 `CommandExecutor` 则无 Outbox 兜底。不得错配两套语义。
- **补偿不替代 Outbox**：补偿管「外部副作用回滚」，Outbox 管「事件可靠投递」，二者叠加使用。
- **幂等是硬约束**：`compensate` 在回滚路径与崩溃恢复路径都会触发，外部系统须支持按业务单号幂等撤销。
- **内存 / durable 二选一**：内存零 DB 依赖；durable 需 `pragmatic-ddd-mybatis` + `SqlSessionTemplate` + 建 `compensation_log` 表 + 业务提供 `ICompensationHandler`，底座缺失时 starter 告警回落内存（详见 [Spring Boot Starter 集成 §9.4](../integration/spring-boot.md) 与 [配置项参考](../reference/configuration.md)）。

## 5. 关键机制与避坑

- **业务方法内"先 `recordOperation` 后 `collectEvent`"**：事件 `operationCode` 自动取本次记录的因果操作（单值）；顺序颠倒抛 `OperationException`。详见 [操作注册表设计](./operation-registry-design.md)。
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
| 跨本地事务的外部动作不设补偿 | 外部预占 / 扣减成功后本地校验失败，外部副作用残留 | 声明 `IExternalRequirement` + 实现 `ICompensableAction`，用 `CompensableCommandExecutor`（见 §4.10） |
| 可补偿动作 `compensate` 不幂等 | 回滚路径与崩溃恢复路径重复触发，外部被重复撤销 | 外部系统须支持按业务单号幂等撤销（如按预占单号释放） |
| 可补偿动作里直改聚合字段 / 从入参读事实 | 绕过充血方法、补偿与正向读到不一致的业务事实 | `apply` 只调聚合充血方法；正向 / 回写 / 补偿一律从聚合读取 |
| 补偿与 Outbox 二选一 | 既要可靠事件又要回滚外部副作用时只做其一，另一风险裸露 | `CompensableCommandExecutor` 装饰 `OutboxCommandExecutor`，二者叠加 |
| durable 模式漏配 `ICompensationHandler` | WAL 照写但无中继回抢，崩溃后补偿记录堆积 | 为每个需兜底的动作提供 `ICompensationHandler`，`actionName()` 等于需求 `code` |
| 把补偿执行器当通用执行器注入基类 | 无需补偿的用例也被套上补偿范围，徒增开销 | 仅在声明外部需求的用例注入 `CompensableCommandExecutor`，其余走基类 `execute` |

## 7. 下一步

- [聚合目录落地骨架](./aggregate-structure.md)：应用层目录的落位
- [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)：规则容器的构造与触发
- [领域服务落地模式](./domain-service.md)：应用层实现领域服务
- [外部依赖声明落地模式](./dependency-declaration.md)：`ICompensableAction` 调用的防腐适配器端口从哪来
- [Outbox 链路装配](./outbox-config.md)：`OutboxCommandExecutor` 装配
- [核心：应用服务](../core/application-service.md)：`CommandExecutor` / `UnitOfWork` / Outbox / 可补偿执行器详解
- [投影读模型代码落地指南](./projection-design.md)：`ReadService` 的完整落地（三跳取数 + Redis→ES 多源编排）
