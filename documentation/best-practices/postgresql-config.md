# PostgreSQL 配置设计原则（数据源与方言）

> 把 `pragmatic-ddd` 落到 PostgreSQL 时，Java 侧几乎不需要改：框架 SQL 以 XML 承载，方言差异集中在**方言 XML、`databaseId`、建表脚本与参数绑定**四处。本文按装配顺序给出可直接复用的落地骨架；把示例中的 `Order` 换成目标聚合、把 `postgres` 换成目标环境即可复用（举一反三）。
>
> 本文与 [MySQL 配置设计原则](./mysql-config.md) 平行；ORM 会话工厂、TypeHandler 多聚合装配与 Mapper 扫描的通用部分见 [MyBatis 配置设计原则](./mybatis-config.md)。

## 1. 本质与边界

PostgreSQL 支持不是一套新的 Java API。框架的仓储、Outbox、补偿与号段实现只向 MyBatis 传 `namespace.statementId`，**SQL 全部在 XML 里**。因此切 PG 的落点是：

| 层面 | 落点 | 是否需要为 PG 改动 |
| --- | --- | --- |
| JDBC | `DataSource` / `PlatformTransactionManager` | 换 URL 与驱动，装配骨架不变 |
| ORM | `SqlSessionFactory` 的方言选择 | 扫 PG 版 XML + 设置 `databaseId` |
| 框架 SQL 资产 | `outbox` / `compensation` / `id` 三组 XML 与建表脚本 | 取 jar 内 PG 版，执行 PG 建表脚本 |
| 业务 Mapper | 使用方自己写的 XML | 由使用方适配（本文给约定） |
| starter | `pragmatic-ddd-spring-boot` 自动装配 | 不改 |

::: info 与 `pragmatic-ddd-spring-boot` 的关系
starter 只**消费**容器内的 `PlatformTransactionManager` 与 `SqlSessionTemplate`，不创建数据源、不构建 `SqlSessionFactory`、不加载 Mapper XML，也不设置 `databaseId`。本文的 `PostgresConfig` 与 `MyBatisConfig` 仍需使用方显式提供。
:::

**不做什么**：

- 不引入方言 SPI 或 SQL 运行时改写层；框架只做“选择哪一份 XML”。
- 不在 Java 里拼方言 SQL；方言只出现在 XML 与 DDL 中。
- 不承诺业务 Mapper 自动可移植；框架只提供 TypeHandler 能力与装配约定。

## 2. 装配总览

装配顺序即依赖顺序：

```text
org.postgresql:postgresql（运行期驱动）
        │
        ▼
DataSource（Hikari） ──────────────► PlatformTransactionManager
        │
        ▼
SqlSessionFactory（databaseId=postgres） ──► SqlSessionTemplate
        │
        ▼
Mapper XML：
  classpath*:io/pragmatic/ddd/mybatis/outbox/postgres/*.xml
  classpath*:io/pragmatic/ddd/mybatis/id/postgres/*.xml
  classpath*:io/pragmatic/ddd/mybatis/compensation/postgres/*.xml
  classpath*:mapper/**/*.xml
```

推荐的包结构：

```text
infrastructure/config/                      # 通用技术配置
├── PostgresConfig.java                     # DataSource / TransactionManager
├── MyBatisConfig.java                      # SqlSessionFactory / databaseId / Mapper 扫描
└── {agg}/
    └── {Agg}MybatisTypeHandlerConfig.java  # 该聚合的枚举与值对象 TypeHandler
```

依赖方向单向：`MyBatisConfig → PostgresConfig`；`PostgresConfig` 不 import 任何 MyBatis 类型。

## 3. 数据源与事务

### 3.1 依赖与连接参数

使用方需要引入 PostgreSQL JDBC 驱动。框架模块只在 `test` scope 引入驱动，不会把驱动打进产物：

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

