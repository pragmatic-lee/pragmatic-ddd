# BP 卡：投影读设计文档模板（读模型）

> 源：`documentation/best-practices/design-doc-read-template.md` · 派生时间：2026-10-26 · 框架版本：2.0.0

## 1. 定位

投影读（读模型）设计文档模板，落地 `docs/design/{聚合名}-projection-read-design-v{N}.md`。两份文档之一、**专项**：只聚焦读模型设计**及其投影持久化**（§4 ES 索引 / Redis 结构），**不含**独立命名的「改动清单 / 测试 / 风险」章节（由写文档统管）；允许在设计章节内用段落描述读路径。

## 2. 7 节骨架

| # | 章节 | 必选 |
| --- | --- | --- |
| 1 | 概述与背景 | ✅ |
| 2 | 投影整体设计 | ✅ |
| 3 | 读模型静态结构 | ✅ |
| 4 | 投影持久化（ES / Redis）设计 | 视场景 |
| 5 | 投影构建与同步路径 | 视场景 |
| 6 | 查询设计 | ✅ |
| 7 | 接口层设计（读） | 视场景 |

> 规则：第 4/5/7 节按场景选填；其余 4 节（1/2/3/6）恒必选。不适用节保留标题写理由。
> 副本存储是「构建」与「查询」共同的 substrate，故 §4 先于 §5 / §6。

## 3. 要点

- 结论先行（顶部引用块）。
- 四角色：投影器 `Projector` / 源 `Source`（自身即副本）/ 裁剪器 `Reducer` / 读服务 `ReadService`；领域契约 `I{Agg}Projection` / `I{Agg}{Store}Source`。
- 读模型枚举降级为基础类型（`int` code + `String` name 双写）；金额 `BigDecimal` 原样承载。
- 源只 extends 真实支撑的查询族（Redis 仅 ById 族）；读服务两跳（查全量 → `getReducer` 裁剪），不回源聚合根；`totalCount`/`nextCursor` 取裁剪前。
- **§4 投影持久化**：ES 索引 mapping + `REPLICA_ID` + 分片 / 副本 + `_version` / External 版本语义，须与 §6 查询族呼应；Redis `REPLICA_KEY` + value 自带 `version` 字段 + TTL + 仅 ById 族；其他存储（对象存储 / 其他 KV）选填。
- External 版本启用时不依赖 ES 自增 `_version`；`readVersion` 缺失返回 `0`（判 STALE→rebuild），非 `-1`。

## 4. 自检（7 项）

结论先行 / 四角色与领域契约 / 枚举降级+金额原样 / **§4 投影持久化含 ES mapping+REPLICA_ID+版本语义 与 Redis REPLICA_KEY+自带 version+TTL+仅 ById，且与查询族呼应** / 源 extends 正确族 / 读服务两跳不回源且 totalCount 取裁剪前 / 7 节编号标题一致。

## 5. 被读于

S6 `ddd-projection-create`、S7 `ddd-code-review`、`ddd-design-doc`。
