# 聚合数据库设计原则

> 本文档介绍使用 Pragmatic DDD 进行聚合数据库设计的最佳实践：先明确「一个聚合对应一组表、一张主表还是多张子表」的判定标准，再逐个值对象决定「展开成列还是整列 JSON」，最后落到表结构规范、复杂类型存储形态与一致性策略。
>
> 全文分两层判定：**第一层**（§2）集合怎么落表；**第二层**（§3）值对象怎么落列。两层相互独立，先定第一层，再逐个值对象定第二层。

## 1. 核心原则：表边界 = 聚合边界

一个聚合对应**一组表**：聚合根一张主表，聚合内**有独立身份的实体**各自成一张子表，无身份的内嵌值对象并入父表。数据库表的设计**跟随聚合边界**，聚合是多表还是单表，不是拍脑袋，而是由聚合内子对象的形态决定。

```text
Order 聚合
├─ t_order          聚合根主表（基础列 + 金额展开列 + Customer/Address 等 JSON + search_* 生成列 + version）
└─ t_order_item     聚合内子实体表（OrderItem，有独立 id，TrackedList 差量同步）
```

**不跨聚合建物理外键**：聚合间通过 ID 引用（如 `Order` 持有 `customerId`），表之间不建立物理外键约束，一致性由应用层 + 单聚合事务保证。

---

## 2. 第一层判定：集合怎么落表

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

## 3. 第二层判定：值对象怎么落列（形态 A / B）

集合落表定下来后，再逐个值对象决定列形态。**选择粒度是值对象，不是表、不是聚合**：同一张表可以混用两种形态，但**同一个值对象只能选一种**（不允许 JSON + 同义展开列双存）。

### 3.1 两种形态

| 项 | **形态 A：展开平铺** | **形态 B：JSON + 派生列** |
| --- | --- | --- |
| 值对象怎么落 | 拆成业务事实列 | 整列 JSON / JSONB |
| 写权威 | 列 | JSON |
| 查询列 | 列本身 | `search_*` 生成列（**只许生成列，不许自建**） |
| 加字段成本 | DDL 迁移 | 零 DDL |
| 改结构成本 | DDL 迁移 | 数据迁移（`JSON_SET` / `jsonb_set`） |
| 数据库级约束 | 原生支持 | 生成列 + 唯一索引 |
| BI 直连 | 友好 | 不友好（需视图） |
| 子表适用 | 适用 | 适用 |

**默认形态是 B**；A 是准入制，满足 §3.4 的判据才能用。

### 3.2 形态 A 的硬规则

1. **同一值对象的全部列必须在同一条 INSERT / UPDATE 内写全**——部分列更新会在并发下产生「半个值对象」；
2. **resultMap 用嵌套映射**（多列 → 值对象属性），领域层不感知列，`equalityComponents()` 不变；
3. **可空性与 DDL 对齐**：值对象里不可空的字段，列必须 `NOT NULL`；
4. **不得再给该值对象建同义生成列**——列已是权威，再挂派生列就是两个来源。

```sql
-- Money 展开为两列
amount_value DECIMAL(18, 2) NOT NULL COMMENT '金额数值（元）',
currency     CHAR(3)        NOT NULL COMMENT '币种'
```

```xml
<!-- 多列 → 一个值对象：嵌套属性映射，不走 JSON TypeHandler -->
<result property="totalAmount.amount"   column="amount_value"/>
<result property="totalAmount.currency" column="currency"/>
```

写入侧必须成对出现：`#{totalAmount.amount}` / `#{totalAmount.currency}`，且在同一条语句里。

### 3.3 形态 B 的硬规则

1. **JSON 是唯一写入口**：值对象整体替换，不做 `JSON_SET` 式局部更新；
2. **`search_*` 只能是生成列**：由库从 JSON 推导，应用不写；
3. **生成列不进领域模型、不进 INSERT / UPDATE、不得用 `SELECT *`**：它只被查询侧使用，写回聚合即退化成第二事实源；
4. **每个生成列必须建索引**，且每表 `search_*` 预算 ≤ 5；
5. **生成列只能做单行内、确定性的推导**（见 §3.5 能力缺口）。

```sql
-- MySQL 8：JSON_EXTRACT 是确定性的，STORED 可建索引
ALTER TABLE t_order
    ADD COLUMN search_amount DECIMAL(18, 2)
        GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(actual_amount, '$.amount')) AS DECIMAL(18, 2))) STORED,
    ADD KEY idx_order_search_amount (search_amount);
```

```sql
-- PostgreSQL：必须是 immutable 函数；json_extract_path_text 是 stable，不能用于生成列
ALTER TABLE t_order
    ADD COLUMN search_amount NUMERIC(18, 2)
        GENERATED ALWAYS AS (((actual_amount ->> 'amount')::numeric)) STORED;
CREATE INDEX idx_order_search_amount ON t_order (search_amount);
```

**STORED 还是 VIRTUAL**：需要等值 / 范围过滤默认 `STORED`（可建索引、读无额外计算）；仅取值计算、不建索引时用 `VIRTUAL`。

