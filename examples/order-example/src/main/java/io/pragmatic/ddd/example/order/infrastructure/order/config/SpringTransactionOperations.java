package io.pragmatic.ddd.example.order.infrastructure.order.config;

import io.pragmatic.ddd.application.outbox.spi.TransactionCallback;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 基于 Spring TransactionTemplate 的 TransactionOperations 实现。
 * 桥接框架事务回调与 Spring 事务回调，绑定聚合写与 outbox 写到同一本地事务。
 * 对应设计文档 4.1 / 5.1 节：事务抽象实现。
 *
 * @author wizard-lee
 */
public class SpringTransactionOperations implements TransactionOperations {

    private final TransactionTemplate transactionTemplate;

    public SpringTransactionOperations(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        return transactionTemplate.execute(status -> callback.doInTransaction());
    }
}
