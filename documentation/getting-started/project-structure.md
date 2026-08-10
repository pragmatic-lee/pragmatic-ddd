# 推荐项目结构

> 本文档介绍使用 Pragmatic DDD 时的推荐项目分包结构。

## 1. 标准分层架构

```
com.example.order/
├── domain/                 # 领域层（核心业务逻辑）
│   ├── model/              # 聚合根、实体、值对象
│   ├── rule/               # 业务规则
│   ├── event/              # 领域事件
│   ├── operation/          # 操作注册表
│   └── service/            # 领域服务
├── application/            # 应用层（用例编排）
│   ├── command/            # 命令服务（写）
│   ├── query/              # 查询服务（读）
│   ├── factory/            # 实体工厂
│   └── updater/            # 实体更新器
├── infrastructure/         # 基础设施层（技术实现）
│   ├── persistence/        # 仓储实现、Mapper
│   ├── event/              # 事件管理器配置
│   └── outbox/             # Outbox 实现
└── interfaces/             # 接口层（入口）
    ├── rest/               # REST Controller
    └── rpc/                # RPC 入口
```

## 2. 包结构详解

### domain（领域层）

只依赖 `pragmatic-ddd-core`，不依赖任何框架：

```java
// domain/model/Order.java
public class Order extends AggregateRoot<Long> { ... }

// domain/rule/OrderRule.java
public class OrderRule extends EntityRule<Order> { ... }

// domain/event/OrderCancelledEvent.java
public class OrderCancelledEvent extends BaseDomainEvent { ... }

// domain/operation/OrderOperationRegistry.java
public class OrderOperationRegistry extends OperationRegistry { ... }
```

### application（应用层）

编排领域逻辑，依赖 `pragmatic-ddd-core`：

```java
// application/command/OrderCommandService.java
public class OrderCommandService extends AbstractApplicationService
        implements ICommandApplicationService { ... }

// application/query/OrderQueryService.java
public class OrderQueryService implements IQueryApplicationService { ... }
```

### infrastructure（基础设施层）

依赖 `pragmatic-ddd-mybatis` / `pragmatic-ddd-rocketmq`：

```java
// infrastructure/persistence/OrderRepositoryImpl.java
public class OrderRepositoryImpl implements IRepository<Long, Order> { ... }

// infrastructure/event/EventManagerConfig.java
@Configuration
public class EventManagerConfig {
    @Bean
    public IEventManager eventManager() { ... }
}
```

## 3. 多模块项目

大型项目可按限界上下文拆分为多模块：

```
order-service/
├── order-domain/           # 领域层（纯核心库依赖）
├── order-application/      # 应用层
├── order-infrastructure/   # 基础设施层
├── order-interface/        # 接口层
└── order-bootstrap/        # 启动模块（Spring Boot）
```

::: tip 领域层零框架依赖
`order-domain` 模块只依赖 `pragmatic-ddd-core`（仅 Lombok provided），不依赖 Spring / MyBatis / MQ，保证领域逻辑纯净可测。
:::
