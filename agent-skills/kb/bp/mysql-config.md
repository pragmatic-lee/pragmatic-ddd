# BP 卡：MySQL 配置设计原则（mysql-config，数据源与事务）

> 源：`documentation/best-practices/mysql-config.md` · 派生时间：2026-10-17 · 框架版本：2.0.0

## 1. 职责边界

- **`MySqlConfig`（本卡）**：只产 `DataSource` 与 `PlatformTransactionManager`，并开启 `@EnableTransactionManagement`；**不 import 任何 MyBatis 类型**。
- **`MyBatisConfig`**：产 `SqlSessionFactory` / `SqlSessionTemplate`，聚合各聚合 `TypeHandlerContext` 并加载 Mapper XML（含框架固定 Mapper）→ 见 `bp/mybatis-config.md`。
- **聚合专属 `{Agg}MybatisTypeHandlerConfig`**：每聚合一个，落 `infrastructure/config/{agg}/`，只登记本聚合枚举/值对象。
- 依赖方向单向：`MyBatisConfig → MySqlConfig`（会话工厂依赖数据源），无循环。`MySqlConfig` 不感知 ORM、不感知聚合。

```text
infrastructure/config/
├── MySqlConfig.java                      # DataSource / TransactionManager
├── MyBatisConfig.java                    # SqlSessionFactory / SqlSessionTemplate
└── {agg}/
    └── {Agg}MybatisTypeHandlerConfig.java
```

## 2. DataSource 装配

- 连接参数（URL / 账号 / 密码 / 池参数）全部外部化到 `spring.datasource.*`，代码不硬编码任何连接串与密钥。
- **显式声明数据源**，启动类排除自动配置：`@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})`。
- 配置与使用分离：配置类产 Bean，仓储实现消费 `SqlSessionTemplate`（自动参与 Spring 声明式事务）。

HikariCP 参数约束：

| 参数 | 取值建议 | 说明 |
| --- | --- | --- |
| `maximum-pool-size` | `DB 最大连接数 / 应用实例数` 估算 | 避免压垮数据库 |
| `minimum-idle` | 小于 `maximum-pool-size` | 保持少量热连接 |
| `connection-timeout` | 毫秒级显式设置 | 超时抛异常，避免线程无限阻塞 |
| `max-lifetime` | **必须小于** MySQL `wait_timeout` | 防拿到服务端已断开的死连接 |
| `keepalive-time` | 小于 `max-lifetime` | 主动探活 |
| `validation-timeout` | 毫秒级显式设置 | 借出时校验存活 |

> starter（`pragmatic-ddd-spring-boot`）**不创建数据源**，只以容器内已有 `PlatformTransactionManager` / `SqlSessionTemplate` 为前置条件挂接上层装配。因此「显式声明数据源」与是否引入 starter 无关。

## 3. 事务管理器

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

- `@EnableTransactionManagement` **全局只声明一次**，统一放本类，不要重复加到 `MyBatisConfig`。
- 事务边界由应用层 `@Transactional` 编排，仓储不自行管理事务。

## 4. 避坑

| 反模式 | 正确做法 |
| --- | --- |
| 连接参数硬编码在 Java 中 | 外部化 `spring.datasource.*` |
| 依赖 `DataSourceAutoConfiguration` 隐式装配 | 显式声明数据源，启动类排除自动配置 |
| `max-lifetime` 大于 MySQL `wait_timeout` | `max-lifetime` 严格小于 `wait_timeout` |
| 仓储内自管事务 | 交应用层 `@Transactional` |
| `@EnableTransactionManagement` 多处重复声明 | 只在数据源配置类声明一次 |
| 把 TypeHandler / Mapper 加载写进 `MySqlConfig` | 拆到 `MyBatisConfig` |

## 5. 被读于

S3 `ddd-infra-config`、S7 `ddd-code-review`。ORM 层（会话工厂 / TypeHandler / Mapper）见 `bp/mybatis-config.md`。
