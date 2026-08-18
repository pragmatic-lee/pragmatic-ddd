# 框架概览

> 欢迎使用 Pragmatic DDD！本文档帮助你快速了解框架的设计哲学、模块组成与核心概念层级。

## 1. 这是什么框架

Pragmatic DDD 是一个务实可落地的领域驱动设计（DDD）框架，把 DDD 战术模式（实体、值对象、聚合根、领域事件、仓储、应用服务）做成开箱即用的 Java 基类与接口。框架不追求 CQRS / Event Sourcing 全家桶复杂度，聚焦于核心战术模式的标准化表达，让团队以最小的学习成本把 DDD 真正写进代码；同时作为基础库，框架保持通用、零强依赖，便于其他项目引用并快速集成。

适用场景：

- 需要 DDD 战术建模能力（聚合根、规则校验、领域事件）的 Java 后端项目
- 希望核心领域逻辑与 Spring / MyBatis / MQ 等基础设施解耦的团队
- 需要事务性发件箱（Transactional Outbox）保证事件可靠投递的项目

## 2. 设计哲学

| 原则 | 说明 |
| --- | --- |
| **务实优先** | 聚焦 DDD 战术模式标准化，不追求 CQRS / Event Sourcing 全家桶 |
| **零框架依赖核心** | `pragmatic-ddd-core` 仅依赖 Lombok（provided），不绑定 Spring、MyBatis 或任何消息中间件 |
| **端口-适配器** | 核心定义 SPI 端口，基础设施实现延迟到独立集成包，可自由替换 |
| **开箱即用** | 聚合根基类内聚规则校验、事件收集、操作追踪、版本号，继承即获得全部能力 |

> **核心主张**：领域层只声明业务事实（状态流转、领域事件、业务规则），而把执行驱动（校验时机、持久化、事件分发）交给框架托管——让领域层成为**业务指挥中心**，而非代码执行者。这一理念的完整阐释见 [设计理念](./design-philosophy.md)。

## 3. 模块组成

| 模块 | 状态 | 职责 |
| --- | --- | --- |
| `pragmatic-ddd-core` | ✅ 成熟 | 核心库：实体、聚合根、规则、事件、仓储、应用服务、操作追踪、变更追踪、号段 ID |
| `pragmatic-ddd-mybatis` | ✅ 成熟 | MyBatis 集成：枚举/JSON/集合 TypeHandler、Outbox 落库、号段 ID 生成器 |
| `pragmatic-ddd-rocketmq` | ✅ 成熟 | RocketMQ 事件基础设施（Remoting 4.x + gRPC 5.x 双协议） |
| `pragmatic-ddd-kafka` | ⏳ 规划中 | Kafka 集成（待开发） |
| `pragmatic-ddd-spring-boot` | ⏳ 规划中 | Spring Boot Starter（待开发） |
| `pragmatic-ddd-bom` | ✅ 成熟 | BOM，统一版本管理 |

## 4. 核心概念层级

```
IEntity<T>                    实体标识契约（getEntityId）
   └── AbstractEntity<T>      实体基类：ID、软删、审计、基于 ID 的 equals/hashCode
         └── AggregateRoot<T> 聚合根：+ 规则校验 + 版本号 + 事件收集 + 操作追踪

IValueObject                  值对象标记
   └── ValueObject            可选基类：按 equalityComponents() 结构相等
   └── IEnumValue<T,K>        枚举值对象（替代 Java enum）

IRule<T>                      规则的根契约
   └── EntityRule<T>          规则列表容器（继承并覆写 init()）

IDomainEvent                  领域事件契约
   └── BaseDomainEvent        不可变事件基类（自动 UUID + 时间戳）

IRepository<ID,T>             聚合仓储契约（save / findById / remove）

ICommandExecutor              单聚合根命令执行器
IUnitOfWork                   跨聚合根工作单元

ExternalCall                  防腐层（ACL）固定套路调用器
IConfigurationSource          配置源（Map / Spring / Nacos）
IFeatureToggle                特性开关（OFF / ROLLOUT / ON + 灰度）

IBroadcastMessenger           对外广播发送端口
AggregateMessageEnvelope<P>   广播信封（元数据 + payload）
```

这些类型在一次写请求中的协作关系：

```text
命令执行器（ICommandExecutor / IUnitOfWork）
  ├─ 1. 执行聚合根业务方法  → 修改状态、recordOperation、collectEvent
  ├─ 2. 规则校验           → aggregateRoot.satisfiesRule(entityRule)
  ├─ 3. 持久化            → repository.save(aggregateRoot)
  ├─ 4. 发布事件           → eventManager.publish(domainEvents)
  └─ 5. 清空工作单元        → clearWorkUnitState()
```

即：**聚合根负责收集事实，应用编排器负责按固定模板执行并驱动校验、落库与事件分发**。完整编排细节见 [应用服务](../core/application-service.md)。

## 5. 技术栈要求

- **JDK 17+**：框架大量使用 Java 17 特性（record、sealed、pattern matching、switch 表达式）
- **构建工具**：Maven（通过 BOM 管理版本）
- **可选集成**：MyBatis 3.x、RocketMQ 4.x/5.x、Spring Boot 3.x

## 6. Maven 坐标与 BOM

推荐通过 BOM 一次性管理所有模块版本（以下 `2.0.0` 以当前最新版本为准，请以 BOM 实际发布版本替换）：

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
```

然后按需引入模块（无需写版本号）：

```xml
<!-- 核心库（必选） -->
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-core</artifactId>
</dependency>

<!-- MyBatis 集成（可选） -->
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-mybatis</artifactId>
</dependency>

<!-- RocketMQ 集成（可选） -->
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-rocketmq</artifactId>
</dependency>
```

::: tip 核心模块零框架依赖
`pragmatic-ddd-core` 仅依赖 Lombok（provided scope），不传递任何框架依赖。即使你只用核心库不集成 MyBatis/MQ，也能完成完整的 DDD 战术建模。
:::

下一步：

- [设计理念 →](./design-philosophy.md)：理解框架为什么这样设计领域层
- [快速开始 →](./quick-start.md)：跑通你的第一个聚合根
