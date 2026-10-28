# BP 卡：PostgreSQL 配置设计原则（postgresql-config）

> 源：`documentation/best-practices/postgresql-config.md` · 派生时间：2026-10-28 · 框架版本：2.1.0-SNAPSHOT

## 1. 本质与边界

PostgreSQL 支持不是一套新的 Java API：框架 SQL 全在 XML，仓储 / Outbox / 补偿 / 号段实现只传 `namespace.statementId`。切 PG 的落点：

| 层面 | 落点 | 是否为 PG 改动 |
| --- | --- | --- |
| JDBC | `DataSource` / `PlatformTransactionManager` | 换 URL 与驱动，装配骨架不变（与 `mysql-config.md` 同构，配置类命名 `PostgresConfig`） |
| ORM | `SqlSessionFactory` 方言选择 | 扫 PG 版 XML + 设置 `databaseId` |
| 框架 SQL 资产 | outbox / compensation / id 三组 XML 与建表脚本 | 取 jar 内 PG 版，执行 PG 建表脚本 |
| 业务 Mapper | 使用方自写 XML | 由使用方适配（本文给约定） |
| starter | `pragmatic-ddd-spring-boot` 自动装配 | 不改（只消费 `PlatformTransactionManager` 与 `SqlSessionTemplate`，不建数据源、不加载 Mapper、不设 `databaseId`） |

不做：不引入方言 SPI / SQL 运行时改写；不在 Java 拼方言 SQL；不承诺业务 Mapper 自动可移植。

依赖：`org.postgresql:postgresql`（`runtime` scope；框架模块只 test scope 引入，不打进产物）。连接参数外部化 `spring.datasource.*`（`driver-class-name: org.postgresql.Driver`）。

包结构：`infrastructure/config/` 下 `PostgresConfig.java`（DataSource / TransactionManager）+ `MyBatisConfig.java`（SqlSessionFactory / databaseId / Mapper 扫描）+ `{agg}/{Agg}MybatisTypeHandlerConfig.java`。依赖方向单向：`MyBatisConfig → PostgresConfig`。

## 2. 方言选择（三条硬约束）

三组方言 XML（namespace 与 MySQL 版相同，每条语句带 `databaseId="postgres"`）：

| 能力 | PG XML（jar 内） | 建表脚本（jar 内） | namespace |
| --- | --- | --- | --- |
| Outbox | `io/pragmatic/ddd/mybatis/outbox/postgres/OutboxMapper.xml` | `outbox/schema/outbox-schema-postgres.sql` | `io.pragmatic.ddd.mybatis.outbox.OutboxMapper` |
| 补偿 | `io/pragmatic/ddd/mybatis/compensation/postgres/CompensationMapper.xml` | `compensation/schema/compensation-log-schema-postgres.sql` | `io.pragmatic.ddd.mybatis.compensation.CompensationMapper` |
| 号段 ID | `io/pragmatic/ddd/mybatis/id/postgres/IdSegmentMapper.xml` | `id/schema/id-segment-schema-postgres.sql` | `io.pragmatic.ddd.mybatis.id.IdSegmentMapper` |

1. **只扫一种方言 glob**：`classpath*:io/pragmatic/ddd/mybatis/{outbox,compensation,id}/postgres/*.xml` + `classpath*:mapper/**/*.xml`；同时扫两份不会启动冲突，但会多注册一套无用语句。
2. **必须设置 `databaseId`**：Spring 用 `VendorDatabaseIdProvider`（`setProperties(Map.of("PostgreSQL","postgres","MySQL","mysql"))`，注入 `SqlSessionFactoryBean.setDatabaseIdProvider`）；非 Spring 用 `configuration.setDatabaseId("postgres")`。未设置时**静默失效**——启动正常，首次调用 SQL 才抛 `Mapped Statements collection does not contain value for ...`。
3. **可空参数逐个显式 `jdbcType`**：MyBatis 默认 `jdbcTypeForNull=OTHER`，PG 报 `could not determine data type of parameter $n`（`setJdbcTypeForNull(JdbcType.NULL)` 可全局规避，但影响全部 Mapper，框架推荐逐参数声明）。

触发矩阵：Outbox 缺失→首发事件报 `...OutboxMapper.insertBatch`；补偿 durable 缺失→`...CompensationMapper.insert`；号段缺失→取号时 `...IdSegmentMapper.selectForUpdate` 找不到。建议启动期 `ApplicationRunner` + `configuration.hasStatement(OutboxStatements.CLAIM_PENDING / IdSegmentStatements.SELECT_FOR_UPDATE)` 校验实际启用的能力。

## 3. 建表脚本与转写规则

三份 PG 脚本幂等（`IF NOT EXISTS`）：`outbox-schema-postgres.sql` / `compensation-log-schema-postgres.sql` / `id-segment-schema-postgres.sql`（路径见 §2 表）。

