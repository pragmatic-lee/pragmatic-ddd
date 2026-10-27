# MyBatis 配置设计原则（会话工厂与 Mapper）

> 本文档只讲 **ORM 层**：`SqlSessionFactory`、`SqlSessionTemplate`、TypeHandler 多聚合装配与 Mapper XML 加载。数据源与事务管理器见 [MySQL 配置设计原则](./mysql-config.md)；MyBatis 模块内部实现（枚举 / JSON / 集合三通道）见 [MyBatis 集成](./../integration/mybatis.md)。

## 1. 职责边界

- **`MyBatisConfig`（本文）**：产 `SqlSessionFactory` 与 `SqlSessionTemplate`，聚合各聚合的 `TypeHandlerContext`，并把**框架固定 Mapper**与业务 Mapper 一并纳入扫描。
- **`MySqlConfig`（见 [MySQL 配置设计原则](./mysql-config.md)）**：只产 `DataSource` 与 `PlatformTransactionManager`。
- **聚合专属 `{Agg}MybatisTypeHandlerConfig`**：每个聚合一个，只登记本聚合的枚举与值对象，落在 `infrastructure/config/{agg}/`。

依赖方向单向：`MyBatisConfig → MySqlConfig`。`MyBatisConfig` 的 `sqlSessionFactory` 以 `DataSource` 为入参，Spring 按类型注入，与类名无关。

## 2. SqlSessionFactory 构建

构建要点集中在三处：**原生 `Configuration` 注入 TypeHandler**、**全局开关**、**Mapper XML 加载策略**。

### 2.1 原生 Configuration 对象，而不是 configLocation

不使用 `setConfigLocation` 加载 `mybatis-config.xml` 来装配 TypeHandler，而是**创建原生 `Configuration` 对象**，在构建阶段把各聚合 `TypeHandlerContext` 逐个 `registerInto`，再通过 `setConfiguration` 注入会话工厂。

```java
@Bean
public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                           List<TypeHandlerContext> typeHandlerContexts) throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

    // 各聚合 TypeHandler 必须在 XML 解析前灌入
    typeHandlerContexts.forEach(context -> context.registerInto(configuration));

    configuration.setMapUnderscoreToCamelCase(true);
    configuration.setCacheEnabled(true);
    configuration.setLazyLoadingEnabled(true);
    configuration.setAggressiveLazyLoading(false);
    configuration.setLogImpl(Slf4jImpl.class);

    SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
    sessionFactory.setDataSource(dataSource);
    sessionFactory.setConfiguration(configuration);
    sessionFactory.setMapperLocations(loadMapperLocations());
    return sessionFactory.getObject();
}
```

> `setConfiguration` 与 `setConfigLocation` **互斥**，同时设置会抛 `IllegalStateException`。既然 TypeHandler 必须通过原生 `Configuration` 注入，就不要再用 `mybatis-config.xml`。

### 2.2 全局开关

| 开关 | 值 | 说明 |
| --- | --- | --- |
| `mapUnderscoreToCamelCase` | `true` | 下划线列 → 驼峰属性自动映射 |
| `cacheEnabled` | `true` | 开启全局缓存 |
| `lazyLoadingEnabled` | `true` | 开启延迟加载（支撑子集合懒加载） |
| `aggressiveLazyLoading` | `false` | 按需加载，不激进触发 |
| `logImpl` | `Slf4jImpl.class` | 统一走 SLF4J 日志 |

### 2.3 TypeHandler 多聚合装配

**入参必须是 `List<TypeHandlerContext>`**：每个聚合各产出一个 `TypeHandlerContext` Bean（如 `orderTypeHandlerContext`），声明为 `List<T>` 时 Spring 会把这些 Bean 全部收集注入。若声明为单个 `TypeHandlerContext`，多聚合时会因无法确定注入哪一个而抛 `NoUniqueBeanDefinitionException`。

- 单聚合：列表只有一个元素，行为不变；
- 多聚合：自动收集全部并逐个注册，**新增聚合只需新增一个 `TypeHandlerContext` Bean，`MyBatisConfig` 零改动**；
- 零聚合：注入空列表、循环空转，不报错。

**为什么必须手动装配而非包扫描**：`UniversalEnumTypeHandler`、`GenericJsonTypeHandler`、`ListTypeHandler` 都是运行时按类型动态构建的（泛型类需针对每个值对象 new 实例，集合处理器需运行期装配查表配置），包扫描只能发现"类存在"，无法生成对应实例。

**单点来源**：枚举单列通道与 JSON 通道必须共用同一 `resolver` / `serializer` / `enumRules`，否则同一枚举在两处解析结果不一致。括号细节见 [MyBatis 集成 §2](./../integration/mybatis.md)。

> 空列表不报错，但 Mapper XML 解析期缺 handler 会抛 `BuilderException`。单独装配测试时，`@Import` 必须同时带上 `MySqlConfig`、`MyBatisConfig` 与对应聚合的 TypeHandler 配置。

## 3. 纯 XML Mapper 加载

### 3.1 不使用 @MapperScan

