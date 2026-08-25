package io.pragmatic.ddd.example.order.domain.order.repository;

import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.repository.IRepository;

/**
 * 订单聚合仓储契约：领域层声明，由基础设施层实现。
 *
 * @author wizard-lee
 */
public interface IOrderRepository extends IRepository<Long, Order> {
}
