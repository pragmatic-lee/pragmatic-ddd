package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.application.spi.Propagation;
import io.pragmatic.ddd.application.spi.TransactionCallback;
import io.pragmatic.ddd.application.spi.TransactionOperations;

/**
 * 测试专用无事务实现：不开启真实事务，直接在当前线程执行回调。
 * 仅用于单测与无持久化事务需求的场景；生产环境务必注入真实事务实现。
 */
public class NoOpTransactionOperations implements TransactionOperations {

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        return callback.doInTransaction();
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback, Propagation propagation) {
        return callback.doInTransaction();
    }
}
