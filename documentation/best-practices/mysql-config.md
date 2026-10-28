# MySQL 配置设计原则（数据源与事务）

> 本文档只讲 **JDBC 层**：数据源、连接池与事务管理器。MyBatis 的会话工厂、TypeHandler 与 Mapper 加载见 [MyBatis 配置设计原则](./mybatis-config.md)。
>
> PostgreSQL 使用方见 [PostgreSQL 配置设计原则](./postgresql-config.md)：JDBC 数据源与事务管理器骨架可复用，但驱动、方言 XML、`databaseId` 与建表脚本不同。

::: info 与 `pragmatic-ddd-spring-boot` 的关系
本文主张的「显式声明、不依赖自动配置」只适用于 **DataSource / PlatformTransactionManager** 这一层，它们属于环境专属决策，starter 不接管、也不重造。starter 只**消费**本配置产出的 `PlatformTransactionManager`，以及 [MyBatis 配置](./mybatis-config.md) 产出的 `SqlSessionTemplate`，把上层的 `TransactionOperations`、Outbox、号段 ID 等通用装配接起来（见 [Spring Boot Starter 集成](./../integration/spring-boot.md)）。

注意：无论是否引入 starter，框架自带的 `io/pragmatic/ddd/mybatis/**/*.xml` 都必须纳入 Mapper 扫描路径，详见 [MyBatis 配置设计原则 §3](./mybatis-config.md)。
:::

## 1. 职责边界

数据访问装配拆成两层，避免把 ORM 细节混进数据源配置：

- **`MySqlConfig`（本文）**：只产 `DataSource` 与 `PlatformTransactionManager`，并开启 `@EnableTransactionManagement`；不 import 任何 MyBatis 类型。
- **`MyBatisConfig`（见 [MyBatis 配置设计原则](./mybatis-config.md)）**：产 `SqlSessionFactory` 与 `SqlSessionTemplate`，聚合各聚合的 `TypeHandlerContext` 并加载 Mapper XML。
- **聚合专属 `{Agg}MybatisTypeHandlerConfig`**：每个聚合一个，产出该聚合的枚举与值对象 TypeHandler 清单。

依赖方向单向：`MyBatisConfig → MySqlConfig`（会话工厂依赖数据源），无循环。

```text
infrastructure/config/                      # 通用技术配置
├── MySqlConfig.java                        # DataSource / TransactionManager
├── MyBatisConfig.java                      # SqlSessionFactory / SqlSessionTemplate
└── {agg}/
    ├── {Agg}MybatisTypeHandlerConfig.java  # 该聚合专属 TypeHandler 装配
    └── ...                                 # 其它绑定聚合的配置
```

**设计含义**：数据源配置不感知 ORM，也不感知具体聚合；新增聚合只新增一个 `TypeHandlerContext` Bean，`MySqlConfig` 与 `MyBatisConfig` 均零改动。

## 2. DataSource 装配

### 2.1 外部化配置，不硬编码

连接参数（URL / 账号 / 密码 / 连接池参数）一律从外部化配置（`application.yaml` 或环境变量）读取，通过 `spring.datasource.*` 注入，**代码中不出现任何连接串与密钥**。

```java
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
    // 连接池参数同理从 spring.datasource.hikari.* 注入
    return dataSource;
}
```

> 对应 `application.yaml`：`spring.datasource.url=...`、`spring.datasource.username=...`、`spring.datasource.password=...`。禁止在 Java 里硬编码。

### 2.2 不依赖自动配置，显式声明

配置类显式声明数据源，不依赖 Spring Boot 的 `DataSourceAutoConfiguration`（需在启动类中排除）。这样数据源与事务管理器由本配置全权掌控，避免隐式自动装配带来的不可预期性。

```java
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class AppStart {
    public static void main(String[] args) {
        SpringApplication.run(AppStart.class, args);
    }
}
```

该结论与是否引入 `pragmatic-ddd-spring-boot` 无关：starter 不创建数据源，只以容器内已有的 `PlatformTransactionManager` / `SqlSessionTemplate` 为前置条件挂接上层装配。

### 2.3 连接池参数

HikariCP 参数按实例数与环境容量估算，常见约束：

| 参数 | 取值建议 | 说明 |
| --- | --- | --- |
| `maximum-pool-size` | 按 `DB 最大连接数 / 应用实例数` 估算 | 避免压垮数据库 |
| `minimum-idle` | 小于 `maximum-pool-size` | 保持少量热连接，降低突发建连开销 |
| `connection-timeout` | 毫秒级显式设置 | 超时抛异常，避免线程无限阻塞 |
| `max-lifetime` | **必须小于** MySQL `wait_timeout` | 防止拿到已被服务端断开的死连接 |
| `keepalive-time` | 小于 `max-lifetime` | 主动探活，防中间设备断链 |
| `validation-timeout` | 毫秒级显式设置 | 借出时校验存活 |

## 3. 事务管理器

绑定数据源的事务管理器，为 `@Transactional` 提供底层支撑。MyBatis 场景用 `DataSourceTransactionManager`：

```java
@Configuration
@EnableTransactionManagement
public class MySqlConfig {

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
```

`@EnableTransactionManagement` 全局只需声明一次，统一放在本配置类，不要重复加到 `MyBatisConfig`。

事务边界由应用层（`@Transactional`）负责编排，仓储不自行管理事务（详见 [仓储设计原则](./repository-design.md) §1.5）。

## 4. 配置与使用分离

配置类产出 Bean，仓储实现消费 Bean（`SqlSessionTemplate`）。仓储只关心"怎么用"，不关心"怎么配"；配置只关心"怎么建"，不关心"怎么查"。

## 5. 常见反模式

| 反模式 | 问题 | 正确做法 |
| --- | --- | --- |
| 连接参数硬编码在 Java 中 | 密钥泄露、无法按环境切换 | 从 `spring.datasource.*` 外部化配置读取 |
| 依赖 `DataSourceAutoConfiguration` 隐式装配 | 数据源不可控、与自定义 TypeHandler 冲突 | 显式声明数据源，启动类排除自动配置 |
| `max-lifetime` 大于 MySQL `wait_timeout` | 拿到已被服务端断开的死连接 | `max-lifetime` 严格小于 `wait_timeout` |
| 仓储内自管事务 | 事务边界混乱 | 交给应用层 `@Transactional`，配置层只产出事务管理器 |
| `@EnableTransactionManagement` 在多处重复声明 | 语义混乱、难排查 | 只在数据源配置类声明一次 |
| 把 ORM 内容（TypeHandler / Mapper）写进 `MySqlConfig` | 职责混杂，换 ORM 需改动数据源配置 | 拆到 `MyBatisConfig` |

## 下一步

- [MyBatis 配置设计原则](./mybatis-config.md)：会话工厂、TypeHandler 多聚合装配、框架固定 Mapper 与业务 Mapper 加载
- [仓储设计原则](./repository-design.md)：仓储如何消费 `SqlSessionTemplate`、纯 XML Mapper 约定
- [聚合数据库设计原则](./aggregate-database-design.md)：表边界、审计列与版本列
- [Spring Boot Starter 集成](./../integration/spring-boot.md)：starter 如何消费 `PlatformTransactionManager` / `SqlSessionTemplate`
