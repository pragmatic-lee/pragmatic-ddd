package io.pragmatic.ddd.mybatis.compensation;

import io.pragmatic.ddd.application.compensation.CompensationRecordConflictException;
import io.pragmatic.ddd.application.compensation.spi.CompensationRecord;
import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import io.pragmatic.ddd.application.spi.Propagation;
import io.pragmatic.ddd.application.spi.TransactionOperations;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.util.List;

/**
 * {@link ICompensationLog} 的官方 MyBatis 实现（补偿 WAL 持久化，传统纯 XML 直调方式）。
 *
 * <ul>
 *   <li><b>独立短事务</b>：全部写操作与查询各自包裹在注入的 {@link TransactionOperations}
 *       （REQUIRES_NEW）内作为独立短事务立即提交，绝不参与业务本地事务。</li>
 *   <li><b>原子认领</b>：{@link #markCompensating} 依赖单条
 *       {@code UPDATE ... WHERE action_key = ? AND status IN ('EXECUTED', 'FAILED')} 的受影响行数，
 *       多实例并发下同一记录只会被一个实例认领成功。</li>
 *   <li><b>冲突 fail-fast</b>：{@link #record} 遇到主键 / 唯一键冲突时翻译成
 *       {@link CompensationRecordConflictException}，不静默降级。</li>
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
        try {
            txOps.execute(() -> {
                executor.insert(CompensationStatements.INSERT, record);
                return null;
            }, Propagation.REQUIRES_NEW);
        } catch (RuntimeException e) {
            if (isIntegrityConstraintViolation(e)) {
                throw new CompensationRecordConflictException(record.actionKey(), e);
            }
            throw e;
        }
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
    public void markConfirmed(String actionKey) {
        txOps.execute(() -> {
            executor.markConfirmed(CompensationStatements.MARK_CONFIRMED, actionKey);
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

    @Override
    public List<CompensationRecord> findRetryableFailed(int limit, int maxAttempts) {
        return txOps.execute(
                () -> executor.findRetryableFailed(CompensationStatements.FIND_RETRYABLE_FAILED, limit, maxAttempts),
                Propagation.REQUIRES_NEW);
    }

    @Override
    public int releaseStaleClaims(Duration lease) {
        return txOps.execute(
                () -> executor.releaseStaleClaims(CompensationStatements.RELEASE_STALE_CLAIMS, lease.toSeconds()),
                Propagation.REQUIRES_NEW);
    }

    /**
     * 沿 cause 链查找 JDBC 完整性约束违反（主键 / 唯一键冲突）。
     * 用 JDBC 标准异常 / SQLState 而非 Spring 的重复键异常：本模块不依赖 Spring。
     * MySQL 驱动直接抛 {@link SQLIntegrityConstraintViolationException}；
     * PostgreSQL 驱动抛 PSQLException（SQLState 23505），不走该子类，
     * 故补充标准 SQLState「23 类」（完整性约束违反）判断，跨数据库通用。
     *
     * @param e 待检查的异常
     * @return 是否为完整性约束冲突
     */
    private static boolean isIntegrityConstraintViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            if (t instanceof SQLException sqlEx) {
                String sqlState = sqlEx.getSQLState();
                if (sqlState != null && sqlState.startsWith("23")) {
                    return true;
                }
            }
        }
        return false;
    }
}
