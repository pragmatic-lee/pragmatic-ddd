# BP 卡：MySQL 配置设计原则（mysql-config）

> 源：`documentation/best-practices/mysql-config.md` · 派生时间：2026-10-14 · 框架版本：2.0.0

## 1. 关键约定

- 通用 `MySqlConfig` 统一提供 `DataSource`/`SqlSessionFactory`/`SqlSessionTemplate`/`TransactionManager` 四 Bean，并**聚合注册**各聚合 TypeHandler；不 import 任何聚合类型，可跨模块复用。
- 聚合专属 `{Agg}MybatisTypeHandlerConfig` 落在 `infrastructure/config/{agg}/`，只产出本聚合枚举/值对象 TypeHandler 清单。
- 连接参数（`spring.datasource.*`）外部化，代码不硬编码密钥。
- 显式声明数据源，启动类排除 `DataSourceAutoConfiguration`，不依赖自动装配。
- 配置与使用分离：配置产 Bean，仓储实现消费 `SqlSessionTemplate`（自动参与 Spring 声明式事务）。

## 2. SqlSessionFactory 构建要点

- 创建**原生 `Configuration` 对象**，在构建阶段把各聚合 `TypeHandlerContext` 逐个 `registerInto`，再 `setConfiguration` 注入工厂（不用 `setConfigLocation` 加载 XML 装配 TypeHandler）。
- `sqlSessionFactory` 入参必须是 **`List<TypeHandlerContext>`**：Spring 自动收集所有 `T` 型 Bean；若声明单个 `TypeHandlerContext`，多聚合时抛 `NoUniqueBeanDefinitionException`。新增聚合只新增专属配置产 Bean，通用 `MySqlConfig` 零改动。
- 全局开关：`mapUnderscoreToCamelCase=true`、`cacheEnabled=true`、`lazyLoadingEnabled=true`、`aggressiveLazyLoading=false`、`logImpl=Slf4jImpl.class`。
- 纯 XML Mapper：不用 `@MapperScan`、Java 不持 Mapper 接口类；由 `mybatis-config.xml` `<mappers>` 统一声明，工厂 `classpath:mapper/**/*.xml` 定位。仓储按 `namespace.id` 调语句。

## 3. TypeHandler 体系（用 TypeHandlerContext，非包扫描）

原因：框架 `UniversalEnumTypeHandler`/`GenericJsonTypeHandler<T>`/`ListTypeHandler` 均运行时动态构建，包扫描无法生成对应实例。

`TypeHandlerContext` 是 record，固定 6 参数：

| 参数 | 含义 |
| --- | --- |
| `resolver` | `EnumValueResolver` 枚举解析注册表 |
| `serializer` | JSON 序列化器（默认 `Fastjson2JsonSerializer`） |
| `jdbcJsonValue` | JDBC JSON 方言（`JdbcJsonValue.MYSQL` / `PgJdbcJsonValue`） |
| `enumRules` | 枚举类→持久化策略（`EnumRule.CODE`…） |
| `voTypes` | 需 JSON 通道的 `IValueObject` 清单 |
| `collections` | 集合通道配置（`CollectionElementTypeConfig.empty()`） |

三通道：枚举（`EnumRule.CODE` 写 `getValue()`）、JSON（`GenericJsonTypeHandler` 写原生 JSON 列）、集合（`ListTypeHandler` 注册 `List.class`）。
**单点来源**：枚举单列与 JSON 通道必须共用同一 `resolver`/`serializer`/`enumRules`。MySQL 用 `JdbcJsonValue.MYSQL`（文本）；PG 用 `PgJdbcJsonValue`。不同聚合对同一枚举设定不一致策略会后注册覆盖，应避免。

> 列表为空不报错但 Mapper XML 解析期缺 handler 抛 `BuilderException`：单独装配须 `@Import` 同时带 `MySqlConfig` 与聚合 TypeHandler 配置。

## 4. 事务管理器

`DataSourceTransactionManager` 绑定数据源，配合 `@EnableTransactionManagement`；事务边界由应用层 `@Transactional` 编排，仓储不自管事务。

## 5. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 连接参数硬编码 | 外部化 `spring.datasource.*` |
| 依赖 `DataSourceAutoConfiguration` | 排除自动配置，显式声明 |
| 聚合 TypeHandler 写进通用 `MySqlConfig` | 放各聚合专属 `TypeHandlerConfig` |
| `sqlSessionFactory` 用单个 `TypeHandlerContext` 入参 | 入参用 `List<TypeHandlerContext>` |
| 用包扫描装配复杂 TypeHandler | `TypeHandlerContext.registerInto` 手动注入 |
| 单独测试只 import `MySqlConfig` | 同时 `@Import` 聚合 TypeHandler 配置 |
| 枚举单列与 JSON 通道配置不一致 | 共用同一 resolver/serializer/enumRules |
| 集合 TypeHandler 遇多表同名列 | SQL `AS` 别名隔离 |
| MySQL 用错 JSON 方言 | `JdbcJsonValue.MYSQL`，PG 用 `PgJdbcJsonValue` |
| `aggressiveLazyLoading=true` | 置 `false` |
| 仓储内自管事务 | 交应用层 `@Transactional` |

## 6. 被读于

S3 `ddd-infra-config`、S7 `ddd-code-review`。
