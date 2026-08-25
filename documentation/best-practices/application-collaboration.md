# 应用层落地模式

> 本文档介绍应用服务层的落地方式：WriteService 怎么搭、`execute()` 模板顺序、创建 / 修改 / 预校验三场景、规则装配与执行器选择。前置阅读：[聚合目录落地骨架](./aggregate-structure.md) · [聚合设计原则](./aggregate-design.md)。

## 1. 本质与定位

应用服务层（Application）是**编排领域逻辑**的层：接收 Input → 建聚合（`EntityFactory`）/ 改聚合（`EntityUpdater`）→ 规则校验 → 仓储持久化 → 领域事件发布，全部由 `execute()` 模板统一编排，置于同一事务边界。

- 职责：**编排**（把 Factory / Updater / Rule / Repository / EventManager 串起来）。
- 不做：不写业务逻辑（业务逻辑在聚合根）、不操作持久化细节（仓储承担）、不做协议转换（UI 层承担）。
- 核心形态：命令应用服务（`{Agg}WriteService`）继承 `AbstractApplicationService`，每个用例一个公开方法，内部走 `execute()` / `tryExecute()`。

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
                             IOutboxStore outboxStore,
                             IEventSerializer eventSerializer,
                             EagerOutboxPublisher eagerOutboxPublisher,
                             TransactionOperations txOps,
                             OrderFactory orderFactory,
                             OrderRule orderRule,
                             OrderRepository orderRepository,
                             OrderPayUpdater orderPayUpdater) {
        // 执行器选择：OutboxCommandExecutor（或默认 CommandExecutor，见 §4.7）
        super(eventManager,
                new OutboxCommandExecutor(outboxStore, txOps, eventSerializer, eagerOutboxPublisher));
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

- 继承 `AbstractApplicationService`，构造器选择执行器（默认 `CommandExecutor`，需要 Outbox 用 `OutboxCommandExecutor`）。
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

## 7. 下一步

- [聚合目录落地骨架](./aggregate-structure.md)：应用层目录的落位
- [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md)：规则容器的构造与触发
- [领域服务落地模式](./domain-service.md)：应用层实现领域服务
- [Outbox 链路装配](./outbox-config.md)：`OutboxCommandExecutor` 装配
- [核心：应用服务](../core/application-service.md)：`CommandExecutor` / `UnitOfWork` / Outbox 详解
