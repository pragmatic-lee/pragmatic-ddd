# Spring Boot Starter 集成

`pragmatic-ddd-spring-boot` 把「每个 Spring 项目都要手抄一遍的框架胶水」收敛为一次性自动装配：事务抽象、配置源、事件总线、Outbox 链路、号段 ID、补偿装饰、就绪后启动，引依赖 + 写配置即可就位；任何一环不想要，声明同类型 Bean 或关掉开关即可精确停止。

::: info 文档口径
本文所有结论以 `io.pragmatic.ddd.springboot` 包下的实际代码为准。核心模块（`core` / `mybatis` / `rocketmq` / `kafka`）继续保持零 Spring 依赖，Spring 相关代码只出现在本模块。
:::

## 1. 定位与边界

### 1.1 它替你做了什么

| 类别 | 内容 | 上提前（使用方手写） | 上提后 |
| --- | --- | --- | --- |
| 桥接适配 | `TransactionOperations` ↔ Spring 事务、`IConfigurationSource` ↔ `Environment`、Outbox / 号段语句执行器 ↔ `SqlSessionTemplate` | 3 个 `Spring*` 类 + 逐键搬运的配置源 | 由 starter 提供（补偿语句执行器未上提，见 §9.4） |
| 装配流程 | 配置源、事务、事件总线、Outbox 七个 Bean、号段 ID、补偿装饰器 | 100+ 行 `@Bean` 方法 | 8 个 AutoConfiguration |
| 启动时机 | 应用就绪后再拉起事件收发与兜底轮询 | 多个 `ApplicationRunner` | 1 个 `DddStartupLifecycle` |

### 1.2 模块依赖与 optional 矩阵

starter 对集成模块全部使用 `<optional>true</optional>`，靠 classpath 上有没有对应类来决定是否装配：

| starter 的依赖 | 声明方式 | 缺失时的后果 |
| --- | --- | --- |
| `pragmatic-ddd-core` | compile（必选） | — |
| `spring-boot-autoconfigure` | compile | — |
| `org.springframework:spring-tx` | optional | 无 `PlatformTransactionManager` 则事务链不装配（通常由 `spring-boot-starter-jdbc` 带入） |
| `pragmatic-ddd-mybatis` / `mybatis-spring` | optional | Outbox、号段 ID 两块装配整块跳过 |
| `pragmatic-ddd-rocketmq` | optional | `event.bus=rocketmq` 时事件总线不装配 |
| `pragmatic-ddd-kafka` | optional | `event.bus=kafka` 时事件总线不装配 |

::: warning optional 不传递
optional 依赖不会随 starter 传递给使用方。要用 Outbox / 号段 ID，必须**自己显式引入** `pragmatic-ddd-mybatis`（以及 `mybatis-spring` 或 `mybatis-spring-boot-starter`）；要用某种 MQ，必须显式引入 `pragmatic-ddd-rocketmq` 或 `pragmatic-ddd-kafka`。
:::

### 1.3 starter 明确不做的三件事

| 不做 | 原因 | 你要怎么做 |
| --- | --- | --- |
| 不接管 `DataSource` / `SqlSessionFactory` / `Redis` / ES | 属环境决策，官方自动配置已覆盖 | 用官方自动配置，或自建（见 §9.3），只要产出 `PlatformTransactionManager` 与 `SqlSessionTemplate` 即可挂接 |
| 不代劳 `ITopicResolver` | 聚合 → topic 的映射是领域知识 | 自己声明一个 `ITopicResolver` Bean，否则总线整块跳过（见 §9.2） |
| 不扫描 `IHandle` 订阅者、不注册聚合仓储的 TypeHandler | 领域注册是业务动作 | 沿用 `IEventRegistry.registerSubscriber(...)` 与各聚合的 `TypeHandlerContext` |

## 2. 引入方式

版本由 BOM 统一管理（当前 `2.0.0`），框架尚未发布到中央仓库，先按[快速开始](/getting-started/quick-start) 的 §0 本地安装：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.pragmatic.ddd</groupId>
            <artifactId>pragmatic-ddd-bom</artifactId>
            <version>2.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.pragmatic.ddd</groupId>
        <artifactId>pragmatic-ddd-spring-boot</artifactId>
    </dependency>

    <!-- 按需二选一（或都引入后用 event.bus 切换） -->
    <dependency>
        <groupId>io.pragmatic.ddd</groupId>
        <artifactId>pragmatic-ddd-rocketmq</artifactId>
    </dependency>

    <!-- 需要 Outbox / 号段 ID 时引入 -->
    <dependency>
        <groupId>io.pragmatic.ddd</groupId>
        <artifactId>pragmatic-ddd-mybatis</artifactId>
    </dependency>
