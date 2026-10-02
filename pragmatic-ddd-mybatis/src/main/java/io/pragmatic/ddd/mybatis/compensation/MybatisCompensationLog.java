package io.pragmatic.ddd.mybatis.compensation;

import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.application.spi.Propagation;
import io.pragmatic.ddd.application.spi.TransactionOperations;

import java.util.List;

/**
 * {@link ICompensationLog} 的官方 MyBatis 实现（补偿 WAL 持久化，传统纯 XML 直调方式）。
 *
 * <ul>
 *   <li><b>独立短事务</b>：全部写操作与查询各自包裹在注入的 {@link TransactionOperations}
 *       （REQUIRES_NEW）内作为独立短事务立即提交，绝不参与业务本地事务。</li>
 *   <li><b>原子认领</b>：{@link #markCompensating} 依赖单条
 *       {@code UPDATE ... WHERE action_key = ? AND status = 'EXECUTED'} 的受影响行数，
 *       多实例并发下同一记录只会被一个实例认领成功。</li>
 * </ul>
 *
 * @author wizard-lee
 */
public class MybatisCompensationLog implements ICompensationLog {

    private final ICompensationStatementExecutor executor;
    private final TransactionOperations txOps;

    public MybatisCompensationLog(ICompensationStatementExecutor executor, TransactionOperations txOps) {
        this.executor = executor;
        this.txOps = txOps;
    }

    @Override
    public void record(CompensationRecord record) {
        txOps.execute(() -> {
            executor.insert(CompensationStatements.INSERT, record);
            return null;
        }, Propagation.REQUIRES_NEW);
    }

    @Override
    public void markExecuted(String actionKey) {
        txOps.execute(() -> {
            executor.markExecuted(CompensationStatements.MARK_EXECUTED, actionKey);
            return null;
        }, Propagation.REQUIRES_NEW);
    }

    @Override
    public boolean markCompensating(String actionKey, String claimToken) {
        Integer affected = txOps.execute(
                () -> executor.markCompensating(CompensationStatements.MARK_COMPENSATING, actionKey, claimToken),
                Propagation.REQUIRES_NEW);
        return affected > 0;
    }

    @Override
    public void markCompensated(String actionKey) {
        txOps.execute(() -> {
            executor.markCompensated(CompensationStatements.MARK_COMPENSATED, actionKey);
            return null;
        }, Propagation.REQUIRES_NEW);
    }

    @Override
    public void markFailed(String actionKey, String reason) {
        txOps.execute(() -> {
            executor.markFailed(CompensationStatements.MARK_FAILED, actionKey, reason);
            return null;
        }, Propagation.REQUIRES_NEW);
    }

    @Override
    public List<CompensationRecord> findExecuted(int limit) {
        return txOps.execute(
                () -> executor.findExecuted(CompensationStatements.FIND_EXECUTED, limit),
                Propagation.REQUIRES_NEW);
    }

    @Override
    public List<CompensationRecord> findSuspended(int limit) {
        return txOps.execute(
                () -> executor.findSuspended(CompensationStatements.FIND_SUSPENDED, limit),
                Propagation.REQUIRES_NEW);
    }

    @Override
    public List<CompensationRecord> findFailed(int limit) {
        return txOps.execute(
                () -> executor.findFailed(CompensationStatements.FIND_FAILED, limit),
                Propagation.REQUIRES_NEW);
    }
}
