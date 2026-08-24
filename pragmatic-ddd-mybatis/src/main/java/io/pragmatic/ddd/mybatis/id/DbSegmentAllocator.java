package io.pragmatic.ddd.mybatis.id;

import io.pragmatic.ddd.application.outbox.spi.Propagation;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.base.id.IdSegment;
import io.pragmatic.ddd.base.id.IIdSegmentAllocator;

/**
 * 基于数据库号段的 IIdSegmentAllocator 实现（传统纯 XML 直调方式）。
 * 号段 SQL 由 IIdSegmentStatementExecutor 执行；本类以独立短事务（REQUIRES_NEW）包裹分配，
 * 保证 SELECT ... FOR UPDATE 行锁在事务内有效，与框架其他执行抽象形态统一。
 *
 * @author wizard-lee
 */
public class DbSegmentAllocator implements IIdSegmentAllocator {

    private final IIdSegmentStatementExecutor executor;
    private final TransactionOperations txOps;

    public DbSegmentAllocator(IIdSegmentStatementExecutor executor, TransactionOperations txOps) {
        this.executor = executor;
        this.txOps = txOps;
    }

    @Override
    public IdSegment allocateNext(String bizKey) {
        return txOps.execute(
                () -> executor.allocateNext(
                        IdSegmentStatements.SELECT_FOR_UPDATE, IdSegmentStatements.INCREMENT_MAX, bizKey),
                Propagation.REQUIRES_NEW);
    }
}
