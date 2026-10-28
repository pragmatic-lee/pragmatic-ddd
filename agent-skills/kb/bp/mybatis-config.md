# BP 卡：MyBatis 配置设计原则（mybatis-config）

> 源：`documentation/best-practices/mybatis-config.md` · 派生时间：2026-10-28 · 框架版本：2.1.0-SNAPSHOT

## 1. 职责边界（ORM 层）

`MyBatisConfig` 产 `SqlSessionFactory` 与 `SqlSessionTemplate`，聚合各聚合的 `TypeHandlerContext`，并把**框架固定 Mapper**与业务 Mapper 一并扫描。数据源/事务管理器见 `mysql-config.md`。

## 2. SqlSessionFactory 构建

### 2.1 原生 Configuration，不用 configLocation

用原生 `Configuration` 在构建阶段 `registerInto` 各聚合 `TypeHandlerContext`，再 `setConfiguration`：

```java
@Bean
public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                           List<TypeHandlerContext> typeHandlerContexts) throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
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

> `setConfiguration` 与 `setConfigLocation` **互斥**，同时设置抛 `IllegalStateException`。

### 2.2 全局开关

`mapUnderscoreToCamelCase=true`、`cacheEnabled=true`、`lazyLoadingEnabled=true`、`aggressiveLazyLoading=false`、`logImpl=Slf4jImpl.class`。

### 2.3 TypeHandler 多聚合装配

- 入参必须是 **`List<TypeHandlerContext>`**（Spring 收集全部 Bean）；声明单个会多聚合时抛 `NoUniqueBeanDefinitionException`。
- 新增聚合只需新增一个 `TypeHandlerContext` Bean，`MyBatisConfig` 零改动。
- 必须手动装配（`TypeHandlerContext.registerInto`），包扫描发现不了动态构建的处理器。

## 3. 纯 XML Mapper 加载

### 3.1 不使用 @MapperScan

仓储按 `namespace.statementId` 经 `sqlSession.insert/update/select/delete` 直调语句。

### 3.2 必须用 classpath*:

```java
resolver.getResources("classpath*:io/pragmatic/ddd/mybatis/outbox/*.xml");
resolver.getResources("classpath*:io/pragmatic/ddd/mybatis/id/*.xml");
resolver.getResources("classpath*:io/pragmatic/ddd/mybatis/compensation/*.xml");
resolver.getResources("classpath*:mapper/**/*.xml");
```

- 必须 `classpath*:`（带星），否则跨 jar 扫不到框架 XML。
- 不能只扫 `mapper/**`，框架 XML 物理路径是 `io/pragmatic/ddd/mybatis/...`。

### 3.3 框架固定 Mapper 清单（必须由使用方扫描加载）

| 能力 | Mapper XML（jar 内） | 表 | namespace |
| --- | --- | --- | --- |
| Outbox | `io/pragmatic/ddd/mybatis/outbox/OutboxMapper.xml` | `outbox_message` | `io.pragmatic.ddd.mybatis.outbox.OutboxMapper` |
| 补偿 durable | `io/pragmatic/ddd/mybatis/compensation/CompensationMapper.xml` | `compensation_log` | `io.pragmatic.ddd.mybatis.compensation.CompensationMapper` |
| 号段 ID | `io/pragmatic/ddd/mybatis/id/IdSegmentMapper.xml` | `id_segment` | `io.pragmatic.ddd.mybatis.id.IdSegmentMapper` |

对应语句常量：`OutboxStatements` / `CompensationStatements` / `IdSegmentStatements`。建表脚本：`io/pragmatic/ddd/mybatis/{outbox,compensation,id}/schema/*-mysql.sql`（PG 版路径为同目录 `*-postgres.sql`，见 `postgresql-config.md` §5）。

**PostgreSQL 方言**：三组 Mapper 均提供 PG 版 XML，目录 `io/pragmatic/ddd/mybatis/{outbox,compensation,id}/postgres/*.xml`（namespace 与 MySQL 版相同，每条语句带 `databaseId="postgres"`）。使用 PG 时有三条硬约束：

1. 扫描 glob 换成 `{outbox,compensation,id}/postgres/*.xml`，**只扫一种方言**；
2. 必须为 `SqlSessionFactory` 设置 `databaseId`（Spring 用 `VendorDatabaseIdProvider`，原生用 `configuration.setDatabaseId("postgres")`），否则方言语句全部不注册；
3. 可空参数逐个显式 `jdbcType`，否则 PG 报 `could not determine data type of parameter $n`。

完整装配骨架、PG 建表脚本、JSON 列策略与语句改写见 `postgresql-config.md`。

> `pragmatic-ddd-spring-boot` 的 `Ddd*AutoConfiguration` **只装配 Bean，不加载 Mapper XML**；starter 不能替代本节配置。

## 4. SqlSessionTemplate

```java
@Bean
public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
}
```

线程安全，自动参与 Spring 声明式事务，供仓储 `doInsert/doUpdate/doRemove` 使用。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 用 `mybatis-config.xml` + `setConfigLocation` 装配 TypeHandler | 原生 `Configuration` + `setConfiguration` |
| `sqlSessionFactory` 用单个 `TypeHandlerContext` 入参 | 入参用 `List<TypeHandlerContext>` |
| 包扫描装配复杂 TypeHandler | `TypeHandlerContext.registerInto` 手动注入 |
| 只扫 `classpath:mapper/**` | `classpath*:` + 显式列出三个框架目录 |
| 认为 starter 自动注册框架 Mapper | 在 `MyBatisConfig` 显式扫描 |
| 只 `@Import(MySqlConfig.class)` 做单测 | 同时导入 `MySqlConfig`+`MyBatisConfig`+聚合 TypeHandler 配置 |
| 枚举/JSON 通道配置不一致 | 共用同一 resolver/serializer/enumRules |
| 集合 TypeHandler 多表同名列 | SQL `AS` 别名隔离 |
| MySQL 用错 JSON 方言 | MySQL 用 `JdbcJsonValue.MYSQL`，PG 用 `PgJdbcJsonValue` |
| PG 场景仍套用 MySQL 装配 | 扫错方言 XML、未设 `databaseId` 或可空参数漏 `jdbcType`，运行期才报错；按 `postgresql-config.md` 装配 |

## 6. 被读于

S3 `ddd-infra-config`、`S7 `ddd-code-review`。