### 3.4 判据：默认 B，A 需准入

| 判据（满足任一才用 A） | 说明 |
| --- | --- |
| 该值对象**全部字段**都是查询 / 约束依据 | 不是「个别字段要查」 |
| 该值对象结构**极稳定**（预计一年不变） | 如 `Money`、`GeoPoint`、`DateRange` |
| 需要**数据库级约束**（唯一 / 非空 / 条件约束） | JSON 表达不了 |
| 外部系统**必须直连平表**且不允许加视图 | 且只用于只读副本表 |

一句话：**A 给稳定的核心值对象，B 给演进中的业务数据。**

### 3.5 生成列解决不了的需求（必须转读侧）

| # | 需求 | 为什么生成列做不到 | 正确解法 |
| --- | --- | --- | --- |
| G1 | 跨行汇总（如订单总额 = Σ 明细） | 不能跨行 / 子查询 | 视图 / 直读 JOIN / 同事务内维护业务事实列 |
| G2 | 外部数据（客户名、汇率、字典翻译） | 依赖其他表或外部系统 | 读侧 JOIN / ES 投影；不进写表 |
| G3 | 全文 / 分词检索 | 生成列只能等值 / 前缀 | MySQL `FULLTEXT`、PG `GIN(to_tsvector(...))`、ES |
| G4 | JSON 数组包含查询 | 生成列产出标量 | MySQL 多值索引、PG `GIN` |

两个易踩的细节：枚举 `name` 不用 `CASE` 生成列（枚举一加值就要改 DDL，改为读侧 `of(code).getName()` 回填）；时区 / 本地日期统一 **UTC 存、读侧换算**。

### 3.6 迁移：A 与 B 之间没有免费通道

| 方向 | 步骤 | 代价 |
| --- | --- | --- |
| B → A | 加列 → 回填 → 改 resultMap / SQL → 切写 → 删 JSON | 高，需窗口或双写期 |
| A → B | `JSON_SET` 回填 JSON → 双读兼容 → 切写 → 删列 | 高，需窗口或双写期 |

因此**形态必须在设计期定**，不做「先随便选一个」。

### 3.7 台账：形态必须登记

形态选择**无法从表结构反推**（JSON 列看不出它为什么不是平铺）。因此在聚合事实文档（`design-doc-facts`）里登记两项：

| 登记项 | 内容 |
| --- | --- |
| 表形态 | 每张表用了哪些 A 型值对象与 B 型值对象 |
| search 列台账 | JSON path → 列名 → 单位 / 精度 → 索引名 |

## 4. 合理性三问检验法

一组表设计得是否合理，用三个问题检验，**都通过才算合理**：

1. **一致性**：这一组表是否总能在**同一个事务**内保持一致？→ Order 两张表在 `@Transactional` 内整存整取，能保证。
2. **边界**：子实体是否从不被聚合外单独访问/修改？→ `OrderItem` 只随 `Order` 增删，成立。
3. **读写**：有没有因"一张表塞太多 JSON 导致没法查"或"多张表 join 太重"？→ Order 子表可独立过滤，合理。

若某个子实体越界被独立访问，或某块数据只是"临时聚合"不需要一致性，就应拆成独立聚合/独立表，而不是硬塞进聚合。

> **一句话**：一张主表 + N 张子实体表（N = 聚合内独立实体数），并以"同一事务一致性 + 边界不外泄 + 读写顺畅"三项检验合理性。
>
> **形态复核**：每个值对象是否已按 §3.4 选定 A / B 并登记台账（§3.7）；未登记即视为未完成。

---

## 5. 表结构设计规范

### 5.1 主键与标识

- **聚合根表主键** = 聚合根 `entityId`（如 `order_id`），由应用层或 ID 生成器（`IdSegmentMapper`）生成，非自增。
- **子实体表主键** = 子实体自身 ID（`id`），`order_id` 作为**逻辑外键**（过滤键），不是领域属性，不映射进 `OrderItem`，仅用于查询与级联。

### 5.2 字段与列映射

- **值对象默认整列 JSON**（形态 B）；只有满足 §3.4 准入的才展开成列（形态 A），**粒度是值对象不是字段**。
- **派生列只能是生成列**：`search_*` 由 JSON 推导，不进领域模型、不进 INSERT / UPDATE、不用 `SELECT *`。
- **显式映射**：列名下划线、属性驼峰，靠 `mapUnderscoreToCamelCase` 自动映射 + 显式 resultMap；A 型值对象用嵌套属性映射（`totalAmount.amount`）。
- **逻辑外键不入领域**：`order_id`（对子实体而言）是查询过滤键，不纳入 resultMap 显式映射、不作为领域属性。
- **审计列统一**：`created_at` / `created_by` / `updated_at` / `updated_by` 四列由框架父类托管，表必须预留。
- **版本列**：聚合根表必须有 `version` 列，作为乐观锁基线（读入 `oldVersion`，更新时 CAS）。
- **业务唯一号与 `entity_id` 区分**：两者不同时，业务号单独成列 + 唯一索引；不要塞进 JSON。

