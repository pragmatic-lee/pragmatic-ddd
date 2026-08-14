# 推荐项目结构

> 本文介绍使用 Pragmatic DDD 框架时的**推荐项目分包方式**，帮助你把代码落到正确的层与包中。
> 分包以**聚合（Aggregate）**为第一级目录，按四层模型（领域 / 应用 / 基础设施 / 用户接口）组织。

## 1. 概述

### 1.1 这份文档讲什么

当你引入 `pragmatic-ddd` 框架后，本框架要求业务代码按四层模型组织。本文说明每一层**放什么、怎么分包、有哪些使用边界**，让你在新建聚合或模块时有可直接照抄的结构。

### 1.2 四层模型与依赖方向

| 层 | 职责 | 你在该层主要编写的内容                                                                                      | 不要在该层放的东西 |
|----|------|-------------------------------------------------------------------------------------------------------------|--------------------|
| 领域层 Domain | 业务原子零件与契约 | 聚合/实体/值对象、仓储接口、领域事件、规则、投影、领域服务接口、外部依赖**声明**、参数对象                  | SQL/ORM、RPC、MQ 发送、事务、框架配置、协议 DTO、Input |
| 应用服务层 Application | 编排领域逻辑、汇聚依赖 | Input、WriteService/ReadService、Factory/Updater/Resolver、四类原子领域服务**实现**、规则组装、事件订阅登记 | 直接 `new` 基础设施实现、聚合根业务行为、SQL/ORM |
| 基础设施层 Infrastructure | 技术实现，落地领域层契约 | Repository、ProjectionQuery、DAO/Mapper、ACL 网关、配置源                                                   | 业务逻辑、领域规则、状态判定 |
| 用户接口层 UI | 协议适配与参数转换 | Controller、协议 Request/Response、双向转换                                                                 | 业务编排、聚合调用、仓储直连 |

```text
依赖方向：UI → Application → Domain ← Infrastructure
（基础设施层实现领域层定义的接口，即依赖倒置）
```

### 1.3 标准目录结构（总览）

```text
com/yourcompany/{module}/
├── api/{agg}/            # UI：cmd/ query/ dto/（协议 Request / Response）
├── controller/{agg}/     # UI：{Agg}Controller
├── application/{agg}/    # Application
│   ├── {Agg}WriteService.java      # 命令应用服务（外层）
│   ├── {Agg}ReadService.java       # 查询应用服务（外层）
│   ├── input/            # {Action}Input（业务语义入参）
│   ├── service/          # 四类原子领域服务实现（每文件一能力）
│   ├── rule/             # {Agg}RuleAssembler（规则表组装）
│   ├── subscriber/       # {Agg}EventSubscriberRegistry（事件订阅登记）
│   └── factory/ updater/ resolver/
├── domain/{agg}/         # Domain（无 acl/ 包）
│   ├── model/ projection/ materializer/ repository/
│   ├── rule/ event/ dependency/ service/
│   ├── config/ operation/ param/
└── infrastructure/{agg}/ # Infrastructure
    ├── repository/ materializer/ query/ dependency/ config/
```

> ⚠️ **分包维度**：以聚合 `{agg}` 为第一级目录（如 `order/`、`product/`）。不同聚合之间不共享包或实体；聚合间通信优先使用领域事件。

---

## 2. 各层包结构与职责

### 2.1 领域层（Domain）

**路径**：`domain/{agg}/`
**依赖说明**：只依赖 JDK 与框架核心抽象（`pragmatic-ddd-core`），不依赖 Spring / MyBatis / MQ 等具体技术。

#### 2.1.1 分包与契约

| 包 | 你放的内容 | 继承/注解的框架类型 | 说明 |
|----|-----------|---------------------|------|
| `model/` | 聚合根、实体、值对象、`enums/`、`valueobject/` | `AggregateRoot` | 业务数据与行为载体 |
| `repository/` | `I{Aggregate}Repository` | `IRepository<ID,T>` | 写模型持久化契约（仅接口） |
| `projection/` | `{Agg}Projection`（sealed 基类）、具体投影、`I{Agg}Query` | `IAggregateProjection`、`IAggregateQuery` | 读模型视图 + 查询契约 |
| `materializer/` | `I{Agg}{Store}Materializer` | `IProjectionMaterializer` | 异构存储写入契约（`projectionType()`+`target()` 由框架 `ProjectorRegistry` 寻址） |
| `rule/` | `{Agg}EntityRule`、`{Agg}BrokenRuleRegistry` | `EntityRule`、`BrokenRuleRegistry` | 规则表类型（空壳）+ 消息码 |
| `event/` | `{Agg}{Action}Event` | `IDomainEvent` | 领域业务事实（事件定义） |
| `dependency/` | `I{External}Dependency` | `@ExternalDependency` | **仅声明**本聚合依赖哪些外部系统（端口/契约），实现在基础设施层 |
| `service/` | `I{Agg}{Ability}` | `@DomainService` | 领域服务接口，四类（见 2.1.3） |
| `config/` | `{Agg}Configuration` | `AbstractConfiguration` | 语义化配置门面（只读） |
| `operation/` | `{Agg}OperationRegistry` | `OperationRegistry` | 操作驱动建模时暴露（内部 `static final EntityOperation` 常量，非独立子类） |
| `param/` | `{Agg}{Action}Param` | `IParamObject` | 实体构造/业务方法入参 |

