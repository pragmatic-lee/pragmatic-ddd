package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IListQuerySearcher;
import io.pragmatic.ddd.repository.query.projection.IOneQuerySearcher;
import io.pragmatic.ddd.repository.query.projection.IPagedQuerySearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.projection.IReducer;

/**
 * 订单投影源接口（领域层端口）：以 ES 承载 {@link OrderEsProjection} 的副本，
 * 声明其支持的读能力族（ById / One / List / Page）。
 *
 * <p>应用层只依赖本接口，不依赖基础设施具体实现；基础设施层的 ES 源实现它。
 * 能力族决定"能调哪些方法"——本接口声明四族全能力。</p>
 *
 * @author wizard-lee
 */
public interface IOrderESSource
        extends IProjectionByIdSearcher<OrderEsProjection>,
                IOneQuerySearcher<OrderEsProjection, OrderOneQuery>,
                IListQuerySearcher<OrderEsProjection, OrderListQuery>,
                IPagedQuerySearcher<OrderEsProjection, OrderPageQuery> {

    /**
     * 按目标子投影类型取裁剪器（由源基类实现，声明在此供应用层调用）。
     *
     * @param target 目标子投影类型
     * @param <X> 目标子投影类型
     * @return 裁剪器；未注册返回 null
     */
    <X extends IAggregateProjection> IReducer<OrderEsProjection, X> getReducer(Class<X> target);
}
