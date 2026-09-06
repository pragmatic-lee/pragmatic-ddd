package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.application.spi.Propagation;
import io.pragmatic.ddd.application.spi.TransactionCallback;
import io.pragmatic.ddd.application.spi.TransactionOperations;

/**
 * 工作单元测试专用事务夹具：统计事务开启次数，用于验证持久化是否落在同一事务内、
 * 以及校验失败时事务是否根本未开启。
 */
public class CountingTransactionOperations implements TransactionOperations {

    private int executeCount = 0;

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        executeCount++;
        return callback.doInTransaction();
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback, Propagation propagation) {
        executeCount++;
        return callback.doInTransaction();
    }

    /** 返回累计的事务开启次数。 */
    public int executeCount() {
        return executeCount;
    }
}
