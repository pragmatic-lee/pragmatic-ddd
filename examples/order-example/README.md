# order-example 完整示例

> 基于 pragmatic-ddd 框架的**完整 DDD 落地示例**：订单域聚合根 + 领域事件 + 事务发件箱（Outbox）+ 号段 ID + Elasticsearch 投影，一条命令跑通全链路。
> 与 [API 级快速开始](https://github.com/pragmatic-lee/pragmatic-ddd/blob/main/documentation/getting-started/quick-start.md)（5 分钟，零依赖）互补：本示例演示框架与 MySQL / RocketMQ / Elasticsearch 的完整集成。

## 目录

- [1. 示例简介](#1-示例简介)
- [2. 组件与端口](#2-组件与端口)
- [3. 方式一：一键启动（推荐）](#3-方式一一键启动推荐)
  - [3.1 前置要求](#31-前置要求)
  - [3.2 构建](#32-构建)
  - [3.3 一键初始化](#33-一键初始化)
  - [3.4 验证全链路](#34-验证全链路)
- [4. 方式二：已有环境接入](#4-方式二已有环境接入)
  - [4.1 场景说明](#41-场景说明)
  - [4.2 单独初始化 MySQL](#42-单独初始化-mysql)
  - [4.3 单独初始化 Elasticsearch](#43-单独初始化-elasticsearch)
  - [4.4 初始化 Topic（无需手动）](#44-初始化-topic无需手动)
  - [4.5 连接配置](#45-连接配置)
  - [4.6 运行应用](#46-运行应用)
- [5. docker 目录结构](#5-docker-目录结构)
- [6. 常见问题 FAQ](#6-常见问题-faq)
- [7. 下一步](#7-下一步)

---

## 1. 示例简介

示例覆盖了 pragmatic-ddd 框架的以下核心能力：

| 能力 | 落地位置 |
| --- | --- |
| 聚合根 / 实体 / 值对象 | `Order` 聚合根、`OrderItem` 实体、`Money`/`Address` 等值对象 |
| 领域事件 | `OrderDataSyncEvent` 等，经 RocketMQ 异步投递 |
| 事务发件箱（Outbox） | 下单事务内写 `outbox_message`，保证事件不丢 |
| 号段 ID 生成 | `id_segment` 表 + 号段分配器（step=1000） |
| ES 投影（CQRS 读模型） | 事件消费后投影 `OrderEsProjection` 写入 ES `order_index` |
| 对账与补偿 | `ReconciliationRegistry` 登记仓储/版本解析器/补偿器 |

**数据流**：

```
HTTP 下单 → Order 聚合根 → MySQL（t_order + outbox_message 同事务）
                              │ 发件箱投递
                              ▼
                      RocketMQ（data_sync_event）
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
| rocketmq-dashboard | `apacherocketmq/rocketmq-dashboard:2.1.0` | 8080 | 消息可视化 |
| redis | `redis:7.2` | 6379 | 预留（示例暂未使用） |
| qdrant | `qdrant/qdrant` | 6333 / 6334 | 预留向量库（示例暂未使用） |

## 3. 方式一：一键启动（推荐）

> 适用：全新环境（本机无 MySQL / ES / RocketMQ 或可接受独占端口）。

### 3.1 前置要求

- Docker（含 docker compose 插件）
- JDK 17 + Maven（仅构建需要，运行在容器内）
- 空闲端口：9500、3306、9200、9876、10909、10911、8080、6379、6333

### 3.2 构建

```bash
# 在仓库根目录执行（编译框架 + 示例，产物含 Dockerfile）
mvn -pl examples/order-example -am clean package -DskipTests
```

### 3.3 一键初始化

```bash
cd examples/order-example/docker
bash setup.sh
```

`setup.sh` 全程幂等、可重复执行，内部按序完成：

| 步骤 | 动作 | 说明 |
| --- | --- | --- |
| 1 | 启动中间件 | MySQL / Redis / ES / RocketMQ（compose） |
| 2 | 等待 MySQL 就绪 | `mysqladmin ping` 轮询（上限 60s） |
| 3 | 初始化 MySQL | 建库建表 + `id_segment` 初始行（幂等 SQL） |
| 4 | 等待 ES 就绪 | `curl :9200` 轮询（上限 60s） |
| 5 | 检查 IK 插件 | 缺失则给出安装指引并退出 |
| 6 | 创建 ES 索引 | `order_index` 已存在则跳过（幂等） |
| 7 | 构建应用镜像 | `docker build -t order-example:2.0.0` |
| 8 | 启动应用 | `compose up -d order-example` |
| 9 | 就绪自检 | `curl :9500/health` 轮询（上限 60s） |

### 3.4 验证全链路

```bash
# 1. 健康检查
curl http://localhost:9500/health

# 2. 触发下单（内置示例数据，返回订单号）
curl http://localhost:9500/testOrder
# → 1111

# 3. 核对 MySQL（订单 + 发件箱已发送）
docker exec my-mysql mysql -h127.0.0.1 -uroot -pMySqlXXL123 order_example \
  -e "SELECT COUNT(*) FROM t_order; SELECT status, COUNT(*) FROM outbox_message GROUP BY status;"

# 4. 核对 ES（文档数 +1，最新 orderId）
curl "http://localhost:9200/_cat/indices/order_index?v"
curl -s "http://localhost:9200/order/_search?pretty" -H 'Content-Type: application/json' \
  -d '{"query":{"match_all":{}},"sort":[{"orderId":"desc"}],"size":1}'

# 5. 消息轨迹可视化
open http://localhost:8080   # RocketMQ Dashboard（按 topic=data_sync_event 查看）
```

可选：修改地址触发更新链路（`version` 递增 + ES 同步更新）：

```bash
curl "http://localhost:9500/testChangeAddress?orderId=1111"
```

## 4. 方式二：已有环境接入

> 适用：你已经有 MySQL / Elasticsearch / RocketMQ，**不想**为示例再起一套中间件。

### 4.1 场景说明

已有环境只需做三件事：**初始化数据库 → 初始化 ES 索引 → 改连接配置**。初始化脚本全部是环境无关的（纯 SQL / 纯 curl），可以直接指向你的已有中间件。

### 4.2 单独初始化 MySQL

在已有 MySQL 上执行仓库里的两个幂等脚本（建库建表 + 初始数据）：

```bash
# 用你的 mysql 客户端连接已有实例
mysql -h<你的MySQL地址> -P3306 -uroot -p < docker/mysql/init/01-schema.sql
mysql -h<你的MySQL地址> -P3306 -uroot -p < docker/mysql/init/02-data.sql
```

- 脚本会创建库 `order_example` 及 4 张表（`t_order` / `t_order_item` / `id_segment` / `outbox_message`）
- **幂等**：可重复执行；`02-data.sql` 用 `INSERT IGNORE`，已有 `order` 号段行不会被覆盖
- `id_segment` 的 `order` 初始行**必须存在**，否则应用启动后下单会报错（号段分配查不到渠道）

### 4.3 单独初始化 Elasticsearch

已有 ES 上直接执行建索引脚本，用 `ES_URL` 指向你的实例：

```bash
ES_URL=http://<你的ES地址>:9200 bash docker/es/init/order-es-create-index.sh
```

- 创建 `order_index`（3 分片 / 1 副本）并绑定读写别名 `order`
- **前置要求**：ES 需安装 **IK 分词插件**（版本须与 ES 严格一致，示例为 8.17.0），索引的 `order_default_ik` 分析器依赖它；未安装会报 `analyzer [order_default_ik] not found`
- 幂等：索引已存在时脚本会因 400 冲突失败，属预期行为（可先确认索引已存在后忽略）

### 4.4 初始化 Topic（无需手动）

RocketMQ 的 `data_sync_event` topic **不需要手动创建**：应用启动时框架 `RocketMqEventManager.start()` 自动创建 Consumer 订阅，broker 侧 `autoCreateTopicEnable=true` 自动建 topic。前提：你的 broker 开启了自动建 topic（默认开启）。

### 4.5 连接配置

应用的外部化配置在 `docker/order-example/config/application-dev.properties`（容器挂载 `/app/config`，覆盖 jar 内默认值）。已有环境需修改以下连接项：

| 配置项 | 一键启动值（compose 服务名） | 已有环境改为 |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mysql://mysql:3306/order_example` | 你的 MySQL 地址 |
| `spring.datasource.password` | `MySqlXXL123` | 你的密码 |
| `elasticsearch.hosts` | `http://elasticsearch:9200` | 你的 ES 地址 |
| `rocketmq.name-server` | `rmqnamesrv:9876` | 你的 NameServer 地址 |

其余配置（连接池、超时、重试）为框架推荐值，一般无需改动。

> **密码安全建议**：`rocketmq.name-server` 支持环境变量注入（`ROCKETMQ_NAMESRV`）；MySQL 密码目前示例为明文，接入生产环境前请修改并考虑用环境变量替换。

### 4.6 运行应用

**方式 A：本地 JVM 直接跑**（最快，适合已有中间件映射在本机端口）

```bash
mvn -pl examples/order-example -am spring-boot:run -Dspring-boot.run.profiles=dev
```

- 使用 `src/main/resources/application.properties`（默认 `localhost` 地址）；若中间件不在本机，按 [4.5](#45-连接配置) 改配置
- **注意**：本机 JVM 直连 RocketMQ 时，broker 注册地址（`broker.conf` 的 `brokerIP1`）必须是本机可达的地址——compose 里的 `rmqbroker` 服务名仅在容器网络内有效，本机直连需改为宿主机 IP 或 `127.0.0.1`

**方式 B：构建镜像跑**（与一键启动相同的镜像，但不依赖 compose 中间件）

```bash
mvn -pl examples/order-example -am clean package -DskipTests
docker build -t order-example:2.0.0 -f examples/order-example/target/Dockerfile examples/order-example/target
docker run -d --name my-order-example -p 9500:9500 \
  -e SPRING_PROFILES_ACTIVE=dev \
  -v <你的配置目录>:/app/config \
  order-example:2.0.0
```

`<你的配置目录>` 里放修改后的 `application-dev.properties`（见 [4.5](#45-连接配置)）。容器需能访问你的中间件地址（网络互通即可，不要求在同一 compose 网络）。

## 5. docker 目录结构

```
docker/
├── docker-compose.yml            # 8 服务编排（中间件 + 应用）
├── setup.sh                      # 一键初始化（幂等）
├── Dockerfile / build.sh         # 应用镜像构建（mvn package 后自动复制到 target/）
├── .gitignore                    # 运行期数据与日志（禁止提交）
├── mysql/
│   ├── init/                     # ★ 建库建表脚本（01-schema.sql、02-data.sql）
│   ├── data/ logs/ conf/         # 运行期数据（不入库）
├── es/
│   ├── init/                     # ★ ES 建索引脚本（order-es-create-index.sh）
│   ├── data/ plugins/            # 运行期数据 + IK 插件（不入库）
├── rocketmq/
│   └── conf/broker.conf          # broker 配置（brokerIP1、autoCreateTopicEnable）
├── redis/ data/                  # 运行期数据（不入库）
├── qdrant/ storage/              # 运行期数据（不入库）
└── order-example/
    └── config/application-dev.properties   # 应用外部化配置（容器挂载 /app/config）
```

## 6. 常见问题 FAQ

**Q1：`setup.sh` 报"IK 插件未安装"怎么办？**

两种方式（二选一）：
- 在线安装：`docker exec -it my-es bin/elasticsearch-plugin install https://github.com/infinilabs/analysis-ik/releases/download/v8.17.0/elasticsearch-analysis-ik-8.17.0.zip && docker restart my-es`
- 离线放置：下载对应版本插件包，解压到 `docker/es/plugins/ik` 后 `docker restart my-es`

**Q2：想修改 MySQL 密码？**

需要三处联动：① `docker-compose.yml` 的 `MYSQL_ROOT_PASSWORD`；② `application-dev.properties` 的 `spring.datasource.password`；③ 已初始化的数据卷不会自动更新密码（MySQL 仅在首次初始化时读取该变量），需手动 `ALTER USER` 或删除 `mysql/data/` 后重新初始化。

**Q3：本地 JVM 运行连不上 RocketMQ（超时/找不到 broker）？**

检查 `broker.conf` 的 `brokerIP1`：compose 内用 `rmqbroker` 服务名；本机直连需改为宿主机局域网 IP 或 `127.0.0.1`，改后 `docker restart rmqbroker`。

**Q4：端口被占用导致容器起不来？**

先确认占用方：`lsof -i :9500`（应用）、`:3306`（MySQL）等。若本机已有同端口服务，二选一：停掉冲突服务，或修改 `docker-compose.yml` 的端口映射（注意 `application-dev.properties` 连接地址也要同步改）。

**Q5：想重置数据重新体验？**

```bash
cd examples/order-example/docker
docker compose down
# 删除数据目录（保留 init 脚本与配置）
rm -rf mysql/data es/data rocketmq/broker/store redis/data
docker compose up -d
# 幂等脚本会自动重建 schema；ES 索引缺失时再执行 setup.sh 第 6 步
bash setup.sh
```

**Q6：`setup.sh` 构建镜像很慢？**

首次构建需下载基础镜像与依赖；之后复用缓存。也可先 `docker pull eclipse-temurin:17-jre` 预热。

## 7. 下一步

- **跑通后看代码**：从 `Order` 聚合根（`domain/order/model`）→ 应用服务（`application/order/service`）→ 投影链路（`infrastructure/order/projection`）逐层阅读
- **接入你自己的项目**：复用本仓库的中间件 compose 与 `Dockerfile` / `build.sh` / `setup.sh` 模板（见 [docker 目录结构](#5-docker-目录结构) 与 [4.5 连接配置](#45-连接配置)），业务代码改为引用 `pragmatic-ddd-bom` 依赖
- **查阅框架文档**：VitePress 文档站（`documentation/`），入口 `documentation/index.md`
