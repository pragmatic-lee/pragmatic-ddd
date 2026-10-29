# BP 卡：投影读设计文档模板（读模型）

> 源：`documentation/best-practices/design-doc-read-template.md` · 派生时间：2026-10-29 · 框架版本：2.1.0-SNAPSHOT

## 1. 定位

三层文档中的第三层，与写设计成对：写管「数据怎么变」，本卡管「数据怎么查」。

落盘：`docs/design/{agg}-projection-read-design-v{N}.md`；与写文档**同批归档**；不设改动清单 / 测试 / 风险（由写文档统管）。

## 2. 三种读源

| 读源 | 需要写副本 | 需要定义 | 走四角色 | 有同步事件 |
| --- | --- | --- | --- | --- |
| Elasticsearch | 是 | mapping / 索引名 / REPLICA_ID / 版本策略 | 是 | 是 |
| Redis | 是 | 投影类 / key / TTL / version | 是（仅 ById） | 是 |
| 数据库同源表 | 否 | SQL / Mapper / 索引 / 返回 DTO | 否 | 否 |

每个 Q 必须落到一个读源并标注澄清状态；未澄清的登记待办、对应查询暂不设计。

## 3. 固定 6 节骨架

| # | 章节 | 关键产物 |
| --- | --- | --- |
| 1 | 概述与背景 | 查询需求（承语义 §8 Q 编号）/ 来源事件 / 读源与澄清 + 待澄清表 |
| 2 | 投影整体设计 | 四角色与直读差异 / 副本维度 / 领域契约 |
| 3 | 读模型静态结构 | 投影 DTO（含直读 DTO）/ 条件族（含直读参数） |
| 4 | 读源结构设计 | ES 结构 / Redis 结构 / 数据库直读 / 副本版本语义 |
| 5 | 投影构建与同步路径 | 投影器 / 裁剪器 / 源 / 订阅与路径（仅异构副本） |
| 6 | 查询设计 | 读服务 / 查询族与源对应 / 分页 / ES 条件工厂 / 一致性边界 |

条件子节不删、不重编号，正文写「本副本不适用：{原因}」。

## 4. 关键硬约束

**读模型**

- 枚举一律降级：`Integer` code + `String` name 双写，投影里不出现枚举类型。
- 金额 `BigDecimal` 原样承载，换算只发生在「聚合 → 全量投影」一次。
- 投影 DTO 用 `@Data`（读模型允许，与聚合根约定不同）；缓存副本自带 `version` 字段。
- 条件族三族互不继承；One / List 字段必填，Page 全 `Optional`；条件不含分页参数。
- 源端口只 `extends` 真实支撑的族；数据库直读参数为普通 record，不实现 criteria。

**读源结构**

- ES 索引名带版本（`{agg}_index_v1`），对外用别名；金额用 `scaled_float`，factor 与精度一致；子实体集合用 nested。
- Redis `readVersion` 副本缺失返回 `0`（判 STALE→rebuild），**不返回 `-1`**；`-1` 只表示写模型不存在。
- 数据库直读不写副本、不发同步事件；SQL 里用 `JSON_EXTRACT` 抽数值，状态名由读服务回填；过滤列必须在写侧 DDL 有列或生成列与索引。

**构建与同步**

- 源构造五参顺序：`ProjectionSource` → `aggregateType` → `projectionType` → `projector` → `reducers`。
- 订阅者先按 `getDataSyncAction()` 分流：`PURGE` → `purge(id)`；`UPSERT` → 回查聚合 → `sync`；聚合为 `null` 转 `purge`。
- `rebuild` 从写模型当前快照重建，不重放单条事件。
- 投影侧物化实现与订阅接线必须在本文档；写文档只留业务订阅。

**查询**

- 读服务 `implements IQueryApplicationService`，不继承查询基类；选源写死在方法体内。
- 两跳：查全量 → `getReducer(目标类型).reduce(...)`；`getReducer` 返回 null 抛 `ProjectionReducerNotFoundException`。
- `totalCount` / `nextCursor` 取裁剪前；不回源聚合根；Redis 未命中返回 null，不回退 ES。
- 条件 → DSL 下沉到 ES 条件工厂（`public final` 纯函数）。

## 5. 代码形式比例

投影根接口 / 源端口 / 副本常量 / 装配类完整；投影 DTO、投影器、裁剪器、订阅者各 1 个完整示例；读服务 1–2 个方法 + 查询族对应表；ES mapping 完整；直读 SQL 完整。

## 6. 自检要点（17 项，摘要）

6 节无横切章 / Q 编号与语义 §8 对齐 / 四角色与契约齐 / 枚举降级 + 金额原样 / §4 只给结构产物 / ES 与 Redis 结构齐全 / 源只 extends 支撑族 / 两跳且不回源 / 订阅按 action 分流 / `readVersion` 返回 0 / 新增副本三接线齐 / 每个 Q 有读源与澄清状态 / 直读过滤列有索引。

## 7. 被读于

S6 `ddd-projection-create`、S7 `ddd-code-review`、`ddd-design-doc`。
