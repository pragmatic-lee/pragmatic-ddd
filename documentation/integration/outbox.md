# 事务性发件箱（Outbox）集成

> 本文档面向使用 `pragmatic-ddd` 框架、需要"领域事件不丢失"的开发者，说明 core 提供的**事务性发件箱模式**能力：状态机、建表 SQL、Eager 主动推送、Relay 兜底轮询、多实例部署，以及与 RocketMQ 的可靠投递配合。
> **装配落地（Spring Bean 怎么接）见 [Outbox 链路装配](../best-practices/outbox-config.md)**，本文只讲模式能力与用法，不写装配细节。

## 1. 模式解决的问题

领域事件在进程内 `ThreadPoolEventManager` 仅内存执行，进程退出即丢；即便投递到 MQ，也存在"业务已提交、事件发送失败"的窗口。事务性发件箱把**事件写入与业务写入放进同一个本地 DB 事务**，事务提交后由框架异步推送，保证"业务成功则事件一定不丢"。

它解决的典型场景：

- **可靠投递**：业务事务提交后，事件随 Outbox 落库为 `PENDING`；即使即时推送（MQ Producer 发送）失败，也有 Relay 兜底重推，杜绝"业务成功、事件丢失"。
- **最终一致性**：下游消费失败由 MQ 重试/死信兜底（见 [RocketMQ 集成](./rocketmq.md) 第 3 节），Outbox 只负责"把事件可靠交给 MQ"，不介入下游处理。
- **崩溃补偿**：进程在推送前崩溃，Outbox 表里的 `PENDING` 行由 Relay 在下次启动时认领重推。

## 2. 状态机

Outbox 消息状态流转：

```text
PENDING ──(eager 主动推送成功 / Relay 重推成功)──▶ SENT
   │
   └──(推送失败且达到 maxAttempts)──▶ FAILED（死信，需人工/监控介入）
```

- `PENDING`：事务提交后写入，等待推送。
- `SENT`：推送成功（含幂等的重复 `markSent`，状态守卫保证安全）。
- `FAILED`：重试耗尽，进入死信，不再自动推送。

> 两条推送路径（Eager 主动 + Relay 兜底）都调用同一个 `IEventManager.publish`，`markSent` 带 status 守卫（PENDING/PROCESSING → SENT），重复调用安全——但下游仍需自行幂等，消息可能被重复投递。

## 3. 建表 SQL

Outbox 表最小 schema（以 MySQL 为例，其他方言改类型即可）：

```sql
CREATE TABLE outbox (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    aggregate_id  VARCHAR(100)  NOT NULL,
    event_type    VARCHAR(200)  NOT NULL,
    payload       TEXT          NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    created_at    DATETIME      NOT NULL,
    sent_at       DATETIME,
    attempts      INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_status_created (status, created_at)
);
```

`MybatisOutboxStore` 通过 `IOutboxStatementExecutor` 按 `OutboxStatements` 提供的 key 调用 SQL，SQL 写在 MyBatis XML 中（namespace.statementId 绑定），不写进 Java。

## 4. Eager 主动推送

事务提交后，`EagerOutboxPublisher` 异步主动推送 PENDING 事件，是低延迟的"快路径"。

```java
// 有界线程池：避免事件洪峰时线程无限增长
ExecutorService pool = Executors.newFixedThreadPool(4);
// serializer 必须复用 rocketmq 模块的 Fastjson2EventSerializer，与 RocketMqEventManager 共用同一 IEventSerializer 端口
IEventSerializer serializer = new io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer();
EagerOutboxPublisher eager = new EagerOutboxPublisher(outboxStore, eventManager, pool);
```

> ⚠️ 序列化器必须复用 `io.pragmatic.ddd.rocketmq.Fastjson2EventSerializer`：Relay 反序列化重发与 `RocketMqEventManager` 共用同一 `IEventSerializer` 端口，两套实现会导致格式不一致、重发失败。
> ⚠️ `pool` 必须有界：eager 在事务提交后异步发送，无界队列在事件洪峰时拖垮 JVM；溢出任务保持 PENDING 由 Relay 兜底。

## 5. Relay 兜底轮询

`OutboxRelay` 是"慢路径"，周期认领 `age > grace` 的 PENDING 行重推，补偿 eager 崩溃 / MQ 抖动。

```java
OutboxRelayConfig relayConfig = OutboxRelayConfig.defaultConfig();
// 或绑定配置源：outbox.poll-interval / outbox.grace / outbox.batch-size / outbox.max-attempts
// OutboxRelayConfig.bind(configurationSource);

OutboxRelay relay = new OutboxRelay(
        outboxStore,
        eventManager,
        serializer,                                    // 同 Eager 的复用实现
        Executors.newSingleThreadScheduledExecutor(),  // 单线程调度
        relayConfig);
relay.start();
```

`defaultConfig()` 取值：`pollInterval=5min`、`grace=30s`、`batchSize=200`、`maxAttempts=10`。

> ⚠️ 单线程调度：认领靠 `claim_token` 做原子认领；多实例各持唯一 token 互不抢，单实例内用单线程避免同一批 PENDING 被并发认领两次。
> ⚠️ `grace` 必须 > eager 推送典型耗时：否则 Relay 会抢 eager 还在发的同一批，造成重复发送。事件量大时按实际 P99 推送耗时上调。

## 6. 多实例部署

- **认领安全**：`claimPending` 的 SQL 带行锁 + 实例唯一 `claim_token`，多实例各自只认领到自己 token 的行，互不重复。
- **每实例一个 Relay**：每个实例各起一个单线程 `OutboxRelay`，认领隔离保证安全，无需外部协调。
- **下游幂等**：多实例 + 重试/重投可能导致同一事件被多次投递，消费逻辑必须幂等（用业务键去重）。

## 7. 与 RocketMQ 配合

见 [RocketMQ 集成](./rocketmq.md) 第 5 节：RocketMQ 事件管理器作为即时推送通道，与 OutboxRelay 配合加固"事件不丢"——事件先随业务事务写 Outbox，发送失败时由 Relay 兜底重推。

```text
业务事务提交 → Outbox 落库(PENDING) + EagerPublisher 主动推送
                                    ↓ 推送失败
                          OutboxRelay 兜底轮询 → 重新推送
                                    ↓ 重试耗尽
                                markFailed（死信）
```

## 8. 总结速查

| 概念 | 关键事实 | 最关键约束 |
| --- | --- | --- |
| 状态机 | PENDING → SENT / FAILED，`markSent` 幂等 | 下游仍需自行幂等 |
| 建表 | `outbox` 表 + `idx_status_created` | SQL 在 MyBatis XML，不写进 Java |
| Eager | 事务提交后主动推送（快路径） | 复用 rocketmq 序列化器；线程池有界 |
| Relay | 周期兜底轮询（慢路径） | 单线程调度；`grace` > eager 耗时 |
| 多实例 | 每实例一个 Relay | `claim_token` 隔离 + 下游幂等 |
| 与 MQ 配合 | RocketMQ 作即时通道，Outbox 兜底 | 见 RocketMQ 集成第 5 节 |
