package io.pragmatic.ddd.afull.infrastructure.repository.memory.order;

import io.pragmatic.ddd.afull.domain.order.model.IOrderRepository;
import io.pragmatic.ddd.afull.domain.order.model.Order;
import io.pragmatic.ddd.repository.AbstractRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于内存的订单仓储实现。
 *
 * @author wizard-lee
 */
public class OrderRepository extends AbstractRepository<Long, Order> implements IOrderRepository {

    private final List<Order> memoryDataList = new ArrayList<>();

    @Override
    public Order findByOrderId(long orderId) {
        return this.memoryDataList.stream()
                .filter(s -> s.getEntityId().equals(orderId))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Order findById(Long id) {
        return findByOrderId(id);
    }

    @Override
    protected void doInsert(Order order) {
        this.memoryDataList.add(order);
    }

    @Override
    protected void doUpdate(Order order) {
        for (int i = 0; i < this.memoryDataList.size(); i++) {
            if (this.memoryDataList.get(i).getEntityId().equals(order.getEntityId())) {
                this.memoryDataList.set(i, order);
            }
        }
    }

    @Override
    protected void doRemove(Order order) {
        this.memoryDataList.removeIf(s -> s.getEntityId().equals(order.getEntityId()));
    }
}
