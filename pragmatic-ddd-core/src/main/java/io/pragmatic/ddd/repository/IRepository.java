package io.pragmatic.ddd.repository;

import io.pragmatic.ddd.base.AggregateRoot;

public interface IRepository<ID, T extends AggregateRoot<ID>> {

    void insert(T aggregateRoot);

    void update(T aggregateRoot);

    default void save(T aggregateRoot) {
        if (aggregateRoot.isNewEntity()) {
            insert(aggregateRoot);
        } else {
            update(aggregateRoot);
        }
    }

    T findById(ID id);

    /**
     * 按主键删除聚合。实现方必须提供真实删除逻辑（本方法不再有默认空实现）。
     */
    void removeById(ID id);

    /**
     * 判断主键是否存在。默认实现基于 {@link #findById(Object)} 是否为 null。
     */
    default boolean existsById(ID id) {
        return findById(id) != null;
    }
}
