package io.pragmatic.ddd.example.order.domain.order.projection;

import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.projection.IReducer;

/**
 * 订单投影源接口（领域层端口）：以 Redis 承载 {@link OrderCacheProjection} 的缓存副本，
 * 仅声明 ById 能力族（不支持条件检索 / 分页）。
 *
 * <p>应用层只依赖本接口，不依赖基础设施具体实现；基础设施层的 Redis 源实现它。
 * 因未 extends 条件族接口，对实现类调用 {@code search} / {@code searchPage} 编译期即失败。</p>
 *
 * @author wizard-lee
 */
public interface IOrderRedisSource extends IProjectionByIdSearcher<OrderCacheProjection> {

    /**
     * 按目标子投影类型取裁剪器（由源基类实现，声明在此供应用层调用）。
     *
     * @param target 目标子投影类型
     * @param <X> 目标子投影类型
     * @return 裁剪器；未注册返回 null
     */
    <X extends IAggregateProjection> IReducer<OrderCacheProjection, X> getReducer(Class<X> target);
}
