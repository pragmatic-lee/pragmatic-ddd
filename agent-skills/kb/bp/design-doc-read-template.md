# BP 卡：投影读设计文档模板（读模型）

> 源：`documentation/best-practices/design-doc-read-template.md` · 派生时间：2026-10-28 · 框架版本：2.1.0-SNAPSHOT

## 1. 定位

投影读（读模型）设计文档模板，落地 `docs/design/{聚合名}-projection-read-design-v{N}.md`。两份文档之一、**专项**：只聚焦读模型设计**及其投影持久化**（ES 索引 / Redis 结构），**不含**「改动清单 / 测试 / 风险」章节（由写文档统管）；**不设「接口层设计（读）」章节**（HTTP / RPC 端点、Response DTO 不在范围）。

## 2. 写法约定（产设计产物，不产设计说明书）

1. **只写设计，不写论证**：只产出 mapping / 结构 / DTO / Query 条件族 / 常量 / 路径。
2. **产物优先**：能用表 / 代码 / mapping 表达的不写散文；背景只给结论。
3. **写侧只列不展开**：写侧设计一律指向聚合写文档。
4. **演进根因与版本取舍不进本文**：写 `aggregate-evolution-design` 或 v(N+1)。
5. **框架符号不确定项交写文档统管**：本文不设风险章节。
6. **结论与理由分离**；**不写顶部「结论先行」块**。

## 3. 章节骨架（完整 6 节；选填节删除后顺延重编号）

| # | 章节 | 必选 |
| --- | --- | --- |
| 1 | 概述与背景（查询需求 / 来源事件 / 存储选型，不写选型理由） | ✅ |
| 2 | 投影整体设计（四角色 / 副本维度 / 领域契约） | ✅ |
| 3 | 读模型静态结构（投影 DTO / 条件族 Query / 副本常量） | ✅ |
| 4 | 投影持久化（ES / Redis）设计 | 视场景 |
| 5 | 投影构建与同步路径 | 视场景 |
| 6 | 查询设计 | ✅ |

> 规则：第 4/5 节按场景选填；**不适用时直接删除并顺延重编号**，编号连续，文档内声明删除原因；§4 内不适用的存储子节（ES / Redis / 其他）同样直接删除。**不设接口层、改动清单、测试、风险章节**。副本存储是「构建」与「查询」共同的 substrate，故 §4 先于 §5 / §6。

## 4. 关键约束

- 四角色：投影器 `Projector` / 源 `Source`（自身即副本）/ 裁剪器 `Reducer` / 读服务 `ReadService`；领域契约 `I{Agg}Projection` / `I{Agg}{Store}Source`。
- 读模型枚举降级为基础类型（`int` code + `String` name 双写）；金额 `BigDecimal` 原样承载。
- 源只 extends 真实支撑的查询族（Redis 仅 ById 族）；读服务两跳（查全量 → `getReducer` 裁剪），不回源聚合根；`totalCount`/`nextCursor` 取裁剪前。
- **§4 投影持久化只给结构产物**：ES 索引 mapping + `REPLICA_ID` + 分片副本 + 版本语义；Redis `REPLICA_KEY` + value 自带 `version` 字段 + TTL + 仅 ById 族；与 §6 查询族呼应，不含选型论证。
- External 版本启用时不依赖 ES 自增 `_version`；`readVersion` 缺失返回 `0`（判 STALE→rebuild），非 `-1`。

## 5. 自检（8 项，不进产出文档）

概述说清需求来源选型（不写理由）/ 四角色与领域契约 / 枚举降级+金额原样 / **§4 只给结构产物且与查询族呼应** / 源 extends 正确族（Redis 仅 ById）/ 读服务两跳不回源且 totalCount 取裁剪前 / 编号连续·声明删节·不设接口层 / **全文无论证性散文**。

## 6. 被读于

S6 `ddd-projection-create`、S7 `ddd-code-review`、`ddd-design-doc`。
