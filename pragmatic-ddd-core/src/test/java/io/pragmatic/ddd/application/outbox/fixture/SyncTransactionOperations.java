package io.pragmatic.ddd.application.outbox.fixture;

import io.pragmatic.ddd.application.outbox.spi.Propagation;
import io.pragmatic.ddd.application.outbox.spi.TransactionCallback;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;

/**
 * 同步事务操作测试夹具：不开启真实事务，直接在当前线程执行回调并返回结果。
 *
 * @author wizard-lee
 */
public class SyncTransactionOperations implements TransactionOperations {

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        return callback.doInTransaction();
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback, Propagation propagation) {
        return callback.doInTransaction();
    }
}
