# BP 卡：聚合目录落地骨架（aggregate-structure）

> 源：`documentation/best-practices/aggregate-structure.md` · 派生时间：2026-11-05（判据表扩至四层：新增应用层 / 实现层 12 行；骨架树补 `action/` `compensation/` `requirement/` 与 `projection/query/`；`service/` 行改注解按 category） · 框架版本：2.1.0

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
│   ├── service/  # 领域服务实现（注解按 category：EVENT_SUBSCRIBER→@Component，其余→@Service）
│   ├── action/  compensation/ # 可补偿动作 / 兜底处理器
│   └── subscriber/ # 事件订阅登记
├── domain/{agg}/              # Domain：业务原子零件与契约（无 acl/ 包）
│   ├── model/  event/  operation/  param/  rule/
│   ├── service/  calculator/  dependency/  repository/  requirement/  config/
│   ├── projection/            # 投影契约（含 query/ 查询条件三族、replica/ 副本版本）
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
| `domain/{agg}/param` | 参数对象（[聚合设计原则](./aggregate-design.md)） |
| `domain/{agg}/rule` | registry-design · order-rule-pattern |
| `domain/{agg}/service` + `calculator` | domain-service |
| `domain/{agg}/dependency` | 核心：依赖体系 |
| `domain/{agg}/repository` | repository-design |
| `domain/{agg}/projection` | projection-design |
| `domain/{agg}/requirement` | application-collaboration |
| `application/{agg}/input` | application-collaboration |
| `application/{agg}/factory`+`updater`+`resolver` | domain-service · application-collaboration |
| `application/{agg}/service` | domain-service |
| `application/{agg}/action` + `compensation` | application-collaboration |
| `application/{agg}/subscriber` | projection-design · rocketmq-config |
| `infrastructure/persistent/{agg}/repository` | repository-design · mybatis-config |
| `infrastructure/persistent/{agg}/projection` | projection-design · elasticsearch-config |
| `infrastructure/config`(+`config/{agg}/`) | mysql-config · mybatis-config · elasticsearch-config · rocketmq-config · outbox-config |
| `infrastructure/dependency/{agg}`(+`shared/`) | 核心：依赖体系 · ACL 防腐层 |

## 3.1 类类型 → 包：落点判据（唯一权威表）

> 本表是包落点的**唯一来源**；其他卡与文档站页面只允许指向本表，不得复制清单。

判定采用**双条件**：`对象类型（含标记接口）+ 所在层`。不看调用方，可机械执行。

