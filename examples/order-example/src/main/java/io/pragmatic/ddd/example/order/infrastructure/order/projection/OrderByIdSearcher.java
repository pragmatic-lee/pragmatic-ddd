package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
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
 * @author wizard-lee
 */
@Component
public class OrderByIdSearcher implements IProjectionByIdSearcher<IOrderProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderByIdSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<IOrderProjection> projectionType() {
        return IOrderProjection.class;
    }

    @Override
    public IOrderProjection getById(Object id, Class<IOrderProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> doGetById(id.toString(), projectionType), "getById");
    }

    @Override
    public List<IOrderProjection> getByIds(List<Object> ids, Class<IOrderProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> doGetByIds(ids, projectionType), "getByIds");
    }

    @SneakyThrows
    private IOrderProjection doGetById(String id, Class<IOrderProjection> projectionType) {
        return elasticsearchClient.get(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .id(id), projectionType).source();
    }

    @SneakyThrows
    private List<IOrderProjection> doGetByIds(List<Object> ids, Class<IOrderProjection> projectionType) {
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
