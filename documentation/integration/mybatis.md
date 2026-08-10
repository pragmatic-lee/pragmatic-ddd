# MyBatis 集成

> 本文档介绍 `pragmatic-ddd-mybatis` 模块的使用，包括 TypeHandler 装配、Outbox 落库与号段 ID 生成。

## 1. 概述

`pragmatic-ddd-mybatis` 提供以下能力：

| 组件 | 作用 |
| --- | --- |
| **枚举通道** | `UniversalEnumTypeHandler` — 单泛型类覆盖所有枚举，`EnumRule` 控制持久化形态 |
| **JSON 通道** | `GenericJsonTypeHandler` — 值对象整体序列化到 JSON 列 |
| **集合通道** | `ListTypeHandler` — 单列 JSON 数组，按列标签精确还原泛型 |
| **TypeHandlerContext** | 统一装配上下文，一次性注册三大通道 |
| **MybatisOutboxStore** | Outbox 表的 MyBatis 实现（`IOutboxStore`） |
| **DbSegmentAllocator** | 数据库号段 ID 生成器（`IIdSegmentAllocator`） |

::: tip 零 Spring 依赖
所有组件纯 MyBatis API 装配，Spring Boot 项目和非 Spring 项目均可使用。
:::

## 2. 引入依赖

```xml
<dependency>
    <groupId>io.pragmatic.ddd</groupId>
    <artifactId>pragmatic-ddd-mybatis</artifactId>
</dependency>
```

## 3. TypeHandler 装配

### 3.1 枚举通道

枚举持久化通过 `EnumRule` 控制形态：

```java
public enum EnumRule {
    NAME,     // 按枚举名持久化（toString）
    ORDINAL,  // 按 ordinal 持久化
    CODE,     // 按 IEnumValue.getValue() 持久化（推荐）
    LABEL     // 按 IEnumValue.getName() 持久化
}
```

策略选择建议：

| 策略 | 可读性 | 稳定性 | 适用场景 |
| --- | --- | --- | --- |
| `NAME` | 高 | 低（改名即破坏） | 枚举名稳定的场景 |
| `ORDINAL` | 低 | 低（顺序敏感） | 不推荐 |
| `CODE` | 高 | 高 | **推荐**，业务 code 稳定 |
| `LABEL` | 高 | 中 | 需国际化的场景 |

```java
// 枚举需实现 IEnumValue
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
```

### 3.2 JSON 通道

值对象整体序列化到 JSON 列：

```java
// 值对象实现 IValueObject
public class Address implements IValueObject {
    private String province;
    private String city;
    // ...
}
```

`GenericJsonTypeHandler` 会自动识别 `IValueObject` 子类并整体序列化。`JdbcJsonValue` 适配不同数据库：

| 实现 | 数据库 | 行为 |
| --- | --- | --- |
| `JdbcJsonValue.DEFAULT` | 通用 | 透传对象 |
| `JdbcJsonValue.MYSQL` | MySQL | 序列化为文本 |
| `PgJdbcJsonValue` | PostgreSQL | 反射构造 PGobject（jsonb/json），零硬依赖 |

### 3.3 集合通道

单列 JSON 数组持久化，按列标签精确还原泛型：

```java
// 声明集合映射
CollectionMapping mapping = CollectionMapping.builder()
        .columnLabel("tags")           // 列标签
        .elementType(String.class)     // 元素类型
        .build();

CollectionElementTypeConfig config = CollectionElementTypeConfig.from(
        List.of(mapping), enumValueResolver);
```

### 3.4 统一装配 `TypeHandlerContext`

