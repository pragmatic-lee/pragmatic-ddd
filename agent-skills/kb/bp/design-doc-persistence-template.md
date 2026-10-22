# BP 卡：持久化设计文档模板（存储）

> 源：`documentation/best-practices/design-doc-persistence-template.md` · 派生时间：2026-10-20 · 框架版本：2.0.0

## 1. 定位

持久化（存储）设计文档模板，落地 `docs/design/{聚合名}-persistence-design-v{N}.md`。三份文档之一、**专项**：只聚焦存储设计（关系表 + ES 索引 + Redis 结构），**不含**独立命名的改动清单 / 测试 / 风险 / 时序（由写文档统管）。

## 2. 5 节骨架

| # | 章节 | 必选 |
| --- | --- | --- |
| 1 | 概述与背景 | ✅ |
| 2 | 关系表设计 | ✅ |
| 3 | ES 索引设计 | 视场景 |
| 4 | Redis 结构设计 | 视场景 |
| 5 | 其他存储 | 视场景 |

> 规则：第 3/4/5 节按场景选填；第 1/2 节恒必选。不适用节保留标题写「本次不适用」。

## 3. 要点

- 结论先行（顶部引用块）。
- 关系表遵循「表边界 = 聚合边界」：聚合根主表 + 有独立身份的子实体子表（`TrackedList` 差量）+ 无身份值对象 JSON 列；无物理外键（仅 ID 引用）。
- 聚合根表必含 `version` 乐观锁列与审计列（`created_at/by`、`updated_at/by`）；逻辑外键（如 `order_id`）只作过滤键、不映射进子实体。
- ES：索引 mapping、`REPLICA_ID`、分片 / 副本、`_version` / External 版本、支撑查询族，与投影读模板呼应。
- Redis：`REPLICA_KEY`、`value` 自带 `version` 字段、TTL、仅 ById 族。

## 4. 自检（5 项）

结论先行 / 表边界=聚合边界+无物理外键+version与审计列 / 子实体子表+TrackedList、值对象 JSON 列 / ES mapping+REPLICA_ID+版本语义 / Redis REPLICA_KEY+自带version+TTL+仅ById。

## 5. 被读于

S3 `ddd-infra-config`、S7 `ddd-code-review`、`ddd-design-doc`。
