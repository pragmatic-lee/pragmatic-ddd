package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.repository.query.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.ProjectionExceptions;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单列表查询（queryList）的 ES 检索器，覆盖 OrderListQuery 一族，族内按子类分发。
 * 对应框架 {@link IProjectionSearcher}，注册键 (OrderListQuery.class, projectionType)。
 *
 * @author wizard-lee
 */
@Component
public class OrderListSearcher implements IProjectionSearcher<OrderListQuery, IOrderProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderListSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<OrderListQuery> criteriaType() {
        return OrderListQuery.class;
    }

    @Override
    public Class<IOrderProjection> projectionType() {
        return IOrderProjection.class;
    }

    @Override
    public List<IOrderProjection> search(OrderListQuery condition, Class<IOrderProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> {
            if (condition instanceof OrderListQuery.TopByAmount c) {
                return searchTopByAmount(c, projectionType);
            }
            if (condition instanceof OrderListQuery.TopRecent c) {
                return searchTopRecent(c, projectionType);
            }
            return List.<IOrderProjection>of();
        }, "search");
    }

    @SneakyThrows
    private List<IOrderProjection> searchTopByAmount(
            OrderListQuery.TopByAmount condition, Class<IOrderProjection> projectionType) {
        Query query = buildCustomerStatusQuery(condition.customerId(), condition.status());
        return elasticsearchClient.search(req -> req
                        .index(OrderEsTargets.ORDER_INDEX_NAME)
                        .query(query)
                        .sort(sort -> sort.field(f -> f.field("totalAmount").order(SortOrder.Desc)))
                        .size(condition.top()), projectionType).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }

    @SneakyThrows
    private List<IOrderProjection> searchTopRecent(
            OrderListQuery.TopRecent condition, Class<IOrderProjection> projectionType) {
        Query query = buildCustomerStatusQuery(condition.customerId(), condition.status());
        return elasticsearchClient.search(req -> req
                        .index(OrderEsTargets.ORDER_INDEX_NAME)
                        .query(query)
                        .sort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                        .size(condition.top()), projectionType).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }

    private Query buildCustomerStatusQuery(Long customerId, Integer status) {
        List<Query> must = new ArrayList<>();
        must.add(Query.of(q -> q.term(TermQuery.of(t ->
                t.field("customer.customerId").value(customerId))))
        );

        must.add(Query.of(q -> q.term(TermQuery.of(t ->
                t.field("status").value(status))))
        );

        BoolQuery bool = BoolQuery.of(b -> b.must(must));
        return Query.of(q -> q.bool(bool));
    }
}
