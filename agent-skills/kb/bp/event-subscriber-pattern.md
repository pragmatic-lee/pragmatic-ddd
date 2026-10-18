# BP 卡：事件订阅领域服务落地模式（event-subscriber-pattern）

> 源：`documentation/best-practices/event-subscriber-pattern.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

`EVENT_SUBSCRIBER` 是四类领域服务中唯一**由事件总线驱动**的一类：领域层声明"某事件后做什么"，应用层实现，装配期显式注册到 `IEventRegistry`。

- 三层角色：①订阅契约（领域层 `service/`，仅接口）②订阅者实现（应用层 `service/`，`handleEvent` 编排）③订阅注册表（应用层 `subscriber/`，绑定）④基础设施（防腐适配器、投影构件）。
- 端口关系：`IEventSubscriberService<T> extends IDomainService, IHandle<T>`；`IHandle<T>.handleEvent(T)`；`IEventRegistry.registerSubscriber(...)` 6 重载；`IEventManager extends IEventPublisher, IEventRegistry, IEventLifecycle`。
- 边界：订阅者只依赖 `IHandle<T>` + 领域依赖端口，不出现 MQ / ES / Redis 客户端；不在 `handleEvent` 内同步编排多个聚合写。

## 2. 命名与包结构

```text
domain/{agg}/
├── event/      OrderPaidEvent / OrderDataSyncEvent + buildEvent 工厂
├── dependency/ 外部依赖端口（ISmsDependency / IUserPointsDependency）
├── projection/ 投影视图 + 目标常量（读模型型订阅者用）
└── service/    订阅契约接口（仅接口，带 @DomainService）
application/{agg}/
├── service/    OrderPaidSmsNotifyHandle 等（@Component，接口名去 I）
└── subscriber/ OrderEventSubscriberRegistry（事件 → 订阅者绑定）
infrastructure/dependency/{agg}/   SmsDependencyAdapter 等防腐适配器
infrastructure/persistent/{agg}/projection/  Projector / Source
```

| 元素 | 格式 | 示例 |
| --- | --- | --- |
| 事件类 | `{聚合}{动作}Event` | `OrderPaidEvent` |
| 契约接口 | `I{事件}{业务意图}Handle` | `IOrderPaidSmsNotifyHandle` |
| 实现 | 接口名去 `I` | `OrderPaidSmsNotifyHandle` |
| 注册表 | `{聚合}EventSubscriberRegistry` | `OrderEventSubscriberRegistry` |
| 别名 | kebab-case `{动作}-on-{事件}` | `sms-notify-on-order-paid` |

> 接口名必须体现业务意图（发短信/发积分/投影到 ES），不得用 `Handler`/`Processor`/`Listener` 占位。

## 3. 落地方式（核心）

1. **事件**：只带聚合标识（可带少量路由 ID），`buildEvent(聚合)` 工厂，`operationCode`/`version` 由 `collectEvent` 回填。
2. **契约**：`@DomainService(category = EVENT_SUBSCRIBER, targetName = "<事件类名>", description = "...")`，`extends IDomainService, IHandle<XxxEvent>`，接口内不写实现。
3. **实现**：两类形态，结构一致「取标识 → 回源聚合 → 空值短路 → 执行动作」：
   - 形态 A 外部联动：`findById` 回源、外部调用经 `IDependency` 端口、空值/非法 `return` 短路、外部副作用带幂等键（`bizId` = 聚合标识）。
   - 形态 B 读模型物化：一个副本一个订阅者（ES/Redis 平级）、物化版本取 `event.getVersion()`、`源.sync(aggregate)` 收敛编排。
4. **注册**：`{聚合}EventSubscriberRegistry` 在 `@Configuration` 构造期 `registerSubscriber(别名, 事件.class, 实现)`，注入 `IEventRegistry`（即 `eventManager` Bean）。

## 4. 关键机制与避坑

| 约束 | 正确做法 |
| --- | --- |
| 框架**不扫描** `@Component` 的 `IHandle` | 必须经 `registerSubscriber` 显式注册，否则事件无人消费且无提示 |
| 事件只带标识 | `handleEvent` 内必须 `findById` 回源聚合，不靠载荷做业务判断 |
| 事件类名即路由 key（简单类名） | 不同包下不得定义同名事件类；`registerSubscriber` 的 `Class<T>` 须与 `IHandle<T>` 的 `T` 一致 |
| 别名按事件唯一 | 同一事件下重复别名抛 `IllegalArgumentException`；建议全局唯一 |
| 两重条件异常即 SKIP | `IExecuteCondition` 抛异常被 `AbstractEventManager` 捕获当 SKIP，不中断分发 |
| 严格顺序用 `dependSubscriber` | 不要依赖注册顺序（不构成顺序保证）；成环注册抛 `IllegalStateException` |
| 无源登记表 | 不存在「源 id → 实例」反查；订阅者直接持有目标源调 `源.sync`，`Missing Bean` 启动期失败 |
| `handleEvent` 不吞异常 | 基础设施异常上抛交重试/死信；仅业务「无需处理」用 `if` 短路 |
| 物化版本口径 | 事件路径取 `event.getVersion()`；对账路径才用 `getOldVersion()` |
| 幂等键 | MQ 重投会重复触发，外部副作用用聚合标识作 `bizId` |
| `start()` 必须调 | RocketMQ 场景漏 `eventManager.start()` 则 Consumer 不订阅 |

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 以为 `@Component` 会被自动扫描注册 | 在注册表显式 `registerSubscriber` |
| 事件携带整份业务快照 | 只带聚合标识，`handleEvent` 里 `findById` 回源 |
| 一个契约 `instanceof` 分支处理多事件 | 一个契约一个事件类型 |
| 一个订阅者塞多个不相关动作 | 按业务意图拆分、别名独立注册 |
| 订阅者直接注入第三方 SDK | 抽 `IDependency` 端口，适配器放基础设施层 |
| 依赖注册顺序保证执行顺序 | 用 `dependSubscriber` 显式声明 |
| `handleEvent` try-catch 静默返回 | 基础设施异常上抛；仅业务短路用 `if` |
| 不标 `@DomainService`/`category` 错 | 标 `EVENT_SUBSCRIBER`，`targetName` 填事件类名 |
| 不同包同名事件类 | 事件类名全局唯一 |
| 物化版本取 `order.getOldVersion()` | 事件路径取 `event.getVersion()` |
| 外部副作用不带幂等键 | 用聚合标识作 `bizId` |
| 订阅者内同步编排多聚合写 | 写编排放应用服务 |

## 6. 被读于

S5 `ddd-aggregate-extend`、S7 `ddd-code-review`、咨询（订阅者/事件分发）。
