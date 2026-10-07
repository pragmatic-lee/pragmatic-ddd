# order-example 完整示例

> 基于 pragmatic-ddd 框架的**完整 DDD 落地示例**：订单域聚合根 + 领域事件 + 事务发件箱（Outbox）+ 号段 ID + Elasticsearch 投影，一条命令跑通全链路。
> 与 [API 级快速开始](https://github.com/pragmatic-lee/pragmatic-ddd/blob/main/documentation/getting-started/quick-start.md)（5 分钟，零依赖）互补：本示例演示框架与 MySQL / Elasticsearch + 事件总线（**RocketMQ 与 Kafka 二选一**）的完整集成。

## 目录

- [1. 示例简介](#1-示例简介)
- [2. 组件与端口](#2-组件与端口)
- [3. 事件总线：RocketMQ / Kafka 二选一](#3-事件总线rocketmq--kafka-二选一)
  - [3.1 切换方式](#31-切换方式)
  - [3.2 Kafka 关键配置项](#32-kafka-关键配置项)
  - [3.3 Kafka 消费失败重试链路](#33-kafka-消费失败重试链路)
- [4. 方式一：一键启动（推荐）](#4-方式一一键启动推荐)
  - [4.1 前置要求](#41-前置要求)
  - [4.2 构建](#42-构建)
  - [4.3 一键初始化](#43-一键初始化)
  - [4.4 启动 Kafka（脚本未覆盖，需手动一次）](#44-启动-kafka脚本未覆盖需手动一次)
  - [4.5 验证全链路](#45-验证全链路)
- [5. 方式二：已有环境接入](#5-方式二已有环境接入)
  - [5.1 场景说明](#51-场景说明)
  - [5.2 单独初始化 MySQL](#52-单独初始化-mysql)
  - [5.3 单独初始化 Elasticsearch](#53-单独初始化-elasticsearch)
  - [5.4 初始化 Topic（无需手动）](#54-初始化-topic无需手动)
  - [5.5 连接配置](#55-连接配置)
  - [5.6 运行应用](#56-运行应用)
- [6. docker 目录结构](#6-docker-目录结构)
- [7. 常见问题 FAQ](#7-常见问题-faq)
- [8. 下一步](#8-下一步)

---

## 1. 示例简介

示例覆盖了 pragmatic-ddd 框架的以下核心能力：

| 能力 | 落地位置 |
| --- | --- |
| 聚合根 / 实体 / 值对象 | `Order` 聚合根、`OrderItem` 实体、`Money`/`Address` 等值对象 |
| 领域事件 | `OrderDataSyncEvent` 等，经 RocketMQ 或 Kafka 异步投递（`event.bus` 切换） |
| 事务发件箱（Outbox） | 下单事务内写 `outbox_message`，保证事件不丢 |
| 号段 ID 生成 | `id_segment` 表 + 号段分配器（step=1000） |
| ES 投影（CQRS 读模型） | 事件消费后投影 `OrderEsProjection` 写入 ES `order_index` |
| 事件总线可替换 | `event.bus=kafka` 走 `KafkaEventManager`，`rocketmq` 走 `RocketMqEventManager`，其余业务代码零改动 |
| 对账与补偿 | `ReconciliationRegistry` 登记仓储/版本解析器/补偿器 |

**数据流**：

```
HTTP 下单 → Order 聚合根 → MySQL（t_order + outbox_message 同事务）
                              │ 发件箱投递（目标中间件由 event.bus 决定）
                              ▼
                RocketMQ（data_sync_event）
                    或 Kafka（data_sync_event / data_sync_event-retry / data_sync_event-dlq）
                              │ 消费
                              ▼
                    加载聚合 → 投影 → ES（order_index / 别名 order）
```

## 2. 组件与端口

| 服务 | 镜像 | 端口 | 用途 |
| --- | --- | --- | --- |
| order-example | `order-example:2.0.0`（自构建） | 9500（应用）/ 5005（远程调试） | 示例应用 |
| mysql | `mysql:8.0` | 3306 | 业务库 `order_example` |
| elasticsearch | `elasticsearch:8.17.0` | 9200 / 9300 | 投影读模型（需 IK 插件） |
| rocketmq-namesrv | `apache/rocketmq:5.3.2` | 9876 | 注册中心 |
| rocketmq-broker | `apache/rocketmq:5.3.2` | 10909 / 10911 | 消息存储 |
| rocketmq-dashboard | `apacherocketmq/rocketmq-dashboard:2.1.0` | 8080 | RocketMQ 消息可视化 |
| kafka | `confluentinc/cp-kafka:7.6.1` | 9092（broker）/ 9093（controller） | 事件总线（KRaft 模式，无需 ZooKeeper），底层 Kafka 3.6.1 |
| kafka-ui | `provectuslabs/kafka-ui:latest` | 8082 | Kafka topic / 消息可视化 |
| redis | `redis:7.2` | 6379 | 缓存副本（事件驱动的写缓存/对账兜底） |
| qdrant | `qdrant/qdrant` | 6333 / 6334 | 预留向量库（示例暂未使用） |

> RocketMQ 与 Kafka 两套中间件同时编排在 compose 中，按 `event.bus` 选用其一作为事件总线；不用的一侧可以保持运行但不会产生流量。

## 3. 事件总线：RocketMQ / Kafka 二选一

### 3.1 切换方式

`IEventManager` 有两套实现，示例通过 `event.bus` 单开关切换，业务代码（聚合根、投影、订阅者登记）完全一致：

| `event.bus` | 装配类 | 实现模块 | 配置前缀 |
| --- | --- | --- | --- |
| `kafka`（示例默认） | `KafkaConfig` | `pragmatic-ddd-kafka` | `kafka.*` |
| `rocketmq` | `RocketMQConfig` | `pragmatic-ddd-rocketmq` | `rocketmq.*` |

- 两个配置类都用 `@ConditionalOnProperty` 互斥（Kafka 侧 `havingValue = "kafka"`，RocketMQ 侧 `havingValue = "rocketmq", matchIfMissing = true`），保证容器内只有一个 `IEventManager` Bean；
- 切换只需改一个配置项，无需改代码：

```properties
# src/main/resources/application.properties 或 docker 挂载的 application-dev.properties
event.bus=kafka      # 走 Kafka
event.bus=rocketmq   # 走 RocketMQ
```

- topic 由 `OrderEventTopicConfig` 的 `ConfigurableTopicResolver` 统一提供，主 topic 固定为 `data_sync_event`；Kafka 侧在此基础上按后缀派生 `-retry`（重试）与 `-dlq`（死信）。

> `KafkaConfig` 只负责装配：`kafkaProperties(...)` 把 `kafka.*` 绑定成 `KafkaProperties`，`orderEventManager(...)` 构建 `KafkaEventManager`（producer/consumer 内部自建），`startKafkaOnReady(...)` 在应用就绪后（`ApplicationRunner`）调用 `start()` 订阅 topic。

### 3.2 Kafka 关键配置项

完整清单见 `docker/order-example/config/application-dev.properties`，下表按用途摘取：

| 配置项 | dev 容器值 | 说明 |
| --- | --- | --- |
| `kafka.bootstrap-servers` | `kafka:9092` | broker 地址列表，容器内用服务名，本机 JVM 直跑用 `127.0.0.1:9092` |
| `kafka.group` | `order_example_consumer` | 消费组 |
| `kafka.auto-offset-reset` | `latest` | 无已提交位点时的起始位点 |
| `kafka.ack` | `all` | 生产者 `acks`，配合幂等开启最强持久性 |
| `kafka.enable-idempotence` | `true` | 幂等生产者，去重重试导致的重复写入 |
| `kafka.send-timeout-ms` | `10000` | 生产者发送超时 |
| `kafka.max-poll-records` | `500` | 单次 `poll` 消息上限 |
| `kafka.poll-timeout-ms` | `1000` | `poll` 阻塞超时 |
| `kafka.concurrency` | `1` | 消费者并发度 |
| `kafka.batch-budget-ms` | `30000` | 单批处理时间预算，超预算的剩余消息留待下轮，避免长时间不 `poll` 被判为失效成员 |
| `kafka.inline-retry-intervals-ms` | `100,500` | 消费失败先内联重试的间隔表（毫秒） |
| `kafka.retry-topic-suffix` | `-retry` | 重试 topic 后缀，置空则关闭重试通道 |
| `kafka.retry-hop-backoff-ms` | `1000,5000,10000,30000,60000,120000,180000,240000` | 重试 topic 每轮往返退避表（8 档，超末档复用末档） |
| `kafka.dlq-suffix` | `-dlq` | 死信 topic 后缀 |
| `kafka.max-reconsume` | `16` | 重试预算耗尽后转死信 |
| `kafka.delayed-policy` | `immediate` | DELAYED 策略降级为即时发送（不使用 `-delay` topic） |
| `kafka.compression-type` / `kafka.client-id` | 空 | 可选，按需开启 |

Kafka 模式下实际落地的 topic（`data_sync_event` 为主 topic）：

| topic | 何时产生 |
| --- | --- |
| `data_sync_event` | 发件箱投递的业务事件 |
| `data_sync_event-retry` | 内联重试耗尽后转投，等待退避到期回投 |
| `data_sync_event-dlq` | 重试次数超 `kafka.max-reconsume` 后的死信 |

### 3.3 Kafka 消费失败重试链路

```
消费失败 → 内联重试（100ms、500ms）
        → 转 data_sync_event-retry（KafkaRedeliverRelay 按退避表到期回投）
        → 回投仍失败，累计超 16 次
        → 进入 data_sync_event-dlq
```

对应实现类在 `pragmatic-ddd-kafka`：`KafkaEventManager`、`KafkaRedeliverRelay`、`KafkaProperties`（所有配置键的默认值都在 `KafkaProperties.withDefaults(...)`）、`KafkaEventSerializer`。

## 4. 方式一：一键启动（推荐）

> 适用：全新环境（本机无 MySQL / ES / RocketMQ / Kafka 或可接受独占端口）。
>
> ⚠️ 示例默认 `event.bus=kafka`，但 `setup.sh` 目前**没有拉起 Kafka**，跑完脚本后请务必补 [4.4](#44-启动-kafka脚本未覆盖需手动一次) 这一步，否则事件会滞留在 `outbox_message`。

### 4.1 前置要求

- Docker（含 docker compose 插件）
- JDK 17 + Maven（仅构建需要，运行在容器内）
- 空闲端口：9500、3306、9200、9876、10909、10911、8080、9092、9093、8082、6379、6333

### 4.2 构建

```bash
# 在仓库根目录执行（编译框架 + 示例，产物含 Dockerfile）
mvn -pl examples/order-example -am clean package -DskipTests
```

### 4.3 一键初始化

```bash
cd examples/order-example/docker
bash setup.sh
```

`setup.sh` 全程幂等、可重复执行，内部按序完成：

| 步骤 | 动作 | 说明 |
| --- | --- | --- |
| 0 | 前置检查 | 校验 `target/order-example.jar` 与 `target/Dockerfile` 存在 |
| 1 | 启动中间件 | MySQL / Redis / ES / RocketMQ（compose）；**不含 Kafka，见 [4.4](#44-启动-kafka脚本未覆盖需手动一次)** |
| 2 | 等待 MySQL 就绪 | `mysqladmin ping` 轮询（上限 60s） |
| 3 | 初始化 MySQL | 建库建表 + `id_segment` 初始行（幂等 SQL） |
| 4 | 等待 ES 就绪 | `curl :9200` 轮询（上限 60s） |
| 5 | 检查 IK 插件 | 缺失则给出安装指引并退出 |
| 6 | 创建 ES 索引 | `order_index` 已存在则跳过（幂等） |
| 7 | 构建应用镜像 | `docker build -t order-example:2.0.0` |
| 8 | 启动应用 | `compose up -d order-example` |
| 9 | 就绪自检 | `curl :9500/api/health` 轮询（上限 60s） |

### 4.4 启动 Kafka（脚本未覆盖，需手动一次）

> ⚠️ 当前 `setup.sh` 的第 1 步**没有拉起 `kafka` / `kafkaui`**，也没有等待 Kafka 就绪；而 `docker/order-example/config/application-dev.properties` 里 `event.bus=kafka`、`kafka.bootstrap-servers=kafka:9092`。因此走 Kafka 事件总线时，`setup.sh` 跑完后还需补一条命令（幂等，可重复执行）：

```bash
cd examples/order-example/docker

# 1. 启动 Kafka（KRaft 单节点，无需 ZooKeeper）与 Kafka UI
docker compose up -d kafka kafkaui

# 2. 等待 Kafka 就绪（轮询，上限 90s）
i=0
until docker exec my-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 90 ]; then
    echo "Kafka 等待超时（90s），请检查 docker compose ps / docker logs my-kafka"
    break
  fi
  sleep 1
done

# 3. 查看 topic（新环境首次投递后 broker 才自动建出来）
docker exec my-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

- topic **不需要手动创建**：broker 默认开启自动建 topic，框架首次 `send()` 时 broker 自动创建（`data_sync_event`；发生消费失败后才会派生 `-retry` / `-dlq`）；
- 建议**先启动 Kafka、再下单**：若 Kafka 未就绪，事件仍会安全落在 `outbox_message`（PENDING），业务请求不受影响，但投影不会更新；`OutboxRelay` 兜底轮询（默认 5 分钟一轮、最多 10 次）会在 Kafka 恢复后自动重投，也可以直接重启应用立即触发推送；
- 用 RocketMQ 事件总线时可跳过本节：把配置改为 `event.bus=rocketmq`，或直接用 `setup.sh` 已拉起的 RocketMQ。

### 4.5 验证全链路

```bash
# 1. 健康检查
curl http://localhost:9500/api/health

# 2. 触发下单（内置示例数据，返回订单号）
curl http://localhost:9500/api/testOrder
# → 1111

# 3. 核对 MySQL（订单 + 发件箱已发送）
docker exec my-mysql mysql -h127.0.0.1 -uroot -pMySqlXXL123 order_example \
  -e "SELECT COUNT(*) FROM t_order; SELECT status, COUNT(*) FROM outbox_message GROUP BY status;"

# 4. 核对 ES（文档数 +1，最新 orderId）
curl "http://localhost:9200/_cat/indices/order_index?v"
curl -s "http://localhost:9200/order/_search?pretty" -H 'Content-Type: application/json' \
  -d '{"query":{"match_all":{}},"sort":[{"orderId":"desc"}],"size":1}'

# 5. 消息轨迹可视化（二选一，按 event.bus 打开对应面板）
open http://localhost:8082   # Kafka UI（event.bus=kafka，按 topic=data_sync_event 查看）
open http://localhost:8080   # RocketMQ Dashboard（event.bus=rocketmq）
```

可选：修改地址触发更新链路（`version` 递增 + ES 同步更新）：

```bash
curl "http://localhost:9500/api/testChangeAddress?orderId=1111"
```

Kafka 模式补充核对：

```bash
# 主 topic 分区与消息量
docker exec my-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic data_sync_event

# 消费组位点（LAG 应逐步收敛到 0）
docker exec my-kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group order_example_consumer
```

> 第 3 步若查到的 `outbox_message.status` 不是 `SENT`，说明投递未成功（Kafka 未就绪等）；事件不会丢，会由 `OutboxRelay` 兜底轮询重投（累计 10 次仍失败转 `FAILED`，可人工改回 `PENDING` 或调大重试）。

## 5. 方式二：已有环境接入

> 适用：你已经有 MySQL / Elasticsearch + RocketMQ / Kafka，**不想**为示例再起一套中间件。

### 5.1 场景说明

已有环境只需做三件事：**初始化数据库 → 初始化 ES 索引 → 改连接配置**。初始化脚本全部是环境无关的（纯 SQL / 纯 curl），可以直接指向你的已有中间件。

### 5.2 单独初始化 MySQL

在已有 MySQL 上执行仓库里的两个幂等脚本（建库建表 + 初始数据）：

```bash
# 用你的 mysql 客户端连接已有实例
mysql -h<你的MySQL地址> -P3306 -uroot -p < docker/mysql/init/01-schema.sql
mysql -h<你的MySQL地址> -P3306 -uroot -p < docker/mysql/init/02-data.sql
```

- 脚本会创建库 `order_example` 及 4 张表（`t_order` / `t_order_item` / `id_segment` / `outbox_message`）
- **幂等**：可重复执行；`02-data.sql` 用 `INSERT IGNORE`，已有 `order` 号段行不会被覆盖
- `id_segment` 的 `order` 初始行**必须存在**，否则应用启动后下单会报错（号段分配查不到渠道）

### 5.3 单独初始化 Elasticsearch

已有 ES 上直接执行建索引脚本，用 `ES_URL` 指向你的实例：

```bash
ES_URL=http://<你的ES地址>:9200 bash docker/es/init/order-es-create-index.sh
```

- 创建 `order_index`（3 分片 / 1 副本）并绑定读写别名 `order`
- **前置要求**：ES 需安装 **IK 分词插件**（版本须与 ES 严格一致，示例为 8.17.0），索引的 `order_default_ik` 分析器依赖它；未安装会报 `analyzer [order_default_ik] not found`
- 幂等：索引已存在时脚本会因 400 冲突失败，属预期行为（可先确认索引已存在后忽略）

### 5.4 初始化 Topic（无需手动）

- **RocketMQ**：`data_sync_event` topic **不需要手动创建**。应用启动时框架 `RocketMqEventManager.start()` 自动创建 Consumer 订阅，broker 侧 `autoCreateTopicEnable=true` 自动建 topic（前提：你的 broker 开启自动建 topic，默认开启）。
- **Kafka**：同样**不需要手动创建**，也没有建 topic 脚本。broker 默认 `auto.create.topics.enable=true`，首次 `send()` 时自动创建 `data_sync_event`；`-retry` / `-dlq` 只在发生消费失败时才会被创建。

### 5.5 连接配置

应用的外部化配置在 `docker/order-example/config/application-dev.properties`（容器挂载 `/app/config`，覆盖 jar 内默认值）。已有环境需修改以下连接项：

| 配置项 | 一键启动值（compose 服务名） | 已有环境改为 |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mysql://mysql:3306/order_example` | 你的 MySQL 地址 |
| `spring.datasource.password` | `MySqlXXL123` | 你的密码 |
| `elasticsearch.hosts` | `http://elasticsearch:9200` | 你的 ES 地址 |
| `event.bus` | `kafka` | 你的事件总线（`kafka` / `rocketmq`） |
| `kafka.bootstrap-servers` | `kafka:9092` | 你的 Kafka broker 地址 |
| `kafka.group` | `order_example_consumer` | 你的消费组（避免与他人共用） |
| `rocketmq.name-server` | `rmqnamesrv:9876` | 你的 NameServer 地址（`event.bus=rocketmq` 时生效） |

其余配置（连接池、超时、重试、批量预算）为框架推荐值，一般无需改动。

> **密码安全建议**：`rocketmq.name-server` 支持环境变量注入（`ROCKETMQ_NAMESRV`）；MySQL 密码目前示例为明文，接入生产环境前请修改并考虑用环境变量替换。

### 5.6 运行应用

**方式 A：本地 JVM 直接跑**（最快，适合已有中间件映射在本机端口）

```bash
mvn -pl examples/order-example -am spring-boot:run -Dspring-boot.run.profiles=dev
```

- 使用 `src/main/resources/application.properties`（默认 `localhost` 地址、`event.bus=kafka`、`kafka.bootstrap-servers=127.0.0.1:9092`）；若中间件不在本机，按 [5.5](#55-连接配置) 改配置
- **注意（RocketMQ）**：本机 JVM 直连 RocketMQ 时，broker 注册地址（`broker.conf` 的 `brokerIP1`）必须是本机可达的地址——compose 里的 `rmqbroker` 服务名仅在容器网络内有效，本机直连需改为宿主机 IP 或 `127.0.0.1`
- **注意（Kafka）**：compose 里 `kafka` 的 `KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092` 只声明了容器网络内的地址，本机 JVM 用 `127.0.0.1:9092` 连接时元数据会返回不可达的 `kafka:9092`，消费者无法工作；本机直连需为 broker 增加一个宿主机 listener（如 `PLAINTEXT_HOST://localhost:9092`）并把 `kafka.bootstrap-servers` 指向它

**方式 B：构建镜像跑**（与一键启动相同的镜像，但不依赖 compose 中间件）

```bash
mvn -pl examples/order-example -am clean package -DskipTests
docker build -t order-example:2.0.0 -f examples/order-example/target/Dockerfile examples/order-example/target
docker run -d --name my-order-example -p 9500:9500 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -v <你的配置目录>:/app/config \
  order-example:2.0.0
```

`<你的配置目录>` 里放修改后的 `application-dev.properties`（见 [5.5](#55-连接配置)）。容器需能访问你的中间件地址（网络互通即可，不要求在同一 compose 网络）。

## 6. docker 目录结构

```
docker/
├── docker-compose.yml            # 10 服务编排（中间件 + 应用，含 kafka / kafkaui）
├── setup.sh                      # 一键初始化（幂等；当前不含 Kafka，见 4.4）
├── reset-read-model.sh           # 重置 ES 读模型（不碰 MySQL 数据）
├── Dockerfile / build.sh         # 应用镜像构建（mvn package 后自动复制到 target/）
├── .gitignore                    # 运行期数据与日志（禁止提交，含 kafka/）
├── mysql/
│   ├── init/                     # ★ 建库建表脚本（01-schema.sql、02-data.sql）
│   ├── data/ logs/ conf/         # 运行期数据（不入库）
├── es/
│   ├── init/                     # ★ ES 建索引脚本（order-es-create-index.sh）
│   ├── data/ plugins/            # 运行期数据 + IK 插件（不入库）
├── kafka/
│   └── data/                     # KRaft 存储与各 topic 分区数据（不入库）
├── rocketmq/
│   └── conf/broker.conf          # broker 配置（brokerIP1、autoCreateTopicEnable）
├── redis/ data/                  # 运行期数据（不入库）
├── qdrant/ storage/              # 运行期数据（不入库）
└── order-example/
    └── config/application-dev.properties   # 应用外部化配置（容器挂载 /app/config）
```

## 7. 常见问题 FAQ

**Q1：`setup.sh` 跑完后应用日志报 Kafka 连接超时 / 投影不更新？**

`setup.sh` 未拉起 Kafka，按 [4.4](#44-启动-kafka脚本未覆盖需手动一次) 手动执行 `docker compose up -d kafka kafkaui` 并等待就绪，然后重启应用消费积压事件（事件已落在 `outbox_message`，不会丢）。

**Q2：想从 Kafka 换回 RocketMQ（或反过来）？**

只改一个配置项 `event.bus`（`kafka` / `rocketmq`）并重启应用；两套中间件可以同时保持运行。topic 名字（`data_sync_event`）与订阅者登记代码不变。

**Q3：Kafka 下事件消费失败会怎么处理？**

内联重试（100ms、500ms）→ 转 `data_sync_event-retry` 按退避表回投 → 累计超 16 次进 `data_sync_event-dlq`。可用 Kafka UI 查看这三个 topic 的消息与位点。

**Q4：`setup.sh` 报"IK 插件未安装"怎么办？**

两种方式（二选一）：
- 在线安装：`docker exec -it my-es bin/elasticsearch-plugin install https://github.com/infinilabs/analysis-ik/releases/download/v8.17.0/elasticsearch-analysis-ik-8.17.0.zip && docker restart my-es`
- 离线放置：下载对应版本插件包，解压到 `docker/es/plugins/ik` 后 `docker restart my-es`

**Q5：想修改 MySQL 密码？**

需要三处联动：① `docker-compose.yml` 的 `MYSQL_ROOT_PASSWORD`；② `application-dev.properties` 的 `spring.datasource.password`；③ 已初始化的数据卷不会自动更新密码（MySQL 仅在首次初始化时读取该变量），需手动 `ALTER USER` 或删除 `mysql/data/` 后重新初始化。

**Q6：本地 JVM 运行连不上 RocketMQ（超时/找不到 broker）？**

检查 `broker.conf` 的 `brokerIP1`：compose 内用 `rmqbroker` 服务名；本机直连需改为宿主机局域网 IP 或 `127.0.0.1`，改后 `docker restart rmqbroker`。

**Q7：本地 JVM 运行连不上 Kafka（连得上 bootstrap 但收不到消息）？**

多为 `advertised.listeners` 问题：broker 只向客户端返回容器内地址 `kafka:9092`，本机不可达。给 broker 补一个宿主机 listener 并同步 `kafka.bootstrap-servers`，详见 [5.6 方式 A](#56-运行应用) 的注意事项。

**Q8：端口被占用导致容器起不来？**

先确认占用方：`lsof -i :9500`（应用）、`:3306`（MySQL）、`:9092`（Kafka）等。若本机已有同端口服务，二选一：停掉冲突服务，或修改 `docker-compose.yml` 的端口映射（注意 `application-dev.properties` 连接地址也要同步改）。

**Q9：想重置数据重新体验？**

```bash
cd examples/order-example/docker
docker compose down
# 删除数据目录（保留 init 脚本与配置）
rm -rf mysql/data es/data rocketmq/broker/store kafka/data redis/data
docker compose up -d
# 幂等脚本会自动重建 schema；ES 索引缺失时再执行 setup.sh 第 6 步
bash setup.sh
```

**Q10：`setup.sh` 构建镜像很慢？**

首次构建需下载基础镜像与依赖；之后复用缓存。也可先 `docker pull eclipse-temurin:17-jre` 预热。

## 8. 下一步

- **跑通后看代码**：`KafkaConfig`（事件总线装配）→ `OrderEventTopicConfig`（topic 解析）→ `OrderEventSubscriberRegistry`（订阅者登记）→ 从 `Order` 聚合根（`domain/order/model`）→ 应用服务（`application/order/service`）→ 投影链路（`infrastructure/persistent/order/projection`）逐层阅读
- **换回 RocketMQ**：把 `event.bus` 改为 `rocketmq`，对比 `RocketMQConfig` 与 `KafkaConfig` 的差异，理解同一套领域事件如何适配两种中间件
- **接入你自己的项目**：复用本仓库的中间件 compose 与 `Dockerfile` / `build.sh` / `setup.sh` 模板（见 [docker 目录结构](#6-docker-目录结构) 与 [5.5 连接配置](#55-连接配置)），业务代码改为引用 `pragmatic-ddd-bom` 依赖
- **查阅框架文档**：VitePress 文档站（`documentation/`），入口 `documentation/index.md`
