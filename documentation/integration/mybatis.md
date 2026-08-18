# MyBatis 集成

> 本文档面向已实现 `pragmatic-ddd-core` 领域模型的开发者，说明 `pragmatic-ddd-mybatis` 如何将领域类型（枚举 / 值对象 / 集合）持久化到关系型数据库，以及 Outbox 落库与号段 ID 生成的用法。

## 1. 概述

### 1.1 核心定位

`pragmatic-ddd-mybatis` 在 MyBatis 的 `TypeHandler` 扩展点上，提供三类开箱即用的类型处理器，把领域类型无损映射到数据库列：

- **枚举通道**：单列存储枚举，持久化形态可在 `code / name / label / ordinal` 间切换。
- **JSON 通道**：值对象整体写入原生 JSON 列（MySQL `JSON` / PostgreSQL `jsonb`）。
- **集合通道**：单列 JSON 数组存储 `List<T>`，按列标签精确还原泛型。

配套提供 `TypeHandlerContext` 统一装配以上三通道，以及 `MybatisOutboxStore`、`DbSegmentAllocator` 两个基础设施实现。

**它解决的典型场景问题**：

- **枚举无法按业务 code 存储、且每个枚举都要手写 handler**：MyBatis 默认 enum 处理只能存 `name`/`ordinal`，拿不到"存 `CREATED` 而非 `0`"的稳定业务码；`UniversalEnumTypeHandler` 用单个泛型类覆盖全部枚举，避免 N 个枚举 N 个 handler 的样板代码，并补齐带 `jdbcType` 的 `getResult` 以免退回默认处理。
- **值对象没有独立表、扁平拆列会丢失结构**：`Address` 这类复合结构无需建表，`GenericJsonTypeHandler` 将其整体写入原生 JSON 列，保留嵌套结构、整体存取。
- **`List<T>` 反序列化丢失泛型**：MyBatis 原生对集合只能存文本且取回时泛型丢失；`ListTypeHandler` 按列标签精确还原 `List<String>` / `List<OrderStatus>` 等具体类型。
- **枚举在单列与 JSON 内双通道配置漂移**：`TypeHandlerContext` 让枚举通道与 JSON 通道共用同一 `EnumValueResolver` / `JsonSerializer` / `enumRules`，杜绝同枚举在两处解析结果不一致。

### 1.2 模块依赖与类型关系

```text
pragmatic-ddd-mybatis
  ├── typehandler.enums
  │     ├── UniversalEnumTypeHandler   (单泛型 MyBatis TypeHandler)
  │     ├── EnumValueResolver          (注册表 / 运行期 O(1) 查表)
  │     ├── EnumRule                   (枚举持久化形态枚举)
  │     ├── EnumCodec (SPI)            (code 提取 / 归一化)
  │     └── DefaultEnumCodec           (按 IEnumValue.getValue())
  ├── typehandler.json
  │     ├── GenericJsonTypeHandler     (登记 IValueObject)
  │     ├── Fastjson2JsonSerializer    (策略感知, 实现 core IEventSerializer)
  │     ├── JsonSerializer (SPI)
  │     └── JdbcJsonValue / PgJdbcJsonValue (驱动适配)
  ├── typehandler.list
  │     ├── ListTypeHandler            (注册到 List.class)
  │     ├── CollectionMapping          (字段→列标签 映射)
  │     ├── CollectionElementTypeConfig(构建期冲突校验)
  │     └── ElementConverter (SPI)     (元素级转换钩子)
  ├── TypeHandlerContext (record)      (三通道统一装配)
  ├── MybatisOutboxStore              (实现 core IOutboxStore)
  └── DbSegmentAllocator              (实现 core IIdSegmentAllocator)

依赖关系：
  enumRules / EnumValueResolver ──┐
                                  ├─► TypeHandlerContext ──► SqlSessionFactory
  JsonSerializer / JdbcJsonValue ─┤
  CollectionElementTypeConfig ────┘
  EnumValueResolver、JsonSerializer 被 JSON 通道与枚举通道共用（单点来源）。
```

