package io.pragmatic.ddd.example.order.application.order.action;

import io.pragmatic.ddd.application.compensation.ICompensableAction;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.example.order.domain.order.dependency.IInventoryDependency;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.requirement.ReserveInventoryRequirement;
import org.springframework.stereotype.Component;

/**
 * 库存预占补偿动作：正向预占、回写订单、逆向释放，全部从聚合读取业务事实。
 * 实现无状态，作为单例由容器管理并注入外部依赖客户端与序列化器。
 *
 * @author wizard-lee
 */
@Component
public class ReserveInventoryAction implements ICompensableAction<Order, ReserveInventoryRequirement, String> {

    private final IInventoryDependency inventoryDependency;
    private final IEventSerializer serializer;

    public ReserveInventoryAction(IInventoryDependency inventoryDependency,
                                  IEventSerializer serializer) {
        this.inventoryDependency = inventoryDependency;
        this.serializer = serializer;
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

    /**
     * 持久化载荷：正向执行之前即可确定的补偿依据，供崩溃后由中继还原释放。
     * 补偿不得依赖外部返回值——外部系统须支持按业务单号幂等撤销。
     * 序列化统一走框架 IEventSerializer SPI，不在动作里直接依赖具体 JSON 库。
     *
     * <p>durable 模式下本方法必须返回非空，否则前置校验 fail-fast。</p>
     */
    @Override
    public String payload(Order order, ReserveInventoryRequirement requirement) {
        return this.serializer.serialize(new ReservePayload(
                order.getEntityId(),
                order.getOrderItems().getAllItems().stream()
                        .map(item -> new ReservePayload.Line(
                                item.getProductId(),
                                item.getProductName(),
                                item.getQuantity()))
                        .toList()));
    }
}
