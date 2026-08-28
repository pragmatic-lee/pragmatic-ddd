package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ProjectorRegistry;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单读模型查询的基础设施实现，从投影注册表按型定位检索器并薄薄转发。
 * 不持有任何 ES 客户端与字段映射，纯转发到对应 searcher。
 *
 * @author wizard-lee
 */
@Service
public class OrderQuery implements IOrderQuery {

    private final ProjectorRegistry registry;

    public OrderQuery(ProjectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <X extends IOrderProjection> X queryById(Long id, Class<X> projectionType) {
        IProjectionByIdSearcher<X> searcher = registry.getByIdSearcher(projectionType);
        return searcher.getById(id, projectionType);
    }

    @Override
    public <X extends IOrderProjection> List<X> queryByIds(List<Long> ids, Class<X> projectionType) {
        IProjectionByIdSearcher<X> searcher = registry.getByIdSearcher(projectionType);
        List<Object> idObjects = List.copyOf(ids);
        return searcher.getByIds(idObjects, projectionType);
    }

    @Override
    public <X extends IOrderProjection> List<X> queryList(OrderListQuery query, Class<X> projectionType) {
        IProjectionSearcher<OrderListQuery, X> searcher = registry.getSearcher(OrderListQuery.class, projectionType);
        return searcher.search(query, projectionType);
    }

    @Override
    public <X extends IOrderProjection> X queryOne(OrderOneQuery query, Class<X> projectionType) {
        IProjectionSearcher<OrderOneQuery, X> searcher = registry.getSearcher(OrderOneQuery.class, projectionType);
        return searcher.search(query, projectionType).stream().findFirst().orElse(null);
    }

    @Override
    public <X extends IOrderProjection> PageResult<X> queryPage(
            OrderPageQuery query,
            PageRequest pageRequest,
            Class<X> projectionType) {
        IProjectionPagedSearcher<OrderPageQuery, X> searcher =
                registry.getPagedSearcher(OrderPageQuery.class, projectionType);
        return searcher.searchPage(query, pageRequest, projectionType);
    }

    @Override
    public <X extends IOrderProjection> ScrollResult<X> queryScroll(
            OrderPageQuery query,
            ScrollPosition cursor,
            int pageSize,
            Class<X> projectionType) {
        IProjectionPagedSearcher<OrderPageQuery, X> searcher =
                registry.getPagedSearcher(OrderPageQuery.class, projectionType);
        return searcher.searchScroll(query, cursor, pageSize, projectionType);
    }
}