> 本文档聚焦 typehandler 三大通道（第 2~4 节）；Outbox 与号段 ID 见第 5、6 节。

### 1.3 前置概念

阅读第 2 节前，需认识以下来自 `io.pragmatic.ddd.base` 或 MyBatis 的基础术语：

| 术语 | 来源 | 含义 |
|------|------|------|
| `IEnumValue<V, E>` | core（`io.pragmatic.ddd.base`） | 枚举接口，提供业务 code（`getValue()`）与展示名（`getName()`），是 `CODE` / `LABEL` 策略的数据来源 |
| `IValueObject` | core（`io.pragmatic.ddd.base`） | 值对象标记接口；实现它的类可被 `GenericJsonTypeHandler` 整体写入 JSON 列 |
| `columnLabel` | MyBatis 结果集 | 查询结果列的标签，对应 SQL 的 `AS` 别名（或缺省列名）；集合通道靠它精确还原泛型 |
| `TypeHandler` | MyBatis | MyBatis 类型处理器扩展点，负责 Java 类型与 JDBC 类型的互转 |

> 只要领域模型已实现 core 的 `IEnumValue` / `IValueObject`，即可直接接入本模块，无需额外改造。

## 2. 核心概念详解

### 2.1 枚举通道

#### 契约与形态：EnumRule

枚举持久化形态由 `EnumRule` 决定：

| 成员 | 持久化来源 | 反序列化索引 |
|------|-----------|-------------|
| `NAME` | `Enum.name()` | name 索引 |
| `ORDINAL` | `Enum.ordinal()` | ordinal 索引（所有枚举可用） |
| `CODE` | `IEnumValue.getValue()` | value 索引（默认策略） |
| `LABEL` | `IEnumValue.getName()` | label 索引 |

> `EnumRule` 位于 `io.pragmatic.ddd.mybatis.typehandler.enums`；`IEnumValue`、`IValueObject` 来自 `io.pragmatic.ddd.base`。

#### 组件能力：EnumValueResolver

| 成员 | 类型 | 说明 |
|------|------|------|
| `EnumValueResolver()` | 构造 | 默认策略 `CODE` |
| `register(Class, EnumRule?)` | `void` | 注册单个枚举；可显式指定策略 |
| `registerAll(List<Class>)` | `void` | 批量，统一使用默认策略 |
| `registerAll(Map<Class, EnumRule>)` | `void` | 批量，各自策略 |
| 运行期查表 | O(1) | 反序列化只查预建索引，零反射 |

`EnumCodec`（SPI）定义 code 提取与归一化：`toCode(Enum)` 提取 code，`normalize(Object)` 默认原样返回（用于把 `"1"` 与 `1` 视作同一 code）。默认实现 `DefaultEnumCodec` 按 `IEnumValue.getValue()` 取值。

#### 关键设计点 / 限制条件

- 反序列化走启动期预建索引（`EnumValueIndex`），不依赖反射；索引在 `register` 时构建。
- 未实现 `IEnumValue` 的"无码枚举"仅 `ORDINAL` 索引可用，`CODE` / `LABEL` 策略不适用（无 `getValue()` / `getName()`）。
- 重复 code / name 在索引构建期即抛 `IllegalArgumentException`（fail-fast）。

#### 代码示例

```java
public enum OrderStatus implements IEnumValue<String, OrderStatus> {
    CREATED("CREATED", "已创建"),
    PAID("PAID", "已支付");

    private final String value;
    private final String name;

    OrderStatus(String value, String name) {
        this.value = value;
        this.name = name;
    }

    @Override public String getValue() { return value; }
    @Override public String getName() { return name; }
}

EnumValueResolver resolver = new EnumValueResolver();        // 默认策略 CODE
resolver.register(OrderStatus.class);                        // 按默认策略
resolver.register(OrderStatus.class, EnumRule.NAME);         // 显式策略
resolver.registerAll(Map.of(OrderStatus.class, EnumRule.CODE));
```

### 2.2 JSON 通道

#### 契约：JsonSerializer（SPI）

