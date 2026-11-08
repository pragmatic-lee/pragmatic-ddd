# BP 卡：MyBatis 配置设计原则（mybatis-config）

> 源：`documentation/best-practices/mybatis-config.md` · 派生时间：2026-11-08 · 框架版本：2.1.0

## 1. 职责边界（ORM 层）

`MyBatisConfig` 产 `SqlSessionFactory` 与 `SqlSessionTemplate`，接收框架合并出的唯一 `TypeHandlerContext`，并把**框架固定 Mapper**与业务 Mapper 一并扫描。数据源/事务管理器见 `mysql-config.md`。

## 2. SqlSessionFactory 构建

### 2.1 原生 Configuration，不用 configLocation

用原生 `Configuration` 在构建阶段 `registerInto` 唯一 `TypeHandlerContext`，再 `setConfiguration`：

```java
@Bean
public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                           TypeHandlerContext typeHandlerContext) throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
    typeHandlerContext.registerInto(configuration);   // 全局唯一，只注册一次
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

### 2.3 TypeHandler 装配：声明与装配分离

- **一个 `Configuration` 只能装配一份 `TypeHandlerContext`**：MyBatis 对 `List.class` 只有全局单槽（`register` 是 `put` 覆盖），每聚合一个上下文时只有最后一个生效。
- 聚合侧只产 `TypeHandlerDeclaration`（`enumRules` / `voTypes` / `collections` + 预留 `scope`），不持有 resolver / serializer / `JdbcJsonValue`。
- 框架侧 `TypeHandlerAssembler.assemble(declarations, jdbcJsonValue)` 合并全部声明并启动期 fail-fast，产出唯一上下文。
- `pragmatic-ddd-spring-boot` 提供 `DddMybatisTypeHandlerAutoConfiguration` 自动产出该单例（业务自定义则退让），方言优先取显式 `JdbcJsonValue` Bean，否则按数据源探测。
- 合并期 fail-fast：同枚举两种 `EnumRule`、同类型同时在 `enumRules`/`voTypes`、`voTypes` 未实现 `IValueObject`、集合元素枚举未声明策略、同标签不同类型/不同转换器、`scope` 非 `default`；运行期未登记列标签直接报错。
- 无任何集合声明时不注册 `List.class` 处理器。
- 列标签即契约：`CollectionMapping` 声明的标签必须与 Mapper XML 的 `<result column>` / SQL `AS` 别名逐字符一致（全限定符号见 `api-contract.md`）。
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
| 每个聚合各产一个 `TypeHandlerContext` Bean、入参用 `List<TypeHandlerContext>` | 聚合侧产出 `TypeHandlerDeclaration`，由 `TypeHandlerAssembler` 合并为唯一上下文 |
| 包扫描装配复杂 TypeHandler | `TypeHandlerContext.registerInto` 手动注入 |
| 只扫 `classpath:mapper/**` | `classpath*:` + 显式列出三个框架目录 |
| 认为 starter 自动注册框架 Mapper | 在 `MyBatisConfig` 显式扫描 |
| 只 `@Import(MySqlConfig.class)` 做单测 | 同时导入 `MySqlConfig`+`MyBatisConfig`+聚合 TypeHandler 声明 |
| 枚举/JSON 通道各自 new 解析器 | 只产 `TypeHandlerDeclaration`，全局组件由装配器统一构建 |
| 集合声明标签与 Mapper XML 不一致 | 标签与 `<result column>` / SQL `AS` 逐字符对齐（否则运行期报错） |
| MySQL 用错 JSON 方言 | 显式声明 `JdbcJsonValue` Bean，或依赖自动配置探测 |
| PG 场景仍套用 MySQL 装配 | 扫错方言 XML、未设 `databaseId` 或可空参数漏 `jdbcType`，运行期才报错；按 `postgresql-config.md` 装配 |

## 6. 被读于

S3 `ddd-infra-config`、`S7 `ddd-code-review`。