#### 2.1.2 包结构示例

```text
domain/{agg}/
├── model/                          # 聚合根 / 实体 / 值对象
│   ├── Order.java                  # 聚合根，承载业务数据与行为
│   ├── OrderItem.java              # 实体
│   ├── enums/OrderStatus.java      # 枚举值对象
│   └── valueobject/                # 值对象
│       ├── Address.java
│       └── Money.java
├── repository/IOrderRepository.java          # 继承 IRepository<ID, T>
├── projection/                     # 读模型视图形态
│   ├── OrderProjection.java         # sealed 基类，实现 IAggregateProjection（含 id+version+子实体）
│   ├── OrderSummaryProjection.java  # permits 于 OrderProjection
│   ├── OrderDetailProjection.java
│   ├── OrderItemProjection.java     # 子实体投影（被聚合根投影包裹，自身不实现 IAggregateProjection）
│   └── IOrderQuery.java             # 继承 IAggregateQuery
├── materializer/                   # 异构存储写入契约（仅接口）
│   ├── IOrderEsMaterializer.java
│   └── IOrderRedisMaterializer.java
├── rule/                           # 规则表类型 + 消息码
│   ├── OrderEntityRule.java        # 继承 EntityRule（空壳）
│   └── OrderBrokenRuleRegistry.java # 继承 BrokenRuleRegistry
├── event/                          # 领域事件（业务事实）
│   ├── OrderCreatedEvent.java
│   └── OrderPayedEvent.java
├── dependency/                     # 外部依赖声明（仅端口，@ExternalDependency 接口）
│   ├── IInventoryDependency.java
│   └── IUserDependency.java
├── service/                        # 领域服务接口（四类，每文件一能力）
│   ├── IOrderIdGenerator.java      # @DomainService(CAPABILITY_PROVIDER)
│   ├── IOrderItemAmountRule.java   # @DomainService(RULE_VALIDATOR)
│   ├── IOrderTotalPriceCalculator.java # @DomainService(ATTRIBUTE_CALCULATOR)
│   └── IOrderCreatedNoticeWarehouse.java # @DomainService(EVENT_SUBSCRIBER)
├── config/OrderConfiguration.java           # 继承 AbstractConfiguration
├── operation/OrderOperationRegistry.java     # 继承 OperationRegistry（static final EntityOperation 常量）
└── param/                          # 参数对象
    ├── OrderInitParam.java
    └── OrderUpdateAddressParam.java
```

#### 2.1.3 领域服务四类（`@DomainService(category=...)`）

接口写在领域层 `service/`，实现写在应用层 `service/`（详见 2.2）。

| 分类 | 含义 | 接口定义位置 | 实现位置 |
|------|------|--------------|----------|
| `CAPABILITY_PROVIDER` | 领域能力供给 | 领域层 `service/` | 应用层 `service/` |
| `RULE_VALIDATOR` | 单条校验规则 | 领域层 `service/` | 应用层 `service/` |
| `ATTRIBUTE_CALCULATOR` | 属性计算 | 领域层 `service/` | 应用层 `service/` |
| `EVENT_SUBSCRIBER` | 事件订阅处理 | 领域层 `service/` | 应用层 `service/` |

#### 2.1.4 使用前须知（领域层）

> ⚠️ **领域层没有 `acl/` 包**：防腐（ACL）实现统一放在基础设施层 `dependency/`；领域层 `dependency/` 只声明外部依赖端口，不要写实现。
>
> ⚠️ **投影要闭合**：子实体/值对象投影必须由聚合根级投影（含 `id` + `version`）包裹；根投影实现 `IAggregateProjection`，子投影不实现。
>
> ⚠️ **`operation/` 可选**：仅在用操作驱动建模时才需要；常规方法驱动建模不引入此包。`EntityOperation` 用注册表常量声明，不要为每个操作建一个子类文件。