连接参数一律外部化，代码中不出现连接串与密钥：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pragmatic_ddd
    username: ${PG_USER}
    password: ${PG_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      max-lifetime: 1800000
      keepalive-time: 300000
      validation-timeout: 5000
```

### 3.2 数据源与事务管理器

JDBC 层与 MySQL 的骨架完全同构，只换 URL、驱动与连接池参数来源：

```java
@Configuration
@EnableTransactionManagement
public class PostgresConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Bean
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        // 连接池参数同样从 spring.datasource.hikari.* 注入
        return dataSource;
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
```

启动类排除 `DataSourceAutoConfiguration`，由本配置全权掌控数据源：

```java
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class AppStart {
    public static void main(String[] args) {
        SpringApplication.run(AppStart.class, args);
    }
}
```

### 3.3 连接池参数

| 参数 | 取值建议 | PG 侧注意点 |
| --- | --- | --- |
| `maximum-pool-size` | 按 `DB 最大连接数 / 应用实例数` 估算 | 经 PgBouncer 时按代理的连接上限折算 |
| `minimum-idle` | 小于 `maximum-pool-size` | 保持少量热连接，降低突发建连开销 |
| `connection-timeout` | 毫秒级显式设置 | 超时抛异常，避免线程无限阻塞 |
| `max-lifetime` | 小于数据库或代理的空闲断连时间 | PG 没有 MySQL 的 `wait_timeout` 同名参数；若经 PgBouncer / 云代理，按代理的空闲断连时间设置 |
| `keepalive-time` | 小于 `max-lifetime` | 主动探活，防中间设备断链 |
| `validation-timeout` | 毫秒级显式设置 | 借出时校验存活 |

## 4. 方言选择

### 4.1 三组方言 XML

框架为三组 SQL 资产各提供一份 PG 版 XML，namespace 与 MySQL 版相同：

| 能力 | PostgreSQL XML（jar 内） | 建表脚本（jar 内） | namespace（固定） |
| --- | --- | --- | --- |
| Outbox | `io/pragmatic/ddd/mybatis/outbox/postgres/OutboxMapper.xml` | `outbox/schema/outbox-schema-postgres.sql` | `io.pragmatic.ddd.mybatis.outbox.OutboxMapper` |
| 补偿 | `io/pragmatic/ddd/mybatis/compensation/postgres/CompensationMapper.xml` | `compensation/schema/compensation-log-schema-postgres.sql` | `io.pragmatic.ddd.mybatis.compensation.CompensationMapper` |
| 号段 ID | `io/pragmatic/ddd/mybatis/id/postgres/IdSegmentMapper.xml` | `id/schema/id-segment-schema-postgres.sql` | `io.pragmatic.ddd.mybatis.id.IdSegmentMapper` |

PG 版 XML 的每条语句都带 `databaseId="postgres"`，只有 `Configuration.databaseId` 为 `"postgres"` 时才注册。

### 4.2 必须设置 databaseId

Spring 场景声明 `DatabaseIdProvider`，并在构建 `SqlSessionFactory` 时注入：

```java
@Bean
public DatabaseIdProvider databaseIdProvider() {
    VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
    provider.setProperties(Map.of(
            "PostgreSQL", "postgres",
            "MySQL", "mysql"));
    return provider;
}

@Bean
public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                           List<TypeHandlerContext> typeHandlerContexts,
                                           DatabaseIdProvider databaseIdProvider) throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
    typeHandlerContexts.forEach(context -> context.registerInto(configuration));

    SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
    sessionFactory.setDataSource(dataSource);
    sessionFactory.setConfiguration(configuration);
    sessionFactory.setDatabaseIdProvider(databaseIdProvider);
    sessionFactory.setMapperLocations(loadPostgresMapperLocations());
    return sessionFactory.getObject();
}
```

非 Spring 场景直接设置：

```java
configuration.setDatabaseId("postgres");
```

`VendorDatabaseIdProvider` 按 `DatabaseMetaData#getDatabaseProductName()` 匹配；PostgreSQL 返回 `PostgreSQL`，MySQL 返回 `MySQL`。

### 4.3 只扫一种方言 glob

PG 场景把框架 Mapper 的三条 glob 换成方言子目录：

```java
private static final String FRAMEWORK_OUTBOX_MAPPERS =
        "classpath*:io/pragmatic/ddd/mybatis/outbox/postgres/*.xml";

private static final String FRAMEWORK_ID_MAPPERS =
        "classpath*:io/pragmatic/ddd/mybatis/id/postgres/*.xml";

private static final String FRAMEWORK_COMPENSATION_MAPPERS =
        "classpath*:io/pragmatic/ddd/mybatis/compensation/postgres/*.xml";

private static final String BUSINESS_MAPPERS = "classpath*:mapper/**/*.xml";
```

