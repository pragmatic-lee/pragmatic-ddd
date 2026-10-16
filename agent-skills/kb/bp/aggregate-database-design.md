# BP 卡：聚合数据库设计（aggregate-database-design）

> 源：`documentation/best-practices/aggregate-database-design.md` · 派生时间：2026-09-18 · 框架版本：2.0.0

## 1. 核心原则：表边界 = 聚合边界

一个聚合对应一组表：聚合根一张主表；有独立身份的实体各自一张子表；无身份内嵌值对象并入父表 JSON 列。不跨聚合建物理外键，一致性由应用层事务 + 单聚合事务保证。

## 2. 一表还是多表（判定递进）

| 子对象形态 | 表设计 |
| --- | --- |
| 有独立 ID、独立行的实体 | 独立子表 + `TrackedList` 差量 |
| 无身份内嵌值对象 | 父表 JSON 列 |

- 判定二：子实体是否只随父聚合存在（不被他聚合独立访问）→ 是则归本聚合子表。
- 判定三：单表（值对象全 JSON，join 少）vs 多表（子实体可独立 CRUD/索引）。
- "多"的定法：聚合根=1 主表 + N 子实体表（N=独立实体数），一个实体只一张表。

## 3. 合理性三问

1. 一致性：同事务内保持一致？
2. 边界：子实体不被聚合外单独访问？
3. 读写：无 JSON 塞太多查不动 / 无 join 太重？

## 4. 表结构规范

- 聚合根主键 = `entityId`（非自增，由 ID 生成器）。
- 子实体主键 = 自身 `id`；`order_id` 是**逻辑外键**（过滤键，不入领域、不入 resultMap）。
- 审计列 `created_at/by updated_at/by` 由父类托管，表须预留。
- 聚合根表必须有 `version` 列（乐观锁 CAS 基线，读入 `oldVersion`）。

## 5. 复杂类型存储三通道

| 类型 | 形态 | 通道 |
| --- | --- | --- |
| 单列枚举 | 整数值 | `EnumRule.CODE` |
| 内嵌值对象 | 整列 JSON | `GenericJsonTypeHandler` |
| 有身份子实体集合 | 独立子表 | `TrackedList` 差量 |
| 无身份值对象集合 | 单列 JSON 数组 | `ListTypeHandler` |

判定核心：有独立身份、需独立行 → 子表；无身份、随父替换 → JSON。

## 6. 一致性策略

- 无物理外键；应用事务 + 仓储级联（先子后主）。
- 乐观锁：`version` CAS，冲突抛 `OptimisticLockingFailureException`。
- 子集合差量：`TrackedList` 只发增量 INSERT/DELETE。

## 7. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 为无身份值对象建子表 | 并入父表 JSON |
| 有身份实体压进 JSON | 独立子表 + TrackedList |
| 跨聚合物理外键 | 仅 ID 引用 |
| 实体多表重复存储 | 一实体一聚合一表 |
| 子表无逻辑外键索引 | `order_id` 建普通索引 |
| 缺 `version`/审计列 | 预留 |

## 8. 被读于

S4 `ddd-aggregate-create`（步骤 4 仓储）、S3 `ddd-infra-config`（建表）、S7 `ddd-code-review`。