</dependencies>
```

版本兼容口径：starter 自身按 Spring Boot `3.2.4` 编译（通过 `spring-boot-dependencies` 以 import 方式管理），**只声明下限、不向上锁死**使用方的 Spring Boot 版本；`mybatis-spring` 按 `3.0.4` 编译。

::: info 与示例的关系
`examples/order-example` 尚未迁移到 starter，仍以手写 `@Configuration` 的方式演示全链路。两者能力等价：示例可用于理解每一环在装配什么，本文用于实际接入。本文的开关、条件与 Bean 清单均以 `pragmatic-ddd-spring-boot` 源码为准。
:::

## 3. 装配链路总览

8 个自动配置类经 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 注册，语义上构成一条链：

```text
DddConfigurationAutoConfiguration        IConfigurationSource（读 Spring Environment）
        │
        ▼
DddTransactionAutoConfiguration          TransactionOperations（桥接 PlatformTransactionManager）
        │            ┌───────────────────┘
        ▼            ▼
DddRocketMq / DddKafkaAutoConfiguration  IEventSerializer + IEventManager（event.bus 二选一）
        │
        ▼
DddOutboxAutoConfiguration               执行器 / 存储 / 线程池 / 推送器 / 轮询器 / 命令执行器 / 工作单元工厂
DddIdGeneratorAutoConfiguration          号段执行器 / 分配器 / 注册中心
        │
        ▼
DddCompensationAutoConfiguration         把 OutboxCommandExecutor 装饰为可补偿执行器
        │
        ▼
