package io.pragmatic.ddd.afull.domain.order.model;

/**
 * @author lixiaojing
 */
public interface IOrderRepository {

    Order findByOrderId(long orderId);

    void update(Order order);

    void create(Order order);
}
