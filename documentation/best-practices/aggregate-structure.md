# 聚合目录落地骨架

> 新建聚合的第一步：照本文建立 `{agg}` 的目录骨架，再**按图索骥**到各阶段落地模式文档填充。目录即地图——每个文件夹对应一篇（或多篇）模式文档。模块级四层全貌见 [推荐项目结构](../getting-started/project-structure.md)。

## 1. 本质与定位

本文档把「新建聚合」的第一步模板化：初始化 `domain/{agg}` / `application/{agg}` / `infrastructure/{agg}`（+ `api` / `controller`）目录骨架。它不是单一能力点的模式，而是**聚合全链路的目录地图**——每个文件夹指向对应的落地模式文档，按目录逐个填充即完成聚合落地。

- 适用：在既有模块内新建聚合（如 `order` / `product`），或从零初始化一个模块。
- 不适用：理解框架整体分包与多模块拆分（见 [推荐项目结构](../getting-started/project-structure.md)）。

## 2. 目录骨架（核心）

以聚合 `{agg}` 为第一级目录，四层分包：

```text
{module}/
├── api/{agg}/                 # UI：协议 Request / Response
│   ├── cmd/                   #   命令协议
│   ├── query/                 #   查询协议
│   └── dto/                   #   响应 DTO
├── controller/{agg}/          # UI：{Agg}Controller
├── application/{agg}/         # Application：编排领域逻辑、汇聚依赖
│   ├── input/                 #   {Action}Input 业务语义入参
│   ├── factory/               #   EntityFactory 聚合工厂（先算后赋）
│   ├── updater/               #   EntityUpdater 修改场景编排
│   ├── resolver/              #   Command → 领域输入适配
│   ├── rule/                  #   规则容器组装
│   ├── service/               #   领域服务实现（@Service）
│   └── subscriber/            #   事件订阅登记
├── domain/{agg}/              # Domain：业务原子零件与契约（无 acl/ 包）
│   ├── model/                 #   聚合根 / 实体 / 值对象（含 enums/ valueobject/）
│   ├── event/                 #   领域事件
│   ├── operation/             #   操作注册表
│   ├── param/                 #   参数对象 IParamObject
│   ├── rule/                  #   规则容器 + 规则注册表
│   ├── service/               #   事件订阅 / 校验规则 / 能力供给契约
│   ├── calculator/            #   属性计算契约
│   ├── dependency/            #   外部依赖声明
│   ├── repository/            #   仓储抽象
│   ├── projection/            #   投影契约（含 materializer/ 版本 / 补偿专属契约）
│   └── config/                #   领域配置
└── infrastructure/{agg}/      # Infrastructure：技术实现，落地领域层契约
    ├── repository/            #   仓储实现（MyBatis）
    ├── projection/            #   投影器（含 materializer/ 写读一体源 / 版本 / 对账）
    ├── dependency/            #   外部依赖实现
    ├── service/               #   领域服务实现（依赖基础设施类）
    └── config/                #   @Configuration 装配（MySQL / ES / MQ / Outbox）
```

> ⚠️ **分包维度**：以聚合 `{agg}` 为第一级目录；不同聚合不共享包或实体，聚合间通信走领域事件。

## 3. 按图索骥：目录 → 模式文档

建好目录后，按文件夹逐个填充，每个文件夹对应一篇（或多篇）落地模式：

