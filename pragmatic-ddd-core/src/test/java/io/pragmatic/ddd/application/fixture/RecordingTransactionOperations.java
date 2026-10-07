package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.application.spi.Propagation;
import io.pragmatic.ddd.application.spi.TransactionCallback;
import io.pragmatic.ddd.application.spi.TransactionOperations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 命令执行器测试专用事务夹具：记录事务与外部动作的先后顺序，用于断言
 * "事务内只落库、事件发布严格晚于事务提交"；并在回调抛异常时模拟整体回滚，
 * 通过提交/回滚回调把数据库的最终状态通知到仓储夹具。
 */
public class RecordingTransactionOperations implements TransactionOperations {

    /** 事务开启标记。 */
    public static final String TX_BEGIN = "tx-begin";
    /** 事务提交标记。 */
    public static final String TX_COMMIT = "tx-commit";

    private final List<String> actions = new ArrayList<>();
    private final List<Consumer<Boolean>> completionHooks = new ArrayList<>();

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        return this.runInTransaction(callback);
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback, Propagation propagation) {
        return this.runInTransaction(callback);
    }

    private <T> T runInTransaction(TransactionCallback<T> callback) {
        actions.add(TX_BEGIN);
        try {
            T result = callback.doInTransaction();
            actions.add(TX_COMMIT);
            this.notifyCompletion(true);
            return result;
        } catch (RuntimeException e) {
            // 回滚：撤销本事务内已记录的动作，模拟数据库整体回滚
            actions.remove(TX_BEGIN);
            this.notifyCompletion(false);
            throw e;
        }
    }

    private void notifyCompletion(boolean committed) {
        this.completionHooks.forEach(hook -> hook.accept(committed));
    }

    /** 注册事务完成钩子，committed 为 true 表示提交、false 表示回滚。 */
    public void onCompletion(Consumer<Boolean> hook) {
        this.completionHooks.add(hook);
    }

    /** 记录一条事务外发生的动作。 */
    public void record(String action) {
        this.actions.add(action);
    }

    /** 按给定顺序记录多条事务外动作。 */
    public void recordAll(String... names) {
        Arrays.stream(names).forEach(this::record);
    }

    /** 返回累计的动作顺序。 */
    public List<String> actions() {
        return List.copyOf(this.actions);
    }

    /** 返回事务开启次数。 */
    public long txCount() {
        return this.actions.stream().filter(TX_BEGIN::equals).count();
    }

    /** 判断某动作是否先于另一动作发生。 */
    public boolean happenedBefore(String first, String second) {
        int firstIndex = this.actions.indexOf(first);
        int secondIndex = this.actions.indexOf(second);
        return firstIndex >= 0 && secondIndex >= 0 && firstIndex < secondIndex;
    }
}
