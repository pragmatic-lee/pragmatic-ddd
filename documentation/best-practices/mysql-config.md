# MySQL 配置设计原则

> 本文档介绍使用 Pragmatic DDD 进行 MySQL 数据访问配置的最佳实践与常见反模式：先明确配置类的定位与职责边界，再说明 SqlSessionFactory 的构建要点与 TypeHandler 体系的装配机制，最后落到事务管理器与配置规范。

## 1. MySQL 配置设计原则

### 1.1 集中式配置类：一个模块一个配置

数据访问配置应集中在一个 `@Configuration` 类中（如 `MySqlConfig`），统一提供数据源、会话工厂、会话模板与事务管理器四个核心 Bean。配置不散落在各处，便于审查连接参数、Mapper 加载策略与类型处理器装配。

```java
@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
@EnableTransactionManagement
public class MySqlConfig {
    // DataSource / SqlSessionFactory / SqlSessionTemplate / TransactionManager 四 Bean
}
```

**设计含义**：配置类只负责装配基础设施 Bean，不包含任何业务逻辑，也不参与 Mapper 的 Java 接口管理（见 §2.3）。

### 1.2 外部化配置，不硬编码

连接参数（URL / 账号 / 密码 / 连接池参数）一律从外部化配置（`application.properties` 或环境变量）读取，通过 `spring.datasource.*` 注入，**代码中不出现任何连接串与密钥**。

```java
@Bean
public DataSource dataSource(DataSourceProperties properties) {
    return properties.initializeDataSourceBuilder()
            .type(com.zaxxer.hikari.HikariDataSource.class)
            .build();
}
```

> 对应 `application.properties`：`spring.datasource.url=...`、`spring.datasource.username=...`、`spring.datasource.password=...`。禁止在 Java 里硬编码。

### 1.3 不依赖自动配置，显式声明

配置类显式声明数据源，不依赖 Spring Boot 的 `DataSourceAutoConfiguration`（需在启动类中排除）。这样数据源、会话工厂、事务管理器由本配置全权掌控，避免隐式自动装配带来的不可预期性。

### 1.4 配置与使用分离

配置类产出 Bean，仓储实现消费 Bean（`SqlSessionTemplate`）。仓储只关心"怎么用"，不关心"怎么配"；配置只关心"怎么建"，不关心"怎么查"。

---

## 2. SqlSessionFactory 构建专题

`SqlSessionFactory` 是 MyBatis 的心脏，其构建要点集中在三处：**原生 Configuration 注入 TypeHandler**、**全局开关设置**、**Mapper XML 加载策略**。

### 2.1 原生 Configuration 对象，注入自定义 TypeHandler

不使用 `setConfigLocation` 加载 XML 配置来装配 TypeHandler，而是**创建原生 `Configuration` 对象**，在构建阶段手动注册 TypeHandler，再通过 `setConfiguration` 注入会话工厂。

```java
@Bean
public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

    // 注入自定义 TypeHandler
    Collection<TypeHandlerRegistration> typeHandlerRegistrations = registerTypeHandlers();
    typeHandlerRegistrations.forEach(typ -> {
        Class<?> aClass = typ.javaType();
        TypeHandler<?> handler = typ.handler();
        configuration.getTypeHandlerRegistry().register(aClass, (TypeHandler) handler);
    });

    // ... 全局开关 ...
    SqlSessionFactoryBean sessionFactory = createSessionFactory(dataSource, configuration);
    return sessionFactory.getObject();
}
```

**为什么必须手动注入而非包扫描**：框架的 `UniversalEnumTypeHandler`、`GenericJsonTypeHandler`、`ListTypeHandler` 都是**运行时按类型动态构建**的（泛型类需针对每个值对象 new 出实例，集合处理器需运行期装配查表配置），包扫描只能发现"类存在"，无法为每个具体类型生成对应实例。因此必须在 Configuration 构建阶段手动装配（详见 §3）。

### 2.2 全局开关

`Configuration` 上的四个开关对框架运行至关重要，按需开启：

