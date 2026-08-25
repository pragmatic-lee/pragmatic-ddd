# Outbox 链路装配

> 把"事务性发件箱"接到你的技术栈时，核心不是写业务逻辑，而是把五个协作 Bean 正确装配成一个 `@Configuration`。
> 本文以订单示例（`Order`）为承载，给出可直接复用的落地骨架，其他聚合把 `Order` 换成目标聚合即可（举一反三）。
> 模式原理见 [事务性发件箱](./../integration/outbox.md)，本文只讲"怎么把代码装配对"。

## 1. 本质与边界

`OutboxConfig` 是一个**纯装配类**，职责是把框架提供的五个协作者组装成 Spring Bean：

| Bean | 类型 | 解决什么 |
| --- | --- | --- |
| `TransactionOperations` | `io.pragmatic.ddd.application.outbox.spi.TransactionOperations` | 把"聚合写 + outbox 写"绑到同一 DB 事务 |
| `IEventSerializer` | `io.pragmatic.ddd.event.spi.IEventSerializer` | 事件体序列化 / 反序列化（Relay 反序列化重发需要） |
| `IOutboxStore` | `io.pragmatic.ddd.application.outbox.spi.IOutboxStore` | outbox 行的落库与认领 |
| `EagerOutboxPublisher` | `io.pragmatic.ddd.application.outbox.EagerOutboxPublisher` | 事务提交后主动推送（快路径） |
| `OutboxRelay` | `io.pragmatic.ddd.application.outbox.OutboxRelay` | 周期兜底轮询（慢路径 / 崩溃补偿） |

边界：装配类**只声明 Bean 与依赖注入**，不写 SQL、不写事务注解、不持有 Mapper 接口。SQL 在 XML，事务由 `TransactionOperations` 抽象，推送由 `CommandExecutor` 触发。

## 2. 命名与包结构

```text
infrastructure/<聚合>/config/
├── OutboxConfig.java              ✅ 集中装配五个 Bean
├── SpringTransactionOperations.java   ✅ TransactionOperations 的 Spring 实现
├── SpringOutboxStatementExecutor.java ✅ IOutboxStatementExecutor 的 Spring 实现
└── MySqlConfig.java               ✅（或等价）暴露 PlatformTransactionManager / SqlSessionTemplate
```

✅ 推荐：`OutboxConfig` 与 `Spring*` 适配类放在同一 `config` 包，贴近基础设施。
❌ 反模式：把 `outboxStore` / `eagerPublisher` 散落到应用服务或 CommandExecutor 里 new。

## 3. 落地顺序（五个 Bean 逐个装配）

落地顺序即依赖顺序，遵守下面的箭头可一次成型：

```text
PlatformTransactionManager ─┐
                            ├─► TransactionOperations
SqlSessionTemplate ─────────┘
        │
        ├─► SpringOutboxStatementExecutor ─► IOutboxStore
        │
IEventSerializer (rocketmq 自带) ─┐
                                ├─► EagerOutboxPublisher ─┐
IOutboxStore ────────────────────┘                        ├─► OutboxRelay
IEventManager (RocketMQ) ─────────────────────────────────┘
```

### 3.1 TransactionOperations

Spring 环境下用 `PlatformTransactionManager` 包一层即可：

```java
@Bean
public TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
    return new SpringTransactionOperations(transactionManager);
}
```

`SpringTransactionOperations` 是你自己实现的一个薄适配，把 `TransactionTemplate` 包成框架的 `TransactionOperations` 接口。框架 core 不含 Spring 绑定，这类适配放在示例 / 基础设施层。

### 3.2 IEventSerializer