`JsonSerializer` 定义 JSON 读写两个平面：`PreparedStatement` 写入（`toJsonValue` / `serialize`）与读回（`fromJsonValue` / `deserialize`），并提供参数化类型重载供集合通道精确还原 `List<T>`。默认实现 `Fastjson2JsonSerializer` 同时实现 core 的 `IEventSerializer`，使 Outbox 事件与 JSON 列共用同一 JSON 行为。

#### 组件能力：GenericJsonTypeHandler + JdbcJsonValue

| 成员 | 类型 | 说明 |
|------|------|------|
| `GenericJsonTypeHandler` | TypeHandler | 仅登记 `IValueObject` 子类；列类型统一 `Types.OTHER` |
| `Fastjson2JsonSerializer` | `JsonSerializer` | 枚举策略感知；作用域私有，不污染 fastjson2 全局 |
| `JdbcJsonValue.DEFAULT` | 接口常量 | 透传 JSONObject（驱动可直接接受） |
| `JdbcJsonValue.MYSQL` | 接口常量 | 序列化为 JSON 文本字符串 |
| `PgJdbcJsonValue` | 独立类 | `new PgJdbcJsonValue()` 反射构造 `PGobject`（默认 type=jsonb） |

#### 关键设计点 / 限制条件

- 值对象必须实现 `io.pragmatic.ddd.base.IValueObject`，否则 `GenericJsonTypeHandler` 不会登记。
- `Fastjson2JsonSerializer` 定制作用域私有，仅服务于本模块，不会注册到 fastjson2 全局实例，不影响应用其它 JSON 序列化。
- MySQL 与 PostgreSQL 用不同 `JdbcJsonValue` 适配器隔离驱动差异；PostgreSQL 场景下 `PgJdbcJsonValue` 通过反射构造 `PGobject`，对 `org.postgresql` 依赖为运行期可选。

#### 代码示例

```java
public class Address implements IValueObject {
    private String province;
    private String city;
    // getter / setter
}

// MySQL：JdbcJsonValue.MYSQL；PostgreSQL：new PgJdbcJsonValue()
JsonSerializer serializer = new Fastjson2JsonSerializer(
        resolver, Map.of(OrderStatus.class, EnumRule.CODE));
```

### 2.3 集合通道

#### 契约：CollectionMapping + ElementConverter（SPI）

`CollectionMapping` 声明"实体字段 → 列标签 → 元素类型"的映射；`ElementConverter`（函数式接口，`IDENTITY` 为默认）在反序列化得到元素后做最终归一化，仅作多表隔离之外的补充兜底。

#### 组件能力：ListTypeHandler + CollectionElementTypeConfig

| 成员 | 类型 | 说明 |
|------|------|------|
| `ListTypeHandler` | TypeHandler | 单例注册到 `List.class`；按结果集 `columnLabel` 还原泛型 |
| `CollectionMapping.of(entityClass, field, elementType)` | 静态工厂 | 必须传 entityClass + field + elementType |
| `.columnLabel(String)` | 链式 | 与 SQL `AS` 别名对齐；缺省由 `table + field` 推导为 `table_field` |
| `.converter(ElementConverter)` | 链式 | 元素级转换钩子 |
| `CollectionElementTypeConfig.from(List, EnumValueResolver)` | 静态工厂 | 构建期校验冲突；枚举元素类型自动预注册 |

#### 关键设计点 / 限制条件

- `CollectionMapping` 由 `of(entityClass, field, elementType)` 构造，**不可**用 `builder()`，三者缺一不可。
- 运行期还原依赖 MyBatis 结果集的 `columnLabel`，必须与 SQL `AS` 别名或推导出的 `table_field` 一致。
- 枚举元素类型会在 `from(...)` 时自动在传入的 `EnumValueResolver` 中预注册，无需手动 `register`。

#### 代码示例

```java
CollectionMapping tags = CollectionMapping.of(Order.class, "tags", String.class)
        .columnLabel("tags")           // 与 SELECT tags AS tags 对齐
        .build();

CollectionMapping amounts = CollectionMapping.of(Order.class, "amounts", BigDecimal.class)
        .converter(ElementConverter.IDENTITY)
        .build();

CollectionElementTypeConfig config = CollectionElementTypeConfig.from(
        List.of(tags, amounts), resolver);
```