| 开关 | 值 | 说明 |
| --- | --- | --- |
| `mapUnderscoreToCamelCase` | `true` | 下划线列 → 驼峰属性自动映射 |
| `cacheEnabled` | `true` | 开启全局缓存 |
| `lazyLoadingEnabled` | `true` | 开启延迟加载（支撑子集合懒加载） |
| `aggressiveLazyLoading` | `false` | 按需加载，不激进触发 |
| `logImpl` | `Slf4jImpl.class` | 统一走 SLF4J 日志 |

```java
configuration.setMapUnderscoreToCamelCase(true);
configuration.setCacheEnabled(true);
configuration.setLazyLoadingEnabled(true);
configuration.setAggressiveLazyLoading(false);
configuration.setLogImpl(Slf4jImpl.class);
```

### 2.3 纯 XML Mapper 加载

Mapper 通过 MyBatis 原生 SQL Mapper Config（`mybatis-config.xml`）组织，**不使用 `@MapperScan`，也不在 Java 中持有 Mapper 接口类**。所有 Mapper 的 XML 由 `mybatis-config.xml` 的 `<mappers>` 统一声明加载（含框架提供的 `OutboxMapper` / `IdSegmentMapper`），会话工厂通过 `classpath:mapper/**/*.xml` 定位 XML 文件。

```java
private SqlSessionFactoryBean createSessionFactory(DataSource dataSource, Configuration configuration) throws IOException {
    SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
    sessionFactory.setDataSource(dataSource);
    sessionFactory.setConfiguration(configuration); // 注入已配置的 Configuration

    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    sessionFactory.setMapperLocations(resolver.getResources("classpath:mapper/**/*.xml"));
    return sessionFactory;
}
```

**设计含义**：仓储通过 `sqlSession.insert/update/select/delete("OrderMapper.xxx", param)` 按 namespace + id 调用语句，与 [仓储设计原则](./repository-design.md) 中的纯 XML Mapper 约定一致。

### 2.4 会话模板

`SqlSessionTemplate` 是 Spring 托管的**线程安全** SqlSession，绑定上述工厂，供仓储实现（`doInsert` / `doUpdate` / `doRemove`）直接操作 MyBatis，并**自动参与到 Spring 声明式事务**中。

```java
@Bean
public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
    return new SqlSessionTemplate(sqlSessionFactory);
}
```

---

## 3. TypeHandler 体系装配

### 3.1 为什么用 TypeHandlerContext 而非包扫描

框架的复杂类型（枚举 / 值对象 JSON / 集合）通过 `TypeHandlerContext` 统一初始化，而不是包扫描。原因：

- **枚举** `UniversalEnumTypeHandler` 需按 `EnumRule` 针对每个枚举类运行时构建；
- **值对象 JSON** `GenericJsonTypeHandler<T>` 是泛型类，需为每个 `IValueObject` 用 `new GenericJsonTypeHandler<>(voType, ...)` 实例化后才能配对注册；
- **集合** `ListTypeHandler` 是单例，但内部持有运行期才装配的查表配置。

包扫描无法为这些动态构建的处理器生成对应实例，因此必须由 `TypeHandlerContext` 在 Configuration 构建阶段手动装配。

### 3.2 三通道装配

`TypeHandlerContext` 是一个 record，按固定顺序接收 6 个参数，内部把三类 TypeHandler 并行构建汇入同一注册表：

| 参数 | 含义 |
| --- | --- |
| `resolver` | 枚举解析注册表（运行期反序列化 O(1) 查表） |
| `serializer` | JSON 序列化器（默认 `Fastjson2JsonSerializer`） |
| `jdbcJsonValue` | JDBC 驱动 JSON 方言适配器 |
| `enumRules` | 枚举类 → 持久化策略映射 |
| `voTypes` | 需登记 JSON 通道的 `IValueObject` 类型清单 |
| `collections` | 集合通道配置 |

```java
private Collection<TypeHandlerRegistration> registerTypeHandlers() {
    EnumValueResolver resolver = new EnumValueResolver();
    Map<Class<?>, EnumRule> enumRules = Map.of(
            OrderStatus.class, EnumRule.CODE,
            PaymentMethod.class, EnumRule.CODE
    );
    List<Class<?>> voTypes = List.of(
            Customer.class, Address.class, Money.class,
            PaymentInfo.class, LogisticsInfo.class
    );
    CollectionElementTypeConfig collections = CollectionElementTypeConfig.empty();
    TypeHandlerContext context = new TypeHandlerContext(
            resolver,
            new Fastjson2JsonSerializer(resolver, enumRules),
            JdbcJsonValue.MYSQL,
            enumRules,
            voTypes,
            collections
    );
    return context.registrations();
}
```

