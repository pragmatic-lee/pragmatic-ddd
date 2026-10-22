# 持久化设计文档模板（存储）

> 本模板用于产出「持久化（存储）」设计文档，落地 `docs/design/{聚合名}-persistence-design-v{N}.md`。
> 它是 **ddd-design-doc** 一次需求产出的**三份文档之一**（另见 聚合写模板 / 投影读模板）。
> 本份**只聚焦存储设计本身**（关系表 + ES 索引 + Redis 结构），**不含**独立命名的「改动清单 / 测试 / 风险 / 时序」章节——这些横切内容由聚合写文档统管。

## 1. 章节骨架（5 节，编号与标题恒定）

| # | 章节 | 必选 | 回答的问题 |
| --- | --- | --- | --- |
| 1 | 概述与背景 | ✅ | 存储需求、选型理由（关系表 + ES + Redis 的组合） |
| 2 | 关系表设计 | ✅ | DDL、字段、索引；表边界 = 聚合边界；`version` / 审计列；无物理外键；映射与仓储 |
| 3 | ES 索引设计 | 视场景 | 索引 mapping、`REPLICA_ID`、分片 / 副本、`_version` / External；查询族支撑 |
| 4 | Redis 结构设计 | 视场景 | `REPLICA_KEY`、value 结构（自带 `version`）、TTL、仅 ById 族 |
| 5 | 其他存储 | 视场景 | 如适用（对象存储 / 其他 KV）；不适用则保留标题写「本次不适用」 |

> 规则：第 3 / 4 / 5 节按场景选填；第 1 / 2 节恒必选。某节不适用时保留标题并写明理由。

## 2. 完整模板（可直接复制）

```markdown
# {聚合名} 持久化设计文档（v{N}）

> 结论先行：**{存储方案结论}**。{一句话理由}。
>
> 本文为设计文档之一（另含聚合写、投影读设计），未改动任何代码。

## 1. 概述与背景
{存储需求；选型理由（关系表 + ES + Redis 的组合与各自职责）}

## 2. 关系表设计
### 2.1 DDL
### 2.2 索引
### 2.3 映射与仓储
{表边界 = 聚合边界；聚合根主表 + 有独立身份的子实体子表 + 无身份值对象 JSON 列}
{version 乐观锁列；审计列 created_at/by、updated_at/by；逻辑外键无物理约束}

## 3. ES 索引设计（如适用）
{索引 mapping；REPLICA_ID；分片 / 副本策略；_version / External 版本}
{支撑的查询族；与投影读模板的 ES 源呼应}

## 4. Redis 结构设计（如适用）
{REPLICA_KEY 设计；value 结构（自带 version 字段）；TTL；仅 ById 族}

## 5. 其他存储（如适用）
{不适用保留标题写「本次不适用」}
```

## 3. 符合性自检（5 项）

| # | 检查项 | 不满足的后果 |
| --- | --- | --- |
| 1 | 顶部有结论先行块 | 评审方向不明 |
| 2 | 关系表遵循「表边界 = 聚合边界」，无物理外键，预留 `version` / 审计列 | 跨聚合外键、缺乐观锁 |
| 3 | 有独立身份的实体独立子表 + `TrackedList`，无身份值对象 JSON 列 | 表设计错配 |
| 4 | ES 索引含 mapping / `REPLICA_ID` / 版本语义，与投影读设计呼应 | 索引与读模型错位 |
| 5 | Redis 结构含 `REPLICA_KEY` / 自带 version / TTL，仅 ById 族 | 缓存结构不可用 |

## 4. 框架机制参考

- [聚合数据库设计](/best-practices/aggregate-database-design)：表边界 = 聚合边界、一表还是多表、表结构规范、一致性策略
- [ES 配置](/best-practices/elasticsearch-config)：RestClient 单 Bean、投影写入、限流
- [投影读模型设计](/best-practices/projection-design)：副本常量 `REPLICA_ID` / `REPLICA_KEY`、源端口
- [MySQL 配置](/best-practices/mysql-config) / [MyBatis 配置](/best-practices/mybatis-config)：数据源、连接池、TypeHandler 装配
- [聚合写模板](./design-doc-write-template.md) / [投影读模板](./design-doc-read-template.md)
