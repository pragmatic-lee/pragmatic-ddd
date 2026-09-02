package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import io.pragmatic.ddd.repository.query.projection.IProjectionReducer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.repository.query.projection.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.exception.ProjectionExceptions;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单列表查询（queryList）的 ES 检索器，覆盖 OrderListQuery 一族，族内按子类分发。
 * 对应框架 {@link IProjectionSearcher}，注册键 (OrderListQuery.class, projectionType)。
 *
 * <p>本检索器绑定索引 {@code order_index} 的索引级全量投影 {@link OrderEsProjection}，
 * 只负责取回该全量形状；业务子投影由 {@link IProjectionReducer} 在内存裁剪。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderListSearcher implements IProjectionSearcher<OrderListQuery, OrderEsProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderListSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<OrderListQuery> criteriaType() {
        return OrderListQuery.class;
    }

    @Override
    public List<OrderEsProjection> search(OrderListQuery condition) {
        return ProjectionExceptions.retrieve(() -> {
            if (condition instanceof OrderListQuery.TopByAmount c) {
                return searchTopByAmount(c);
            }
            if (condition instanceof OrderListQuery.TopRecent c) {
                return searchTopRecent(c);
            }
            return List.<OrderEsProjection>of();
        }, "search");
    }

    @SneakyThrows
    private List<OrderEsProjection> searchTopByAmount(OrderListQuery.TopByAmount condition) {
        Query query = buildCustomerStatusQuery(condition.customerId(), condition.status());
        return elasticsearchClient.search(req -> req
                        .index(OrderEsTargets.ORDER_INDEX_NAME)
                        .query(query)
                        .sort(sort -> sort.field(f -> f.field("totalAmount").order(SortOrder.Desc)))
                        .size(condition.top()), OrderEsProjection.class).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }

    @SneakyThrows
    private List<OrderEsProjection> searchTopRecent(OrderListQuery.TopRecent condition) {
        Query query = buildCustomerStatusQuery(condition.customerId(), condition.status());
        return elasticsearchClient.search(req -> req
                        .index(OrderEsTargets.ORDER_INDEX_NAME)
                        .query(query)
                        .sort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                        .size(condition.top()), OrderEsProjection.class).hits().hits().stream()
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