| 扫描方式 | `databaseId` | 结果 |
| --- | --- | --- |
| 只扫 MySQL 版 XML | `null` 或 `mysql` | MySQL 语句正常注册 |
| 只扫 PG 版 XML | `postgres` | PG 语句正常注册 |
| 只扫 PG 版 XML | `null` | 启动不报错，首次调用时抛 `Mapped Statements collection does not contain value for ...` |
| 同时扫两份 | `postgres` | 不因 statement 冲突而启动失败，PG 语句生效；会多注册一套无用的 MySQL 语句 |

> ⚠️ **重要约束**：扫描 PG 版 XML 时必须设置 `databaseId`。方言语句的注册依赖 `Configuration.databaseId`，未设置时不会报错，问题延迟到运行期首次执行 SQL 才暴露。
>
> ⚠️ **重要约束**：推荐只扫一种方言。虽然 PG 版语句带 `databaseId`，误扫两份通常不会启动冲突，但会多注册一套无用语句，并使“当前生效的是哪份 SQL”难以判断。

## 5. 框架 Mapper 与建表脚本

### 5.1 触发矩阵

框架三组 XML 与三张表**按能力按需启用**：

| 使用的能力 | 必须扫描 | 必须建表 | 缺失症状 |
| --- | --- | --- | --- |
| Outbox | `outbox/postgres/*.xml` | `outbox_message` | 首发事件抛 `Mapped Statements collection does not contain value for ...OutboxMapper.insertBatch` |
| 补偿 durable | `compensation/postgres/*.xml` | `compensation_log` | 同上，`...CompensationMapper.insert` |
| DB 号段 ID | `id/postgres/*.xml` | `id_segment` | 取号时 `...IdSegmentMapper.selectForUpdate` 找不到 |

### 5.2 建表脚本

三份 PG 脚本均幂等（`IF NOT EXISTS`），可重复执行，也可放入 `docker/pgsql/init/`：

```text
io/pragmatic/ddd/mybatis/outbox/schema/outbox-schema-postgres.sql
io/pragmatic/ddd/mybatis/compensation/schema/compensation-log-schema-postgres.sql
io/pragmatic/ddd/mybatis/id/schema/id-segment-schema-postgres.sql
```

MySQL 到 PG 的转写规则：

| MySQL | PostgreSQL | 说明 |
| --- | --- | --- |
| `VARCHAR(n)` / `INT` / `BIGINT` | 同名 | 不变 |
| `LONGTEXT` | `TEXT` | 框架表 `payload` 使用 `TEXT` |
| `DATETIME(3)` | `TIMESTAMP(3)` | 无时区，Java 侧统一 `Instant`（UTC 语义） |
| `DATETIME` | `TIMESTAMP` | 无精度要求的列（如 `id_segment.created_at`） |
| `AUTO_INCREMENT` | `BIGSERIAL` | 仅业务自增主键需要；框架表主键由应用生成或 `biz_key` 指定 |
| 内联 `INDEX idx_... (...)` | 独立 `CREATE INDEX idx_... ON ...;` | PG 无内联索引语法 |
| `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4` | 删除 | 由数据库/服务器编码决定 |
| `ON UPDATE CURRENT_TIMESTAMP` | 删除，改由 SQL 显式赋值 | PG 无该列选项；`id_segment.updated_at` 由 `incrementMax` 显式写 |
| 表/列 `COMMENT '...'` | `COMMENT ON TABLE / COLUMN ... IS '...'` | 可选，保留关键说明 |

> ⚠️ **重要约束**：`id_segment` 的建表脚本只建表，**不包含渠道种子数据**。应用启动前必须为每个 `biz_key` 插入一行初始记录（`step` 为号段步长）；否则取号时 `selectForUpdate` 查不到行。
>
> ```sql
> INSERT INTO id_segment (biz_key, current_max_id, step, remark)
> VALUES ('order', 0, 1000, 'order')
> ON CONFLICT (biz_key) DO NOTHING;
> ```
>
> `current_max_id = 0`、`step = 1000` 表示首个号段为 `[1, 1000]`。