本框架的 Mapper 走**传统纯 XML 直调**：不使用 `@MapperScan`，不在 Java 中持有 Mapper 接口类，仓储按 `namespace.statementId` 通过 `sqlSession.insert/update/select/delete` 调用语句，与 [仓储设计原则](./repository-design.md) 的纯 XML 约定一致。

### 3.2 必须使用 classpath*:

```java
private Resource[] loadMapperLocations() throws IOException {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    return Stream.of(
                    resolver.getResources("classpath*:io/pragmatic/ddd/mybatis/outbox/*.xml"),
                    resolver.getResources("classpath*:io/pragmatic/ddd/mybatis/id/*.xml"),
                    resolver.getResources("classpath*:io/pragmatic/ddd/mybatis/compensation/*.xml"),
                    resolver.getResources("classpath*:mapper/**/*.xml"))
            .flatMap(Arrays::stream)
            .toArray(Resource[]::new);
}
```

- **必须用 `classpath*:`（带星号）**：单 `classpath:` 只能在单个 classpath 根内匹配，跨 jar 时扫不到 `pragmatic-ddd-mybatis` 依赖内的框架 XML。
- **不要只扫 `mapper/**`**：框架 XML 的物理路径是 `io/pragmatic/ddd/mybatis/...`，与业务 `mapper/` 并列，`mapper/**` 命中不了。
- **框架 Mapper 建议无条件注册**：MyBatis 解析 XML 不校验表是否存在，多注册安全；漏注册只会在对应功能首次调用时暴露。

### 3.3 框架固定 Mapper 清单

`pragmatic-ddd-mybatis` 内置三组与业务无关的 Mapper，**必须由使用方的 `SqlSessionFactory` 扫描加载**：

| 能力 | Mapper XML（jar 内） | 表 | namespace（固定） |
| --- | --- | --- | --- |
| Outbox | `io/pragmatic/ddd/mybatis/outbox/OutboxMapper.xml` | `outbox_message` | `io.pragmatic.ddd.mybatis.outbox.OutboxMapper` |
| 补偿（durable） | `io/pragmatic/ddd/mybatis/compensation/CompensationMapper.xml` | `compensation_log` | `io.pragmatic.ddd.mybatis.compensation.CompensationMapper` |
| 号段 ID | `io/pragmatic/ddd/mybatis/id/IdSegmentMapper.xml` | `id_segment` | `io.pragmatic.ddd.mybatis.id.IdSegmentMapper` |

对应的语句常量类：`OutboxStatements` / `CompensationStatements` / `IdSegmentStatements`（与 XML 的 `namespace.statementId` 一一对应）。

**触发矩阵**：

| 使用的能力 | 必须注册 | 必须建表 | 缺失症状 |
| --- | --- | --- | --- |
| Outbox（`OutboxCommandExecutor` / `OutboxRelay`） | `outbox/*.xml` | `outbox_message` | 启动正常，首发事件抛 `Mapped Statements collection does not contain value for ...OutboxMapper.insertBatch` |
| 补偿 durable（`pragmatic-ddd.compensation.mode=durable`） | `compensation/*.xml` | `compensation_log` | 同上，`...CompensationMapper.insert` |
| DB 号段 ID（`IdGeneratorDefinition` / `DbSegmentAllocator`） | `id/*.xml` | `id_segment`（含渠道初始行） | 取号时 `...IdSegmentMapper.selectForUpdate` 找不到 |
| 纯内存补偿 / 不用 Outbox / 用 Redis 号段 | 不需要 | 不需要 | — |

> 注意：`pragmatic-ddd-spring-boot` 的 `DddOutboxAutoConfiguration` / `DddCompensationAutoConfiguration` / `DddIdGeneratorAutoConfiguration` **只做 Bean 装配**（以容器内 `SqlSessionTemplate` 为前置条件），**不负责加载 Mapper XML**。starter 不能替代本节配置。

**PostgreSQL 方言**：三组 Mapper 均提供 PG 版 XML，目录为 `io/pragmatic/ddd/mybatis/{outbox,compensation,id}/postgres/*.xml`（namespace 与 MySQL 版相同，每条语句带 `databaseId="postgres"`，建表脚本见 §3.4）。使用 PG 时的两点差异：

1. 扫描 glob 由 `outbox/*.xml` 换成 `outbox/postgres/*.xml`（compensation / id 同理），**双方言只扫一种**；即使误扫两份也不会启动冲突，但会多注册一套无用语句；
2. 必须为 `SqlSessionFactory` 设置方言标识，否则方言语句全部不注册（静默失效，运行期首调 SQL 才报 `Mapped Statements collection does not contain value for ...`）：

```java
@Bean
public DatabaseIdProvider databaseIdProvider() {
    VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
    provider.setProperties(Map.of("PostgreSQL", "postgres", "MySQL", "mysql"));
    return provider;
}
// SqlSessionFactoryBean 需同时 setDatabaseIdProvider(...)；
// 非 Spring 场景直接 configuration.setDatabaseId("postgres")
```

