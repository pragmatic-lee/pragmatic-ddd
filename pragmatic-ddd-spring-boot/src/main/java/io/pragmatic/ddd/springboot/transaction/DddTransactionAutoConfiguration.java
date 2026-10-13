package io.pragmatic.ddd.springboot.transaction;

import io.pragmatic.ddd.application.spi.TransactionOperations;
import io.pragmatic.ddd.springboot.bridge.SpringTransactionOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 装配框架事务抽象 {@link TransactionOperations} 的 Spring 实现。
 * 以官方 JDBC 自动配置产出的 {@link PlatformTransactionManager} 为后端，是使用方最常用的一块自动配置，
 * 也是 outbox / id / compensation 装配的共同前置。
 *
 * @author wizard-lee
 */
@AutoConfiguration(after = DataSourceTransactionManagerAutoConfiguration.class)
@ConditionalOnClass({TransactionOperations.class, PlatformTransactionManager.class})
@ConditionalOnBean(PlatformTransactionManager.class)
public class DddTransactionAutoConfiguration {

    /**
     * 桥接 Spring 事务管理的 {@link TransactionOperations} 实现。
     *
     * @param transactionManager 容器中的 Spring 事务管理器
     * @return 事务抽象实现
     */
    @Bean
    @ConditionalOnMissingBean(TransactionOperations.class)
    public TransactionOperations transactionOperations(PlatformTransactionManager transactionManager) {
        return new SpringTransactionOperations(transactionManager);
    }
}
