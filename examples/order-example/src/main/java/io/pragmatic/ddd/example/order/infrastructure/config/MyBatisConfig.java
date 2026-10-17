package io.pragmatic.ddd.example.order.infrastructure.config;

import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import org.apache.ibatis.logging.slf4j.Slf4jImpl;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * 通用 MyBatis 会话装配（ORM 层）。
 * 提供 SqlSessionFactory 与 SqlSessionTemplate：构建阶段把各聚合专属的
 * {@link TypeHandlerContext} 灌入原生 Configuration，并把框架固定 Mapper XML
 * 与业务 Mapper XML 一并纳入扫描。
 *
 * <p>Mapper 采用纯 XML 方式加载：不使用 @MapperScan，也不在 Java 中持有 Mapper 接口，
 * 框架按 namespace.statementId 直接调用 SQL。框架三组 XML（outbox / id / compensation）
 * 随 jar 发布，必须用 classpath*: 前缀才能扫到；某组是否真正生效取决于对应表是否已建。</p>
 *
 * @author wizard-lee
 */
@Configuration
public class MyBatisConfig {

    private static final String FRAMEWORK_OUTBOX_MAPPERS = "classpath*:io/pragmatic/ddd/mybatis/outbox/*.xml";

    private static final String FRAMEWORK_ID_MAPPERS = "classpath*:io/pragmatic/ddd/mybatis/id/*.xml";

    private static final String FRAMEWORK_COMPENSATION_MAPPERS =
            "classpath*:io/pragmatic/ddd/mybatis/compensation/*.xml";

    private static final String BUSINESS_MAPPERS = "classpath*:mapper/**/*.xml";

    /**
     * 构建 MyBatis 会话工厂。
     *
     * <p>复杂类型（枚举 / 值对象 JSON / 集合）的 TypeHandler 由各聚合专属配置类产出的多个
     * {@link TypeHandlerContext} 在工厂构建阶段逐个经 {@code registerInto} 注入原生 Configuration，
     * 确保 Mapper XML 解析前完成装配；本类不感知任何具体聚合类型，新增聚合只需新增一个
     * {@code TypeHandlerContext} Bean 即可被自动聚合注册。</p>
     *
     * @param dataSource           数据源
     * @param typeHandlerContexts  复杂类型 TypeHandler 装配上下文集合（每个聚合专属配置各产出一个）
     * @return MyBatis 会话工厂
     * @throws Exception 资源扫描或工厂构建失败时抛出
     */
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

        SqlSessionFactoryBean sessionFactory = this.createSessionFactory(dataSource, configuration);
        return sessionFactory.getObject();
    }

    private SqlSessionFactoryBean createSessionFactory(DataSource dataSource,
                                                       org.apache.ibatis.session.Configuration configuration) throws IOException {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        sessionFactory.setConfiguration(configuration);
        sessionFactory.setMapperLocations(this.loadMapperLocations());
        return sessionFactory;
    }

    private Resource[] loadMapperLocations() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        return Stream.of(
                        resolver.getResources(FRAMEWORK_ID_MAPPERS),
                        resolver.getResources(FRAMEWORK_OUTBOX_MAPPERS),
                        resolver.getResources(FRAMEWORK_COMPENSATION_MAPPERS),
                        resolver.getResources(BUSINESS_MAPPERS))
                .flatMap(Arrays::stream)
                .toArray(Resource[]::new);
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
}
