package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.repository.IRepository;

/**
 * 工作单元测试专用仓储夹具：统计"事务内已落库但可能被回滚"的写入次数。
 * 与可回滚的事务夹具配合，验证中途失败时前面的写入未真正提交。
 */
public class RollbackCountingRepository implements IRepository<Long, DryRunAggregate> {

    private int committedCount = 0;
    private int attemptedCount = 0;

    @Override
    public void insert(DryRunAggregate aggregateRoot) {
        attemptedCount++;
    }

    @Override
    public void update(DryRunAggregate aggregateRoot) {
        attemptedCount++;
    }

    @Override
    public DryRunAggregate findById(Long id) {
        return null;
    }

    @Override
    public void remove(DryRunAggregate aggregateRoot) {
        // 工作单元测试不涉及删除
    }

    /** 事务成功提交时调用，把尝试写入确认为已提交。 */
    public void confirmCommitted() {
        committedCount = attemptedCount;
    }

    /** 返回事务内尝试的写入次数。 */
    public int attemptedCount() {
        return attemptedCount;
    }

    /** 返回已确认提交的写入次数。 */
    public int committedCount() {
        return committedCount;
    }
}