> ⚠️ **重要约束**：序列化器必须使用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer`，不要自行 new 一个 `Fastjson2EventSerializer`。
> 技术原因：`OutboxRelay` 的反序列化重发路径与 `IEventManager`（即 `RocketMqEventManager`）共用同一个 `IEventSerializer` 端口；若 Outbox 与 RocketMQ 各用一套实现，事件格式不一致会导致 Relay 重发时 `deserialize` 失败或字段错位。pragmatic-ddd-rocketmq 已提供该实现，直接复用。

> import 来源见 `OutboxConfig.java` 第 11 行：`import io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer;`

```java
@Bean
public IEventSerializer eventSerializer() {
    return new Fastjson2EventSerializer();
}
```

### 3.3 IOutboxStore

`MybatisOutboxStore` 需要 `IOutboxStatementExecutor`（参与 Spring 事务）+ `TransactionOperations`：

```java
@Bean
public IOutboxStore outboxStore(SqlSessionTemplate sqlSessionTemplate, TransactionOperations txOps) {
    IOutboxStatementExecutor executor = new SpringOutboxStatementExecutor(sqlSessionTemplate);
    return new MybatisOutboxStore(executor, txOps);
}
```

`SpringOutboxStatementExecutor` 只把 `statementKey + 参数` 转发给 `SqlSessionTemplate`，不感知 key 的具体取值；key 与 SQL 的绑定集中在 MyBatis XML（namespace.statementId），框架按此直调 SQL，无需 Mapper 接口。

### 3.4 EagerOutboxPublisher

```java
@Bean
public EagerOutboxPublisher eagerOutboxPublisher(IOutboxStore outboxStore, IEventManager eventManager) {
    ExecutorService pool = Executors.newFixedThreadPool(4);
    return new EagerOutboxPublisher(outboxStore, eventManager, pool);
}
```

> ⚠️ **重要约束**：`pool` 必须是有界线程池（`newFixedThreadPool` / `newCachedThreadPool` 也可，但需设置上限）。
> 技术原因：eager 路径在事务提交后异步发送，若用无界队列且事件洪峰，线程无限增长会拖垮 JVM；有界池让溢出任务保持 PENDING，转由 `OutboxRelay` 兜底。

### 3.5 OutboxRelay（应用就绪后再 start）

`OutboxRelay` 本身只作为普通 Bean 构造，**不在 `initMethod` 里启动**；启动延后到 `ApplicationRunner`，确保「所有 Bean 初始化完成、Web 端口已监听」的应用就绪点之后再开始轮询。

```java
@Bean
public OutboxRelay outboxRelay(IOutboxStore outboxStore,
                               IEventManager eventManager,
                               IEventSerializer serializer) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    return new OutboxRelay(
        outboxStore, eventManager, serializer, scheduler,
        OutboxRelayConfig.defaultConfig());
}

@Bean
public ApplicationRunner startOutboxRelayOnReady(OutboxRelay outboxRelay) {
    return (ApplicationArguments args) -> outboxRelay.start();
}
```

`OutboxRelayConfig.defaultConfig()` 取 `pollInterval=5min、grace=30s、batchSize=200、maxAttempts=10`。也可从配置源绑定：

```java
OutboxRelayConfig.bind(configurationSource);  // 键：outbox.poll-interval / outbox.grace / outbox.batch-size / outbox.max-attempts
```

> ⚠️ **重要约束**：`OutboxRelay` 必须由单线程调度（`newSingleThreadScheduledExecutor`）。
> 技术原因：`claimPending` 靠 `claim_token` 做原子认领，多实例各持唯一 token 互不抢；单实例内用单线程避免同一批 PENDING 被并发认领两次。多实例部署时每个实例各起一个单线程 Relay 即可，认领 SQL 的行锁保证安全。

> ⚠️ **重要约束**：`grace` 必须 > eager 推送的典型耗时。
> 技术原因：Relay 只认领 `age > grace` 的 PENDING；若 grace 太短，eager 还在发，Relay 就来抢同一批，造成重复发送。`defaultConfig` 的 30s 给 eager 留出余量；事件量大时按实际 P99 推送耗时上调。

## 4. 关键机制与避坑

- **两条路径互补**：`EagerOutboxPublisher` 是快路径（提交后即推，低延迟）；`OutboxRelay` 是慢路径（eager 崩溃 / MQ 抖动时补偿）。两者都调用同一个 `IEventManager.publish`，Relay 靠 `IEventSerializer.deserialize` 还原事件。
- **`markSent` 幂等**：`IOutboxStore.markSent` 带 status 守卫（PENDING/PROCESSING → SENT），重复调用安全。因此 eager 与 Relay 即便重复推送同一条，outbox 状态也不会错乱——但下游仍需自行幂等（消息可能重复投递）。
- **SQL 与执行器解耦**：`SpringOutboxStatementExecutor` 不写 SQL，只转发 `statementKey`；SQL 在 MyBatis XML 中按 `OutboxStatements` 提供的 key 实现。改存储方言只动 XML，不动 Java。

## 5. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| Outbox 自己 new 一个 `Fastjson2EventSerializer` | 与 RocketMQ 序列化器不一致，Relay 反序列化失败 | 复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer` |
| `EagerOutboxPublisher` 用无界线程池 | 事件洪峰线程爆炸 | 用 `newFixedThreadPool(n)` 有界池 |
| `OutboxRelay` 用多线程调度器 | 单实例内重复认领同一批 PENDING | `newSingleThreadScheduledExecutor` |
| `grace` 设得比 eager 推送耗时还短 | Relay 与 eager 抢同批，重复发送 | grace > eager P95 推送耗时 |
| 把 SQL / `@Transactional` 写进 `OutboxConfig` | 框架 Spring 无关性被破坏，难以移植 | SQL 放 XML，事务走 `TransactionOperations` |

## 6. 下一步

- [事务性发件箱（Outbox）](../integration/outbox.md)：状态机、SQL 建表、多实例部署
- [RocketMQ 配置设计原则](./rocketmq-config.md)：`IEventManager` 与序列化器配套注册
- [应用服务](../core/application-service.md)：`OutboxCommandExecutor` 的调用方式
