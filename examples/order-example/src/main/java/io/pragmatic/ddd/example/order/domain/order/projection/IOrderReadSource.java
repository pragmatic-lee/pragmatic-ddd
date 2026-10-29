package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.projection.IPagedQuerySearcher;
import io.pragmatic.ddd.repository.query.projection.IReducer;

/**
 * 订单同源直读源端口：声明「本库能怎么查订单」，实现由基础设施提供。
 *
 * <p>只 {@code extends} 真实支撑的查询族：ById 与分页 / 滚动；不支撑条件族就不声明，
 * 调用方在编译期即无法调用。与 {@link IOrderESSource} / {@link IOrderRedisSource}
 * 的区别是：这里承载的是行投影 {@link OrderRowProjection}，不涉及副本、版本与同步事件。</p>
 *
 * @author wizard-lee
 */
public interface IOrderReadSource
        extends IProjectionByIdSearcher<OrderRowProjection>,
                IPagedQuerySearcher<OrderRowProjection, OrderPageQuery> {

    /**
     * 按目标子投影类型取裁剪器。
     *
     * @param target 目标子投影类型
     * @param <X> 目标子投影类型
     * @return 匹配的裁剪器；未注册返回 null
     */
    <X extends IOrderProjection> IReducer<OrderRowProjection, X> getReducer(Class<X> target);
}
