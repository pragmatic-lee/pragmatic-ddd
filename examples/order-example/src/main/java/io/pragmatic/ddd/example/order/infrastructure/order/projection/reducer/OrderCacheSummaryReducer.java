package io.pragmatic.ddd.example.order.infrastructure.order.projection.reducer;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.reducer.IOrderCacheSummaryReducer;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Redis 缓存副本投影到概要投影的裁剪器实现，对应领域契约 {@link IOrderCacheSummaryReducer}。
 * 与 {@link io.pragmatic.ddd.example.order.infrastructure.order.projection.reducer.OrderSummaryReducer}
 * 平级、互不引用，二者各自从自己的物理源产出同一 {@link OrderSummaryProjection}。
 *
 * @author wizard-lee
 */
@Component
public class OrderCacheSummaryReducer implements IOrderCacheSummaryReducer {

    @Override
    public Class<OrderCacheProjection> sourceType() {
        return OrderCacheProjection.class;
    }

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
        summary.setActualAmount(source.getActualAmount());
        summary.setCreatedAt(source.getCreatedAt());
        Optional.ofNullable(source.getCustomer())
                .map(OrderCacheProjection.CustomerProjection::getCustomerName)
                .ifPresent(summary::setCustomerName);
        return summary;
    }
}
