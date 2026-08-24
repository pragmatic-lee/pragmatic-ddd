package io.pragmatic.ddd.mybatis;

import io.pragmatic.ddd.application.outbox.spi.Propagation;
import io.pragmatic.ddd.application.outbox.spi.TransactionCallback;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;

/**
 * 测试用 {@link TransactionOperations}：不开启独立短事务，直接在「当前连接」内执行回调。
 *
 * <p>真实运行期由 Spring 的 {@code TransactionTemplate(Propagation.REQUIRES_NEW)} 提供独立提交语义；
 * 单测场景仅验证 SQL 正确性，session 统一 autoCommit 即可，无需模拟真实事务边界。</p>
 */
public final class NoopTransactionOperations implements TransactionOperations {

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        return callback.doInTransaction();
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback, Propagation propagation) {
        return callback.doInTransaction();
    }
}