MySQL → PG 转写：`LONGTEXT→TEXT`（框架表 `payload` 用 `TEXT`）；`DATETIME(3)→TIMESTAMP(3)`、`DATETIME→TIMESTAMP`（无时区，Java 侧统一 `Instant`）；`AUTO_INCREMENT→BIGSERIAL`（仅业务自增主键需要）；内联 `INDEX` 拆独立 `CREATE INDEX`；删 `ENGINE/CHARSET`；`ON UPDATE CURRENT_TIMESTAMP` 删除改 SQL 显式赋值；`COMMENT` 改 `COMMENT ON TABLE/COLUMN`（可选）。

> ⚠️ `id_segment` 建表脚本**不含渠道种子数据**，应用启动前必须为每个 `biz_key` 插初始行（`INSERT ... ON CONFLICT (biz_key) DO NOTHING`），否则取号 `selectForUpdate` 查不到行。
> ⚠️ 框架表时间列统一 `TIMESTAMP`/`TIMESTAMP(3)`（无时区）+ Java `Instant`；不要未经重审改用 `TIMESTAMPTZ`。

## 4. TypeHandler 适配

枚举 / 值对象 / 集合三通道装配方式与 MySQL 一致（见 `mybatis-config.md` §2.3），PG 场景只把 JSON 适配器换成 `PgJdbcJsonValue`（全限定名见 `api-contract.md`）：

```java
return new TypeHandlerContext(
        resolver,
        new Fastjson2JsonSerializer(resolver, enumRules),
        new PgJdbcJsonValue(),
        enumRules,
        voTypes,
        CollectionElementTypeConfig.empty());
```

`PgJdbcJsonValue` 反射构造 `PGobject`（默认 `type=jsonb`）；`org.postgresql` 不在类路径时首次写入抛 `IllegalStateException`。

JSON 列三策略：

| 策略 | XML 写法 | 适配器 | 建议 |
| --- | --- | --- | --- |
| 显式转换 | `#{profile,jdbcType=OTHER}::jsonb` | `new PgJdbcJsonValue()` | **推荐** |
| 连接串兜底 | `#{profile}` + 连接串 `stringtype=unspecified` | `JdbcJsonValue.MYSQL` | 仅遗留迁移考虑 |
| 文本存储 | `#{profile}`，列 `TEXT` | `JdbcJsonValue.MYSQL` | 放弃 jsonb 索引能力 |

状态列用 `VARCHAR(20)` + `EnumTypeHandler`（按 `name()`），不用 PG 自定义 enum / domain / `CHECK`；单列枚举 `UniversalEnumTypeHandler` 列类型与 MySQL 一致；集合列 `ListTypeHandler` 仍需 SQL `AS` 隔离同名列；时间 `Instant` ↔ `TIMESTAMP(3)`。

## 5. 框架 SQL 方言要点（业务 Mapper 同模式可沿用）

| 语句 | MySQL 写法 | PG 写法 | 关键点 |
| --- | --- | --- | --- |
| `claimPending` | `UPDATE ... ORDER BY ... LIMIT` | 物化 CTE + `FOR UPDATE SKIP LOCKED` | `AS MATERIALIZED` **不能省**（普通子查询被嵌套循环逐行重估→超批次认领）；`ORDER BY created_at, id` 的 `id` 是 tie-breaker |
| `releaseStaleClaims` | `DATE_SUB(... INTERVAL n SECOND)` | `make_interval(secs => #{leaseSeconds,jdbcType=BIGINT})` | `make_interval` 支持绑定参数 |
| 时间函数 | `CURRENT_TIMESTAMP(3)` / `NOW()` | 统一无参 `CURRENT_TIMESTAMP` | 精度由列 DDL 决定，语义不变 |
| 唯一键冲突判断 | `SQLIntegrityConstraintViolationException` | 追加 SQLState `23` 类判断 | PG 抛 `PSQLException`（SQLState `23505`），不走该子类；自定义补偿日志实现沿用同一判断 |

## 6. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| PG 场景继续扫 MySQL 版框架 XML | 改扫 `{outbox,compensation,id}/postgres/*.xml` |
| 扫 PG XML 但未设置 `databaseId` | `VendorDatabaseIdProvider` 或 `setDatabaseId("postgres")` |
| 同时扫两份方言 XML | 只扫一种方言 glob |
| 可空参数不写 `jdbcType` | 逐个显式 `jdbcType` |
| 用 PG 自定义 enum 存状态列 | `VARCHAR` + `EnumTypeHandler` |
| 业务 `jsonb` 列只写 `#{profile}` | `jdbcType=OTHER` + `::jsonb` |
| 未执行 PG 建表脚本就启动 | 先执行三份 `*-postgres.sql` |
| 认为 starter 自动加载方言 XML | 在 `MyBatisConfig` 显式扫描并设置方言 |
| 在 Java 拼方言 SQL | 方言写在 XML，Java 只传 statementId |
| 把 `id_segment` 初始行当建表脚本一部分 | 单独插入渠道初始记录 |

## 7. 被读于

S3 `ddd-infra-config`（PG 数据源 / 方言装配）、S7 `ddd-code-review`、`ddd-kb-consult`。
