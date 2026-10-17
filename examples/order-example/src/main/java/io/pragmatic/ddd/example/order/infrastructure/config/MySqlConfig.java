package io.pragmatic.ddd.example.order.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * 通用 MySQL 数据源与事务配置（JDBC 层）。
 * 只提供 DataSource 与 PlatformTransactionManager 两个核心 Bean，并开启声明式事务，
 * 不依赖 Spring Boot 的 DataSourceAutoConfiguration（已在 AppStart 中排除）。
 *
 * <p>MyBatis 会话工厂、TypeHandler 装配与 Mapper XML 加载见同包的 {@link MyBatisConfig}，
 * 本类不 import 任何 MyBatis 类型。</p>
 *
 * @author wizard-lee
 */
@Configuration
@EnableTransactionManagement
public class MySqlConfig {

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${spring.datasource.hikari.keepalive-time:300000}")
    private long keepaliveTime;

    @Value("${spring.datasource.hikari.validation-timeout:5000}")
    private long validationTimeout;

    /**
     * 直接读取 application.properties 中的 spring.datasource.* 配置项，手动构建 HikariCP 数据源。
     * 连接参数从配置文件或环境变量占位符读取，不在代码中硬编码。
     *
     * @return 数据源实例
     */
    @Bean
    public DataSource dataSource() {
        HikariDataSource hikariDataSource = new HikariDataSource();
        hikariDataSource.setJdbcUrl(url);
        hikariDataSource.setUsername(username);
        hikariDataSource.setPassword(password);
        hikariDataSource.setDriverClassName(driverClassName);
        hikariDataSource.setMaximumPoolSize(maximumPoolSize);
        hikariDataSource.setMinimumIdle(minimumIdle);
        hikariDataSource.setConnectionTimeout(connectionTimeout);
        hikariDataSource.setIdleTimeout(idleTimeout);
        hikariDataSource.setMaxLifetime(maxLifetime);
        hikariDataSource.setKeepaliveTime(keepaliveTime);
        hikariDataSource.setValidationTimeout(validationTimeout);
        return hikariDataSource;
    }

    /**
     * 绑定数据源的事务管理器，为 @Transactional 提供底层支撑。
     *
     * @param dataSource 数据源
     * @return 事务管理器
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
