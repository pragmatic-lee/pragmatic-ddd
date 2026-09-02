package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.AbstractProjectionQuery;
import io.pragmatic.ddd.repository.query.ProjectionSource;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import org.springframework.stereotype.Component;

/**
 * 订单投影查询：以「源」为中心的读路径，三跳（源 → 物理投影 → 业务子投影）由框架承载，
 * 本类仅描述聚合、全量投影与查询条件的类型绑定。
 *
 * <p>按主键查询时支持调用方显式指定取数来源的物理投影类型，委托
 * {@code source(ProjectionSource)} 落源选路，未指定时走默认源反查。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderQuery extends AbstractProjectionQuery<Long, IOrderProjection, OrderOneQuery, OrderListQuery, OrderPageQuery>
        implements IOrderQuery {

    public OrderQuery(ProjectorRegistry registry) {
        super(registry, OrderOneQuery.class, OrderListQuery.class, OrderPageQuery.class);
    }

    @Override
    public <X extends IOrderProjection> X queryById(Object id, Class<?> sourceProjection, Class<X> projectionType) {
        return registry().fullProjectionOf(sourceProjection)
                .map(src -> source(src).queryById((Long) id, projectionType))
                .orElse(null);
    }
}
