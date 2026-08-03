package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.repository.IRepository;

/**
 * 试跑测试专用仓储夹具：仅统计落库调用次数，用于断言 Dry-run 不触发持久化。
 */
public class CountingRepository implements IRepository<Long, DryRunAggregate> {

    private int saveCount = 0;

    @Override
    public void insert(DryRunAggregate aggregateRoot) {
        saveCount++;
    }

    @Override
    public void update(DryRunAggregate aggregateRoot) {
        saveCount++;
    }

    @Override
    public DryRunAggregate findById(Long id) {
        return null;
    }

    @Override
    public void remove(DryRunAggregate aggregateRoot) {
        // 试跑测试不涉及删除
    }

    /** 返回累计的落库次数。 */
    public int saveCount() {
        return saveCount;
    }
}