PG 版方言 XML 内置了三种适配：`claimPending` 用物化 CTE + `FOR UPDATE SKIP LOCKED` 替代 MySQL 的 `UPDATE ... ORDER BY ... LIMIT`（普通子查询会被嵌套循环逐行重估导致超批次，必须 `AS MATERIALIZED`）；租约回收用 `make_interval(secs => ...)` 替代 `DATE_SUB`；可空参数全部显式 `jdbcType`（MyBatis 默认 `jdbcTypeForNull=OTHER` 在 PG 会报无法确定参数类型）。业务自写 Mapper 的 PG 适配约定（JSON 列三策略、枚举状态列不用 PG 自定义 enum 等）见仓库设计文档 `docs/design/core/mybatis/postgres-dialect-support-design.md` §8。

### 3.4 框架表建表脚本

建表脚本同样在 jar 内，按所用能力取用：

```text
io/pragmatic/ddd/mybatis/outbox/schema/outbox-schema-mysql.sql
io/pragmatic/ddd/mybatis/compensation/schema/compensation-log-schema-mysql.sql
io/pragmatic/ddd/mybatis/id/schema/id-segment-schema-mysql.sql
```

PostgreSQL 版（与 MySQL 版逐表等价：`LONGTEXT→text`、`DATETIME(3)→TIMESTAMP(3)`、内联索引拆为独立 `CREATE INDEX`、幂等可重跑）：

```text
io/pragmatic/ddd/mybatis/outbox/schema/outbox-schema-postgres.sql
io/pragmatic/ddd/mybatis/compensation/schema/compensation-log-schema-postgres.sql
io/pragmatic/ddd/mybatis/id/schema/id-segment-schema-postgres.sql
```

三张表的索引要点：

- `outbox_message`：主键 `id`；`(status, created_at)` 支撑 Relay 认领；`claim_token` 支撑批量取回。
- `compensation_log`：主键 `action_key`；`(status, updated_at)` 支撑补偿轮询。
- `id_segment`：主键 `biz_key`；每条渠道一行初始记录。

## 4. SqlSessionTemplate

`SqlSessionTemplate` 是 Spring 托管的**线程安全** SqlSession，绑定上述工厂，供仓储实现（`doInsert` / `doUpdate` / `doRemove`）直接操作 MyBatis，并**自动参与到 Spring 声明式事务**中。

```java
@Bean
public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
}
```

## 5. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 用 `mybatis-config.xml` + `setConfigLocation` 装配 TypeHandler | 与 `setConfiguration` 互斥，装配分散难审查 | 原生 `Configuration` 统一注入后 `setConfiguration` |
| `sqlSessionFactory` 用单个 `TypeHandlerContext` 入参 | 多聚合时抛 `NoUniqueBeanDefinitionException` | 入参用 `List<TypeHandlerContext>` |
| 用包扫描装配复杂 TypeHandler | 泛型/动态构建的处理器无法被发现 | `TypeHandlerContext.registerInto` 在构建阶段手动注入 |
| 只扫 `classpath:mapper/**/*.xml` | 跨 jar 扫不到框架 XML，Outbox/号段功能运行期报绑定异常 | 用 `classpath*:` 并显式列出三个框架目录 |
| 认为 starter 会自动注册框架 Mapper | 自动配置只装配 Bean，不加载 XML | 在 `MyBatisConfig` 显式扫描框架 XML |
| 只 `@Import(MySqlConfig.class)` 做单测 | 缺 `SqlSessionTemplate`，或 Mapper XML 解析期缺 handler | 同时导入 `MySqlConfig` + `MyBatisConfig` + 聚合 TypeHandler 配置 |
| 枚举单列与 JSON 通道配置不一致 | 同一枚举两处解析结果不同 | 三者共用同一 resolver / serializer / enumRules |
| 集合 TypeHandler 遇到多表同名列 | 启动期抛 `IllegalStateException` | 用 SQL `AS` 别名隔离列类型 |
| MySQL 用错 JSON 方言 | 写 JSON 列失败 | MySQL 用 `JdbcJsonValue.MYSQL`，PG 用 `PgJdbcJsonValue` |
| PG 场景仍扫 MySQL 版框架 XML | `claimPending` 等方言语句在 PG 直接语法报错 | 换扫 `{outbox,compensation,id}/postgres/*.xml` 并设置方言标识 |
| PG 设置了方言 XML 却未设置 `databaseId` | 方言语句全部不注册，启动正常但运行期首调 SQL 才报绑定异常 | `VendorDatabaseIdProvider`（Spring）或 `configuration.setDatabaseId("postgres")`（原生） |
| PG 业务 Mapper 可空参数不写 `jdbcType` | `could not determine data type of parameter $n` | 可空参数逐个显式 `jdbcType`（框架 PG XML 已内置，业务 XML 照做） |

## 下一步

- [MySQL 配置设计原则](./mysql-config.md)：数据源、连接池与事务管理器
- [MyBatis 集成](./../integration/mybatis.md)：枚举 / JSON / 集合三通道的内部实现
- [仓储设计原则](./repository-design.md)：仓储如何按 `namespace.statementId` 直调语句
- [Outbox 链路装配](./outbox-config.md)：`OutboxMapper` 与事务性发件箱
