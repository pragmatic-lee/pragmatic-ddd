package io.pragmatic.ddd.example.order.domain.order.repository;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.repository.AbstractRepository;

public abstract class AbstractOrderRepository extends AbstractRepository<Long, Order> {
}