### 2.4 统一装配：TypeHandlerContext

`TypeHandlerContext` 是 `record`，构造参数顺序固定，调用一次 `registerInto(SqlSessionFactory)` 即触发三通道注册。

| 位置 | 参数 | 说明 |
|------|------|------|
| 1 | `EnumValueResolver` | 枚举注册表 |
| 2 | `JsonSerializer` | JSON 序列化器 |
| 3 | `JdbcJsonValue` | 数据库驱动适配器 |
| 4 | `Map<Class, EnumRule>` | 枚举策略 |
| 5 | `List<Class>` | 登记的 `IValueObject` 类型 |
| 6 | `CollectionElementTypeConfig` | 集合通道配置 |

```java
TypeHandlerContext ctx = new TypeHandlerContext(
        resolver,
        serializer,
        JdbcJsonValue.MYSQL,            // 或 new PgJdbcJsonValue()
        Map.of(OrderStatus.class, EnumRule.CODE),
        List.of(Address.class),
        config);

ctx.registerInto(sqlSessionFactory);
```

> 零 Spring 依赖：纯 MyBatis API 装配。Spring Boot 项目只需注入同一个 `TypeHandlerContext` 并调用 `registerInto`。

### 2.5 端到端示例

把上述三通道拼到一个 `Order` 聚合上，对应一张真实表（参考模块测试库 `type_handler_demo` 结构）：

```sql
CREATE TABLE order_demo (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    biz_name     VARCHAR(64)  NOT NULL,
    status_code  INT          DEFAULT NULL,   -- 单列枚举 CODE 策略（存 IEnumValue.getValue()）
    profile_json JSON         DEFAULT NULL,   -- JSON 值对象（Address）
    tags_json    JSON         DEFAULT NULL,   -- 集合 List<String>
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

领域模型与映射：

```java
// 1. 枚举（CODE 策略存 INT）
public enum OrderStatus implements IEnumValue<Integer, OrderStatus> {
    CREATED(1, "已创建"), PAID(2, "已支付");
    private final int value; private final String name;
    OrderStatus(int value, String name) { this.value = value; this.name = name; }
    @Override public Integer getValue() { return value; }
    @Override public String getName() { return name; }
}

// 2. 值对象（整体写 JSON 列）
public class Address implements IValueObject {
    private String province; private String city;
    // getter / setter
}

// 3. 聚合根字段
public class Order {
    private OrderStatus status;       // -> status_code 列
    private Address profile;          // -> profile_json 列
    private List<String> tags;        // -> tags_json 列
    // getter / setter
}
```

查询 SQL 与 `columnLabel` 对齐（集合列用 `AS` 暴露与 `CollectionMapping.columnLabel` 相同的值）：

```sql
SELECT id, biz_name, status_code,
       profile_json,
       tags_json AS tags          -- 与 CollectionMapping.of(Order.class,"tags",String.class).columnLabel("tags") 对齐
FROM order_demo WHERE id = #{id}
```

装配（把 2.1~2.3 的零件一次性注入 `TypeHandlerContext`）：

```java
EnumValueResolver resolver = new EnumValueResolver();
resolver.register(OrderStatus.class);

JsonSerializer serializer = new Fastjson2JsonSerializer(
        resolver, Map.of(OrderStatus.class, EnumRule.CODE));

CollectionMapping tags = CollectionMapping.of(Order.class, "tags", String.class)
        .columnLabel("tags").build();
CollectionElementTypeConfig collections =
        CollectionElementTypeConfig.from(List.of(tags), resolver);

TypeHandlerContext ctx = new TypeHandlerContext(
        resolver, serializer, JdbcJsonValue.MYSQL,
        Map.of(OrderStatus.class, EnumRule.CODE),
        List.of(Address.class), collections);

