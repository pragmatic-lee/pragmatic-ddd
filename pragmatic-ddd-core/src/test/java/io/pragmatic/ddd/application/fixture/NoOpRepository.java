package io.pragmatic.ddd.application.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.IRepository;

/**
 * 通用无操作仓储夹具：仅满足命令执行器对仓储的依赖，不做任何实际持久化。
 */
public class NoOpRepository<T extends AggregateRoot<Long>> implements IRepository<Long, T> {

    @Override
    public void insert(T aggregateRoot) {
        // 无操作
    }

    @Override
    public void update(T aggregateRoot) {
        // 无操作
    }

    @Override
    public T findById(Long id) {
        return null;
    }

    @Override
    public void remove(T aggregateRoot) {
        // 无操作
    }
}