#### 2.1.5 代码示例

```java
// domain/order/model/Order.java
public class Order extends AggregateRoot<Long> { ... }

// domain/order/rule/OrderEntityRule.java
public class OrderEntityRule extends EntityRule<Order> { ... }

// domain/order/event/OrderCancelledEvent.java
public class OrderCancelledEvent extends BaseDomainEvent { ... }
```

---

### 2.2 应用服务层（Application）

**路径**：`application/{agg}/`
**依赖说明**：依赖领域层，并通过框架注入基础设施层实现（不要自己 `new` 实现类）。

#### 2.2.1 包结构与职责

| 包/文件 | 内容 | 位置 | 说明 |
|---------|------|------|------|
| `{Agg}WriteService` | 命令应用服务 | `application/{agg}/` 外层 | 实现 `ICommandApplicationService`，一个聚合根一个 |
| `{Agg}ReadService` | 查询应用服务 | `application/{agg}/` 外层 | 实现 `IQueryApplicationService`，返回 Projection |
| `input/` | `{Action}Input` | 子包 | 业务语义入参，与协议无关 |
| `service/` | 四类原子领域服务**实现** | 子包 | 实现领域层 `service/` 接口，每文件一能力 |
| `factory/` | `{Agg}Factory` | 子包 | 创建场景 Input→实体编排（一个聚合根一个） |
| `updater/` | `{Agg}{Action}Updater` | 子包 | 修改场景 Input→实体编排（按动作拆分） |
| `resolver/` | `{Field}Resolver` | 子包 | 单字段计算，实现 `IEntityPropertyResolver` |
| `rule/` | `{Agg}RuleAssembler` | 子包 | 把各 `RULE_VALIDATOR` 串成 `EntityRule` |
| `subscriber/` | `{Agg}EventSubscriberRegistry` | 子包 | 登记本聚合关注的事件与订阅者（引用 `service/` 实现） |

#### 2.2.2 包分工

- `service/`：原子能力零件（CAPABILITY/RULE/ATTRIBUTE/EVENT_SUBSCRIBER），纯原子可复用。
- `rule/`：把 `service/` 的 `RULE_VALIDATOR` 串联进 `EntityRule`，本身不承载单条校验。
- 外部依赖（防腐层）由基础设施层 `dependency/` 统一承载；应用服务/订阅者直接注入基础设施层 ACL 网关做并列编排，网关之间不相互依赖。
- `subscriber/`：把本聚合事件与 `service/` 的 `EVENT_SUBSCRIBER` 处理器登记为清单，不重复实现。

#### 2.2.3 包结构示例

```text
application/{agg}/
├── OrderWriteService.java           # 命令应用服务（外层）
├── OrderReadService.java            # 查询应用服务（外层）
├── input/                           # 业务语义入参
│   ├── CreateOrderInput.java
│   └── CancelOrderInput.java
├── service/                         # 四类原子领域服务实现（每文件一能力）
│   ├── OrderIdGenerator.java        # 实现 IOrderIdGenerator（CAPABILITY_PROVIDER）
│   ├── OrderItemAmountRule.java     # 实现 IOrderItemAmountRule（RULE_VALIDATOR）
│   ├── OrderTotalPriceCalculator.java  # 实现 IOrderTotalPriceCalculator（ATTRIBUTE_CALCULATOR）
│   └── OrderCreatedNoticeWarehouseHandler.java  # 实现 IOrderCreatedNoticeWarehouse（EVENT_SUBSCRIBER）
├── factory/OrderFactory.java
├── updater/OrderCancelUpdater.java
├── resolver/TotalPriceResolver.java
├── rule/OrderRuleAssembler.java     # 串成 OrderEntityRule
└── subscriber/OrderEventSubscriberRegistry.java
```

#### 2.2.4 代码示例

```java
// application/order/OrderWriteService.java
public class OrderWriteService extends AbstractApplicationService
        implements ICommandApplicationService { ... }

// application/order/OrderReadService.java
public class OrderReadService implements IQueryApplicationService { ... }
```

---

### 2.3 基础设施层（Infrastructure）

**路径**：`infrastructure/{agg}/`，包名与领域层定义包**同名镜像**（如领域层 `repository/` ↔ 基础设施层 `repository/`）。
**依赖说明**：引入 `pragmatic-ddd-mybatis` / `pragmatic-ddd-rocketmq` / `pragmatic-ddd-kafka` 等集成包，实现领域层定义的 SPI 接口。

#### 2.3.1 包与契约

