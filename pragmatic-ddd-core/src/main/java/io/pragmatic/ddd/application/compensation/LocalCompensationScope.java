package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 默认内存补偿范围实现（状态机本体）。
 * 登记已成功执行的动作，判失败则逆序、不中断地补偿；重试采用短退避。
 *
 * @author wizard-lee
 */
final class LocalCompensationScope implements ICompensationScope {

    /** 一次已成功执行的动作：持有动作与它的正向产出，规避泛型擦除。 */
    private record ExecutedAction<T>(ICompensableAction<T> action, T result) {

        void compensate() {
            action.compensate(result);
        }

        boolean confirmable() {
            return action instanceof IConfirmableAction;
        }

        @SuppressWarnings("unchecked")
        void confirm() {
            ((IConfirmableAction<T>) action).confirm(result);
        }
    }

    private final ICompensationLog log;
    private final List<ExecutedAction<?>> executed = new ArrayList<>();
    private final Set<String> actionKeys = new HashSet<>();
    private boolean committed = false;
    private boolean rolledBack = false;

    LocalCompensationScope(ICompensationLog log, CompensationOptions options) {
        this.log = log;
    }

    @Override
    public <T> T execute(ICompensableAction<T> action) {
        ensureOpen();
        String key = action.actionKey();
        if (!actionKeys.add(key)) {
            throw new IllegalArgumentException("duplicate actionKey in scope: " + key);
        }
        recordPending(key);
        T result = action.execute();   // 失败直接冒泡，不登记（所见即所偿）
        executed.add(new ExecutedAction<>(action, result));
        markExecuted(key);
        return result;
    }

    @Override
    public void commit() {
        ensureOpen();
        committed = true;
        for (ExecutedAction<?> item : executed) {
            confirmIfNeeded(item);
        }
    }

    @Override
    public void rollback(Throwable cause) {
        ensureOpen();
        rolledBack = true;
        List<CompensationFailure> failures = new ArrayList<>();
        for (int i = executed.size() - 1; i >= 0; i--) {   // 逆序
            compensateWithRetry(executed.get(i), failures);
        }
        if (!failures.isEmpty()) {
            throw new CompensationFailedException(failures, cause);
        }
    }

    @Override
    public void close() {
        // 仅清理登记项，不抛异常（契约约束）
        executed.clear();
        actionKeys.clear();
    }

    private void confirmIfNeeded(ExecutedAction<?> item) {
        if (!item.confirmable()) {
            return;
        }
        try {
            item.confirm();
        } catch (RuntimeException e) {
            markFailed(item.action().actionKey(), e.getMessage());
        }
    }

    private void compensateWithRetry(ExecutedAction<?> item, List<CompensationFailure> failures) {
        String key = item.action().actionKey();
        CompensationPolicy policy = item.action().policy();
        RuntimeException last = null;
        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            try {
                item.compensate();
                markCompensated(key);
                return;
            } catch (RuntimeException e) {
                last = e;
                sleepQuietly(policy.backoff());
            }
        }
        markFailed(key, last.getMessage());
        failures.add(new CompensationFailure(key, last));
        // 不抛、不中断：单个失败不牵连其余，继续补偿下一个
    }

    private void ensureOpen() {
        if (committed || rolledBack) {
            throw new CompensationStateException("scope already terminated");
        }
    }

    private void recordPending(String key) {
        compensationLog().ifPresent(store -> store.record(pendingRecord(key)));
    }

    private void markExecuted(String key) {
        compensationLog().ifPresent(store -> store.markExecuted(key));
    }

    private void markCompensated(String key) {
        compensationLog().ifPresent(store -> store.markCompensated(key));
    }

    private void markFailed(String key, String reason) {
        compensationLog().ifPresent(store -> store.markFailed(key, reason));
    }

    private Optional<ICompensationLog> compensationLog() {
        return Optional.ofNullable(log);
    }

    private static void sleepQuietly(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static CompensationRecord pendingRecord(String key) {
        return new CompensationRecord(key, CompensationStatus.PENDING, "", "", 0, Instant.now());
    }
}
