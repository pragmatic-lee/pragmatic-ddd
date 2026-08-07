# pragmatic-ddd-rocketmq 测试运行指南

## 目录

- [1. 概述](#1-概述)
- [2. 测试分层与默认行为](#2-测试分层与默认行为)
- [3. 运行集成测试前的环境准备](#3-运行集成测试前的环境准备)
  - [3.1 启动 RocketMQ（4.x / 5.x）](#31-启动-rocketmq4x--5x)
  - [3.2 预建测试 Topic](#32-预建测试-topic)
- [4. 需要注入的配置项](#4-需要注入的配置项)
- [5. 运行命令速查](#5-运行命令速查)
- [6. 常见问题](#6-常见问题)

## 1. 概述

本模块包含两类测试：

- **纯单元测试（`@Tag("unit")`）**：不依赖外部 RocketMQ，默认随 `mvn test` 运行。
- **集成测试（`@Tag("integration")`）**：需要本地或远程已就绪的 RocketMQ（4.x 或 5.x），并由 `@Tag("rocketmq-4x")` / `@Tag("rocketmq-5x")` 区分版本。

集成测试**不会自动创建或删除 Topic**，也不会自动拉起 RocketMQ。运行前必须保证环境就绪且 Topic 已预建（详见下文）。若环境不可用，测试会通过 JUnit `Assumptions` 自动跳过，不会导致构建失败。

## 2. 测试分层与默认行为

模块 `pom.xml` 已为 Surefire 配置 `excludedGroups=integration`，因此：

```bash
mvn test                 # 仅运行纯单元测试，无需任何外部依赖
```

集成测试需要显式通过 `-Dgroups=integration` 开启（见第 5 章）。

## 3. 运行集成测试前的环境准备

### 3.1 启动 RocketMQ（4.x / 5.x）

本模块支持两个大版本，二者可并存，按需准备其一或两者：

- **4.x（Remoting 协议，对应 `rocketmq-4x`）**
  - 需启动 `NameServer`（默认端口 `9876`）+ `Broker`。
  - 可用镜像：`apache/rocketmq:5.x` 的 `namesrv` / `broker` 容器，或 `rocketmqinc/rocketmq`。
- **5.x（gRPC Proxy 协议，对应 `rocketmq-5x`）**
  - 需启动 `Proxy`（默认端口 `8081`，gRPC）+ 其背后的 `NameServer` / `Broker`。
  - 可用镜像：`apache/rocketmq:5.x` 的 `proxy` 容器。

docker-compose 片段（4.x 与 5.x 并存示例）：

```yaml
services:
  namesrv-4x:
    image: apache/rocketmq:5.3.0
    command: ["sh", "mqnamesrv"]
    ports: ["9876:9876"]

  broker-4x:
    image: apache/rocketmq:5.3.0
    command: ["sh", "mqbroker", "-n", "namesrv-4x:9876", "-c", "/home/rocketmq/conf/broker.conf"]
    depends_on: [namesrv-4x]
    ports: ["10911:10911", "10909:10909"]

  namesrv-5x:
    image: apache/rocketmq:5.3.0
    command: ["sh", "mqnamesrv"]
    ports: ["9877:9876"]

  proxy-5x:
    image: apache/rocketmq:5.3.0
    command: ["sh", "mqproxy", "-n", "namesrv-5x:9876"]
    depends_on: [namesrv-5x]
    ports: ["8081:8081"]
```

> 说明：示例端口可按实际调整；若连接远程/共享 RocketMQ，只需保证对方提供可达的 NameServer / Proxy 地址及对应 Topic 权限即可。

### 3.2 预建测试 Topic

测试代码不创建 Topic，请在运行集成测试前显式预建以下 Topic（以 4.x `mqadmin` 为例，5.x 同理使用对应 Proxy 地址与管理工具）：

```bash
# 4.x 通用命令（其余 topic 依次替换 -t 参数）
mqadmin updateTopic -n localhost:9876 -t <TOPIC> -c DefaultCluster
```

实际被测试代码引用的 Topic 清单（来自各测试类的常量定义）：

| Topic 名称 | 用途 | 涉及测试 |
| --- | --- | --- |
| `pdd_ddd_default_topic` | 全局默认 / 共享 topic（共享、条件、延时、重试、顺序链、Stub 单测） | `RocketMqDomainEventManagerTest` 其余用例、`RocketMqDomainEventOrderedManagerTest`、`LocalStubEventManagerTest` |
| `pdd_ddd_classname_topic` | 以类名作为 topic 的发布订阅 | `RocketMqDomainEventManagerTest#topicUseClassNameTest`、`RocketMqGrpcEventManagerTest`、`ConsumerTriggerCheckTest` |
| `pdd_ddd_smoke_topic` | 原生收发连通冒烟 | `SimpleRocketMqSendConsumeTest` |
| `pdd_ddd_single_topic` | 路由模式 A：所有 Event 共用同一底层 topic | `RocketMqDomainEventManagerTest#route_allEventsToSingleTopic` |
| `pdd_ddd_event_a_topic` | 路由模式 B：按事件类型分 topic（事件 A） | `RocketMqDomainEventManagerTest#route_differentEventsToDifferentTopics` |
| `pdd_ddd_event_b_topic` | 路由模式 B：按事件类型分 topic（事件 B） | `RocketMqDomainEventManagerTest#route_differentEventsToDifferentTopics` |
| `pdd_ddd_sub_a_topic` | 路由模式 C：按订阅者分 topic（可选） | `RocketMqDomainEventManagerTest` 模式 C 用例 |

一键创建脚本（4.x 本地默认地址）：

```bash
for t in pdd_ddd_default_topic pdd_ddd_classname_topic pdd_ddd_smoke_topic \
         pdd_ddd_single_topic pdd_ddd_event_a_topic pdd_ddd_event_b_topic pdd_ddd_sub_a_topic; do
  mqadmin updateTopic -n localhost:9876 -t "$t" -c DefaultCluster
done
```

## 4. 需要注入的配置项

集成测试通过系统属性（`-D`）注入连接信息，由 `RocketMqTestSupport` 读取，**不硬编码 localhost**：

| 配置项 | 系统属性 | 默认值 | 说明 |
| --- | --- | --- | --- |
| 4.x NameServer 地址 | `rocketmq.name-server` | `localhost:9876` | 4.x 集成测试连接地址 |
| 5.x gRPC Proxy 地址 | `rocketmq.proxy-addr` | `localhost:8081` | 5.x 集成测试连接地址 |
| 是否跳过 4.x 集成 | `rocketmq.skip-4x` | `false` | `true` 时即使可用也跳过 4.x 集成 |
| 是否跳过 5.x 集成 | `rocketmq.skip-5x` | `false` | `true` 时即使可用也跳过 5.x 集成 |

> `RocketMqTestSupport.is4xAvailable()` / `is5xAvailable()` 内部先用「是否显式 skip」判断，再做短超时连接探测；连接不上自动跳过，保证构建成功。

## 5. 运行命令速查

```bash
# 只跑纯单元（CI 默认，无需外部依赖）
mvn test

# 跑 4.x 集成
mvn test -Dgroups=integration,rocketmq-4x -Drocketmq.name-server=host:9876

# 跑 5.x 集成
mvn test -Dgroups=integration,rocketmq-5x -Drocketmq.proxy-addr=host:8081

# 全量（本地已就绪双版本）
mvn test -Dgroups=integration

# 仅某一类
mvn test -Dtest=RocketMqDomainEventManagerTest
```

## 6. 常见问题

- **集成测试全部被跳过？** 检查 RocketMQ 是否启动、地址是否正确、端口是否可达；或确认是否误设了 `rocketmq.skip-4x` / `rocketmq.skip-5x`。
- **报 Topic 不存在？** 集成测试不会自动建 Topic，请按第 3.2 节预建全部 Topic（尤其 `pdd_ddd_` 前缀系列）。
- **纯单测失败？** 纯单测不依赖外部，若失败通常为代码或依赖问题，与 RocketMQ 环境无关。
