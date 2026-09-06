package io.pragmatic.ddd.example.order.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * 通用 MySQL 数据访问配置。
 * 集中提供 DataSource、SqlSessionFactory、SqlSessionTemplate 与事务管理器四个核心 Bean，
 * 不依赖 Spring Boot 的 DataSourceAutoConfiguration（已在 AppStart 中排除）。
 *
 * <p>Mapper 采用传统纯 XML 方式加载：不使用 @MapperScan，也不在 Java 中持有 Mapper 接口类。
 * 框架与业务 Mapper 均通过 setMapperLocations 路径扫描 XML 加载（含框架提供的
 * OutboxMapper / IdSegmentMapper 与业务 classpath 下 mapper 目录的 XML），框架按
 * namespace.statementId 直接调用 SQL。</p>
 *
 * <p>MyBatis 复杂类型（枚举 / 值对象 JSON / 集合）的 TypeHandler 清单由各聚合专属配置类提供
 * （如订单对应的 infrastructure.config.order.OrderMybatisTypeHandlerConfig），本配置在工厂
 * 构建阶段统一将其灌入原生 Configuration，不感知具体聚合类型。</p>
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
     * 构建 MyBatis 会话工厂。
     *
     * <p>会话工厂加载 mybatis-config.xml（其中 {@code <mappers>} 统一组织所有 Mapper XML）。
     * 复杂类型（枚举 / 值对象 JSON / 集合）的 TypeHandler 由各聚合专属配置类产出的多个
     * {@link TypeHandlerContext} 在工厂构建阶段逐个经 {@code registerInto} 注入原生 Configuration，
     * 确保 Mapper XML 解析前完成装配；本类不感知任何具体聚合类型，新增聚合只需新增一个
     * {@code TypeHandlerContext} Bean 即可被自动聚合注册。</p>
     *
     * @param dataSource             数据源
     * @param typeHandlerContexts    复杂类型 TypeHandler 装配上下文集合（每个聚合专属配置各产出一个）
     * @return MyBatis 会话工厂
     * @throws Exception 资源扫描或工厂构建失败时抛出
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource,
                                               List<TypeHandlerContext> typeHandlerContexts) throws Exception {
        // 1. 创建原生的 Configuration 对象
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

        // 2. 将各聚合专属的复杂类型 TypeHandler 灌入 Configuration（XML 解析前完成）
        for (TypeHandlerContext context : typeHandlerContexts) {
            context.registerInto(configuration);
        }

        // 3. 全局配置
        configuration.setMapUnderscoreToCamelCase(true); // 开启驼峰命名自动映射
        configuration.setCacheEnabled(true); // 开启全局缓存
        configuration.setLazyLoadingEnabled(true); // 开启延迟加载
        configuration.setAggressiveLazyLoading(false); // 按需加载

        configuration.setLogImpl(Slf4jImpl.class);
        SqlSessionFactoryBean sessionFactory = this.createSessionFactory(dataSource, configuration);

        return sessionFactory.getObject();
    }

    private  SqlSessionFactoryBean createSessionFactory(DataSource dataSource, org.apache.ibatis.session.Configuration configuration) throws IOException {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);

        // 3. 将配置好的 Configuration 注入 (彻底抛弃 setConfigLocation)
        sessionFactory.setConfiguration(configuration);

        // 4. 指定 Mapper XML 文件的路径
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] frameworkOutboxMappers = resolver.getResources("classpath*:io/pragmatic/ddd/mybatis/outbox/*.xml");
        Resource[] frameworkIdGenMappers = resolver.getResources("classpath*:io/pragmatic/ddd/mybatis/id/*.xml");
        Resource[] businessMappers = resolver.getResources("classpath*:mapper/**/*.xml");

        Resource[] allMappers = Stream.of(frameworkIdGenMappers,frameworkOutboxMappers,businessMappers)
                .flatMap(Arrays::stream)
                .toArray(Resource[]::new);
        sessionFactory.setMapperLocations(allMappers);

        // 5. (可选) 扫描实体类的包路径，用于类型别名 (TypeAliases)
        sessionFactory.setTypeAliasesPackage("com.example.entity");
        return sessionFactory;
    }

    /**
     * Spring 托管的线程安全 SqlSession，绑定到上述 SqlSessionFactory，
     * 供仓储实现（如 OrderRepository 的 doInsert/doUpdate/doRemove）直接操作 MyBatis，
     * 并自动参与到 Spring 声明式事务中。
     *
     * @param sqlSessionFactory MyBatis 会话工厂
     * @return SqlSessionTemplate 实例
     */
    @Bean
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
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
