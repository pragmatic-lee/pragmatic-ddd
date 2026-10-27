# BP 卡：Outbox 链路装配（outbox-config）

> 源：`documentation/best-practices/outbox-config.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与边界

`OutboxConfig` 是**纯装配类**，把框架提供的五个协作者组装成 Spring Bean；不写 SQL、不写 `@Transactional`、不持有 Mapper 接口。

| Bean | 类型 | 作用 |
| --- | --- | --- |
| `TransactionOperations` | `io.pragmatic.ddd.application.spi.TransactionOperations` | 聚合写 + outbox 写绑同一 DB 事务 |
| `IEventSerializer` | `io.pragmatic.ddd.event.spi.IEventSerializer` | 事件序列化（Relay 反序列化重发共用） |
| `IOutboxStore` | `io.pragmatic.ddd.application.outbox.spi.IOutboxStore` | outbox 行落库与认领 |
| `EagerOutboxPublisher` | `io.pragmatic.ddd.application.outbox.EagerOutboxPublisher` | 提交后主动推送（快路径） |
| `OutboxRelay` | `io.pragmatic.ddd.application.outbox.OutboxRelay` | 周期兜底轮询（慢路径） |

> ⚠️ **前置条件**：Outbox 的 SQL 在 `io/pragmatic/ddd/mybatis/outbox/OutboxMapper.xml`，starter 不自动加载；必须纳入 `SqlSessionFactory` 扫描并建 `outbox_message` 表（脚本 `io/pragmatic/ddd/mybatis/outbox/schema/outbox-schema-mysql.sql`）。缺任一项运行期报 `Mapped Statements collection does not contain value for ...`。PostgreSQL：改扫 `outbox/postgres/*.xml` + 设 `databaseId=postgres`，脚本取同目录 `outbox-schema-postgres.sql`。

## 2. 命名与包结构

五个 Bean 为跨聚合**通用**装配，放 `infrastructure/config/`（不绑定聚合）；`SpringTransactionOperations` / `SpringOutboxStatementExecutor` 同层。

## 3. 落地顺序（五个 Bean）

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

### 3.1~3.5 关键约束

```java
@Bean
public TransactionOperations transactionOperations(PlatformTransactionManager tm) {
    return new SpringTransactionOperations(tm);   // 用户自备薄适配，core 不含 Spring 绑定
}

@Bean
public IEventSerializer eventSerializer() {
    return new Fastjson2EventSerializer();   // ⚠️ 必须 io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer，勿自行 new
}

@Bean
public IOutboxStore outboxStore(SqlSessionTemplate tpl, TransactionOperations txOps) {
    IOutboxStatementExecutor executor = new SpringOutboxStatementExecutor(tpl);
    return new MybatisOutboxStore(executor, txOps);
}

@Bean
public EagerOutboxPublisher eagerOutboxPublisher(IOutboxStore store, IEventManager mgr) {
    ExecutorService pool = Executors.newFixedThreadPool(4);   // ⚠️ 必须有界线程池
    return new EagerOutboxPublisher(store, mgr, pool);
}

@Bean
public OutboxRelay outboxRelay(IOutboxStore store, IEventManager mgr, IEventSerializer ser) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();  // ⚠️ 必须单线程
    return new OutboxRelay(store, mgr, ser, scheduler, OutboxRelayConfig.defaultConfig());
}

@Bean
public ApplicationRunner startOutboxRelayOnReady(OutboxRelay relay) {
    return args -> relay.start();   // ⚠️ 应用就绪后再 start，不在 initMethod 启动
}
```

- `OutboxRelayConfig.defaultConfig()`：`pollInterval=5min`、`grace=30s`、`batchSize=200`、`maxAttempts=10`；亦 `OutboxRelayConfig.bind(source)`（键 `outbox.poll-interval` 等）。
- `grace` 必须 > eager 推送典型耗时，否则 Relay 与 eager 抢同批导致重复发送。

## 4. 关键机制

- 两条路径互补：eager 快路径（低延迟）+ Relay 慢路径（崩溃/抖动补偿）；都调用同一 `IEventManager.publish`，Relay 靠 `IEventSerializer.deserialize` 还原。
- `markSent` 幂等（`PENDING/PROCESSING → SENT`），重复推送安全；下游仍需自行幂等。
- SQL 与执行器解耦：`SpringOutboxStatementExecutor` 只转发 `statementKey`，SQL 在 XML 按 `OutboxStatements` 实现。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 自行 new `Fastjson2EventSerializer` | 复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer` |
| `EagerOutboxPublisher` 无界线程池 | `newFixedThreadPool(n)` 有界池 |
| `OutboxRelay` 多线程调度 | `newSingleThreadScheduledExecutor` |
| `grace` 短于 eager 耗时 | grace > eager P95 耗时 |
| SQL/`@Transactional` 写进 `OutboxConfig` | SQL 放 XML，事务走 `TransactionOperations` |

## 6. 被读于

S3 `ddd-infra-config`、S5 `ddd-aggregate-extend`、S7 `ddd-code-review`。
