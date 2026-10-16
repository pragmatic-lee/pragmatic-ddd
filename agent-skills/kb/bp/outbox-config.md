# BP 卡：Outbox 链路装配（outbox-config）

> 源：`documentation/best-practices/outbox-config.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 关键约定

`OutboxConfig` 是**纯装配类**，把框架协作者组装成 Spring Bean（不写 SQL、不写事务注解、不持 Mapper 接口）。`infrastructure/config/`，不绑定聚合类型。

⚠️ **框架类 vs 用户自备 Spring 适配（A7 已验证）**：
- **框架提供**（直接注入）：`TransactionOperations`(`io.pragmatic.ddd.application.spi`)、`IEventSerializer`(`io.pragmatic.ddd.event.spi`)、`IOutboxStore`(`io.pragmatic.ddd.application.outbox.spi`)、`EagerOutboxPublisher`/`OutboxRelay`(`io.pragmatic.ddd.application.outbox`)、`MybatisOutboxStore`/`IOutboxStatementExecutor`(`io.pragmatic.ddd.mybatis.outbox`)、`Fastjson2EventSerializer`(`io.pragmatic.ddd.rocketmq`)。
- **用户从示例复制的参考实现（非框架类，core 不含 Spring 绑定）**：`SpringTransactionOperations`（实现 `TransactionOperations`）、`SpringOutboxStatementExecutor`（实现 `IOutboxStatementExecutor`），二者放进自己的 `infrastructure/config/`，不要当框架类去 import。

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `TransactionOperations` | `io.pragmatic.ddd.application.spi.TransactionOperations` | 聚合写+outbox 写绑同一 DB 事务（🔧 自身需用户自备 `SpringTransactionOperations`） |
| `IEventSerializer` | `io.pragmatic.ddd.event.spi.IEventSerializer` | 事件序列化（Relay 重发需反序列化） |
| `IOutboxStore` | `io.pragmatic.ddd.application.outbox.spi.IOutboxStore` | outbox 行落库与认领 |
| `EagerOutboxPublisher` | `io.pragmatic.ddd.application.outbox.EagerOutboxPublisher` | 提交后主动推送（快路径） |
| `OutboxRelay` | `io.pragmatic.ddd.application.outbox.OutboxRelay` | 周期兜底轮询（慢路径/崩溃补偿） |
| `IOutboxStatementExecutor`（用户实现） | `io.pragmatic.ddd.mybatis.outbox.IOutboxStatementExecutor` | `MybatisOutboxStore` 的语句执行端口 🔧 用户自备 `SpringOutboxStatementExecutor` |

## 2. 落地顺序（依赖箭头）

```text
PlatformTransactionManager ─┐
                            ├─► TransactionOperations
SqlSessionTemplate ─────────┘
        │ ├─► SpringOutboxStatementExecutor ─► IOutboxStore
IEventSerializer(rocketmq) ─┐
                            ├─► EagerOutboxPublisher ─┐
IOutboxStore ───────────────┘                        ├─► OutboxRelay
IEventManager(RocketMQ) ─────────────────────────────┘
```

```java
// framework: io.pragmatic.ddd.application.spi / .outbox / .mybatis.outbox ; rocketmq: io.pragmatic.ddd.rocketmq
// 🔧 SpringTransactionOperations / SpringOutboxStatementExecutor 为用户自备（实现 TransactionOperations / IOutboxStatementExecutor），非框架类
@Bean public TransactionOperations transactionOperations(PlatformTransactionManager tm)
    { return new SpringTransactionOperations(tm); }
@Bean public IEventSerializer eventSerializer() { return new Fastjson2EventSerializer(); }  // rocketmq 自带
@Bean public IOutboxStore outboxStore(SqlSessionTemplate tpl, TransactionOperations tx) {
    // MybatisOutboxStore 在 io.pragmatic.ddd.mybatis.outbox；入参是实现 IOutboxStatementExecutor 的用户自备类
    IOutboxStatementExecutor executor = new SpringOutboxStatementExecutor(tpl);
    return new MybatisOutboxStore(executor, tx);
}
@Bean public EagerOutboxPublisher eagerOutboxPublisher(IOutboxStore s, IEventManager m) {
    return new EagerOutboxPublisher(s, m, Executors.newFixedThreadPool(4));   // 有界池
}
@Bean public OutboxRelay outboxRelay(IOutboxStore s, IEventManager m, IEventSerializer ser) {
    return new OutboxRelay(s, m, ser, Executors.newSingleThreadScheduledExecutor(),
                           OutboxRelayConfig.defaultConfig());   // poll=5min,grace=30s,batch=200,maxAttempts=10
}
@Bean public ApplicationRunner startOutboxRelayOnReady(OutboxRelay r)
    { return args -> r.start(); }   // 应用就绪后再 start
```

## 3. 硬约束

- **序列化器必须复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer`**：Outbox Relay 与 `RocketMqEventManager` 共用同一 `IEventSerializer` 端口；各用一套会导致 Relay 重发 `deserialize` 失败。
- **`EagerOutboxPublisher` 线程池必须有界**（`newFixedThreadPool`）：无界队列+洪峰会线程爆炸；溢出任务保持 PENDING 转 Relay 兜底。
- **`OutboxRelay` 必须单线程调度**（`newSingleThreadScheduledExecutor`）：`claimPending` 靠 `claim_token` 原子认领，多实例各持唯一 token，单实例内单线程避免并发重复认领。
- **`grace` 必须 > eager 推送典型耗时**：Relay 只认领 `age > grace` 的 PENDING；grace 太短会与 eager 抢同批重复发送。
- `OutboxRelay` 不在 `initMethod` 启动，延后到 `ApplicationRunner`（所有 Bean 就绪、Web 已监听）再 `start()`。

## 4. 关键机制

- 快/慢路径互补：均调同一 `IEventManager.publish`；Relay 靠 `serializer.deserialize` 还原事件。
- `IOutboxStore.markSent` 带 status 守卫（PENDING/PROCESSING→SENT）幂等；下游仍需自行幂等（消息可能重复投递）。
- SQL 与执行器解耦：`SpringOutboxStatementExecutor` 只转发 `statementKey`，SQL 在 MyBatis XML 按 `OutboxStatements` 的 key 实现。

## 5. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| Outbox 自己 new `Fastjson2EventSerializer` | 复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer` |
| eager 用无界线程池 | `newFixedThreadPool(n)` 有界 |
| Relay 用多线程调度器 | `newSingleThreadScheduledExecutor` |
| grace 短于 eager 推送耗时 | grace > eager P95 推送耗时 |
| SQL/`@Transactional` 写进 `OutboxConfig` | SQL 放 XML，事务走 `TransactionOperations` |

## 6. 被读于

S3 `ddd-infra-config`、S7 `ddd-code-review`。