DddLifecycleAutoConfiguration            容器就绪后按序 start()
```

其中 `DddOutboxAutoConfiguration` 与 `DddIdGeneratorAutoConfiguration` 显式声明 `after = DddTransactionAutoConfiguration`。

## 4. 生效条件矩阵（排查首选）

| 自动配置 | classpath 条件 | 容器条件 | 属性条件 | 产出 Bean（可覆盖性见 §6） |
| --- | --- | --- | --- | --- |
| `DddConfigurationAutoConfiguration` | 无 | 无 | 无 | `IConfigurationSource` |
| `DddTransactionAutoConfiguration` | `TransactionOperations`、`PlatformTransactionManager` | 存在 `PlatformTransactionManager` | 无 | `TransactionOperations` |
| `DddRocketMqAutoConfiguration` | `RocketMqEventManager`、`IEventManager` | 存在 `ITopicResolver` | `event.bus=rocketmq`，**缺省即生效** | `RocketMqProperties`、`IEventSerializer`、`IEventManager` |
| `DddKafkaAutoConfiguration` | `KafkaEventManager`、`IEventManager` | 存在 `ITopicResolver` | `event.bus=kafka`（**必须显式**） | `KafkaProperties`、`IEventSerializer`、`IEventManager` |
| `DddOutboxAutoConfiguration` | `MybatisOutboxStore`、`SqlSessionTemplate` | 同时存在 `SqlSessionTemplate`、`TransactionOperations`、`IEventSerializer`、`IEventManager` | `pragmatic-ddd.outbox.enabled`，缺省生效 | `IOutboxStatementExecutor`、`IOutboxStore`、两个线程池、`EagerOutboxPublisher`、`OutboxRelay`、`OutboxCommandExecutor`、`Supplier<IUnitOfWork>` |
| `DddIdGeneratorAutoConfiguration` | `DbSegmentAllocator`、`SqlSessionTemplate` | 存在 `SqlSessionTemplate`、`TransactionOperations` | 无（**没有开关**） | `IIdSegmentStatementExecutor`、`IIdSegmentAllocator`、`IdGeneratorRegistry` |
| `DddCompensationAutoConfiguration` | 无 | 存在 `OutboxCommandExecutor` 且至少一个 `ICompensableAction` | `pragmatic-ddd.compensation.enabled`，缺省生效 | `CompensationActionRegistry`、`CompensableCommandExecutor` |
| `DddLifecycleAutoConfiguration` | 无 | 无（两类协作者均可缺位） | 无 | `DddStartupLifecycle` |

::: tip 条件缺一会怎样
`@ConditionalOnBean` 不满足时**整块静默跳过，不打印任何告警**。若应用启动后找不到 `OutboxCommandExecutor`，按本表从下往上回溯到第一个不满足的条件，再配合 `--debug` 查看自动配置评估报告定位。
:::

## 5. 你必须提供的 Bean（注入槽位）

starter 只负责「把框架组件接进容器」，以下是它预留、由业务填充的位置：

### 5.1 `ITopicResolver`：事件主题路由

```java
// io.pragmatic.ddd.event.internal.defaults.ConfigurableTopicResolver
@Bean
public ITopicResolver orderTopicResolver() {
    return ConfigurableTopicResolver.builder()
            .globalDefaultTopic("data_sync_event")
            .build();
}
```

主题路由与传输层（RocketMQ / Kafka）无关，一个应用一份即可，可被两种总线共用。

### 5.2 号段渠道：`IdGeneratorDefinition`

`IdGeneratorRegistry` 由自动配置产出骨架，业务渠道靠声明 definition Bean 注册，无需再手搓 registry：

```java
@Bean
public IdGeneratorDefinition orderIdGeneratorDefinition() {
    return new IdGeneratorDefinition("order", 1L, 1000, IdType.LONG, null, "订单号");
}
```

::: warning 与不使用 starter 时的差异
手工装配路线往往直接声明一个 `IdGeneratorRegistry` Bean。一旦声明，自动配置的 `@ConditionalOnMissingBean(IdGeneratorRegistry.class)` 即失效，此时**所有** `IdGeneratorDefinition` Bean 都不会被注册——二选一，别混用。
:::

### 5.3 补偿动作：`ICompensableAction`

动作本身（如库存预占的逆操作）由业务提供并声明为 Bean，装配点收敛在 starter，领域层与应用服务不感知补偿。

### 5.4 持久化底座

`PlatformTransactionManager` 与 `SqlSessionTemplate` 由官方自动配置或自建配置提供（见 §9.3），并按 §9.1 把框架 Mapper XML 纳入扫描。

## 6. 覆盖与关闭的三个粒度

### 6.1 单点覆盖：声明同类型 Bean

绝大多数 Bean 带 `@ConditionalOnMissingBean`，声明同类型 Bean 即可精确替换（如换一个 `IEventSerializer` 实现、换一套 `IOutboxStore`）：

```java
@Bean
public IEventSerializer eventSerializer() {
    return new CustomEventSerializer();
}
```

### 6.2 属性开关：关掉整块

| 开关 | 默认 | 关掉后的连带影响 |
| --- | --- | --- |
| `pragmatic-ddd.outbox.enabled=false` | true | Outbox 整块不装配，补偿链路因缺 `OutboxCommandExecutor` 一并不装配 |
| `pragmatic-ddd.compensation.enabled=false` | true | 补偿装饰器不装配，应用侧需改为注入 `OutboxCommandExecutor` |
| `event.bus=kafka` / `rocketmq` | 缺省 rocketmq | 两条总线装配互斥，容器内永远只有一个 `IEventManager` |

::: warning 没有开关的两处
`DddIdGeneratorAutoConfiguration` 无 `enabled` 属性；outbox 的两个线程池（`dddOutboxPublisherExecutor`、`dddOutboxRelayScheduler`）与 `Supplier<IUnitOfWork>` 工厂（`outboxUnitOfWorkFactory`）**未标 `@ConditionalOnMissingBean`**，无法单点替换，只能通过 `pragmatic-ddd.outbox.enabled=false` 关掉整块后自行装配。
:::

### 6.3 排除整类：`spring.autoconfigure.exclude`

需要彻底摘掉某个自动配置（例如有意与官方自动配置共存）时，用 Spring Boot 标准机制按类名排除：

```properties
spring.autoconfigure.exclude=io.pragmatic.ddd.springboot.outbox.DddOutboxAutoConfiguration
```

## 7. 配置项一览

完整清单见[配置项参考](/reference/configuration)，此处只标口径：

| 键 / 前缀 | 绑定方式 | 备注 |
| --- | --- | --- |
| `event.bus` | `@ConditionalOnProperty` | 取值 `rocketmq`（缺省生效）/ `kafka`（需显式）；**无 `pragmatic-ddd.` 前缀** |
| `pragmatic-ddd.outbox.*` | 官方 `@ConfigurationProperties` | `enabled`、`publisher-pool-size`（4）、`poll-interval`（5m）、`grace`（30s）、`batch-size`（200）、`max-attempts`（10）；Duration 支持 ISO-8601 与简单写法（`30s` / `5m`） |
| `pragmatic-ddd.compensation.enabled` | `@ConditionalOnProperty` | 缺省 true |
| `pragmatic-ddd.compensation.mode` | `@Value`，默认 `in-memory` | `durable` 且容器中存在 `ICompensationLog` Bean 时才走持久化模式 |
| `rocketmq.*` | 框架 `ConfigurationBinder` | `name-server`、`proxy-addr`、`producer-group`、`consumer-group`、`retry-times-when-send-failed`、`send-msg-timeout`、`compress-msg-body-over-howmuch`、`default-delay-level`、`max-reconsume-times`；**无 `pragmatic-ddd.` 前缀** |
| `kafka.*` | 框架 `ConfigurationBinder` | 缺省回退 `bootstrap-servers=127.0.0.1:9092`、`group=pragmatic_ddd_consumer`；**无 `pragmatic-ddd.` 前缀** |

::: danger 两套绑定规则并存
`pragmatic-ddd.outbox.*` 走 Spring 官方绑定，支持 relaxed binding；`rocketmq.*` / `kafka.*` 走框架的 `ConfigurationBinder`，**只做 kebab-case 精确匹配**——必须写 `rocketmq.name-server`，写成 `rocketmq.nameServer` 读不到。
:::

## 8. 生命周期：就绪后启动

`DddStartupLifecycle` 实现 `SmartLifecycle`，`getPhase()` 取 `Integer.MAX_VALUE - 1024`，保证在所有普通单例初始化完成后才启动：

```text
start(): IEventManager.start()  →  OutboxRelay.start()   （先具备收发能力，再开兜底轮询）
stop():  仅置 running=false
关闭：    交由各 Bean 的 destroyMethod（IEventManager#shutdown、线程池#shutdown）
```

两类协作者用 `ObjectProvider` 注入，缺一也能正常工作（只用本地事件管理器、或完全不启用 Outbox 的场景）。若需要更早启动或自定义停止顺序，声明自己的 `DddStartupLifecycle` Bean 覆盖即可（见 §6.1）。

## 9. 关键机制与避坑指南

### 9.1 框架 Mapper XML 必须显式纳入扫描路径

`pragmatic-ddd-mybatis` 的 XML 位于 `io/pragmatic/ddd/mybatis/**`，而官方 `mybatis-spring-boot-starter` 默认只扫 `classpath*:mapper/**/*.xml`。starter **不代为追加**，因此必须自己配上，否则会出现「`IOutboxStore` 装配成功、运行期报找不到语句」的延迟故障：

```properties
mybatis.mapper-locations=classpath*:mapper/**/*.xml,classpath*:io/pragmatic/ddd/mybatis/outbox/*.xml,classpath*:io/pragmatic/ddd/mybatis/id/*.xml
```

走自建 `SqlSessionFactory` 路线（见 [MySQL 配置设计原则 §2.3](/best-practices/mysql-config)）时，同样要用 `setMapperLocations` 或 `mybatis-config.xml` 把框架 XML 显式列入。

框架现有三份 XML：`outbox/OutboxMapper.xml`、`id/IdSegmentMapper.xml`、`compensation/CompensationMapper.xml`（仅持久化补偿需要，见 §9.4）。

### 9.2 没有 `ITopicResolver` 就没有事件总线

`ITopicResolver` 是总线装配的硬前置，缺失时 RocketMQ / Kafka 装配整块跳过，且**无日志提示**。表现是：应用能正常启动，但注入 `IEventManager` 失败、Outbox 链路也连带不装配。

### 9.3 事务管理器来源不限官方

`DddTransactionAutoConfiguration` 只要求容器里有 `PlatformTransactionManager`。自建 `DataSourceTransactionManager`（如为掌控 Hikari / `SqlSessionFactory` 而显式装配数据源）同样能挂接，此时它 `after` 的是官方 `DataSourceTransactionManagerAutoConfiguration`，不影响自建配置的生效。

### 9.4 durable 补偿需要自己装配 `ICompensationLog`

starter 的 bridge 层只上提了事务、配置源、Outbox 与号段三个执行器，**没有**补偿语句执行器。要启用持久化补偿，需自行装配 `MybatisCompensationLog`（依赖 `ICompensationStatementExecutor` 与 `TransactionOperations`）并声明为 Bean，同时把 `pragmatic-ddd.compensation.mode` 设为 `durable`。缺一，模式静默回落到 `in-memory`。

### 9.5 只用本地事件管理器时 Outbox 不装配

`ThreadPoolEventManager`（本地总线）不在 starter 的装配范围内。若 classpath 上没有 RocketMQ / Kafka 模块，需自己声明 `IEventManager` Bean，Outbox 链路才会随之装配。

### 9.6 与「显式声明、不依赖自动配置」路线的关系

[MySQL 配置设计原则](/best-practices/mysql-config) 中「不依赖 `DataSourceAutoConfiguration`，显式声明数据源」的结论**仍然成立**，它约束的是 DataSource / SqlSessionFactory / TypeHandler 这一层。两条路线的分界是：

- **数据源与聚合 TypeHandler**：显式装配（业务专属，starter 不接管）；
- **框架桥接与通用装配链**：交给 starter（抄不出差异，无需重复）。

引 starter 后，[Outbox 链路装配](/best-practices/outbox-config)、[事件管理器装配与选择](/best-practices/event-manager-config)、[RocketMQ](/best-practices/rocketmq-config) / [Kafka 配置设计原则](/best-practices/kafka-config) 转为原理说明与排查参考。

### 9.7 补偿装配存在求值顺序耦合

`DddCompensationAutoConfiguration` 依赖 `@ConditionalOnBean(OutboxCommandExecutor.class)`，但未声明 `after = DddOutboxAutoConfiguration`。自动配置按类名排序时 `compensation` 早于 `outbox`（而 `outbox` 又被 `after` 推到 `transaction` 之后），条件可能在 Outbox 的 Bean 定义注册之前求值，导致补偿装配不生效。在该顺序问题于框架侧收口之前，稳妥做法是**自己声明 `CompensableCommandExecutor` Bean**（同示例的 `OrderCompensationConfig`）：此时补偿块即使被跳过也不影响使用。

## 10. 接入前后对比

以「不使用 starter 的手工装配」为基线，接入后可以删除的内容。仓库内的 `examples/order-example` 目前仍采用手工装配路线，恰好可作为逐行对照读物：

| 手工装配产物 | 归属 | 接入 starter 后 |
| --- | --- | --- |
| `SpringTransactionOperations` | 本地桥接类 | 删除，改用 `io.pragmatic.ddd.springboot.bridge` 版本 |
| `SpringOutboxStatementExecutor` / `SpringIdSegmentStatementExecutor` | 本地桥接类 | 同上 |
| `MapConfigurationSource` 逐键搬运配置 | 配置装配 | 删除，改由 `EnvironmentConfigurationSource` 直读 |
| `OutboxConfig` 中 7 个 `@Bean` | Outbox 装配 | 删除，由 `DddOutboxAutoConfiguration` 产出 |
| `RocketMQConfig` / `KafkaConfig` | 总线装配 | 删除，属性写 `rocketmq.*` / `kafka.*`，用 `event.bus` 切换 |
| `IdGeneratorConfig` 的 registry + allocator 装配 | ID 装配 | 删除，只保留 `IdGeneratorDefinition` Bean |
| 三个 `ApplicationRunner`（就绪后 start） | 生命周期 | 删除，由 `DddStartupLifecycle` 统一接管 |
| `MySqlConfig` / `RedisConfig` / ES 配置 / 聚合 TypeHandler / 主题路由 / 补偿动作 | 环境与领域专属 | **保留不动** |

## 11. 总结速查

| 问题 | 结论 |
| --- | --- |
| starter 里有什么 | 4 个 bridge 适配器 + 8 个 AutoConfiguration + 1 个 `DddOutboxProperties` |
| 怎么确认某一环没装上 | 查 §4 生效条件矩阵，缺条件即整块静默跳过（无告警） |
| 怎么关掉某一环 | 声明同类型 Bean（单点）／ `pragmatic-ddd.*.enabled`（整块）／ `spring.autoconfigure.exclude`（整类） |
| 必须自己给的 Bean | `ITopicResolver`、`IdGeneratorDefinition`、`ICompensableAction`、`TypeHandlerContext`、订阅者注册 |
| 最容易踩的坑 | 框架 Mapper XML 未纳入 `mybatis.mapper-locations`（§9.1）；缺 `ITopicResolver`（§9.2） |
| 补偿执行器注不进来 | 求值顺序耦合，先自己声明 `CompensableCommandExecutor`（§9.7） |
| 配置前缀口径 | `pragmatic-ddd.outbox` / `pragmatic-ddd.compensation` 带前缀；`event.bus` / `rocketmq` / `kafka` 不带 |