```java
// 1. 构建枚举解析器
EnumValueResolver resolver = new EnumValueResolver();
resolver.register(OrderStatus.class);

// 2. 构建 JSON 序列化器
JsonSerializer serializer = new Fastjson2JsonSerializer(
        resolver,
        Map.of(OrderStatus.class, EnumRule.CODE));

// 3. 选择 JDBC 适配器
JdbcJsonValue jdbcJsonValue = JdbcJsonValue.MYSQL;

// 4. 构建集合配置
CollectionElementTypeConfig collections = CollectionElementTypeConfig.from(
        List.of(mapping), resolver);

// 5. 构建装配上下文
TypeHandlerContext ctx = new TypeHandlerContext(
        resolver,
        serializer,
        jdbcJsonValue,
        Map.of(OrderStatus.class, EnumRule.CODE),
        List.of(Address.class),    // VO 类型
        collections);

// 6. 一次性注册到 SqlSessionFactory
ctx.registerInto(sqlSessionFactory);
```

::: tip 启动期 fail-fast
- 枚举重复 code 在 `EnumValueIndex` 构建时即抛异常
- 集合列标签冲突在 `CollectionElementTypeConfig` 构建时即抛异常
:::

## 4. Outbox 落库 `MybatisOutboxStore`

```java
// 1. 注册 Mapper
sqlSessionFactory.getConfiguration().addMapper(OutboxMapper.class);

// 2. 构建 OutboxStore
MybatisOutboxStore outboxStore = new MybatisOutboxStore(
        sqlSessionFactory.openSession());

// 3. 配合 OutboxCommandExecutor 使用
OutboxCommandExecutor executor = new OutboxCommandExecutor(
        outboxStore,
        transactionOperations,
        eventSerializer,
        eagerPublisher);
```

Outbox 表结构：

```sql
CREATE TABLE outbox (
    id            VARCHAR(64) PRIMARY KEY,
    aggregate_id  VARCHAR(64),
    aggregate_type VARCHAR(255),
    event_type    VARCHAR(255),
    entity_id     VARCHAR(64),
    payload       TEXT,
    status        VARCHAR(20) DEFAULT 'PENDING',  -- PENDING/PROCESSING/SENT/FAILED
    attempts      INT DEFAULT 0,
    queue         INT DEFAULT 0,
    claim_token   VARCHAR(64),
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
```

状态机：

```
PENDING → PROCESSING → SENT（成功）
                    └→ FAILED（重试耗尽，死信）
```

`claimPending` 使用 `claim_token`（UUID）实现多实例安全的原子认领。

## 5. 号段 ID 生成 `DbSegmentAllocator`

```java
// 1. 注册 Mapper
sqlSessionFactory.getConfiguration().addMapper(IdSegmentMapper.class);

// 2. 构建分配器
DbSegmentAllocator allocator = new DbSegmentAllocator(sqlSessionFactory.openSession());

// 3. 构建 ID 生成器（Long 类型）
IIdGenerator<Long> longGenerator = new AbstractSegmentIdGenerator<Long>("order", allocator) {
    @Override
    protected Long convert(long rawId) {
        return rawId;
    }
};

// 4. 生成 ID
Long id = longGenerator.nextId();
```

带前缀的 String 类型：

```java
IIdGenerator<String> prefixedGenerator = new AbstractSegmentIdGenerator<String>("order-no", allocator) {
    @Override
    protected String convert(long rawId) {
        return "ORD" + String.format("%08d", rawId);
    }
};

String orderNo = prefixedGenerator.nextId();  // "ORD00000001"
```

号段表结构：

```sql
CREATE TABLE id_segment (
    biz_key       VARCHAR(64) PRIMARY KEY,
    current_max_id BIGINT DEFAULT 0,
    step          INT DEFAULT 1000,
    version       INT DEFAULT 0,
    remark        VARCHAR(255)
);
```

并发安全：`SELECT ... FOR UPDATE` + `UPDATE`，独立短事务，与调用方事务无关。

## 6. 数据库脚本

模块提供 MySQL 建表脚本，位于 `src/main/resources` 下：

- Outbox 表：`outbox.sql`
- 号段表：`id_segment.sql`

换数据库只需替换 Mapper XML 中的 SQL 方言。

---

下一步：

- [Outbox 最佳实践](../best-practices/transactional-outbox.md)
- [RocketMQ 集成](./rocketmq.md)
