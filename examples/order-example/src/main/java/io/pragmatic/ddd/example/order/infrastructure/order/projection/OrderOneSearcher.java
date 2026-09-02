package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
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
 * <p>本检索器绑定索引 {@code order_index} 的索引级全量投影 {@link OrderEsProjection}，
 * 只负责取回该全量形状；业务子投影由 {@link IProjectionReducer} 在内存裁剪。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderOneSearcher implements IProjectionSearcher<OrderOneQuery, OrderEsProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderOneSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<OrderOneQuery> criteriaType() {
        return OrderOneQuery.class;
    }

    @Override
    public List<OrderEsProjection> search(OrderOneQuery condition) {
        return ProjectionExceptions.retrieve(() -> {
            if (condition instanceof OrderOneQuery.LatestByCustomer c) {
                return searchLatestByCustomer(c);
            }
            return List.<OrderEsProjection>of();
        }, "search");
    }

    @SneakyThrows
    private List<OrderEsProjection> searchLatestByCustomer(OrderOneQuery.LatestByCustomer condition) {
        TermQuery term = TermQuery.of(t -> t.field("customer.customerId").value(condition.customerId()));
        Query query = Query.of(q -> q.term(term));
        return elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .sort(sort -> sort.field(f -> f.field("createdAt")
                        .order(co.elastic.clients.elasticsearch._types.SortOrder.Desc)))
                .size(1), OrderEsProjection.class).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }
}
