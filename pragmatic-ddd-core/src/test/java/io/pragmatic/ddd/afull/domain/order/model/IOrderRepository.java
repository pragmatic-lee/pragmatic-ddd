package io.pragmatic.ddd.afull.domain.order.model;

import io.pragmatic.ddd.repository.IRepository;

/**
 * 订单仓储接口。
 *
 * @author wizard-lee
 */
public interface IOrderRepository extends IRepository<Long, Order> {

    Order findByOrderId(long orderId);
}
