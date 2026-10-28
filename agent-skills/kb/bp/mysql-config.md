# BP 卡：MySQL 配置设计原则（mysql-config）

> 源：`documentation/best-practices/mysql-config.md` · 派生时间：2026-10-28 · 框架版本：2.1.0-SNAPSHOT

## 1. 职责边界（JDBC 层）

`MySqlConfig` 只产 **`DataSource` 与 `PlatformTransactionManager`**，不 import 任何 MyBatis 类型。ORM 细节拆到 `MyBatisConfig`（见 `mybatis-config.md`）。

PostgreSQL 使用方见 `postgresql-config.md`：JDBC 数据源与事务管理器骨架可复用（`PostgresConfig` 与 `MySqlConfig` 同构），但驱动、方言 XML、`databaseId` 与建表脚本不同。

```text
infrastructure/config/
├── MySqlConfig.java                 # DataSource / PlatformTransactionManager + @EnableTransactionManagement
├── MyBatisConfig.java               # SqlSessionFactory / SqlSessionTemplate
└── {agg}/
    └── {Agg}MybatisTypeHandlerConfig.java   # 该聚合专属 TypeHandler 装配
```

- 依赖方向单向：`MyBatisConfig → MySqlConfig`（会话工厂依赖数据源），无循环。
- `pragmatic-ddd-spring-boot` 只**消费**本配置产出的 `PlatformTransactionManager` 与 `SqlSessionTemplate`，不创建数据源。

## 2. DataSource 装配

### 2.1 外部化配置，禁止硬编码

连接参数从 `spring.datasource.*` 注入，**代码中不出现连接串与密钥**：

```java
@Value("${spring.datasource.url}")
private String url;
// username / password / driver-class-name 同理

@Bean
public DataSource dataSource() {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(url);
    dataSource.setUsername(username);
    dataSource.setPassword(password);
    dataSource.setDriverClassName(driverClassName);
    return dataSource;
}
```

### 2.2 不依赖自动配置，显式声明

配置类显式声明数据源，启动类排除 `DataSourceAutoConfiguration`：

```java
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class AppStart { /* main */ }
```

### 2.3 连接池（HikariCP）关键约束

| 参数 | 取值建议 |
| --- | --- |
| `maximum-pool-size` | 按 `DB 最大连接数 / 应用实例数` 估算 |
| `min-idle` | 小于 `maximum-pool-size` |
| `max-lifetime` | **必须小于** MySQL `wait_timeout` |
| `keepalive-time` | 小于 `max-lifetime` |
| `connection-timeout` / `validation-timeout` | 毫秒级显式设置 |

## 3. 事务管理器

```java
@Configuration
@EnableTransactionManagement   // 全局只在此声明一次
public class MySqlConfig {
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
```

- 事务边界由应用层（`@Transactional`）负责编排，仓储不自行管理事务（见 `repository-design.md` §1.5）。
- `@EnableTransactionManagement` 不要重复加到 `MyBatisConfig`。

## 4. 配置与使用分离

配置类只产出 Bean，仓储实现消费 Bean（`SqlSessionTemplate`）；仓储只关心"怎么用"，不关心"怎么配"。

## 5. 常见反模式

| 反模式 | 正确做法 |
| --- | --- |
| 连接参数硬编码在 Java | 从 `spring.datasource.*` 外部化读取 |
| 依赖 `DataSourceAutoConfiguration` 隐式装配 | 显式声明数据源，启动类排除自动配置 |
| `max-lifetime` 大于 `wait_timeout` | 严格小于，防死连接 |
| 仓储内自管事务 | 交给应用层 `@Transactional` |
| `@EnableTransactionManagement` 多处重复声明 | 只在数据源配置类声明一次 |
| ORM 内容写进 `MySqlConfig` | 拆到 `MyBatisConfig` |

## 6. 被读于

S3 `ddd-infra-config`（数据源/连接池/事务管理器）、S7 `ddd-code-review`。
