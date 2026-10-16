# BP 卡：聚合目录落地骨架（aggregate-structure）

> 源：`documentation/best-practices/aggregate-structure.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 本质

新建聚合的第一步：初始化四层目录骨架，再按文件夹逐个填充对应模式文档。目录即地图——每个文件夹指向一篇落地模式。

## 2. 分包维度（关键）

- **领域/应用层以聚合 `{agg}` 为第一级**（单个聚合内聚成块）。
- **基础设施层以类型（`persistent`/`dependency`/`config`）为第一级、聚合为第二级**（通用配置抽单一来源）。
- 不同聚合不共享包/实体，通信走领域事件。

## 3. 四层骨架

```text
{module}/
├── api/{agg}/            cmd/ query/ dto/            # UI 协议
├── controller/{agg}/     {Agg}Controller             # UI
├── application/{agg}/    input/ factory/ updater/ resolver/ rule/ service/ subscriber/   # 应用编排
├── domain/{agg}/         model/ event/ operation/ param/ rule/ service/ calculator/ dependency/ repository/ projection/ config/  # 领域契约（零基础设施依赖）
└── infrastructure/       persistent/{agg}/  dependency/{agg}/(+shared/)  config/ (+{agg}/)
```

## 4. 目录 → 模式文档（按图索骥）

| 目录 | 模式文档 |
| --- | --- |
| `domain/{agg}/model` | aggregate-design / entity-design / value-object / enum-value |
| `event` | event-modeling |
| `operation` | operation-registry-design |
| `param` | aggregate-design §3.4 |
| `rule` | registry-design / order-rule-pattern |
| `service`+`calculator` | domain-service |
| `repository` | repository-design |
| `projection` | projection-design |
| `application/input` | application-collaboration |
| `application/factory`+`updater`+`resolver` | domain-service / application-collaboration |
| `subscriber` | projection-design / rocketmq-config |
| `infrastructure/persistent/{agg}` | repository-design / projection-design / mysql-config / elasticsearch-config |
| `infrastructure/config` | mysql/elasticsearch/rocketmq/outbox-config |

## 5. 四层职责速记

| 层 | 职责 | 不放 |
| --- | --- | --- |
| Domain | 业务原子零件与契约 | SQL/ORM/RPC/MQ/事务/Input |
| Application | 编排领域、汇聚依赖 | new 基础设施实现、聚合根行为 |
| Infrastructure | 技术实现落地契约 | 业务逻辑/规则/状态判定 |
| UI | 协议适配与转换 | 业务编排/仓储直连 |

## 6. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 领域/应用层以技术分层为第一级 | 以聚合 `{agg}` 为第一级 |
| 基础设施层以聚合为一级重复通用配置 | 类型优先 + 通用放 `config/` |
| domain 出现 Spring/Mapper/MQ 导入 | 契约 + 依赖声明 |
| 不同聚合共享包/实体 | 目录独立 + 走事件 |
| 跳过骨架直接写代码 | 先骨架再填充 |

## 7. 被读于

S2 `ddd-aggregate-scaffold`、S1 `ddd-project-init`、S7 `ddd-code-review`。