> ⚠️ **重要约束**：框架表时间列统一使用 `TIMESTAMP` / `TIMESTAMP(3)`（无时区），Java 侧统一 `Instant`。不要在没有重审全链路时间语义的情况下改用 `TIMESTAMPTZ`，否则租约、grace 与审计时间可能出现时区偏移。

## 6. TypeHandler 适配

### 6.1 聚合专属 TypeHandlerContext

枚举、值对象与集合三通道的装配方式不变；PG 场景只把 JSON 适配器换成 `PgJdbcJsonValue`：

```java
@Bean
public TypeHandlerContext orderTypeHandlerContext() {
    EnumValueResolver resolver = new EnumValueResolver();
    Map<Class<?>, EnumRule> enumRules = Map.of(
            OrderStatus.class, EnumRule.CODE,
            PaymentStatus.class, EnumRule.CODE,
            ShipmentStatus.class, EnumRule.CODE,
            PaymentMethod.class, EnumRule.CODE);
    List<Class<?>> voTypes = List.of(
            Customer.class, Address.class, Money.class,
            PaymentInfo.class, LogisticsInfo.class);
    return new TypeHandlerContext(
            resolver,
            new Fastjson2JsonSerializer(resolver, enumRules),
            new PgJdbcJsonValue(),
            enumRules,
            voTypes,
            CollectionElementTypeConfig.empty());
}
```

`PgJdbcJsonValue` 通过反射构造 `PGobject`，默认 `type=jsonb`；`org.postgresql` 不在类路径时会在首次写入时抛 `IllegalStateException`。

### 6.2 JSON 列策略

框架三张表的 `payload` 列使用 `TEXT`，不参与 JSON 路径查询，写入时按普通字符串处理。业务表如果要用 PG 原生 JSON 类型，有三种策略：

| 策略 | 列类型 | XML 写法 | TypeHandler 适配器 | 建议 |
| --- | --- | --- | --- | --- |
| 显式转换 | `jsonb` | `#{profile,jdbcType=OTHER}::jsonb` | `new PgJdbcJsonValue()` | **推荐**，语义明确、无全局副作用 |
| 连接串兜底 | `jsonb` / `json` | `#{profile}` | `JdbcJsonValue.MYSQL`（传 JSON 字符串） | 仅在遗留工程迁移时考虑；需在连接串加 `stringtype=unspecified`，会影响该连接上所有未定型参数 |
| 文本存储 | `TEXT` | `#{profile}` | `JdbcJsonValue.MYSQL`（传 JSON 字符串） | 放弃 `jsonb` 的索引与算子能力，换取与 MySQL 最接近的行为 |

采用“显式转换”时，业务 Mapper 的参数必须带 `jdbcType=OTHER`，并在 SQL 中补 `::jsonb`。只写 `#{profile}` 或漏掉 `::jsonb`，PG 可能无法推断参数类型或列类型。

### 6.3 可空参数必须显式 jdbcType

MyBatis 默认 `jdbcTypeForNull=OTHER`，PG 驱动会报：

```text
could not determine data type of parameter $n
```

PG 版 XML 对每个可能为 `null` 的参数显式声明类型：

```xml
#{m.entityId,jdbcType=VARCHAR}
#{m.claimedAt,jdbcType=TIMESTAMP}
#{m.sentAt,jdbcType=TIMESTAMP}
#{m.lastError,jdbcType=VARCHAR}
#{batchSize,jdbcType=INTEGER}
```

> ⚠️ **重要约束**：`configuration.setJdbcTypeForNull(JdbcType.NULL)` 可以全局规避该问题，但会影响使用方全部 Mapper 的参数绑定行为。框架推荐逐参数声明 `jdbcType`，只在明确接受全局影响时使用该开关。

### 6.4 枚举、状态列与集合

| 维度 | PG 约定 |
| --- | --- |
| 状态列 | 使用 `VARCHAR(20)` + `EnumTypeHandler`（按 `name()`）；不使用 PG 自定义 enum、domain 或 `CHECK` 约束 |
| 单列枚举 | `UniversalEnumTypeHandler` 按 `EnumRule` 存取；列类型与 MySQL 保持一致（`VARCHAR` / `INT`） |
| 集合列 | `ListTypeHandler` 按 `columnLabel` 还原泛型；PG 与 MySQL 行为一致，仍需用 SQL `AS` 隔离同名列 |
| 时间 | `Instant` ↔ `TIMESTAMP(3)`；DDL 不带时区 |

