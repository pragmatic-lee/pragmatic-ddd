package io.pragmatic.ddd.example.order.domain.order.projection.reducer;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.repository.query.projection.IProjectionReducer;

/**
 * 订单概要投影的裁剪契约，窄化框架通用裁剪接口为订单领域专属契约。
 *
 * <p>约定本裁剪器的源为索引 {@code order_index} 的索引级全量投影
 * {@link OrderEsProjection}，目标为业务子投影 {@link OrderSummaryProjection}；
 * 具体裁剪实现由基础设施层提供。</p>
 *
 * @author wizard-lee
 */
public interface IOrderSummaryReducer
        extends IProjectionReducer<OrderEsProjection, OrderSummaryProjection> {
}