ctx.registerInto(sqlSessionFactory);   // 单次注册，三通道全部生效
```

> 上例中 `OrderStatus` 无论存为 `status_code` 单列还是嵌在 `profile_json` 内，都走同一 `resolver` 与 `EnumRule.CODE`，解析结果一致。原因见第 3.1 节。

## 3. 关键机制与避坑指南

### 3.1 枚举策略单点来源

> ⚠️ **重要约束**：枚举通道（`UniversalEnumTypeHandler`）与 JSON 通道（`Fastjson2JsonSerializer`）共用同一个 `EnumValueResolver`、`JsonSerializer` 和 `enumRules`。同一枚举无论存为单列还是嵌在 JSON 值对象内，持久化形态与解析逻辑完全一致。不要在两处分别配置不同 `EnumRule`，否则会出现单列举与值对象内枚举解析结果不一致。

### 3.2 多表同名列隔离

> ⚠️ **重要约束**：`ListTypeHandler` 按 `columnLabel` 还原泛型。当两个表存在同名列且元素类型不同（如 `a.tags` 为 `String`、`b.tags` 为 `Integer`），必须用 SQL `AS` 给出不同 `columnLabel`（或设置 `table` 让框架推导为 `table_field`）。否则 `CollectionElementTypeConfig.from` 在构建期抛出 `IllegalStateException`（"同 label + 不同类型"）。该冲突在应用启动期而非运行期暴露。

### 3.3 PostgreSQL 驱动依赖

> ⚠️ **重要约束**：`PgJdbcJsonValue` 是**独立类**而非 `JdbcJsonValue` 的字段，使用时 `new PgJdbcJsonValue()`（可传 `"json"` 改 type 为 json）。它通过反射构造 `PGobject`，运行期若报"无法构造 PGobject"，说明 `org.postgresql:postgresql` 依赖不在类路径中。`org.postgresql` 为本模块可选依赖，无需强制引入（仅 PostgreSQL 场景需要）。

### 3.4 fail-fast 清单

| 阶段 | 触发条件 | 异常 |
|------|----------|------|
| 枚举注册 | 同一枚举重复 `code` 或重复 `name` | `IllegalArgumentException` |
| 集合配置构建 | 同 `columnLabel` 对应不同类型 | `IllegalStateException` |
| 多实例注册 | 同一 `SqlSessionFactory` 重复 `registerInto` | 不抛异常，但后注册的不同策略枚举不生效（绑定首次注册时的 rule） |

## 4. 异常与错误处理体系

本模块不定义独立异常体系，复用 JDK 标准异常做启动期校验：

```text
RuntimeException
  ├── IllegalArgumentException   (EnumValueIndex: 重复 code / name)
  └── IllegalStateException     (CollectionElementTypeConfig: 同 label 不同类型冲突)
```

最佳实践：以上异常均为**启动期**抛出，应在应用启动阶段暴露并终止，不应在业务代码层捕获；修复方式为调整枚举 code 唯一性或为冲突列标设置不同 `columnLabel`。运行期（SQL 执行）异常由 MyBatis / JDBC 原样传递，本模块不拦截。

## 5. Outbox 落库（MybatisOutboxStore）

**解决什么问题**：领域事件若在业务事务提交后再发消息，会出现"数据库已写、消息未发"或"消息已发、事务回滚"的不一致。`MybatisOutboxStore` 把事件作为普通行写入同一事务的 Outbox 表，由可靠投递机制（详见核心文档领域事件章节）异步捞出并发送，从而用本地事务保证"业务数据 + 事件"的原子性。

`MybatisOutboxStore` 实现 core 的 `IOutboxStore`：

```java
// 注册 OutboxMapper（同包同名 OutboxMapper.xml 自动加载绑定）
sqlSessionFactory.getConfiguration().addMapper(OutboxMapper.class);

