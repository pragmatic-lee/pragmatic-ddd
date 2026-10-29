package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderRowProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 订单行投影 → 订单概要投影的裁剪器：纯内存字段转换，不访问存储。
 *
 * <p>行投影是全量读源形状，列表 / TOP N 只需要其中几列，故在内存中裁剪；
 * 分页与总数保持裁剪前结果，本类不改变集合规模。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderRowSummaryReducer implements IReducer<OrderRowProjection, OrderSummaryProjection> {

    @Override
    public Class<OrderSummaryProjection> projectionType() {
        return OrderSummaryProjection.class;
    }

    @Override
    public OrderSummaryProjection reduce(OrderRowProjection source) {
        if (source == null) {
            return null;
        }
        OrderSummaryProjection target = new OrderSummaryProjection();
        Optional.ofNullable(source.getOrderId()).ifPresent(target::setOrderId);
        target.setStatus(source.getStatus());
        Optional.ofNullable(source.getStatusName()).ifPresent(target::setStatusName);
        Optional.ofNullable(source.getActualAmount()).ifPresent(target::setActualAmount);
        Optional.ofNullable(source.getCreatedAt()).ifPresent(target::setCreatedAt);
        return target;
    }
}
