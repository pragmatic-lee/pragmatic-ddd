# BP 卡：MyBatis 配置设计原则（mybatis-config，会话工厂与 Mapper）

> 源：`documentation/best-practices/mybatis-config.md` · 派生时间：2026-10-17 · 框架版本：2.0.0

## 1. 职责边界

- **`MyBatisConfig`（本卡）**：产 `SqlSessionFactory` 与 `SqlSessionTemplate`，聚合各聚合 `TypeHandlerContext`，并把**框架固定 Mapper** 与业务 Mapper 一并纳入扫描。
- **`MySqlConfig`**：只产 `DataSource` 与 `PlatformTransactionManager` → 见 `bp/mysql-config.md`。
- **聚合专属 `{Agg}MybatisTypeHandlerConfig`**：落 `infrastructure/config/{agg}/`，只登记本聚合枚举/值对象。
- 依赖单向：`MyBatisConfig → MySqlConfig`；`sqlSessionFactory` 以 `DataSource` 为入参，Spring 按类型注入，与类名无关。

## 2. SqlSessionFactory 构建

### 2.1 原生 Configuration，而不是 configLocation

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

⚠️ `setConfiguration` 与 `setConfigLocation` **互斥**，同时设置抛 `IllegalStateException`。TypeHandler 必须经原生 `Configuration` 注入，因此**不要再用 `mybatis-config.xml`**。

### 2.2 全局开关

| 开关 | 值 | 说明 |
| --- | --- | --- |
| `mapUnderscoreToCamelCase` | `true` | 下划线列 → 驼峰属性 |
| `cacheEnabled` | `true` | 全局缓存 |
| `lazyLoadingEnabled` | `true` | 支撑子集合懒加载 |
| `aggressiveLazyLoading` | `false` | 按需加载 |
| `logImpl` | `Slf4jImpl.class` | 统一 SLF4J |

### 2.3 入参必须是 List<TypeHandlerContext>

- 每个聚合各产出一个 `TypeHandlerContext` Bean，声明 `List<T>` 时 Spring 全量收集注入。
- 声明单个 `TypeHandlerContext` → 多聚合时抛 `NoUniqueBeanDefinitionException`。
- 单聚合：列表一个元素；多聚合：自动全收集，**新增聚合只新增 Bean，`MyBatisConfig` 零改动**；零聚合：空列表循环空转，不报错。
- 空列表不报错，但 Mapper XML 解析期缺 handler 会抛 `BuilderException`：单测 `@Import` 必须同时带 `MySqlConfig` + `MyBatisConfig` + 聚合 TypeHandler 配置。

`TypeHandlerContext` 是 record，固定 6 参数：

| 参数 | 含义 |
| --- | --- |
| `resolver` | `EnumValueResolver` 枚举解析注册表 |
| `serializer` | JSON 序列化器（默认 `Fastjson2JsonSerializer`） |
| `jdbcJsonValue` | JDBC JSON 方言（`JdbcJsonValue.MYSQL` / `PgJdbcJsonValue`） |
| `enumRules` | 枚举类→持久化策略（`EnumRule.CODE`…） |
| `voTypes` | 需 JSON 通道的 `IValueObject` 清单 |
| `collections` | 集合通道配置（`CollectionElementTypeConfig.empty()`） |

三通道：枚举（`EnumRule.CODE` 写 `getValue()`）、JSON（`GenericJsonTypeHandler` 写原生 JSON 列）、集合（`ListTypeHandler` 注册 `List.class`）。框架 `UniversalEnumTypeHandler` / `GenericJsonTypeHandler<T>` / `ListTypeHandler` 均运行时动态构建，**包扫描无法生成实例**，只能经 `TypeHandlerContext.registerInto` 手动装配。
**单点来源**：枚举单列与 JSON 通道必须共用同一 `resolver` / `serializer` / `enumRules`，否则同一枚举两处解析结果不一致；不同聚合对同一枚举设定不一致策略会后注册覆盖，应避免。MySQL 用 `JdbcJsonValue.MYSQL`（文本），PG 用 `PgJdbcJsonValue`。

## 3. 纯 XML Mapper 加载

### 3.1 不使用 @MapperScan

不使用 `@MapperScan`，不在 Java 中持有 Mapper 接口类；仓储按 `namespace.statementId` 通过 `sqlSession.insert/update/select/delete` 直调语句。

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

- **必须用 `classpath*:`（带星号）**：单 `classpath:` 只在单个 classpath 根内匹配，跨 jar 扫不到 `pragmatic-ddd-mybatis` 依赖内的框架 XML。
- **不能只扫 `mapper/**`**：框架 XML 物理路径是 `io/pragmatic/ddd/mybatis/**`，与业务 `mapper/` 并列，`mapper/**` 命中不了。
- **框架 Mapper 建议无条件注册**：MyBatis 解析 XML 不校验表是否存在，多注册安全；漏注册只在对应功能首次调用时暴露。

