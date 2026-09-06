package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.application.spi.Propagation;
import io.pragmatic.ddd.application.spi.TransactionCallback;
import io.pragmatic.ddd.application.spi.TransactionOperations;

import java.util.List;
import java.util.function.Consumer;

/**
 * 测试专用事务夹具：统计事务开启次数，并在回调成功时确认提交、失败时模拟整体回滚不确认。
 * 用于验证多聚合持久化处在同一事务内、且中途失败时前面的写入不会生效。
 */
public class RollbackCountingTransactionOperations implements TransactionOperations {

    private final List<Consumer<Boolean>> commitHooks;
    private int executeCount = 0;

    public RollbackCountingTransactionOperations(List<Consumer<Boolean>> commitHooks) {
        this.commitHooks = commitHooks;
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        return this.runInTransaction(callback);
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback, Propagation propagation) {
        return this.runInTransaction(callback);
    }

    private <T> T runInTransaction(TransactionCallback<T> callback) {
        executeCount++;
        T result = callback.doInTransaction();
        // 回调未抛异常即视为提交成功，通知各仓储确认写入
        commitHooks.forEach(hook -> hook.accept(true));
        return result;
    }

    /** 返回累计的事务开启次数。 */
    public int executeCount() {
        return executeCount;
    }
}