| 目录 | 放什么 | 去查的模式文档 |
| --- | --- | --- |
| `domain/{agg}/model` | 聚合根、实体、值对象、枚举 | [聚合设计原则](./aggregate-design.md) · [普通实体设计原则](./entity-design.md) · [值对象最佳实践](./value-object.md) · [枚举值对象最佳实践](./enum-value.md) |
| `domain/{agg}/event` | 领域事件类 | [事件建模指南](./event-modeling.md) |
| `domain/{agg}/operation` | 操作注册表 | [操作注册表设计](./operation-registry-design.md) |
| `domain/{agg}/param` | 参数对象 | [聚合设计原则](./aggregate-design.md) §3.4 |
| `domain/{agg}/rule` | 规则容器 + 规则注册表 | [规则注册表设计](./registry-design.md) · [聚合业务规则（OrderRule 范式）](./order-rule-pattern.md) |
| `domain/{agg}/service` + `calculator` | 四类领域服务契约 | [领域服务落地模式](./domain-service.md) |
| `domain/{agg}/dependency` | 外部依赖声明 | [核心：依赖体系](../core/dependency.md) |
| `domain/{agg}/repository` | 仓储抽象 | [仓储设计原则](./repository-design.md) |
| `domain/{agg}/projection` | 投影契约 | [投影读模型代码落地指南](./projection-design.md) |
| `application/{agg}/input` | 业务语义入参 | [应用层落地模式](./application-collaboration.md) |
| `application/{agg}/factory` + `updater` + `resolver` | 聚合装配 / 修改 / 适配 | [领域服务落地模式](./domain-service.md) · [应用层落地模式](./application-collaboration.md) |
| `application/{agg}/service` | 领域服务实现 | [领域服务落地模式](./domain-service.md) |
| `application/{agg}/subscriber` | 事件订阅登记 | [投影读模型代码落地指南](./projection-design.md) · [RocketMQ 配置设计原则](./rocketmq-config.md) |
| `infrastructure/{agg}/repository` | 仓储实现 | [仓储设计原则](./repository-design.md) · [MySQL 配置设计原则](./mysql-config.md) |
| `infrastructure/{agg}/projection` | 投影器 / 物化器 | [投影读模型代码落地指南](./projection-design.md) · [Elasticsearch 配置设计原则](./elasticsearch-config.md) |
| `infrastructure/{agg}/config` | Bean 装配 | [MySQL](./mysql-config.md) · [Elasticsearch](./elasticsearch-config.md) · [RocketMQ](./rocketmq-config.md) · [Outbox](./outbox-config.md) |

## 4. 四层职责速记

| 层 | 职责 | 不要放 |
| --- | --- | --- |
| 领域层 Domain | 业务原子零件与契约 | SQL/ORM、RPC、MQ 发送、事务、Input |
| 应用服务层 Application | 编排领域逻辑、汇聚依赖 | 直接 `new` 基础设施实现、聚合根业务行为 |
| 基础设施层 Infrastructure | 技术实现，落地领域层契约 | 业务逻辑、领域规则、状态判定 |
| 用户接口层 UI | 协议适配与参数转换 | 业务编排、聚合调用、仓储直连 |

## 5. 关键机制与避坑

- **依赖方向**：`UI → Application → Domain ← Infrastructure`（基础设施实现领域层接口，依赖倒置）。
- **领域层零基础设施依赖**：`domain/{agg}` 下不出现 Spring / MyBatis / MQ 导入；依赖外部能力用 `dependency` 声明 + 领域服务契约，由应用 / 基础设施层实现。
- **聚合间不共享**：不同聚合目录独立，通信优先领域事件，不跨聚合直接 new 仓储 / 服务。
- **目录名即语义**：`service` 只放领域服务契约（领域层）或实现（应用 / 基础设施层），不要混放业务逻辑类。

## 6. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 目录以技术分层为第一级（如 `repository/`、`controller/`） | 聚合间耦合、跨聚合难以复用 | 以聚合 `{agg}` 为第一级 |
| `domain/{agg}` 里出现 Spring / Mapper / MQ 导入 | 破坏领域层零基础设施依赖 | 依赖外部能力用契约 + 依赖声明 |
| 不同聚合共享包 / 实体 | 聚合边界模糊、事件驱动失效 | 聚合目录独立，通信走领域事件 |
| `service` 包混放业务逻辑类 | 目录语义失真、难维护 | 契约 / 实现按层分离，业务逻辑内聚聚合根 |
| 跳过目录骨架直接写代码 | 结构散落、后续难以按模式填充 | 先初始化目录，再按图索骥填充 |

## 7. 下一步

- [聚合设计原则](./aggregate-design.md)：填充 `model/` 的第一篇
- [推荐项目结构](../getting-started/project-structure.md)：模块级四层全貌与多模块拆分
- 按 §3 表格逐文件夹进入对应模式文档
