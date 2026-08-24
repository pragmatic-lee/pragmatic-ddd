package io.pragmatic.ddd.mybatis.outbox;

import io.pragmatic.ddd.application.outbox.OutboxMessage;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.Propagation;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;

import java.time.Duration;
import java.util.List;

/**
 * {@link IOutboxStore} 的官方 MyBatis 实现（事件箱持久化，传统纯 XML 直调方式）。
 *
 * <ul>
 *   <li><b>store 同事务</b>：{@link #store} 在调用方事务内执行，与聚合同事务落库，自身不开启事务，
 *       委托 {@link IOutboxStatementExecutor} 按调用方传入的 statementKey 直调。</li>
 *   <li><b>补偿操作独立短事务</b>：claim / markSent / release / claimPending / incrementAttempts / markFailed
 *       各自包裹在注入的 {@link TransactionOperations}（REQUIRES_NEW）内作为独立短事务立即提交。</li>
 * </ul>
 *
 * @author wizard-lee
 */
public class MybatisOutboxStore implements IOutboxStore {

    private final IOutboxStatementExecutor executor;
    private final TransactionOperations txOps;

    public MybatisOutboxStore(IOutboxStatementExecutor executor, TransactionOperations txOps) {
        this.executor = executor;
        this.txOps = txOps;
    }

    @Override
    public void store(List<OutboxMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // 参与调用方事务（外层 REQUIRED）；statementKey 由本层映射，执行器不感知 key
        executor.store(OutboxStatements.INSERT_BATCH, messages);
    }

    @Override
    public OutboxMessage claim(String id) {
        return txOps.execute(() -> executor.claim(OutboxStatements.CLAIM, id), Propagation.REQUIRES_NEW);
    }

    @Override
    public void markSent(String id) {
        txOps.execute(() -> {
            executor.markSent(OutboxStatements.MARK_SENT, id);
            return null;
        }, Propagation.REQUIRES_NEW);
    }

    @Override
    public void release(String id) {
        txOps.execute(() -> {
            executor.release(OutboxStatements.RELEASE, id);
            return null;
        }, Propagation.REQUIRES_NEW);
    }

    @Override
    public List<OutboxMessage> claimPending(int batchSize, Duration grace) {
        return txOps.execute(() -> executor.claimPending(OutboxStatements.CLAIM_PENDING, batchSize, grace),
                Propagation.REQUIRES_NEW);
    }

    @Override
    public int incrementAttempts(String id) {
        return txOps.execute(() -> executor.incrementAttempts(OutboxStatements.INCREMENT_ATTEMPTS, id),
                Propagation.REQUIRES_NEW);
    }

    @Override
    public void markFailed(String id) {
        txOps.execute(() -> {
            executor.markFailed(OutboxStatements.MARK_FAILED, id);
            return null;
        }, Propagation.REQUIRES_NEW);
    }
}
