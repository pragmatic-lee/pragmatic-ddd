package io.pragmatic.ddd.example.order.application.order.action;

import io.pragmatic.ddd.application.compensation.ICompensableAction;
import io.pragmatic.ddd.example.order.domain.order.dependency.IInventoryDependency;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.requirement.ReserveInventoryRequirement;
import org.springframework.stereotype.Component;

/**
 * 库存预占补偿动作：正向预占、回写订单、逆向释放，全部从聚合读取业务事实。
 * 实现无状态，作为单例由容器管理并注入外部依赖客户端。
 *
 * @author wizard-lee
 */
@Component
public class ReserveInventoryAction implements ICompensableAction<Order, ReserveInventoryRequirement, String> {

    private final IInventoryDependency inventoryDependency;

    public ReserveInventoryAction(IInventoryDependency inventoryDependency) {
        this.inventoryDependency = inventoryDependency;
    }

    @Override
    public Class<ReserveInventoryRequirement> requirementType() {
        return ReserveInventoryRequirement.class;
    }

    @Override
    public Class<Order> aggregateType() {
        return Order.class;
    }

    @Override
    public String execute(Order order, ReserveInventoryRequirement requirement) {
        return this.inventoryDependency.reserve(order.getEntityId(), order.getOrderItems().getAllItems());
    }

    @Override
    public void apply(Order order, String reserveNo) {
        order.bindReserveNo(reserveNo);
    }

    @Override
    public void compensate(Order order, String reserveNo) {
        this.inventoryDependency.release(reserveNo);
    }
}
