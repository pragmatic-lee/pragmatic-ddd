package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.repository.query.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.ProjectionExceptions;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单按主键 / 批量主键直取投影的 ES 检索器，覆盖 IOrderQuery 的 queryById / queryByIds。
 * 对应框架 {@link IProjectionByIdSearcher}，注册键仅 (projectionType) 一维。
 *
 * <p>本检索器绑定索引 {@code order_index} 的索引级全量投影 {@link OrderEsProjection}，
 * 只负责取回该全量形状；业务子投影由 {@link IProjectionReducer} 在内存裁剪。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderByIdSearcher implements IProjectionByIdSearcher<OrderEsProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderByIdSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<OrderEsProjection> projectionType() {
        return OrderEsProjection.class;
    }

    @Override
    public OrderEsProjection getById(Object id, Class<OrderEsProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> doGetById(id.toString(), projectionType), "getById");
    }

    @Override
    public List<OrderEsProjection> getByIds(List<Object> ids, Class<OrderEsProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> doGetByIds(ids, projectionType), "getByIds");
    }

    @SneakyThrows
    private OrderEsProjection doGetById(String id, Class<OrderEsProjection> projectionType) {
        return elasticsearchClient.get(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .id(id), projectionType).source();
    }

    @SneakyThrows
    private List<OrderEsProjection> doGetByIds(List<Object> ids, Class<OrderEsProjection> projectionType) {
        List<String> docIds = ids.stream()
                .map(Object::toString)
                .toList();
        IdsQuery idsQuery = IdsQuery.of(q -> q.values(docIds));
        Query query = Query.of(q -> q.ids(idsQuery));
        return elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .size(docIds.size()), projectionType).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }
}
