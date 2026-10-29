# BP 卡：投影读设计文档模板（读模型）

> 源：`documentation/best-practices/design-doc-read-template.md` · 派生时间：2026-10-29（闸门 / 回流 / 直读同步） · 框架版本：2.1.0-SNAPSHOT
> 流程权威：`bp.design-doc-template`（G4 闸门、回流矩阵、一致性校验）。

## 1. 定位

三契约中的第三份，与写设计成对：② 管「数据怎么变」，本卡管「数据怎么查」。

落盘：`docs/design/changes/active/{需求}/{agg}/projection-read.md`；与写文档**同批归档**（随目录移到 `changes/archive/`）；不设改动清单 / 测试 / 风险（由写文档统管）。当前实际形态看 `bp.design-doc-facts`。

**启动条件**（缺一不落笔）：② 已定稿且头部写了 `基于：② vN`；用户明确点名出投影读设计（G4）。未点名先问，不自行开写。

## 2. 三种读源

| 读源 | 需要写副本 | 需要定义 | 读源接入 | 有同步事件 |
| --- | --- | --- | --- | --- |
| Elasticsearch | 是 | mapping / 索引名 / REPLICA_ID / 版本策略 | 继承 `AbstractProjectionSource` + 领域源端口 | 是 |
| Redis | 是 | 投影类 / key / TTL / version | 同上（仅 ById） | 是 |
| 数据库同源表 | 否 | 行投影 `{Agg}RowProjection` / SQL 与 resultMap / 索引 / 读源端口与实现 | 读源端口 + `{Agg}DbReadSource` + Mapper，**不继承基类** | 否 |

> 直读**仍产出投影**（行投影 →（可选）裁剪器 → 目标投影），不是「不做投影」。

每个 Q 必须落到一个读源并标注澄清状态；未澄清的登记待办、对应查询暂不设计。

**回流顺序**：语义 §8 缺 Q 时不在本份凭空新增，固定三步：回 ① §8 补 Q → ① 定稿 → 回 ②（需字段 / 索引 / 事件时）→ 再改本份。判别：业务口径（Q / 筛选维度 / 事件载荷）回 ①；落地形态（读源 / mapping / REPLICA_ID / TTL / 裁剪 / 分页 / 索引）就地改，② 不动。

## 3. 固定 6 节骨架

| # | 章节 | 关键产物 |
| --- | --- | --- |
| 1 | 概述与背景 | 查询需求（承语义 §8 Q 编号）/ 来源事件 / 读源与澄清 + 待澄清表 |
| 2 | 投影整体设计 | 四角色与直读差异 / 副本维度 / 领域契约 |
| 3 | 读模型静态结构 | 投影（含行投影）/ 条件族（含直读参数） |
| 4 | 读源结构设计 | ES 结构 / Redis 结构 / 数据库直读 / 副本版本语义 |
| 5 | 投影构建与同步路径 | 投影器 / 裁剪器 / 源 / 订阅与路径（仅异构副本） |
| 6 | 查询设计 | 读服务 / 查询族与源对应 / 分页 / ES 条件工厂 / 一致性边界 |

条件子节不删、不重编号，正文写「本副本不适用：{原因}」。

## 4. 关键硬约束

**读模型**

- 枚举一律降级：`Integer` code + `String` name 双写，投影里不出现枚举类型。
- 金额 `BigDecimal` 原样承载，换算只发生在「聚合 → 全量投影」一次。
- 投影用 `@Data`（读模型允许，与聚合根约定不同）；缓存副本自带 `version` 字段。
- 条件族三族互不继承；One / List 字段必填，Page 全 `Optional`；条件不含分页参数。
- 源端口只 `extends` 真实支撑的族；数据库直读参数同族实现 criteria（否则进不了 SPI）。

**读源结构**

- ES 索引名带版本（`{agg}_index_v1`），对外用别名；金额用 `scaled_float`，factor 与精度一致；子实体集合用 nested。
- Redis `readVersion` 副本缺失返回 `0`（判 STALE→rebuild），**不返回 `-1`**；`-1` 只表示写模型不存在。
- 数据库直读不写副本、不发同步事件、不进对账，但**必须产出投影**：行投影实现投影根接口，目标形状不同时挂裁剪器；由「读源端口 + 实现 + Mapper」三件组成，读服务不注入 Mapper；SQL 里用 `JSON_EXTRACT` 抽数值，状态名由读源回填；过滤列必须有索引，且**必须可溯源**到 ② §7.1 的 A 型展开列或 B 型 `search_*` 生成列；提供滚动时写明排序键与 tiebreaker。

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

投影根接口 / 源端口 / 副本常量 / 装配类完整；投影（含行投影）、投影器、裁剪器、订阅者各 1 个完整示例；读服务 1–2 个方法 + 查询族对应表；ES mapping 完整；直读 SQL 完整。

## 6. 回流与归档一致性

- Q 集合必须与 ① §8 完全相等；来源事件 ⊆ ② §5.1；投影字段 ⊆ ② §7 表结构。
- ① 升版后本份置「待对齐」，不静默一致化。
- 归档前：② §10.2 无「未回」、本份状态不为「待对齐」。

## 7. 自检要点（21 项，摘要）

6 节无横切章 / Q 编号与语义 §8 对齐 / 四角色与契约齐 / 枚举降级 + 金额原样 / §4 只给结构产物 / ES 与 Redis 结构齐全 / 源只 extends 支撑族 / 两跳且不回源 / 订阅按 action 分流 / `readVersion` 返回 0 / 新增副本三接线齐 / 每个 Q 有读源与澄清状态 / 直读过滤列有索引 / 直读写全读源三件且读服务不注入 Mapper / 行投影字段对齐 SELECT 列 / 两跳裁剪器两端都是投影 / 过滤列可溯源到 ② §7.1。

## 8. 被读于

S6 `ddd-projection-create`、S7 `ddd-code-review`、`ddd-design-doc`。