### 5.3 建表示例（Order）

```sql
-- 聚合根主表：t_order（A / B 混用示例）
CREATE TABLE t_order (
    order_id            BIGINT       NOT NULL COMMENT '订单标识（聚合根 entityId）',
    customer            JSON         NOT NULL COMMENT '客户值对象（B）',
    status              INT          NOT NULL COMMENT '订单状态（枚举 CODE）',
    shipping_address    JSON         NULL COMMENT '收货地址值对象（B）',
    amount_value        DECIMAL(18, 2) NOT NULL COMMENT '实付金额数值（A：Money 展开）',
    currency            CHAR(3)      NOT NULL COMMENT '币种（A：Money 展开）',
    remark              VARCHAR(255) NULL,
    paid_at             DATETIME     NULL,
    payment_method      INT          NULL COMMENT '支付方式（枚举 CODE）',
    search_customer_id  BIGINT
        GENERATED ALWAYS AS (CAST(JSON_UNQUOTE(JSON_EXTRACT(customer, '$.customerId')) AS UNSIGNED)) STORED
        COMMENT '派生列：按客户筛选（生成列，不进领域模型）',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(64)  NULL,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          VARCHAR(64)  NULL,
    version             BIGINT       NOT NULL DEFAULT 1 COMMENT '乐观锁版本',
    PRIMARY KEY (order_id),
    KEY idx_order_search_customer (search_customer_id)
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

## 6. 复杂类型的存储形态

复杂类型通过 TypeHandler 三通道落地，**存储形态由对象性质决定**：

| 类型 | 存储形态 | 通道 | 说明 |
| --- | --- | --- | --- |
| 单列枚举 | 整数值 | 枚举通道（`EnumRule.CODE`） | `status`、`payment_method` 存业务 value |
| 内嵌值对象 · 形态 B | 整列 JSON | JSON 通道（`GenericJsonTypeHandler`） | 默认；`Customer`、`Address` 等 `IValueObject` |
| 内嵌值对象 · 形态 A | 多列展开 | resultMap 嵌套属性映射 | 需全列同写；`Money`、`GeoPoint` 等稳定值对象 |
| JSON 内字段的查询列 | `search_*` 生成列 | 库自动推导 | 只许生成列，必建索引，不进领域模型 |
| 有独立行的子实体集合 | 独立子表 | `TrackedList` 差量同步 | `OrderItem`，只发增量 INSERT/DELETE |
| 无身份值对象集合 | 单列 JSON 数组 | 集合通道（`ListTypeHandler`） | 如颜色列表，整列 JSON，不建子表 |

**判定核心**：有独立身份、需独立行 → 子表；无身份、随父表替换 → JSON 列。不要为无身份值对象集合建子表，也不要把有独立身份的实体压进 JSON 列。

---

## 7. 一致性策略

- **无物理外键**：靠应用层事务 + 仓储级联（先子后主）保证一致性，避免孤儿行。
- **乐观锁**：聚合根表 `version` 列做 CAS，冲突抛 `OptimisticLockingFailureException`。
- **子集合差量**：`TrackedList` 只发增量 INSERT/DELETE，不整表覆盖。

---

## 8. 常见反模式

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
| 同一值对象既 JSON 又手写展开列 | 两个写入口，必然漂移 | 一个值对象只选一种形态 |
| 自建 `search_*` 列（应用写） | 派生值与 JSON 双写，必然不一致 | `search_*` 只能是生成列 |
| 生成列出现在 INSERT / UPDATE，或被映射进聚合 | 派生退化成事实，写回会覆盖权威值 | 生成列只读、只给查询侧 |
| 建了 `search_*` 却不建索引 | 等于没加，还多一份维护成本 | 每个生成列配一个索引 |
| 展开式写值对象时只更新部分列 | 并发下产生半个值对象 | 同一值对象全列同一条语句写全 |
| 用生成列做跨行汇总 / 字典翻译 / 全文检索 | 生成列只能做单行确定性推导 | 转读侧：视图 / 直读 / ES / FULLTEXT / GIN |
| `search_*` 里放手机号、证件号等敏感字段 | 绕过列级脱敏与权限 | 敏感字段不进派生列，按需脱敏后再派生 |

---

## 下一步

- [聚合设计原则](./aggregate-design.md)：聚合根边界、`TrackedList` 子集合、版本号与乐观锁
- [仓储设计原则](./repository-design.md)：仓储如何整存整取聚合、子集合差量同步
- [MyBatis 配置设计原则](./mybatis-config.md)：TypeHandler 三通道装配、全局开关与框架固定 Mapper
- [值对象最佳实践](./value-object.md)：值对象 JSON 序列化往返
- [枚举值](./enum-value.md)：枚举按 CODE 持久化
- [Outbox 链路装配](./outbox-config.md)：`OutboxMapper` 与异构事件投递
