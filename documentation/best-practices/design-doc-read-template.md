# 投影读设计文档模板（读模型）

> 本模板用于产出「投影读（读模型）」设计文档，落地 `docs/design/{聚合名}-projection-read-design-v{N}.md`。
> 它是 **ddd-design-doc** 一次需求产出的**三份文档之一**（另见 聚合写模板 / 持久化模板）。
> 本份**只聚焦读模型设计本身**，**不含**独立命名的「改动清单 / 测试 / 风险 / 时序」章节——这些横切内容由聚合写文档统管。允许在相应设计章节内用段落描述读路径，但不立横切章节标题。

## 1. 章节骨架（6 节，编号与标题恒定）

| # | 章节 | 必选 | 回答的问题 |
| --- | --- | --- | --- |
| 1 | 概述与背景 | ✅ | 这份读模型解决什么查询需求；来源事件 / 聚合；选型（ES / Redis / 双副本） |
| 2 | 投影整体设计 | ✅ | 四角色（投影器 / 源 / 裁剪器 / 读服务）、副本维度、领域契约 |
| 3 | 读模型静态结构 | ✅ | 投影 DTO、条件族 `Query`、副本常量；枚举降级为基础类型 |
| 4 | 投影构建与同步路径 | 视场景 | 写路径触发 `sync`、订阅者、materialize / purge / readVersion / rebuild |
| 5 | 查询设计 | ✅ | 查询族分派、读服务两跳、Redis/ES 选择、分页 / 排序 |
| 6 | 接口层设计（读） | 视场景 | 读 HTTP / RPC、Response DTO 是否变更 |

> 规则：第 4 / 6 节按场景选填；其余 4 节恒必选。某节不适用时保留标题并写明理由。

## 2. 完整模板（可直接复制）

```markdown
# {聚合名} 投影读设计文档（v{N}）

> 结论先行：**{读模型方案结论}**。{一句话理由}。
>
> 本文为设计文档之一（另含聚合写、持久化设计），未改动任何代码。

## 1. 概述与背景
{查询需求；来源事件 / 聚合；选型 ES / Redis / 双副本及其理由}

## 2. 投影整体设计
{四角色：投影器 Projector / 源 Source / 裁剪器 Reducer / 读服务 ReadService}
{副本维度；领域契约 I{Agg}Projection / I{Agg}{Store}Source}

## 3. 读模型静态结构
{投影 DTO（@Data）；条件族 Query（sealed + record）；{Store}Targets 常量}
{枚举降级为基础类型（int code + String name 双写）；金额 BigDecimal 原样承载}

## 4. 投影构建与同步路径
{写路径触发 sync；订阅者 handleEvent → findById → 源.sync}
{materialize / purge / readVersion / rebuild；External 版本语义}

## 5. 查询设计
{查询族分派；读服务两跳（查全量 → getReducer 裁剪）；Redis/ES 选择}
{分页 / 排序；不回源聚合根；totalCount/nextCursor 取裁剪前}

## 6. 接口层设计（读）
{HTTP / RPC；Response DTO 是否变更}
```

## 3. 符合性自检（6 项）

| # | 检查项 | 不满足的后果 |
| --- | --- | --- |
| 1 | 顶部有结论先行块 | 评审方向不明 |
| 2 | 投影整体设计含四角色与领域契约（I{Agg}Projection / I{Agg}{Store}Source） | 端口/实现错位 |
| 3 | 读模型枚举降级为基础类型，金额 BigDecimal 原样 | 查询/裁剪换算散落、类型错配 |
| 4 | 源只 extends 真实支撑的查询族（Redis 仅 ById） | 编译期能力误用 |
| 5 | 读服务两跳、不回源聚合根、totalCount/nextCursor 取裁剪前 | 读路径错误 |
| 6 | 章节编号与标题与本模板一致（固定 6 节） | 文档规格不统一 |

## 4. 框架机制参考

- [投影读模型设计](/best-practices/projection-design)：四角色、落地方式、关键约束与反模式
- [投影基类 API](/best-practices/projection-api)：可编译方法签名（零上下文场景必读）
- [ES 配置](/best-practices/elasticsearch-config)：RestClient 单 Bean、投影写入、限流
- [事件订阅领域服务](/best-practices/event-subscriber-pattern) / [事件建模](/best-practices/event-modeling)
- [枚举值处理约定](/best-practices/enum-value)：条件族与投影 DTO 中枚举降级
- [聚合写模板](./design-doc-write-template.md) / [持久化模板](./design-doc-persistence-template.md)
