package io.pragmatic.ddd.example.order.infrastructure.order.repository;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.repository.AbstractOrderRepository;

public class OrderRepository extends AbstractOrderRepository {
    @Override
    protected void doInsert(Order aggregateRoot) {

    }

    @Override
    protected void doUpdate(Order aggregateRoot) {

    }

    @Override
    protected void doRemove(Order aggregateRoot) {

    }

    @Override
    public Order findById(Long aLong) {
        return null;
    }

    @Override
    public boolean existsById(Long aLong) {
        return super.existsById(aLong);
    }

    @Override
    public long currentVersion(Long aLong) {
        return super.currentVersion(aLong);
    }
}
