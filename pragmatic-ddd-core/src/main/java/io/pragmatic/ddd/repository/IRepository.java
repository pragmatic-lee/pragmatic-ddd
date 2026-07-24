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

    default void remove(T aggregateRoot){}
}
