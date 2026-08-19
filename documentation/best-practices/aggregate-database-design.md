# 聚合的数据库设计原则

> 本文档介绍使用 Pragmatic DDD 进行聚合数据库设计的最佳实践：先明确"一个聚合对应一组表、一张主表还是多张子表"的判定标准，再给出表结构设计的字段与列映射规范，最后落到复杂类型存储形态与一致性策略。

## 1. 核心原则：表边界 = 聚合边界

一个聚合对应**一组表**：聚合根一张主表，聚合内**有独立身份的实体**各自成一张子表，无身份的内嵌值对象并入父表。数据库表的设计**跟随聚合边界**，聚合是多表还是单表，不是拍脑袋，而是由聚合内子对象的形态决定。

```text
Order 聚合
├─ t_order          聚合根主表（订单字段 + Customer/Address/Money 等 JSON 值对象 + version 乐观锁列）
└─ t_order_item     聚合内子实体表（OrderItem，有独立 id，TrackedList 差量同步）
```

**不跨聚合建物理外键**：聚合间通过 ID 引用（如 `Order` 持有 `customerId`），表之间不建立物理外键约束，一致性由应用层 + 单聚合事务保证。

---

## 2. 一表还是多表：判定标准

一个聚合到底该一张表还是多张表，用下面的判定标准递进判断。

### 2.1 判定一：子对象是否有独立 DB 行

先看子对象**有没有独立行身份**。框架的 `TrackedList` 类注释给出了明确边界：

> `TrackedList` 只针对**有独立 DB 行的对象类型**（实体或独立表值对象），不处理基础类型或无身份的内嵌值对象。内嵌值对象的集合整体随父表替换，无需本容器。

| 子对象形态 | 表设计 | 例子 |
| --- | --- | --- |
| 有独立 ID、独立行的实体 | **独立子表** | `OrderItem`（有 `id`，可单独 INSERT/DELETE） |
| 无身份的内嵌值对象 | **父表 JSON 列** | `Customer`、`Address`、`Money` 等 `IValueObject` |

**Order 的取舍**：`OrderItem` 有 `id`、`order_id`、独立行，能被增删 → **独立子表** `t_order_item`；`Customer`、`Address`、`Money` 是内嵌值对象，没有独立身份，不单独成表 → **整列 JSON** 存在 `t_order` 上。

### 2.2 判定二：子实体是否需要独立访问

即使有 ID，还要看它的**生命周期是否跟父聚合一致**：

- 子实体**只在父聚合上下文中存在**，从不被单独查询、单独更新、单独引用 → 留在聚合内，用子表 + `TrackedList` 差量。
- 子实体**可能被其他聚合独立访问**（单独查、被别的聚合引用）→ 说明它可能是独立聚合，应拆出去，而不是硬塞进当前聚合。

Order 里 `OrderItem` 只在订单上下文存在，随订单一起增删，→ 归属订单聚合的子表。

### 2.3 判定三：单表 vs 多表的读写取舍

**单张表（内嵌值对象全用 JSON 列）**：

- 适用：聚合根内部全是无身份值对象，没有需独立行的子实体。
- 优点：表少、join 少、一次读完整聚合、事务简单。
- 缺点：JSON 列内的字段无法用 SQL 单独过滤/索引/统计。

**多张表（子实体独立子表）**：

- 适用：聚合内有有独立身份的实体（如 `OrderItem`），需增删、数量可能很多、要按子行过滤/统计。
- 优点：子行可单独 CRUD（`TrackedList` 差量）、可按子表字段建索引查询、避免 JSON 列过大。
- 缺点：表多、需级联维护、事务跨表。

### 2.4 多张表的"多"怎么定

不是"一个实体一张表"无限铺开，而是：

1. **聚合根 = 一张主表**（`t_order`）：聚合根自身字段 + 内嵌值对象（JSON 列）。
2. **每个有独立身份的聚合内实体 = 一张子表**（`t_order_item`）。
3. **一个实体只归属一个聚合、只一张表**，不会出现在多张表重复存储。

---

## 3. 合理性三问检验法

一组表设计得是否合理，用三个问题检验，**都通过才算合理**：

1. **一致性**：这一组表是否总能在**同一个事务**内保持一致？→ Order 两张表在 `@Transactional` 内整存整取，能保证。
2. **边界**：子实体是否从不被聚合外单独访问/修改？→ `OrderItem` 只随 `Order` 增删，成立。
3. **读写**：有没有因"一张表塞太多 JSON 导致没法查"或"多张表 join 太重"？→ Order 子表可独立过滤，合理。

若某个子实体越界被独立访问，或某块数据只是"临时聚合"不需要一致性，就应拆成独立聚合/独立表，而不是硬塞进聚合。

> **一句话**：一张主表 + N 张子实体表（N = 聚合内独立实体数），并以"同一事务一致性 + 边界不外泄 + 读写顺畅"三项检验合理性。

---

## 4. 表结构设计规范

### 4.1 主键与标识

- **聚合根表主键** = 聚合根 `entityId`（如 `order_id`），由应用层或 ID 生成器（`IdSegmentMapper`）生成，非自增。
- **子实体表主键** = 子实体自身 ID（`id`），`order_id` 作为**逻辑外键**（过滤键），不是领域属性，不映射进 `OrderItem`，仅用于查询与级联。

### 4.2 字段与列映射

