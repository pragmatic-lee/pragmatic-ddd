package io.pragmatic.ddd.example.order.domain.order.projection.reducer;

import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderSummaryProjection;
import io.pragmatic.ddd.repository.query.projection.IProjectionReducer;

/**
 * 订单缓存副本概要裁剪契约，窄化框架通用裁剪接口为订单领域专属契约。
 *
 * <p>约定本裁剪器的源为 Redis 缓存副本 {@link OrderCacheProjection}（与 ES 源 {@link IOrderSummaryReducer}
 * 平级、互不引用），目标为业务子投影 {@link OrderSummaryProjection}；具体裁剪实现由基础设施层提供。</p>
 *
 * @author wizard-lee
 */
public interface IOrderCacheSummaryReducer
        extends IProjectionReducer<OrderCacheProjection, OrderSummaryProjection> {
}