## 7. 框架 SQL 方言要点

框架 PG 版 XML 与 MySQL 版的差异集中在以下语句。业务 Mapper 如果也实现队列认领、租约回收等模式，可沿用同样的写法。

### 7.1 claimPending：物化 CTE + SKIP LOCKED

PG 不支持 `UPDATE ... ORDER BY ... LIMIT`。框架使用物化 CTE 选行并加行锁：

```sql
WITH cand AS MATERIALIZED (
    SELECT id FROM outbox_message
    WHERE status = 'PENDING' AND created_at < #{cutoff,jdbcType=TIMESTAMP}
    ORDER BY created_at ASC, id ASC
    LIMIT #{batchSize,jdbcType=INTEGER}
    FOR UPDATE SKIP LOCKED
)
UPDATE outbox_message o
SET status = 'PROCESSING',
    claimed_at = CURRENT_TIMESTAMP,
    claim_token = #{token,jdbcType=VARCHAR}
FROM cand t
WHERE o.id = t.id
  AND o.status = 'PENDING'
```

> ⚠️ **重要约束**：`AS MATERIALIZED` 不能省略。`UPDATE ... FROM` 的普通子查询在嵌套循环中可能被外层每行重复求值，`LIMIT` 被逐次消耗，导致**超批次认领**（实测 `batchSize=1` 时更新了 2 行）。`ORDER BY created_at, id` 中的 `id` 是 tie-breaker，用于保证批次确定、可复现。

### 7.2 releaseStaleClaims：make_interval

MySQL 的 `DATE_SUB(... INTERVAL n SECOND)` 在 PG 改为可绑定参数的 `make_interval`：

```sql
UPDATE compensation_log
SET status = 'EXECUTED',
    claim_token = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'COMPENSATING'
  AND claimed_at < CURRENT_TIMESTAMP - make_interval(secs => #{leaseSeconds,jdbcType=BIGINT})
```

`make_interval(secs => n)` 支持绑定参数，避免 `INTERVAL '10 seconds'` 这种不能参数化的字面量写法。

### 7.3 时间函数收敛

PG 版补偿 XML 的 7 处 `CURRENT_TIMESTAMP(3)` 统一改为无参 `CURRENT_TIMESTAMP`；号段 XML 的 `NOW()` 同样收敛为 `CURRENT_TIMESTAMP`。精度由列 DDL 的 `TIMESTAMP(3)` 决定，状态机与租约语义不变。

### 7.4 补偿冲突判断

`MybatisCompensationLog` 判断唯一键冲突时，除 `SQLIntegrityConstraintViolationException` 外，还检查标准 SQLState `23` 类：

```java
if (t instanceof SQLException sqlEx && sqlEx.getSQLState() != null
        && sqlEx.getSQLState().startsWith("23")) {
    // 唯一键冲突
}
```

MySQL 驱动抛 `SQLIntegrityConstraintViolationException`；PG 驱动抛 `PSQLException`（SQLState `23505`），不走该子类。使用方无需改代码，但自定义补偿日志实现时应沿用同一判断方式。

## 8. 验证与排查

### 8.1 启动期校验

方言语句未注册是**静默失效**：应用能启动，首次调用才报错。建议在启动阶段校验实际启用的能力：

```java
@Bean
public ApplicationRunner verifyPostgresStatements(SqlSessionFactory sqlSessionFactory) {
    return args -> {
        org.apache.ibatis.session.Configuration configuration =
                sqlSessionFactory.getConfiguration();
        requireStatements(configuration,
                OutboxStatements.CLAIM_PENDING,
                IdSegmentStatements.SELECT_FOR_UPDATE);
    };
}

private void requireStatements(org.apache.ibatis.session.Configuration configuration,
                               String... statementKeys) {
    for (String statementKey : statementKeys) {
        if (!configuration.hasStatement(statementKey)) {
            throw new IllegalStateException(
                    "MyBatis statement 未注册：" + statementKey
                            + "；请检查方言 XML 扫描路径与 databaseId 配置");
        }
    }
}
```

只校验实际使用的模块；未启用补偿时无需校验 `CompensationStatements.*`。

### 8.2 集成测试

框架的 PG 集成测试通过环境变量连接真实库：

