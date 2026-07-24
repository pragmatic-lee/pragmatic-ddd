package io.pragmatic.ddd.afull.infrastructure.repository.memory.order;


import io.pragmatic.ddd.afull.domain.order.model.IOrderRepository;
import io.pragmatic.ddd.afull.domain.order.model.Order;

import java.util.ArrayList;
import java.util.List;

/**
 * 模拟数据库操作
 *
 * @author lixiaojing
 * @date 2021/3/1 5:29 下午
 */
public class OrderRepository implements IOrderRepository {

    private final List<Order> memoryDataList = new ArrayList<>();

    @Override
    public Order findByOrderId(long orderId) {
        return this.memoryDataList.stream().filter(s -> s.getEntityId().equals(orderId)).findFirst().orElse(null);
    }

    @Override
    public void update(Order order) {
        for (int i = 0; i < this.memoryDataList.size(); i++) {
            if (this.memoryDataList.get(i).getEntityId().equals(order.getEntityId())) {
                this.memoryDataList.set(i, order);
            }
        }
    }

    @Override
    public void create(Order order) {
        this.memoryDataList.add(order);
    }
}