| 包 | 对应领域层 | 你实现的契约 | 归属 |
|----|-----------|--------------|------|
| `repository/` | `repository/` | `IRepository` | 持久化（主存储） |
| `materializer/` | `materializer/` | `IProjectionMaterializer`、`IReadModelResynchronizer` | 持久化（异构存储，resync 一并落地） |
| `query/` | `projection/` | `I{Agg}Query` | 持久化（读模型查询） |
| `dependency/` | （外部依赖统一承载） | `ExternalCall`/`Abstract*Gateway`/`Acl*` 异常 | 外部依赖 ACL 执行模板：封装远程调用（纯技术通道） |
| `config/` | `config/` | `IConfigurationSource` | 动态配置 |

#### 2.3.2 框架已提供的能力（你无需自己建包）

- **消息发布**：`IEventPublisher` + 集成包 `RocketMqEventManager` 等；你只需引入集成包并配置即可，不要自建 `event/` 包。
- **读模型对账**：`Reconciler` / `ReconciliationManager` / `ReconciliationRegistry` 随 core 提供；你在 `materializer/` 实现 `IReadModelResynchronizer` 并注册即可，不要自建 `reconciliation/` 包。

#### 2.3.3 包结构示例

```text
infrastructure/{agg}/
├── repository/
│   ├── OrderRepository.java         # 实现 IOrderRepository（不加 Impl 后缀）
│   └── OrderMapper.java             # MyBatis Mapper
├── materializer/
│   ├── OrderEsMaterializer.java     # 实现 IOrderEsMaterializer
│   └── OrderRedisMaterializer.java  # 实现 IOrderRedisMaterializer
├── query/OrderProjectionQuery.java  # 实现 IOrderQuery
├── dependency/                     # 外部依赖 ACL 执行模板（封装远程调用）
│   ├── InventoryGateway.java        # 封装远程调用（防腐层，纯技术通道）
│   └── PaymentGatewayClient.java    # RPC Client
└── config/NacosConfigurationSource.java
```

#### 2.3.4 使用前须知（基础设施层）

> ⚠️ **实现类不加 `Impl` 后缀**：如 `OrderRepository`（而非 `OrderRepositoryImpl`）。
>
> ⚠️ **不要建 `event/`、`reconciliation/` 包**：消息发布与对账由框架提供。
>
> ⚠️ **不要建 `remote/` 等技术类型包**：远程调用统一归入 `dependency/`（ACL 执行模板）。

#### 2.3.5 代码示例

```java
// infrastructure/order/repository/OrderRepository.java
public class OrderRepository implements IRepository<Long, Order> { ... }

// 事件管理器由框架集成包提供，引入 pragmatic-ddd-rocketmq 并配置 RocketMqEventManager 即可
```

---

### 2.4 用户接口层（User Interface）

**路径**：`api/{agg}/` + `controller/{agg}/`

#### 2.4.1 包结构示例

```text
api/{agg}/
├── cmd/{Action}Request.java         # 写操作协议入参
├── query/{Action}Request.java       # 读操作协议入参
└── dto/{Entity}Response.java        # 协议出参
controller/{agg}/
└── {Agg}Controller.java
```

#### 2.4.2 转换边界

> ⚠️ **Request ≠ Input**：Request 在 Controller 转成 Input（入向）。
> **Response ≠ Projection**：Projection 在 Controller 转成 Response（出向）。
> Controller 不得直接调聚合或仓储，必须经应用服务层。

```java
// controller/order/OrderController.java
@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderWriteService writeService;
    private final OrderReadService readService;
    // Request → Input（入向）、Projection → Response（出向）
}
```

---

## 3. 关键机制与避坑指南

### 3.1 依赖倒置（DIP）

领域层定义所有契约接口（`IRepository`、`I{Agg}Query`、`IProjectionMaterializer`、`@ExternalDependency` 端口等）；基础设施层实现这些接口；应用层通过接口注入依赖。依赖方向为 `UI → Application → Domain ← Infrastructure`，领域层不感知任何外层。

### 3.2 防腐层（ACL）怎么放

外部依赖端口**声明**在领域层 `dependency/`（仅 `@ExternalDependency` 接口）；其 ACL 网关**实现**统一在基础设施层 `infrastructure/{agg}/dependency/`（封装远程调用，纯技术通道）。应用层不单列 `dependency/` 包，直接注入基础设施层 ACL 网关使用，网关之间不相互依赖。

### 3.3 读写分离路径