| 变量 | 默认值 |
| --- | --- |
| `PG_HOST` | `127.0.0.1` |
| `PG_PORT` | `5432` |
| `PG_DB` | `pragmatic_ddd` |
| `PG_USER` | `pragmatic` |
| `PG_PASSWORD` | 有测试默认值（见 `PostgresTestSupport`）；CI / 生产必须用环境变量覆盖 |

测试隔离时注意：PG 的 `TRUNCATE` 不重置 `BIGSERIAL` 序列；需要重置自增主键时使用 `TRUNCATE ... RESTART IDENTITY`。

### 8.3 症状与处理

| 症状 | 原因 | 处理 |
| --- | --- | --- |
| `Mapped Statements collection does not contain value for ...` | 扫了 PG XML 但未设置 `databaseId`，或 glob 未命中方言目录 | 设置 `VendorDatabaseIdProvider` / `setDatabaseId("postgres")`，确认 `classpath*:` 与 `postgres/` 路径 |
| `could not determine data type of parameter $n` | 可空参数未写 `jdbcType` | 逐个补 `jdbcType`（见 §6.3） |
| `relation "outbox_message" does not exist` | 未执行 PG 建表脚本 | 执行对应 `*-postgres.sql` |
| `column ... does not exist` | 表结构与查询列不一致；`SELECT *` 掩盖了列集合差异 | 对照 DDL 检查列名，业务 XML 建议显式列清单 |
| `operator does not exist: ... jsonb` | 写了 `PgJdbcJsonValue` 但 SQL 未加 `::jsonb`，或列不是 JSON 类型 | 按 §6.2 选择策略并补显式转换 |
| 取号时 `selectForUpdate` 查不到行 | `id_segment` 缺少渠道初始行 | 为每个 `biz_key` 插入初始记录（见 §5.2） |

## 9. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| PG 场景继续扫 MySQL 版框架 XML | `claimPending`、`releaseStaleClaims` 等语句在 PG 直接语法报错 | 改扫 `{outbox,compensation,id}/postgres/*.xml` |
| 扫了 PG XML 但未设置 `databaseId` | 方言语句全部不注册，启动正常、运行期首调才报错 | 提供 `VendorDatabaseIdProvider` 或 `setDatabaseId("postgres")` |
| 同时扫 MySQL 与 PG 两份方言 XML | 多注册一套无用语句，难以判断当前生效 SQL | 只扫一种方言 glob |
| PG 业务 Mapper 的可空参数不写 `jdbcType` | `could not determine data type of parameter $n` | 可空参数逐个显式 `jdbcType` |
| 用 PG 自定义 enum 存状态列 | 跨方言不一致，后续扩值需改类型 | 用 `VARCHAR` + `EnumTypeHandler` |
| 业务 `jsonb` 列只写 `#{profile}` | PG 无法推断参数类型，或写入失败 | 用 `jdbcType=OTHER` + `::jsonb` |
| 未执行 PG 建表脚本就启动 | 运行期报表不存在 | 先执行三份 `*-postgres.sql` |
| 认为 starter 会自动加载方言 XML | starter 只装配 Bean，不扫描 Mapper | 在 `MyBatisConfig` 显式扫描并设置方言 |
| 在 Java 中拼方言 SQL | 破坏“SQL 归 XML、Java 只传 statementId”的边界 | 方言写在 XML，Java 只传 statementId |
| 把 `id_segment` 初始行当作建表脚本的一部分 | 脚本只建表，取号时查不到渠道行 | 单独插入渠道初始记录 |

## 10. 下一步

- [MyBatis 配置设计原则](./mybatis-config.md)：会话工厂、TypeHandler 多聚合装配与 Mapper 扫描
- [MySQL 配置设计原则](./mysql-config.md)：JDBC 层与连接池的通用装配
- [Outbox 链路装配](./outbox-config.md)：事务性发件箱五个协作 Bean 的装配
- [仓储设计原则](./repository-design.md)：仓储如何按 `namespace.statementId` 直调语句
- [MyBatis 集成](../integration/mybatis.md)：枚举 / JSON / 集合三通道的内部实现
- [Spring Boot Starter 集成](../integration/spring-boot.md)：starter 如何消费 `PlatformTransactionManager` 与 `SqlSessionTemplate`
