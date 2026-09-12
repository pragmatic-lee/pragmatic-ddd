package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Redis 缓存副本投影到概要投影的裁剪器实现。
 * 与 {@link OrderSummaryReducer} 平级、互不引用，二者各自从自己的物理源产出同一 {@link OrderSummaryProjection}。
 *
 * @author wizard-lee
 */
@Component
public class OrderCacheSummaryReducer
        implements IReducer<OrderCacheProjection, OrderSummaryProjection> {

    @Override
    public Class<OrderSummaryProjection> projectionType() {
        return OrderSummaryProjection.class;
    }

    @Override
    public OrderSummaryProjection reduce(OrderCacheProjection source) {
        if (source == null) {
            return null;
        }
        OrderSummaryProjection summary = new OrderSummaryProjection();
        summary.setOrderId(source.getOrderId());
        summary.setStatus(source.getStatus());
        summary.setStatusName(source.getStatusName());
        summary.setPaymentStatus(source.getPaymentStatus());
        summary.setPaymentStatusName(source.getPaymentStatusName());
        summary.setShipmentStatus(source.getShipmentStatus());
        summary.setShipmentStatusName(source.getShipmentStatusName());
        summary.setActualAmount(source.getActualAmount());
        summary.setCreatedAt(source.getCreatedAt());
        Optional.ofNullable(source.getCustomer())
                .map(OrderCacheProjection.CustomerProjection::getCustomerName)
                .ifPresent(summary::setCustomerName);
        return summary;
    }
}