- **显式映射**：每个领域属性对应一列，列名下划线、属性驼峰，靠 `mapUnderscoreToCamelCase` 自动映射 + 显式 resultMap。
- **逻辑外键不入领域**：`order_id`（对子实体而言）是查询过滤键，不纳入 resultMap 显式映射、不作为领域属性。
- **审计列统一**：`created_at` / `created_by` / `updated_at` / `updated_by` 四列由框架父类托管，表必须预留。
- **版本列**：聚合根表必须有 `version` 列，作为乐观锁基线（读入 `oldVersion`，更新时 CAS）。

### 4.3 建表示例（Order）

```sql
-- 聚合根主表：t_order
CREATE TABLE t_order (
    order_id            BIGINT       NOT NULL COMMENT '订单标识（聚合根 entityId）',
    customer            JSON         NOT NULL COMMENT '客户值对象',
    status              INT          NOT NULL COMMENT '订单状态（枚举 CODE）',
    shipping_address    JSON         NULL COMMENT '收货地址值对象',
    currency            VARCHAR(8)   NOT NULL COMMENT '币种',
    total_amount        JSON         NULL COMMENT '金额值对象',
    remark              VARCHAR(255) NULL,
    paid_at             DATETIME     NULL,
    cancel_reason       VARCHAR(255) NULL,
    payment_method      INT          NULL COMMENT '支付方式（枚举 CODE）',
    logistics_info      JSON         NULL COMMENT '物流信息值对象',
    payment_serial_no   VARCHAR(64)  NULL,
    platform_discount   JSON         NULL COMMENT '优惠金额值对象',
    actual_amount       JSON         NULL COMMENT '实付金额值对象',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NULL,
    version             BIGINT       NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    PRIMARY KEY (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单聚合根表';

-- 聚合内子实体表：t_order_item
CREATE TABLE t_order_item (
    id             BIGINT       NOT NULL COMMENT '订单项标识',
    order_id       BIGINT       NOT NULL COMMENT '逻辑外键（查询过滤键，非领域属性）',
    product_id     BIGINT       NOT NULL,
    product_name   VARCHAR(128) NOT NULL,
    spec           VARCHAR(64)  NULL,
    price          JSON         NULL COMMENT '金额值对象',
    quantity       INT          NOT NULL,
    subtotal       JSON         NULL COMMENT '金额值对象',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     VARCHAR(64)  NULL,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     VARCHAR(64)  NULL,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)  -- 逻辑外键索引，无物理外键约束
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单项子实体表';
```

---

## 5. 复杂类型的存储形态

复杂类型通过 TypeHandler 三通道落地，**存储形态由对象性质决定**：

| 类型 | 存储形态 | 通道 | 说明 |
| --- | --- | --- | --- |
| 单列枚举 | 整数值 | 枚举通道（`EnumRule.CODE`） | `status`、`payment_method` 存业务 value |
| 内嵌值对象 | 整列 JSON | JSON 通道（`GenericJsonTypeHandler`） | `Customer`、`Address`、`Money` 等 `IValueObject` |
| 有独立行的子实体集合 | 独立子表 | `TrackedList` 差量同步 | `OrderItem`，只发增量 INSERT/DELETE |
| 无身份值对象集合 | 单列 JSON 数组 | 集合通道（`ListTypeHandler`） | 如颜色列表，整列 JSON，不建子表 |

**判定核心**：有独立身份、需独立行 → 子表；无身份、随父表替换 → JSON 列。不要为无身份值对象集合建子表，也不要把有独立身份的实体压进 JSON 列。

---

## 6. 一致性策略

- **无物理外键**：靠应用层事务 + 仓储级联（先子后主）保证一致性，避免孤儿行。
- **乐观锁**：聚合根表 `version` 列做 CAS，冲突抛 `OptimisticLockingFailureException`。
- **子集合差量**：`TrackedList` 只发增量 INSERT/DELETE，不整表覆盖。

---

## 7. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 为无身份值对象建子表 | 表爆炸、join 重 | 并入父表 JSON 列 |
| 把有独立身份的实体塞进 JSON 列 | 无法按子行过滤/统计/增删 | 独立子表 + `TrackedList` |
| 跨聚合建物理外键 | 锁竞争、性能差、边界模糊 | 只保留 ID 引用，无外键约束 |
| 一个实体在多张表重复存储 | 数据冗余、一致难保证 | 一个实体只归属一个聚合、一张表 |
| 子表不加逻辑外键索引 | 子表过滤慢 | `order_id` 建普通索引 |
| 聚合根表缺 `version` 列 | 乐观锁失效 | 预留版本列做 CAS |
| 表缺审计列 | 无法追踪 | 预留 `created_at/by`、`updated_at/by` |
| 逻辑外键映射进子实体 | 领域模型污染 | 逻辑外键不入领域、不入 resultMap |

---

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根边界、`TrackedList` 子集合、版本号与乐观锁
- [仓储设计原则](./repository-design.md)：仓储如何整存整取聚合、子集合差量同步
- [MySQL 配置设计原则](./mysql-config.md)：TypeHandler 三通道装配、全局开关
- [值对象最佳实践](./value-object.md)：值对象 JSON 序列化往返
- [枚举值](./enum-value.md)：枚举按 CODE 持久化
- [事务性发件箱](./transactional-outbox.md)：`OutboxMapper` 与异构事件投递