- **写操作**：必经聚合根（由框架 `ICommandExecutor` / `IUnitOfWork` 封装持久化）。
- **读操作**：绕过聚合根，经投影 `projection/` + 基础设施层 `query/` 从异构存储查询。

### 3.4 框架替你做好的事

| 能力 | 由谁提供 | 你需要做的 |
|------|----------|------------|
| 消息发布 `IEventPublisher` | 框架 + 集成包 | 引入并配置，不建 `event/` 包 |
| 读模型对账 `Reconciler` 等 | core | 实现 `IReadModelResynchronizer` 并注册，不建 `reconciliation/` 包 |

> ⚠️ **易错点**：在业务代码里新建 `event/`、`reconciliation/`、`acl/`（领域层）、`remote/` 包，都属于放错位置。消息发布与对账引擎由框架提供，你只需引入/配置或在 `materializer/` 实现 `IReadModelResynchronizer`。

---

## 4. 层间使用边界（速记清单）

1. 领域层不放 SQL/ORM/RPC/MQ/事务/框架配置，且**没有 `acl/` 包**；领域层 `dependency/` 只声明外部依赖端口。
2. UI Controller 不直接连仓储或聚合，必须经应用服务层。
3. 应用层不要 `new` 基础设施实现类，用接口注入（DIP）。
4. Request/Input、Projection/Response 必须分开，转换只在 Controller 发生。
5. 写操作必经聚合根（框架 `ICommandExecutor` / `IUnitOfWork` 封装）。
6. 读操作绕过聚合根，经投影从异构存储查询。
7. 四类原子领域服务实现统一在 `application/{agg}/service/`，每文件一能力。
8. 外部依赖端口声明在领域层 `dependency/`，ACL 网关实现统一在基础设施层 `dependency/`；应用层不单列 `dependency/` 包。
9. `rule/` 只组装规则表，不承载单条校验；`subscriber/` 只登记订阅清单，引用 `service/` 实现，不重复实现。
10. 聚合间通信优先领域事件，不共享包/实体。
11. Event Publisher 与对账引擎由框架提供，业务侧不落地 `event/`、`reconciliation/` 包。
12. 基础设施层实现类不加 `Impl` 后缀；远程调用统一归入 `dependency/`，不建 `remote/`。

---

## 5. 多模块项目（按限界上下文拆分）

大型项目可按限界上下文拆成多个模块，每个模块内部仍遵循上述四层分包：

```text
order-service/
├── order-domain/           # 领域层（只依赖 pragmatic-ddd-core，零框架依赖）
├── order-application/      # 应用层
├── order-infrastructure/   # 基础设施层
├── order-interface/        # 接口层
└── order-bootstrap/        # 启动模块（Spring Boot）
```

> ⚠️ **领域层零框架依赖**：`order-domain` 模块只依赖 `pragmatic-ddd-core`（仅 Lombok provided），不依赖 Spring / MyBatis / MQ，保证领域逻辑纯净可测。

---

## 6. 总结速查

| 层 | 路径 | 你主要写的内容                                                       | 最易犯的错 |
|----|------|----------------------------------------------------------------------|------------|
| 领域 Domain | `domain/{agg}/` | 业务原子零件 + 契约接口                                              | 建了 `acl/` 包；在领域层写 SQL/RPC/MQ；`dependency/` 写了实现 |
| 应用 Application | `application/{agg}/` | Input/WriteService/ReadService、四类服务实现、规则组装、事件订阅登记 | `new` 基础设施实现；在 `rule/`、`subscriber/` 重复实现逻辑 |
| 基础设施 Infrastructure | `infrastructure/{agg}/`（包名同名镜像） | 持久化、查询、ACL 网关、配置源                                       | 实现类加 `Impl`；建 `event/`、`reconciliation/`、`remote/` 包 |
| 用户接口 UI | `api/{agg}/` + `controller/{agg}/` | 协议 Request/Response、双向转换                                      | Controller 直连聚合/仓储；Request 与 Input、Projection 与 Response 混用 |

**新建聚合时你需要落地的清单**
- 领域层：聚合/实体/值对象/枚举、仓储接口、投影、`IAggregateProjection`、领域事件、规则、消息注册表、参数对象、领域服务接口（四类）、外部依赖声明接口（仅端口）。
- 应用层：Input、WriteService、ReadService、Factory、Updater、Resolver、原子领域服务实现、规则表组装、事件订阅登记。
- 基础设施层：Repository、ProjectionQuery、DAO/Mapper、dependency 网关（ACL 执行模板）、materializer 的 `IReadModelResynchronizer`、`IConfigurationSource`。
- 用户接口层：Controller、协议 Request/Response、双向转换。
