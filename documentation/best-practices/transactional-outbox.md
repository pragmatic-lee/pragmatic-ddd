# 事务性发件箱

> 本文档介绍 Pragmatic DDD 的事务性发件箱（Transactional Outbox）模式的使用。

## 1. 为什么需要 Outbox

直接在 `CommandExecutor` 中"先落库后发 MQ"存在经典问题：

```
落库成功 → 发 MQ 失败 → 数据已改但事件丢失
落库成功 → 发 MQ 成功 → 消费者处理失败 → 数据已改但下游不一致
```

Outbox 模式的解决思路：**把事件与聚合根放在同一个事务中持久化**，保证两者原子性，再异步推送事件。

## 2. 架构概览

```
                    ┌─────────────────────────────┐
                    │       OutboxCommandExecutor   │
                    │                               │
  业务请求 ───────► │  ① 同事务：聚合落库 + Outbox    │
                    │     落库(PENDING)             │
                    │                               │
                    │  ② 事务提交后：               │
                    │     EagerOutboxPublisher      │
                    │     主动推送                   │
                    └───────────┬───────────────────┘
                                │
                     成功 │     │ 失败
                         │     │
                         ▼     ▼
                    SENT    OutboxRelay 兜底轮询
                            │
                    ┌───────┴────────┐
                    │ 认领 PENDING    │
                    │ 重新推送         │
                    │ 成功 → SENT     │
                    │ 失败 → 重试     │
                    │ 超限 → FAILED   │
                    └────────────────┘
```

## 3. 使用步骤

### 3.1 准备 Outbox 表

```sql
CREATE TABLE outbox_message (
    id            VARCHAR(64) PRIMARY KEY,
    aggregate_id  VARCHAR(64),
    aggregate_type VARCHAR(255),
    event_type    VARCHAR(255),
    entity_id     VARCHAR(64),
    payload       TEXT,
    status        VARCHAR(20) DEFAULT 'PENDING',
    attempts      INT DEFAULT 0,
    queue         INT DEFAULT 0,
    claim_token   VARCHAR(64),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
```

### 3.2 配置 `MybatisOutboxStore`

```java
// 1. 注册 OutboxMapper（同包同名 OutboxMapper.xml 自动加载绑定）
sqlSessionFactory.getConfiguration().addMapper(OutboxMapper.class);

// 2. 提供事务抽象实现（SPI，由使用方按技术栈实现，如 Spring 事务模板 / SqlSession 手动提交）
//    store 在调用方事务内执行，claim / markSent / release 等补偿操作通过它各自开启独立短事务
TransactionOperations txOps = ...;

// 3. 装配（构造签名：OutboxMapper + TransactionOperations）
OutboxMapper mapper = sqlSession.getMapper(OutboxMapper.class);
MybatisOutboxStore outboxStore = new MybatisOutboxStore(mapper, txOps);
```

### 3.3 配置 `OutboxCommandExecutor`

```java
// 事件序列化器
IEventSerializer serializer = new Fastjson2EventSerializer();

// 主动推送器（事务提交后触发）
EagerOutboxPublisher eagerPublisher = new EagerOutboxPublisher(
        outboxStore, eventManager, serializer);

// Outbox 命令执行器
OutboxCommandExecutor executor = new OutboxCommandExecutor(
        outboxStore,
        transactionOperations,  // 事务模板
        serializer,
        eagerPublisher);

// 使用方式与 CommandExecutor 完全一致
executor.execute(order, orderRule, orderRepository, Order::cancel);
```

### 3.4 配置 `OutboxRelay` 兜底

```java
OutboxRelay relay = new OutboxRelay(
        outboxStore,
        eventManager,
        serializer,
        Executors.newScheduledThreadPool(1),
        new OutboxRelayConfig(
                Duration.ofSeconds(5),   // pollInterval
                Duration.ofSeconds(30),  // grace
                100,                      // batchSize
                5));                      // maxAttempts

relay.start();  // 启动周期性轮询
```

## 4. 状态机详解

```
PENDING ──claim──→ PROCESSING ──publish成功──→ SENT ✅
                      │
                      └──publish失败──→ release → PENDING
                                        ↓
                                   attempts > maxAttempts?
                                      ├── 是 → FAILED 💀（死信）
                                      └── 否 → 继续 PENDING（等待下次轮询）
```

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 待发送（刚落库或释放回退） |
| `PROCESSING` | 认领中（某实例正在推送） |
| `SENT` | 已成功发送 |
| `FAILED` | 死信（重试耗尽） |

## 5. 多实例部署

多实例部署时，`claimPending` 使用 `claim_token`（UUID）实现原子认领：

```sql
-- 原子认领（单条 SQL，数据库行锁保证安全）
UPDATE outbox_message
SET status = 'PROCESSING',
    claim_token = #{token},
    updated_at = NOW()
WHERE id IN (
    SELECT id FROM outbox
    WHERE status = 'PENDING'
      AND created_at < NOW() - INTERVAL #{grace} SECOND
    LIMIT #{batchSize}
    FOR UPDATE
)
```

- 每个实例生成唯一 `claim_token`
- 只有认领成功的实例才会推送
- 超时未完成的 `PROCESSING` 记录会被其他实例重新认领

## 6. 配置参考

`OutboxRelayConfig`：

| 参数 | 说明 | 推荐值 |
| --- | --- | --- |
| `pollInterval` | 轮询间隔 | 5-10 秒 |
| `batchSize` | 每次认领批量 | 100-500 |
| `grace` | 宽限时间（超过此时间的 PENDING 才认领） | 30-60 秒 |
| `maxAttempts` | 最大重试次数 | 3-5 次 |

::: tip grace 窗口
`grace` 窗口确保 `EagerOutboxPublisher` 有足够时间主动推送，避免与 Relay 竞争。设为 30 秒意味着：事件落库后 30 秒内不会被 Relay 认领，给主动推送留出时间。
:::

---

下一步：

- [MyBatis 集成](../integration/mybatis.md)
- [RocketMQ 集成](../integration/rocketmq.md)
- [应用服务](../core/application-service.md)