| 对象类型 | 识别特征 | 落点 | 不得落 |
| --- | --- | --- | --- |
| 聚合根 | `extends AggregateRoot<ID>` | `domain/{agg}/model/` | — |
| 子实体 | `extends AbstractEntity<ID>` / `implements ITrackable<ID>` | `domain/{agg}/model/` | — |
| 枚举值对象 | `implements IEnumValue<..>` | `domain/{agg}/model/enums/` | `model/` 平铺 |
| 值对象 | `extends ValueObject` / `implements IValueObject` | `domain/{agg}/model/valueobject/` | `model/` 平铺、`param/` |
| **参数对象** | `implements IParamObject` **且**位于 `domain/` 下，命名 `{Agg}InitData` / `{Agg}{Action}Data` | **`domain/{agg}/param/`** | **`model/`** |
| 领域事件 | `extends BaseDomainEvent` / `BaseDataSyncEvent` | `domain/{agg}/event/` | `model/` |
| 操作注册表 | `extends OperationRegistry` | `domain/{agg}/operation/` | — |
| 规则容器 / 规则注册表 | `extends EntityRule` / `extends BrokenRuleRegistry` | `domain/{agg}/rule/` | — |
| 领域服务契约 | `extends IDomainService` | `domain/{agg}/service/` | `application/` |
| 属性计算契约 | `extends IDomainService` 且命名 `{Agg}{Prop}Calculator` | `domain/{agg}/calculator/` | `service/` |
| 仓储抽象 | `extends AbstractRepository<ID, T>` | `domain/{agg}/repository/` | — |
| 投影契约 | `I{Agg}{Store}Source` / `{Agg}{View}Projection` | `domain/{agg}/projection/` | `infrastructure/` |
| 应用层 Input | `@Data`，命名 `{Action}{Agg}Input`，位于 `application/` 下 | `application/{agg}/input/` | 即使实现 `IParamObject` 也不入 `domain/` |
| 应用写服务 | `extends AbstractApplicationService` 且 `implements ICommandApplicationService` | `application/{agg}/` | `service/` |
| 应用读服务 | `implements IQueryApplicationService`（无基类） | `application/{agg}/` | `service/` |
| 领域服务实现 | 实现 `I{Agg}{X}Service` / `I{Agg}{X}Handle` 且契约带 `@DomainService` | `application/{agg}/service/` | 根层 |
| 订阅注册表 | `@Configuration` + 构造器注入 `IEventRegistry`，命名 `{Agg}EventSubscriberRegistry` | `application/{agg}/subscriber/` | `service/`（实现类同禁） |
| 应用层工厂 / 修改器 / 适配器 / 规则装配 | `EntityFactory` / `EntityUpdater` / resolver / `{Agg}RuleConfig` | `application/{agg}/` 下 `factory` / `updater` / `resolver` / `rule` | — |
| 可补偿动作 / 兜底处理器 | `ICompensableAction` / `ICompensationHandler` | `application/{agg}/` 下 `action` / `compensation` | — |
| 外部需求声明 | record `implements IExternalRequirement` | `domain/{agg}/requirement/` | `param/` |
| 查询条件对象 | `{Agg}{X}Query`（sealed 三族） | `domain/{agg}/projection/query/` | `model/` |
| 仓储实现 | `extends Abstract{Agg}Repository` | `infrastructure/persistent/{agg}/repository/` | `domain/` |
| 投影构件 | Projector / Source / Reducer / Replica / 版本 / 对账 | `infrastructure/persistent/{agg}/projection/…` | `domain/` |
| 依赖适配器 | 实现 `I{Agg}{X}Dependency` | `infrastructure/dependency/{agg}/`（共享 `shared/`） | `application/` |
| 装配配置 | `@Configuration` | `infrastructure/config/`（聚合级 `config/{agg}/`） | `domain/` |

- **不得简化成「只看标记接口」**：Input 与领域参数对象可实现同一个 `IParamObject`，落点仍按所在层区分。
- **参数对象命名固定 `*Data`**（`{Agg}InitData` / `{Agg}{Action}Data`），不用 `*Param`。
- **存量降级**：本表只对新建类 / 本轮新增类强制生效；存量位置不符 → 评审记建议项（Advisory）不阻断，项目长期保留现状则记入该项目判决名单 `accepted.md`。

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
- **包落点只有一个答案**：本卡 §3.1 落点判据表（硬约束 15）。其他篇给的树仅供导览。

## 6. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 参数对象放 `model/`（或命名 `*Param`） | 放 `domain/{agg}/param/`，命名 `{Agg}{Action}Data`（§3.1） |
| 按「实现了 `IParamObject`」就把应用层 Input 搬进领域层 | 落点双条件：类型 + 所在层，Input 永留 `application/{agg}/input/` |
| 领域/应用层以技术分层为第一级（如 `domain/repository/`） | 以聚合 `{agg}` 为第一级 |
| 基础设施层以聚合为一级并重复通用配置 | 类型优先：`persistent`/`dependency`/`config` 一级、聚合二级 |
| `domain/{agg}` 出现 Spring/Mapper/MQ 导入 | 用契约 + 依赖声明 |
| 不同聚合共享包/实体 | 目录独立，通信走领域事件 |
| `service` 混放业务逻辑类 | 契约/实现按层分离 |
| 跳过目录骨架直接写代码 | 先初始化目录再按图索骥 |

## 7. 被读于

S1 `ddd-project-init`、S2 `ddd-aggregate-scaffold`（包结构依据）、S4 `ddd-aggregate-create` / S5 `ddd-aggregate-extend`（每个新类的落点）、S6 `ddd-projection-create`、S7 `ddd-code-review`（硬约束 15 的判据来源）。
