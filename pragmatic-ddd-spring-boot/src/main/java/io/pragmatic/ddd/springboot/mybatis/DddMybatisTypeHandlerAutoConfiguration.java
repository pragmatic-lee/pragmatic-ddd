package io.pragmatic.ddd.springboot.mybatis;

import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerAssembler;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerContext;
import io.pragmatic.ddd.mybatis.typehandler.TypeHandlerDeclaration;
import io.pragmatic.ddd.mybatis.typehandler.json.JdbcJsonValue;
import io.pragmatic.ddd.mybatis.typehandler.json.PgJdbcJsonValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 复杂类型装配自动配置：收集容器内全部 {@link TypeHandlerDeclaration}，合并为唯一的 {@link TypeHandlerContext}。
 *
 * <p>一个 {@code Configuration} 只能装配一份上下文（MyBatis 对 {@code List.class} 只有全局单槽，后注册覆盖前注册），
 * 因此这里只产出单例；业务自定义 {@link TypeHandlerContext} Bean 时整块退让，声明冲突校验则交由装配器统一 fail-fast。</p>
 *
 * <p>JDBC JSON 方言优先取业务显式声明的 {@link JdbcJsonValue} Bean，否则按数据源产品名探测，
 * 无数据源或探测失败时回落到透传实现。</p>
 *
 * @author wizard-lee
 */
@AutoConfiguration
@ConditionalOnClass({TypeHandlerContext.class, TypeHandlerAssembler.class})
public class DddMybatisTypeHandlerAutoConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(DddMybatisTypeHandlerAutoConfiguration.class);

    private static final String PRODUCT_POSTGRESQL = "postgresql";

    private static final String PRODUCT_MYSQL = "mysql";

    private static final String PRODUCT_MARIADB = "mariadb";

    /**
     * 唯一复杂类型装配上下文：把全部声明合并为一份后注册到 MyBatis。
     *
     * @param declarations         各聚合的类型声明
     * @param jdbcJsonValueProvider 业务显式声明的方言适配器（可选）
     * @param dataSourceProvider    数据源（可选，用于方言探测）
     * @return 唯一的装配上下文
     */
    @Bean
    @ConditionalOnBean(TypeHandlerDeclaration.class)
    @ConditionalOnMissingBean(TypeHandlerContext.class)
    public TypeHandlerContext typeHandlerContext(List<TypeHandlerDeclaration> declarations,
                                                ObjectProvider<JdbcJsonValue> jdbcJsonValueProvider,
                                                ObjectProvider<DataSource> dataSourceProvider) {
        JdbcJsonValue jdbcJsonValue = this.resolveJdbcJsonValue(jdbcJsonValueProvider, dataSourceProvider);
        return TypeHandlerAssembler.assemble(declarations, jdbcJsonValue);
    }

    private JdbcJsonValue resolveJdbcJsonValue(ObjectProvider<JdbcJsonValue> provider,
                                               ObjectProvider<DataSource> dataSourceProvider) {
        JdbcJsonValue declared = provider.getIfAvailable();
        if (declared != null) {
            return declared;
        }
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            LOG.info("未发现 DataSource，JSON 列按透传方式写入(JdbcJsonValue.DEFAULT)");
            return JdbcJsonValue.DEFAULT;
        }
        return this.detect(dataSource);
    }

    private JdbcJsonValue detect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            if (metaData == null) {
                LOG.warn("数据源未提供元数据，JSON 列按透传方式写入(JdbcJsonValue.DEFAULT)");
                return JdbcJsonValue.DEFAULT;
            }
            return this.byProductName(metaData.getDatabaseProductName());
        } catch (SQLException ex) {
            LOG.warn("探测数据库产品名失败，JSON 列按透传方式写入(JdbcJsonValue.DEFAULT)", ex);
            return JdbcJsonValue.DEFAULT;
        }
    }

    private JdbcJsonValue byProductName(String productName) {
        String name = Optional.ofNullable(productName).orElse("").toLowerCase(Locale.ROOT);
        if (name.contains(PRODUCT_POSTGRESQL)) {
            LOG.info("检测到 PostgreSQL 数据源，JSON 列使用 PGobject 写入");
            return new PgJdbcJsonValue();
        }
        if (name.contains(PRODUCT_MYSQL) || name.contains(PRODUCT_MARIADB)) {
            LOG.info("检测到 MySQL/MariaDB 数据源，JSON 列使用 JSON 文本写入");
            return JdbcJsonValue.MYSQL;
        }
        LOG.info("未识别数据库产品名 '{}'，JSON 列按透传方式写入(JdbcJsonValue.DEFAULT)", productName);
        return JdbcJsonValue.DEFAULT;
    }
}