// 构造签名：OutboxMapper + TransactionOperations（最小事务抽象 SPI，由使用方按技术栈实现）
OutboxMapper mapper = sqlSession.getMapper(OutboxMapper.class);
IOutboxStore store = new MybatisOutboxStore(mapper, txOps);
store.store(List.of(outboxMessage));  // 在调用方事务内执行，与聚合同事务落库
```

## 6. 号段 ID 生成（DbSegmentAllocator）

**解决什么问题**：分布式下需要高性能、趋势递增且不强依赖中心化发号（如雪花算法对时钟敏感）的业务 ID。`DbSegmentAllocator` 实现 core 的 `IIdSegmentAllocator`，从数据库号段表一次性拉取一段 `[current_max_id, current_max_id + step)` 缓存在本地，用完再续段，把数据库访问从"每次发号"降为"每段一次"，兼顾性能与单调趋势。

```java
IIdSegmentAllocator allocator = new DbSegmentAllocator(sqlSessionFactory);
long id = allocator.nextId("order");
```

## 7. 建表脚本

> 以下脚本以模块 `src/main/resources` 中的 schema 为准（`outbox-schema-mysql.sql` / `id-segment-schema-mysql.sql`）。PostgreSQL 场景请参考同类 schema 文件并适配 `JSON` 类型为 `jsonb`。

### 7.1 Outbox 表（outbox_message）

```sql
CREATE TABLE IF NOT EXISTS outbox_message (
    id             VARCHAR(36)   NOT NULL,
    aggregate_id   VARCHAR(128)  NOT NULL,
    aggregate_type VARCHAR(255)  NOT NULL,
    event_type     VARCHAR(255)  NOT NULL,
    entity_id      VARCHAR(128),
    payload        LONGTEXT      NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    attempts       INT           NOT NULL DEFAULT 0,
    queue          INT           NOT NULL DEFAULT 0,
    created_at     DATETIME(3)   NOT NULL,
    claimed_at     DATETIME(3),
    sent_at        DATETIME(3),
    last_error     VARCHAR(2000),
    claim_token    VARCHAR(36),
    PRIMARY KEY (id),
    INDEX idx_outbox_status_created (status, created_at),
    INDEX idx_claim_token (claim_token)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

### 7.2 号段 ID 表（id_segment）

```sql
CREATE TABLE IF NOT EXISTS id_segment (
    biz_key         VARCHAR(64)   NOT NULL,
    current_max_id  BIGINT        NOT NULL,
    step            INT           NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0,
    remark          VARCHAR(128)  DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (biz_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ID 号段分配表';
```

## 8. 总结速查

| 概念 | 使用方式 | 最关键约束 |
|------|----------|------------|
| `EnumRule` | 在 `EnumValueResolver.register` / `TypeHandlerContext` 构造参数指定 | 默认 `CODE`；无码枚举仅 `ORDINAL` 可用 |
| `EnumValueResolver` | `register` / `registerAll` 启动期注册 | 反序列化 O(1) 查表；重复 code 启动期抛 `IllegalArgumentException` |
| `GenericJsonTypeHandler` | 值对象实现 `IValueObject` 并登记到 `TypeHandlerContext` | 列类型 `Types.OTHER`；`Fastjson2JsonSerializer` 作用域私有 |
| `JdbcJsonValue` / `PgJdbcJsonValue` | MySQL 用常量，PostgreSQL `new PgJdbcJsonValue()` | PostgreSQL 需 `org.postgresql` 运行期依赖 |
| `CollectionMapping` | `of(entityClass, field, elementType).columnLabel(...)` | 不可 `builder()`；三者缺一不可 |
| `ListTypeHandler` | 单例注册 `List.class`，由 `columnLabel` 还原泛型 | 同名列不同类型须用不同 `columnLabel` 隔离（启动期 `IllegalStateException`） |
| `TypeHandlerContext` | `record(6 参数)` → `registerInto(sqlSessionFactory)` | 枚举策略单点来源，勿分头配置 |
| `MybatisOutboxStore` | `new MybatisOutboxStore(mapper, txOps)`（需先 `addMapper(OutboxMapper.class)`） | 实现 core `IOutboxStore`；store 走调用方事务，补偿操作走 `txOps` 独立短事务 |
| `DbSegmentAllocator` | `new DbSegmentAllocator(sqlSessionFactory)` | 实现 core `IIdSegmentAllocator` |
