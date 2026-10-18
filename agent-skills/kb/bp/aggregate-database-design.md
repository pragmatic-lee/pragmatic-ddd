# BP 卡：聚合数据库设计原则（aggregate-database-design）

> 源：`documentation/best-practices/aggregate-database-design.md` · 派生时间：2026-10-18 · 框架版本：2.0.0

## 1. 核心原则：表边界 = 聚合边界

一个聚合对应**一组表**：聚合根一张主表，聚合内**有独立身份的实体**各自成一张子表，无身份的内嵌值对象并入父表。表设计**跟随聚合边界**。

```text
Order 聚合
├─ t_order          聚合根主表（订单字段 + Customer/Address/Money 等 JSON 值对象 + version 乐观锁列）
└─ t_order_item     聚合内子实体表（OrderItem，有独立 id，TrackedList 差量同步）
```

**不跨聚合建物理外键**：聚合间通过 ID 引用（如 `Order` 持有 `customerId`），表之间不建物理外键约束，一致性由应用层 + 单聚合事务保证。

## 2. 一表还是多表：判定标准

### 2.1 判定一：子对象是否有独立 DB 行

`TrackedList` 只针对**有独立 DB 行的对象类型**（实体或独立表值对象），不处理基础类型或无身份内嵌值对象。

| 子对象形态 | 表设计 | 例子 |
| --- | --- | --- |
| 有独立 ID、独立行的实体 | **独立子表** | `OrderItem` |
| 无身份的内嵌值对象 | **父表 JSON 列** | `Customer`/`Address`/`Money` 等 `IValueObject` |

### 2.2 判定二：子实体是否需要独立访问

- 只在父聚合上下文中存在、从不单独查询/更新/引用 → 留在聚合内，子表 + `TrackedList` 差量。
- 可能被其他聚合独立访问 → 应拆成独立聚合，而非硬塞。

### 2.3 判定三：单表 vs 多表读写取舍

- 单表（内嵌值对象全 JSON 列）：表少、join 少、一次读完整、事务简单；缺点 JSON 内字段无法 SQL 过滤/索引/统计。
- 多表（子实体独立子表）：子行可单独 CRUD（`TrackedList` 差量）、可建索引、避免 JSON 过大；缺点表多、需级联、事务跨表。

### 2.4 "多"怎么定

1. 聚合根 = 一张主表（`t_order`）：自身字段 + 内嵌值对象（JSON 列）。
2. 每个有独立身份的聚合内实体 = 一张子表（`t_order_item`）。
3. 一个实体只归属一个聚合、只一张表，不重复存储。

## 3. 合理性三问检验法

1. **一致性**：这组表是否总能在同一事务内保持一致？
2. **边界**：子实体是否从不被聚合外单独访问/修改？
3. **读写**：有没有因一张表塞太多 JSON 没法查，或多表 join 太重？

都通过才算合理；越界被独立访问或只是临时聚合，应拆成独立聚合/表。

## 4. 表结构设计规范

- **主键**：聚合根表主键 = 聚合根 `entityId`（非自增，由 ID 生成器 `IdSegmentMapper` 生成）；子实体表主键 = 子实体自身 `id`，`order_id` 作**逻辑外键**（过滤键），非领域属性、不映射进子实体。
- **列映射**：每领域属性对应一列，下划线列/驼峰属性靠 `mapUnderscoreToCamelCase`+显式 resultMap。
- **审计列**：`created_at`/`created_by`/`updated_at`/`updated_by` 四列由父类托管，表必须预留。
- **版本列**：聚合根表必须有 `version` 列，乐观锁基线（读入 `oldVersion`，更新 CAS）。

## 5. 复杂类型的存储形态

| 类型 | 存储形态 | 通道 | 说明 |
| --- | --- | --- | --- |
| 单列枚举 | 整数值 | 枚举通道（`EnumRule.CODE`） | `status` 存业务 value |
| 内嵌值对象 | 整列 JSON | JSON 通道（`GenericJsonTypeHandler`） | `Customer`/`Address`/`Money` 等 `IValueObject` |
| 有独立行的子实体集合 | 独立子表 | `TrackedList` 差量同步 | `OrderItem`，只发增量 INSERT/DELETE |
| 无身份值对象集合 | 单列 JSON 数组 | 集合通道（`ListTypeHandler`） | 如颜色列表，整列 JSON，不建子表 |

判定核心：有独立身份、需独立行 → 子表；无身份、随父表替换 → JSON 列。

## 6. 一致性策略

- 无物理外键：应用层事务 + 仓储级联（先子后主）保证一致，避免孤儿行。
- 乐观锁：聚合根表 `version` 列 CAS，冲突抛 `OptimisticLockingFailureException`。
- 子集合差量：`TrackedList` 只发增量 INSERT/DELETE，不整表覆盖。

## 7. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 为无身份值对象建子表 | 并入父表 JSON 列 |
| 把有独立身份的实体塞进 JSON 列 | 独立子表 + `TrackedList` |
| 跨聚合建物理外键 | 只保留 ID 引用，无外键约束 |
| 一个实体在多张表重复存储 | 一个实体只归属一个聚合、一张表 |
| 子表不加逻辑外键索引 | `order_id` 建普通索引 |
| 聚合根表缺 `version` 列 | 预留版本列做 CAS |
| 表缺审计列 | 预留 `created_at/by`、`updated_at/by` |
| 逻辑外键映射进子实体 | 逻辑外键不入领域、不入 resultMap |

## 8. 被读于

S3 `ddd-infra-config`、S4 `ddd-aggregate-create`、S7 `ddd-code-review`。
