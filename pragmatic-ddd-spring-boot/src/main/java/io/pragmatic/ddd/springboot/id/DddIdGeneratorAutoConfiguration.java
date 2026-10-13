package io.pragmatic.ddd.springboot.id;

import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.base.id.IdGeneratorDefinition;
import io.pragmatic.ddd.base.id.IdGeneratorRegistry;
import io.pragmatic.ddd.base.id.IIdSegmentAllocator;
import io.pragmatic.ddd.mybatis.id.DbSegmentAllocator;
import io.pragmatic.ddd.mybatis.id.IIdSegmentStatementExecutor;
import io.pragmatic.ddd.springboot.bridge.SpringIdSegmentStatementExecutor;
import io.pragmatic.ddd.springboot.transaction.DddTransactionAutoConfiguration;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * ID 号段生成基础设施自动装配：装配框架号段机制与注册中心骨架，业务渠道由使用方声明
 * {@link IdGeneratorDefinition} Bean 注册（本装配不感知具体 bizKey）。
 *
 * <p>数据库与 {@code id_segment} 表假定已存在，本装配不负责建表与初始化数据。</p>
 *
 * @author wizard-lee
 */
@AutoConfiguration(after = DddTransactionAutoConfiguration.class)
@ConditionalOnClass({DbSegmentAllocator.class, SqlSessionTemplate.class})
@ConditionalOnBean({SqlSessionTemplate.class, TransactionOperations.class})
public class DddIdGeneratorAutoConfiguration {

    /**
     * MyBatis 号段语句执行器：转发语句键 + 参数给 Spring 托管会话模板。
     *
     * @param sqlSessionTemplate MyBatis 会话模板
     * @return 号段语句执行器
     */
    @Bean
    @ConditionalOnMissingBean(IIdSegmentStatementExecutor.class)
    public IIdSegmentStatementExecutor idSegmentStatementExecutor(SqlSessionTemplate sqlSessionTemplate) {
        return new SpringIdSegmentStatementExecutor(sqlSessionTemplate);
    }

    /**
     * 基于数据库号段的分配器（REQUIRES_NEW 独立短事务保证行锁有效）。
     *
     * @param executor 号段语句执行器
     * @param txOps    事务抽象
     * @return 号段分配器
     */
    @Bean
    @ConditionalOnMissingBean(IIdSegmentAllocator.class)
    public IIdSegmentAllocator idSegmentAllocator(IIdSegmentStatementExecutor executor, TransactionOperations txOps) {
        return new DbSegmentAllocator(executor, txOps);
    }

    /**
     * 号段生成注册中心：以容器内全部 {@link IdGeneratorDefinition} Bean 逐个注册对应渠道生成器。
     *
     * @param definitions 业务渠道定义集合（每个渠道一个 Bean）
     * @param allocator   号段分配器
     * @return 已注册业务渠道的注册中心
     */
    @Bean
    @ConditionalOnMissingBean(IdGeneratorRegistry.class)
    public IdGeneratorRegistry idGeneratorRegistry(ObjectProvider<IdGeneratorDefinition> definitions,
                                                   IIdSegmentAllocator allocator) {
        IdGeneratorRegistry registry = new IdGeneratorRegistry();
        definitions.orderedStream().forEach(definition -> registry.register(definition, allocator));
        return registry;
    }
}
