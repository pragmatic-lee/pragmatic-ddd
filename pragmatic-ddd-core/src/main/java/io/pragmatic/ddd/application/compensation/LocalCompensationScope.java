package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.IExternalRequirement;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 默认内存补偿范围实现（状态机本体）。
 * 登记已成功执行的命令，判失败则逆序、不中断地补偿；重试采用短退避；
 * 开启 durable 时以 WAL 全程落日志，rollback 先原子认领。
 *
 * @author wizard-lee
 */
final class LocalCompensationScope implements ICompensationScope {

    /** 一次已成功执行的命令：持有命令与正向产出，规避泛型擦除。 */
    private record Executed<A extends AggregateRoot<?>, REQ extends IExternalRequirement, R>(
            CompensationCommand<A, REQ, R> command, R result) {

        void compensate() {
            command.compensate(result);
        }

        /** Confirm 失败只回调 onFailure，不抛异常——本地已提交，抛出去会令模板误判失败去补偿。 */
        void confirmIfNeeded(Consumer<String> onFailure) {
            if (command.action() instanceof IConfirmableAction<A, REQ, R> confirmable) {
                try {
                    confirmable.confirm(command.aggregateRoot(), result);
                } catch (RuntimeException e) {
                    onFailure.accept(e.getMessage());
                }
            }
        }
    }

    private final ICompensationLog log;
    private final CompensationOptions options;
    private final List<Executed<?, ?, ?>> executed = new ArrayList<>();
    private final Set<String> actionKeys = new HashSet<>();
    private boolean committed = false;
    private boolean rolledBack = false;

    LocalCompensationScope(ICompensationLog log, CompensationOptions options) {
        this.log = log;
        this.options = options;
    }

    @Override
    public <A extends AggregateRoot<?>, REQ extends IExternalRequirement, R> R execute(
            CompensationCommand<A, REQ, R> command) {
        ensureOpen();
        String key = command.actionKey();
        if (!this.actionKeys.add(key)) {
            throw new IllegalArgumentException("duplicate actionKey in scope: " + key);
        }
        // durable 前置校验：必须在正向执行之前——校验失败时不得已产生任何外部副作用
        assertDurablePrerequisites(command);
        recordPending(command);           // [L3] 正向之前落 PENDING（含 handler + payload）
        R result = command.execute();     // 失败直接冒泡，不登记（所见即所偿）
        this.executed.add(new Executed<>(command, result));
        markExecuted(key);
        return result;
    }

    private void assertDurablePrerequisites(CompensationCommand<?, ?, ?> command) {
        if (!this.options.durable()) {
            return;
        }
        if (command.aggregateRoot().getEntityId() == null) {
            throw new IllegalStateException("durable mode requires identified aggregate: "
                    + command.aggregateRoot().getClass().getSimpleName() + " / " + command.handler());
        }
        if (command.payload().isEmpty()) {
            throw new IllegalStateException("durable mode requires non-empty payload: " + command.actionKey());
        }
    }

    @Override
    public void commit() {
        ensureOpen();
        this.committed = true;
        for (Executed<?, ?, ?> item : this.executed) {
            // CONFIRMED 终态（markConfirmed）随阶段三的 SPI 新方法与 mybatis 实现一并落地；
            // 阶段二先保持 v2 语义：提交仅触发 Confirm 回调，不改写日志状态。
            item.confirmIfNeeded(reason -> markFailed(item.command().actionKey(), reason));
        }
    }

    @Override
    public void rollback(Throwable cause) {
        ensureOpen();
        this.rolledBack = true;
        List<CompensationFailure> failures = new ArrayList<>();
        for (int i = this.executed.size() - 1; i >= 0; i--) {   // 逆序，不中断
            this.compensateWithClaim(this.executed.get(i), failures);
        }
        if (!failures.isEmpty()) {
            throw new CompensationFailedException(failures, cause);
        }
    }

    @Override
    public void close() {
        // 仅清理登记项，不抛异常（契约约束）
        this.executed.clear();
        this.actionKeys.clear();
    }

    private void compensateWithClaim(Executed<?, ?, ?> item, List<CompensationFailure> failures) {
        String key = item.command().actionKey();
        // [L3] 先原子认领：认领失败说明已被其他实例接管，跳过避免重复补偿；内存模式恒认领成功
        if (!markCompensating(key)) {
            return;
        }
        CompensationPolicy policy = item.command().policy().orElseGet(this.options::defaultPolicy);
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
        if (this.committed || this.rolledBack) {
            throw new CompensationStateException("scope already terminated");
        }
    }

    private void recordPending(CompensationCommand<?, ?, ?> command) {
        compensationLog().ifPresent(store -> store.record(pendingRecord(command)));
    }

    private static CompensationRecord pendingRecord(CompensationCommand<?, ?, ?> command) {
        return new CompensationRecord(command.actionKey(), CompensationStatus.PENDING,
                command.handler(), command.payload(), 0, Instant.now());
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

    /** 原子认领；无日志（内存模式）时恒返回 true。 */
    private boolean markCompensating(String key) {
        return compensationLog()
                .map(store -> store.markCompensating(key, UUID.randomUUID().toString()))
                .orElse(true);
    }

    private Optional<ICompensationLog> compensationLog() {
        // durable 选项由此真正生效：未开启时不写任何日志
        return this.options.durable() ? Optional.ofNullable(this.log) : Optional.empty();
    }

    private static void sleepQuietly(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
