package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.example.order.domain.order.model.enums.OrderStatus;
import io.pragmatic.ddd.example.order.domain.order.model.enums.PaymentMethod;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Address;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Customer;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.LogisticsInfo;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.Money;
import io.pragmatic.ddd.example.order.domain.order.model.valueobject.PaymentInfo;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerRegistration;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumRule;
import io.pragmatic.ddd.mybatis.typehandler.enums.EnumValueResolver;
import io.pragmatic.ddd.mybatis.typehandler.enums.UniversalEnumTypeHandler;
import io.pragmatic.ddd.mybatis.typehandler.list.CollectionElementTypeConfig;
import io.pragmatic.ddd.mybatis.typehandler.json.Fastjson2JsonSerializer;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.TypeHandler;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 订单示例的 MySQL 数据访问配置。
 * 集中提供 DataSource、SqlSessionFactory、SqlSessionTemplate 与事务管理器四个核心 Bean，
 * 不依赖 Spring Boot 的 DataSourceAutoConfiguration（已在 AppStart 中排除）。
 *
 * <p>Mapper 通过 MyBatis 原生 SQL Mapper Config（mybatis-config.xml）组织，不使用 @MapperScan，
 * 也不在 Java 中持有 Mapper 接口类：所有 Mapper 的 XML 由 mybatis-config.xml 的
 * {@code <mappers>} 统一声明加载（含框架提供的 OutboxMapper / IdSegmentMapper）。</p>
 *
 * <p>MyBatis 复杂类型（枚举 / 值对象 JSON / 集合）通过 {@link TypeHandlerContext#registerInto}
 * 统一初始化，而非包扫描——因为 UniversalEnumTypeHandler、GenericJsonTypeHandler、ListTypeHandler
 * 均为运行时按类型动态构建，包扫描无法发现。</p>
 *
 * @author wizard-lee
 */
@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
@EnableTransactionManagement
public class MySqlConfig {

    private static final String MYBATIS_CONFIG_LOCATION = "classpath:mapper/mybatis-config.xml";

    /**
     * 基于外部化配置（spring.datasource.*）构建 HikariCP 数据源。
     * 连接参数从 application.properties 或环境变量读取，不在代码中硬编码。
     *
     * @param properties 数据源外部化配置
     * @return 数据源实例
     */
    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }

    /**
     * 构建 MyBatis 会话工厂，并初始化 pragmatic-ddd-mybatis 的 TypeHandler 体系。
     *
     * <p>会话工厂加载 mybatis-config.xml（其中 {@code <mappers>} 统一组织所有 Mapper XML），
     * 复杂类型 TypeHandler 通过 {@link TypeHandlerContext#registerInto} 注入 TypeHandlerRegistry。</p>
     *
     * @param dataSource 数据源
     * @return MyBatis 会话工厂
     * @throws Exception 资源扫描或工厂构建失败时抛出
     */
    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        // 1. 创建原生的 Configuration 对象
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();

        // 2. 注入你的自定义 TypeHandler
        Collection<TypeHandlerRegistration> typeHandlerRegistrations = registerTypeHandlers();
        typeHandlerRegistrations
                .forEach(typ -> {
                    Class<?> aClass = typ.javaType();
                    TypeHandler<?> handler = typ.handler();
                    configuration.getTypeHandlerRegistry().register(aClass,(TypeHandler)handler);
                });

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

        // 5. 将配置好的 Configuration 注入 (彻底抛弃 setConfigLocation)
        sessionFactory.setConfiguration(configuration);

        // 6. 指定 Mapper XML 文件的路径
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        sessionFactory.setMapperLocations(resolver.getResources("classpath:mapper/**/*.xml"));

        // 7. (可选) 扫描实体类的包路径，用于类型别名 (TypeAliases)
        sessionFactory.setTypeAliasesPackage("com.example.entity");
        return sessionFactory;
    }

    /**
     * 初始化 pragmatic-ddd-mybatis 的 TypeHandler 体系（枚举 / 值对象 JSON / 集合）。
     *
     * <p>构建 {@link TypeHandlerContext} 后调用 {@code registerInto(Configuration)}，
     * 在 Configuration 构建阶段注入 UniversalEnumTypeHandler、GenericJsonTypeHandler、
     * ListTypeHandler。枚举策略、值对象清单、集合映射三类信息需按需填充。</p>
     *
     */
    private Collection<TypeHandlerRegistration> registerTypeHandlers() {
        EnumValueResolver resolver = new EnumValueResolver();
        Map<Class<?>, EnumRule> enumRules = Map.of(
                OrderStatus.class, EnumRule.CODE,
                PaymentMethod.class, EnumRule.CODE
        );
        List<Class<?>> voTypes = List.of(
                Customer.class,
                Address.class,
                Money.class,
                PaymentInfo.class,
                LogisticsInfo.class
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
//        context.registerInto(configuration);
        return context.registrations();
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
