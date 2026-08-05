package io.pragmatic.ddd.repository.reconciliation.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.IRepository;
import java.util.Map;

/**
 * 内存仓储桩：按 id 返回预设聚合，供 currentVersion 对账判定使用。
 */
public final class StubRepository implements IRepository<Long, StubAggregate> {

    private final Map<Long, StubAggregate> store;

    public StubRepository(Map<Long, StubAggregate> store) {
        this.store = store;
    }

    @Override
    public void insert(StubAggregate aggregateRoot) {
    }

    @Override
    public void update(StubAggregate aggregateRoot) {
    }

    @Override
    public StubAggregate findById(Long id) {
        return store.get(id);
    }

    @Override
    public void remove(StubAggregate aggregateRoot) {
    }
}