**三个通道**：

- **枚举通道**：按 `EnumRule.CODE` 持久化——`UniversalEnumTypeHandler` 写库取 `IEnumValue.getValue()`（业务 code），读库由 `EnumValueResolver` 预建索引反查，零反射。
- **JSON 通道**：`GenericJsonTypeHandler` 把 `IValueObject` 序列化为结构化 JSON，写原生 JSON 列；`JdbcJsonValue.MYSQL` 将 JSON 结构转为文本字符串以兼容 MySQL 驱动写入要求。
- **集合通道**：`ListTypeHandler`（单例，注册到 `List.class`）按列标签还原泛型元素类型。

> 方言差异：MySQL 用 `JdbcJsonValue.MYSQL`（文本形式）；PostgreSQL 需用 `PgJdbcJsonValue`（`PGobject`，需 `org.postgresql` 运行期依赖）。跨库迁移时注意替换。

### 3.3 单点来源：三类配置必须共用

枚举策略是**单点来源**：枚举单列通道与 JSON 通道必须共用**同一 `resolver` / `serializer` / `enumRules`**，否则同一枚举在两处解析结果不一致。上例中 `EnumValueResolver` 与 `Fastjson2JsonSerializer` 都持有 `enumRules`，且 resolver 被 serializer 复用，保证一致。

### 3.4 集合 TypeHandler 的列别名避坑

`ListTypeHandler` 靠结果集 `columnLabel` 还原泛型，**多表同名列不同类型时需用 SQL `AS` 别名隔离**，否则启动期抛 `IllegalStateException`。

---

## 4. 事务管理器

绑定数据源的事务管理器，为 `@Transactional` 提供底层支撑。MyBatis 场景用 `DataSourceTransactionManager`：

```java
@Bean
public PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
}
```

配合 `@EnableTransactionManagement` 开启声明式事务。事务边界由应用层（`@Transactional`）负责编排，仓储不自行管理事务（详见 [仓储设计原则](./repository-design.md) §1.5）。

---

## 5. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 连接参数硬编码在 Java 中 | 密钥泄露、无法按环境切换 | 从 `spring.datasource.*` 外部化配置读取 |
| 依赖 `DataSourceAutoConfiguration` 隐式装配 | 数据源不可控、与自定义 TypeHandler 冲突 | 显式声明数据源，启动类排除自动配置 |
| 用包扫描装配复杂 TypeHandler | 泛型/动态构建的处理器无法被发现 | 用 `TypeHandlerContext.registerInto` 在构建阶段手动注入 |
| `setConfigLocation` 混用 XML 装配 TypeHandler | 装配分散、难审查 | 原生 Configuration 对象统一注入后 `setConfiguration` |
| 枚举单列与 JSON 通道配置不一致 | 同一枚举两处解析结果不同 | 三者共用同一 resolver / serializer / enumRules |
| 集合 TypeHandler 遇到多表同名列 | 启动期抛 `IllegalStateException` | 用 SQL `AS` 别名隔离列类型 |
| MySQL 用错 JSON 方言 | 写 JSON 列失败 | MySQL 用 `JdbcJsonValue.MYSQL`，PG 用 `PgJdbcJsonValue` |
| 开启 `aggressiveLazyLoading=true` | 不必要的级联加载、性能下降 | 按需加载，置为 `false` |
| 仓储内自管事务 | 事务边界混乱 | 交给应用层 `@Transactional`，配置层只产出事务管理器 |

---

## 下一步

- [仓储设计原则](./repository-design.md)：仓储如何消费 `SqlSessionTemplate`、纯 XML Mapper 约定
- [聚合设计原则](./aggregate-design.md)：聚合根、`TrackedList` 子集合与懒加载
- [值对象最佳实践](./value-object.md)：值对象 JSON 序列化往返
- [枚举值](./enum-value.md)：枚举按 CODE 持久化
- [Outbox 链路装配](./outbox-config.md)：`OutboxMapper` 与异构事件投递
- [MyBatis 集成](./../integration/mybatis.md)：框架 mybatis 模块架构详解
