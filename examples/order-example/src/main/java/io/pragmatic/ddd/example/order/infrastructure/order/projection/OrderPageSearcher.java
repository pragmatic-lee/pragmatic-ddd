package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.repository.query.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.PageRequest;
import io.pragmatic.ddd.repository.query.PageResult;
import io.pragmatic.ddd.repository.query.ProjectionExceptions;
import io.pragmatic.ddd.repository.query.ScrollPosition;
import io.pragmatic.ddd.repository.query.ScrollResult;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 订单分页 / 滚动查询（queryPage / queryScroll）的 ES 检索器，覆盖 OrderPageQuery 一族。
 * 对应框架 {@link IProjectionPagedSearcher}，注册键 (OrderPageQuery.class, projectionType)；
 * queryPage 与 queryScroll 共用同一条件族，仅分页装配与游标装配不同。
 *
 * @author wizard-lee
 */
@Component
public class OrderPageSearcher implements IProjectionPagedSearcher<OrderPageQuery, IOrderProjection> {

    private final ElasticsearchClient elasticsearchClient;

    public OrderPageSearcher(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public Class<OrderPageQuery> criteriaType() {
        return OrderPageQuery.class;
    }

    @Override
    public Class<IOrderProjection> projectionType() {
        return IOrderProjection.class;
    }

    @Override
    public PageResult<IOrderProjection> searchPage(
            OrderPageQuery condition, PageRequest pageRequest, Class<IOrderProjection> projectionType) {
        return ProjectionExceptions.retrieve(
                () -> doSearchPage(condition, pageRequest, projectionType), "searchPage");
    }

    @Override
    public ScrollResult<IOrderProjection> searchScroll(
            OrderPageQuery condition, ScrollPosition cursor, int pageSize, Class<IOrderProjection> projectionType) {
        return ProjectionExceptions.retrieve(
                () -> doSearchScroll(condition, cursor, pageSize, projectionType), "searchScroll");
    }

    @SneakyThrows
    private PageResult<IOrderProjection> doSearchPage(
            OrderPageQuery condition, PageRequest pageRequest, Class<IOrderProjection> projectionType) {
        Query query = buildConditionQuery(condition);
        var response = elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .sort(sort -> sort.field(f -> f.field("orderId").order(SortOrder.Desc)))
                .from(pageRequest.offset())
                .size(pageRequest.pageSize())
                .trackTotalHits(t -> t.enabled(true)), projectionType);
        List<IOrderProjection> data = response.hits().hits().stream()
                .map(Hit::source)
                .toList();
        Long total = Optional.of(response)
                .map(SearchResponse::hits)
                .map(HitsMetadata::total)
                .map(TotalHits::value)
                .orElse(0L);
        return PageResult.of(data, total, pageRequest);
    }

    @SneakyThrows
    private ScrollResult<IOrderProjection> doSearchScroll(
            OrderPageQuery condition, ScrollPosition cursor, int pageSize, Class<IOrderProjection> projectionType) {
        Query query = buildConditionQuery(condition);
        var response = elasticsearchClient.search(req -> {
            var b = req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .query(query)
                    .sort(sort -> sort.field(f -> f.field("orderId").order(SortOrder.Desc)))
                    .size(pageSize);
            if (!cursor.isInitial()) {
                b.searchAfter(cursor.cursor());
            }
            return b;
        }, projectionType);
        List<co.elastic.clients.elasticsearch.core.search.Hit<IOrderProjection>> hits = response.hits().hits();
        List<IOrderProjection> data = hits.stream()
                .map(Hit::source)
                .toList();
        String nextCursor = hits.isEmpty() ? null : hits.get(hits.size() - 1).id();
        return ScrollResult.of(data, nextCursor);
    }

    private Query buildConditionQuery(OrderPageQuery condition) {
        List<Query> must = new ArrayList<>();
        if (condition instanceof OrderPageQuery.ByConditions c) {
            c.orderId().ifPresent(v -> must.add(term("orderId", v)));
            c.payStatus().ifPresent(v -> must.add(term("status", v)));
            c.totalAmount().ifPresent(v -> must.add(term("totalAmount", v)));
            c.customerId().ifPresent(v -> must.add(term("customer.customerId", v)));
            c.productName().ifPresent(v -> must.add(match("itemProductNamesText", v)));
        }
        BoolQuery bool = BoolQuery.of(b -> b.must(must));
        return Query.of(q -> q.bool(bool));
    }

    private Query term(String field, Object value) {
        if (value instanceof Long l) {
            return Query.of(q -> q.term(TermQuery.of(t -> t.field(field).value(FieldValue.of(l)))));
        }
        if (value instanceof Integer i) {
            return Query.of(q -> q.term(TermQuery.of(t -> t.field(field).value(FieldValue.of(i.longValue())))));
        }
        if (value instanceof String s) {
            return Query.of(q -> q.term(TermQuery.of(t -> t.field(field).value(FieldValue.of(s)))));
        }
        return Query.of(q -> q.term(TermQuery.of(t -> t.field(field).value(FieldValue.of(value.toString())))));
    }

    private Query match(String field, String value) {
        return Query.of(q -> q.match(MatchQuery.of(m -> m.field(field).query(value))));
    }
}
