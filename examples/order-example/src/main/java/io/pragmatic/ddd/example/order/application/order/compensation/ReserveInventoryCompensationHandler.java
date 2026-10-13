package io.pragmatic.ddd.example.order.application.order.compensation;

import io.pragmatic.ddd.application.compensation.spi.ICompensationHandler;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.example.order.application.order.action.ReservePayload;
import io.pragmatic.ddd.example.order.domain.order.dependency.IInventoryDependency;
import org.springframework.stereotype.Component;

/**
 * 库存预占的崩溃兜底执行器：按持久化的 payload 还原，并按订单标识幂等释放。
 * actionName 必须等于需求的 code（即 compensation_log.handler 列）。
 *
 * @author wizard-lee
 */
@Component
public class ReserveInventoryCompensationHandler implements ICompensationHandler {

    private final IInventoryDependency inventoryDependency;
    private final IEventSerializer serializer;

    public ReserveInventoryCompensationHandler(IInventoryDependency inventoryDependency,
                                               IEventSerializer serializer) {
        this.inventoryDependency = inventoryDependency;
        this.serializer = serializer;
    }

    @Override
    public String actionName() {
        return "reserve-inventory";
    }

    @Override
    public void compensate(String payload) {
        ReservePayload p = this.serializer.deserialize(payload, ReservePayload.class);
        this.inventoryDependency.releaseByOrder(p.orderId());
    }
}
