package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.repository.IRepository;

/**
 * 工作单元测试专用仓储夹具：持久化时抛出模拟异常，用于验证阶段二（事务内持久化）失败后的状态清理。
 */
public class ThrowingRepository implements IRepository<Long, DryRunAggregate> {

    @Override
    public void insert(DryRunAggregate aggregateRoot) {
        throw new IllegalStateException("simulated persist failure");
    }

    @Override
    public void update(DryRunAggregate aggregateRoot) {
        throw new IllegalStateException("simulated persist failure");
    }

    @Override
    public DryRunAggregate findById(Long id) {
        return null;
    }

    @Override
    public void remove(DryRunAggregate aggregateRoot) {
        // 工作单元测试不涉及删除
    }
}
