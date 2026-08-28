package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.repository.query.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.ProjectionExceptions;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单单投影查询（queryOne）的 ES 检索器，覆盖 OrderOneQuery 一族，族内按子类分发。
 * 对应框架 {@link IProjectionSearcher}，注册键 (OrderOneQuery.class, projectionType)。
 *
 * @author wizard-lee
 */
@Component
public class OrderOneSearcher implements IProjectionSearcher<OrderOneQuery, IOrderProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderOneSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<OrderOneQuery> criteriaType() {
        return OrderOneQuery.class;
    }

    @Override
    public Class<IOrderProjection> projectionType() {
        return IOrderProjection.class;
    }

    @Override
    public List<IOrderProjection> search(OrderOneQuery condition, Class<IOrderProjection> projectionType) {
        return ProjectionExceptions.retrieve(() -> {
            if (condition instanceof OrderOneQuery.LatestByCustomer c) {
                return searchLatestByCustomer(c, projectionType);
            }
            return List.<IOrderProjection>of();
        }, "search");
    }

    @SneakyThrows
    private List<IOrderProjection> searchLatestByCustomer(
            OrderOneQuery.LatestByCustomer condition, Class<IOrderProjection> projectionType) {
        TermQuery term = TermQuery.of(t -> t.field("customer.customerId").value(condition.customerId()));
        Query query = Query.of(q -> q.term(term));
        return elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .sort(sort -> sort.field(f -> f.field("createdAt")
                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .size(1), projectionType).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }
}