### 3.3 框架固定 Mapper 清单

`pragmatic-ddd-mybatis` 内置三组与业务无关的 Mapper，**必须由使用方 `SqlSessionFactory` 扫描加载**：

| 能力 | Mapper XML（jar 内） | 表 | namespace（固定） |
| --- | --- | --- | --- |
| Outbox | `io/pragmatic/ddd/mybatis/outbox/OutboxMapper.xml` | `outbox_message` | `io.pragmatic.ddd.mybatis.outbox.OutboxMapper` |
| 补偿（durable） | `io/pragmatic/ddd/mybatis/compensation/CompensationMapper.xml` | `compensation_log` | `io.pragmatic.ddd.mybatis.compensation.CompensationMapper` |
| 号段 ID | `io/pragmatic/ddd/mybatis/id/IdSegmentMapper.xml` | `id_segment` | `io.pragmatic.ddd.mybatis.id.IdSegmentMapper` |

语句常量类（与 XML `namespace.statementId` 一一对应）：`OutboxStatements` / `CompensationStatements` / `IdSegmentStatements`。

**触发矩阵**：

| 使用的能力 | 必须注册 | 必须建表 | 缺失症状 |
| --- | --- | --- | --- |
| Outbox（`OutboxCommandExecutor` / `OutboxRelay`） | `outbox/*.xml` | `outbox_message` | 启动正常，首发事件抛 `Mapped Statements collection does not contain value for ...OutboxMapper.insertBatch` |
| 补偿 durable（`pragmatic-ddd.compensation.mode=durable`） | `compensation/*.xml` | `compensation_log` | 同上，`...CompensationMapper.insert` |
| DB 号段 ID（`IdGeneratorDefinition` / `DbSegmentAllocator`） | `id/*.xml` | `id_segment`（含渠道初始行） | 取号时 `...IdSegmentMapper.selectForUpdate` 找不到 |
| 纯内存补偿 / 不用 Outbox / 用 Redis 号段 | 不需要 | 不需要 | — |

⚠️ `pragmatic-ddd-spring-boot` 的 `DddOutboxAutoConfiguration` / `DddCompensationAutoConfiguration` / `DddIdGeneratorAutoConfiguration` **只做 Bean 装配**（以容器内 `SqlSessionTemplate` 为前置条件），**不负责加载 Mapper XML**，starter 不能替代本节配置。

### 3.4 框架表建表脚本（jar 内）

```text
io/pragmatic/ddd/mybatis/outbox/schema/outbox-schema-mysql.sql
io/pragmatic/ddd/mybatis/compensation/schema/compensation-log-schema-mysql.sql
io/pragmatic/ddd/mybatis/id/schema/id-segment-schema-mysql.sql
```

索引要点：`outbox_message` 主键 `id`、`(status, created_at)` 支撑 Relay 认领、`claim_token` 支撑批量取回；`compensation_log` 主键 `action_key`、`(status, updated_at)` 支撑轮询；`id_segment` 主键 `biz_key`，每条渠道一行初始记录。

## 4. SqlSessionTemplate

`SqlSessionTemplate` 是 Spring 托管的线程安全 SqlSession，绑定上述工厂，供仓储 `doInsert` / `doUpdate` / `doRemove` 直调 MyBatis，并自动参与 Spring 声明式事务。

```java
@Bean
public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
}
```

## 5. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 用 `mybatis-config.xml` + `setConfigLocation` 装配 TypeHandler | 原生 `Configuration` 注入后 `setConfiguration` |
| `sqlSessionFactory` 用单个 `TypeHandlerContext` 入参 | 入参用 `List<TypeHandlerContext>` |
| 用包扫描装配复杂 TypeHandler | `TypeHandlerContext.registerInto` 构建阶段手动注入 |
| 只扫 `classpath:mapper/**/*.xml` | 用 `classpath*:` 并显式列出三个框架目录 |
| 认为 starter 会自动注册框架 Mapper | 在 `MyBatisConfig` 显式扫描框架 XML |
| 只 `@Import(MySqlConfig.class)` 做单测 | 同时导入 `MySqlConfig` + `MyBatisConfig` + 聚合 TypeHandler 配置 |
| 枚举单列与 JSON 通道配置不一致 | 三者共用同一 resolver / serializer / enumRules |
| 集合 TypeHandler 遇多表同名列 | SQL `AS` 别名隔离 |
| MySQL 用错 JSON 方言 | MySQL 用 `JdbcJsonValue.MYSQL`，PG 用 `PgJdbcJsonValue` |

## 6. 被读于

S3 `ddd-infra-config`、S7 `ddd-code-review`；Outbox 链路见 `bp/outbox-config.md`，数据源与事务见 `bp/mysql-config.md`。
