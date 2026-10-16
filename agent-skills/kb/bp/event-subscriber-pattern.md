# BP 卡：事件订阅领域服务（event-subscriber-pattern）

> 源：`documentation/best-practices/event-subscriber-pattern.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 本质

四类领域服务中唯一由事件总线驱动的一类。三层：订阅契约（领域层接口）、订阅者实现（应用层）、订阅注册表（应用层 `@Configuration` 显式绑定到 `IEventRegistry`）。

## 2. 命名

- 事件类 `{聚合}{动作}Event`（过去式）。
- 契约 `I{事件}{业务意图}Handle extends IDomainService, IHandle<T>`。
- 实现 = 接口去 I；注册表 `{聚合}EventSubscriberRegistry`；别名 kebab-case `{动作}-on-{触发事件}`。
- 接口名带业务意图（发短信/积分/投影），禁用 `Handler`/`Processor`/`Listener` 占位。

## 3. 落地四步

1. 事件类：只带聚合标识 + `buildEvent` 工厂（见 `event-modeling.md`）。
2. 领域契约：一行接口 + `@DomainService(category=EVENT_SUBSCRIBER, targetName="事件类名")`。
3. 应用实现：两类形态——外部联动（副作用型，经 `IDependency`）/ 读模型物化（形态 B，`findById`→`源.sync`）。
4. 注册表：`evtManager.registerSubscriber(别名, 事件.class, 实例)`，一行一条。

## 4. 关键约束

- **框架不扫描** `@Component` 的 `IHandle`，必须显式 `registerSubscriber`，否则静默不触发。
- 事件只带标识，处理时 `findById` 回源聚合（快照会过期）。
- 类名即路由 key：不同包同名事件会串事件；注册 `Class<T>` 须与 `IHandle<T>` 一致。
- 别名按事件唯一，建议全局唯一。
- 条件抛异常一律当 SKIP（静默不执行），优先查条件实现。
- 顺序用 `dependSubscriber` 显式声明，不依赖注册顺序。
- 异常不吞：基础设施异常上抛（RocketMQ `RECONSUME_LATER`）；仅业务"无需处理"用 `if` 短路 return。
- 物化版本取 `event.getVersion()`（事件路径），非 `getOldVersion()`（对账路径）。
- 外部副作用带幂等键（聚合标识作 `bizId`）；一个副本一个平级订阅者。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 以为 `@Component` 自动注册 | 显式 `registerSubscriber` |
| 事件带整份快照 | 只带标识 + 处理时回源 |
| 一个契约处理多事件 | 一契约一事件 |
| 订阅者注入第三方 SDK | 抽 `IDependency` 端口 |
| `handleEvent` try-catch 静默 | 异常上抛，仅空值短路 |
| 物化取 `getOldVersion()` | 事件路径取 `event.getVersion()` |

## 6. 被读于

S4 `ddd-aggregate-create`（事件订阅）、S5 `ddd-aggregate-extend`、S6 `ddd-projection-create`、S7 `ddd-code-review`。
