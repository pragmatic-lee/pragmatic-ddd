package io.pragmatic.ddd.example.order.application.order;

import io.pragmatic.ddd.application.IQueryApplicationService;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.AbstractProjectionQuery;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import io.pragmatic.ddd.repository.query.projection.ProjectorRegistry;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单读侧应用服务：查询能力全部由框架 {@link AbstractProjectionQuery} 提供，
 * 本类只声明读侧的选源顺序，调用方仅传目标投影类型，不感知源。
 *
 * <p>选源内置为「Redis 缓存副本优先，未命中回退 ES 索引」：链上源不支持本次查询时
 * （如 Redis 无条件 / 分页检索器、或不承载 ES 全量投影）由框架自动跳过，
 * 因此条件查询与分页查询自然落到 ES，按主键查询自然享受缓存加速。</p>
 *
 * @author wizard-lee
 */
@Service
public class OrderReadService
        extends AbstractProjectionQuery<Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery>
        implements IQueryApplicationService {

    public OrderReadService(ProjectorRegistry projectorRegistry) {
        super(projectorRegistry, OrderOneQuery.class, OrderListQuery.class, OrderPageQuery.class);
    }

    /**
     * 读侧默认回源链：Redis 缓存副本优先，未命中回退 ES 索引。
     *
     * @return 回源顺序
     */
    @Override
    protected List<ProjectionSource> fallbackChain() {
        return List.of(
                ProjectionSource.of(OrderCacheTargets.TARGET_REDIS_ORDERS.storeId()),
                ProjectionSource.of(OrderEsTargets.TARGET_ES_ORDERS.storeId()));
    }
}
