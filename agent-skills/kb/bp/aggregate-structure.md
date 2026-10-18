# BP 卡：聚合目录落地骨架（aggregate-structure）

> 源：`documentation/best-practices/aggregate-structure.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 本质与定位

新建聚合第一步：初始化目录骨架，再**按图索骥**到各模式文档填充。目录即地图——每个文件夹对应一篇（或多篇）模式文档。

## 2. 目录骨架（四层分包）

领域/应用/UI 层以聚合 `{agg}` 为第一级；基础设施层以类型（`persistent`/`dependency`/`config`）为第一级、聚合为第二级：

```text
{module}/
├── api/{agg}/                 # UI：协议 Request / Response
│   ├── cmd/  query/  dto/
├── controller/{agg}/          # UI：{Agg}Controller
├── application/{agg}/         # Application：编排领域逻辑、汇聚依赖
│   ├── input/    # {Action}Input 业务语义入参
│   ├── factory/  # EntityFactory（先算后赋）
│   ├── updater/  # EntityUpdater 修改场景编排
│   ├── resolver/ # Command → 领域输入适配
│   ├── rule/     # 规则容器组装
│   ├── service/  # 领域服务实现（@Service）
│   └── subscriber/ # 事件订阅登记
├── domain/{agg}/              # Domain：业务原子零件与契约（无 acl/ 包）
│   ├── model/  event/  operation/  param/  rule/
│   ├── service/  calculator/  dependency/  repository/  projection/  config/
└── infrastructure/            # Infrastructure：技术实现，落地领域层契约（类型优先）
    ├── persistent/{agg}/      # 仓储实现 + 投影（projector/searcher/reducer/replica/源/版本/对账）
    ├── dependency/{agg}/      # 外部依赖实现（ACL 网关）；跨聚合共享放 dependency/shared/
    └── config/                # @Configuration 装配（MySQL/ES/MQ/Outbox）
        └── {agg}/             # 仅绑定聚合的配置进 config/{agg}/，通用放 config/ 外层
```

> ⚠️ 分包维度：领域/应用层以聚合 `{agg}` 为第一级（契约与编排内聚）；基础设施层类型优先（通用技术配置与跨聚合共享适配器抽成单一来源）。不同聚合不共享包/实体，通信走领域事件。

## 3. 按图索骥（目录 → 模式文档）

| 目录 | 去查的模式文档 |
| --- | --- |
| `domain/{agg}/model` | aggregate-design · entity-design · value-object · enum-value |
| `domain/{agg}/event` | event-modeling |
| `domain/{agg}/operation` | operation-registry-design |
| `domain/{agg}/param` | aggregate-design §3.4 |
| `domain/{agg}/rule` | registry-design · order-rule-pattern |
| `domain/{agg}/service` + `calculator` | domain-service |
| `domain/{agg}/dependency` | 核心：依赖体系 |
| `domain/{agg}/repository` | repository-design |
| `domain/{agg}/projection` | projection-design |
| `application/{agg}/input` | application-collaboration |
| `application/{agg}/factory`+`updater`+`resolver` | domain-service · application-collaboration |
| `application/{agg}/service` | domain-service |
| `application/{agg}/subscriber` | projection-design · rocketmq-config |
| `infrastructure/persistent/{agg}/repository` | repository-design · mybatis-config |
| `infrastructure/persistent/{agg}/projection` | projection-design · elasticsearch-config |
| `infrastructure/config`(+`config/{agg}/`) | mysql-config · mybatis-config · elasticsearch-config · rocketmq-config · outbox-config |
| `infrastructure/dependency/{agg}`(+`shared/`) | 核心：依赖体系 · ACL 防腐层 |

## 4. 四层职责速记

| 层 | 职责 | 不要放 |
| --- | --- | --- |
| Domain | 业务原子零件与契约 | SQL/ORM、RPC、MQ 发送、事务、Input |
| Application | 编排领域逻辑、汇聚依赖 | 直接 `new` 基础设施实现、聚合根业务行为 |
| Infrastructure | 技术实现，落地领域层契约 | 业务逻辑、领域规则、状态判定 |
| UI | 协议适配与参数转换 | 业务编排、聚合调用、仓储直连 |

## 5. 关键机制与避坑

- 依赖方向：`UI → Application → Domain ← Infrastructure`（依赖倒置）。
- 领域层零基础设施依赖：`domain/{agg}` 不出现 Spring/MyBatis/MQ 导入；外部能力用 `dependency` 声明 + 领域服务契约。
- 聚合间不共享：目录独立，通信优先领域事件，不跨聚合 new 仓储/服务。
- 目录名即语义：`service` 只放契约（领域层）或实现（应用/基础设施层）。

## 6. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 领域/应用层以技术分层为第一级（如 `domain/repository/`） | 以聚合 `{agg}` 为第一级 |
| 基础设施层以聚合为一级并重复通用配置 | 类型优先：`persistent`/`dependency`/`config` 一级、聚合二级 |
| `domain/{agg}` 出现 Spring/Mapper/MQ 导入 | 用契约 + 依赖声明 |
| 不同聚合共享包/实体 | 目录独立，通信走领域事件 |
| `service` 混放业务逻辑类 | 契约/实现按层分离 |
| 跳过目录骨架直接写代码 | 先初始化目录再按图索骥 |

## 7. 被读于

S1 `ddd-project-init`、S2 `ddd-aggregate-scaffold`、S7 `ddd-code-review`。
