package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderESSource;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderListQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderOneQuery;
import io.pragmatic.ddd.example.order.domain.order.projection.query.OrderPageQuery;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.projector.OrderEsProjector;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer.OrderSummaryReducer;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.searcher.OrderEsConditionFactory;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.exception.ProjectionExceptions;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import lombok.SneakyThrows;
import org.elasticsearch.client.ResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 订单 ES 投影源：以「源」为中心聚合写（project → index）、读（四族查询能力）与对账（版本读取 / 自我重建）。
 * 实现领域层端口 {@link IOrderESSource}，写读对账共享同一份 ES 索引副本地址。
 *
 * <p>原分散在 OrderByIdSearcher / OrderOneSearcher / OrderListSearcher / OrderPageSearcher
 * 的检索逻辑集中于此；条件构建继续复用 {@link OrderEsConditionFactory}。
 * 子投影由构造注入的 {@link OrderSummaryReducer} 裁剪，经基类 {@code getReducer} 取用。
 * 对账能力（原 OrderEsVersionResolver / OrderEsResynchronizer）亦收敛于本类：源自身即副本。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderEsSource extends AbstractProjectionSource<Order, Long, OrderEsProjection>
        implements IOrderESSource {

    private static final Logger log = LoggerFactory.getLogger(OrderEsSource.class);

    private final ElasticsearchClient elasticsearchClient;

    private final OrderRepository orderRepository;

    public OrderEsSource(
            OrderEsProjector projector,
            OrderSummaryReducer summaryReducer,
            ElasticsearchClient elasticsearchClient,
            OrderRepository orderRepository) {
        super(ProjectionSource.of(OrderEsTargets.REPLICA_ID),
                Order.class, OrderEsProjection.class, projector, List.of(summaryReducer));
        this.elasticsearchClient = elasticsearchClient;
        this.orderRepository = orderRepository;
    }

    @Override
    public void materialize(IAggregateProjection projection, long version) {
        OrderEsProjection es = (OrderEsProjection) projection;
        try {
            elasticsearchClient.index(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .id(es.getOrderId().toString())
                    .versionType(VersionType.External)
                    .version(version)
                    .document(es));
        } catch (ResponseException ex) {
            // external 版本不前进（迟到/重复事件）时 ES 返回 409，按乐观锁语义静默丢弃。
            log.debug("订单 ES 投影物化被版本冲突忽略，orderId={}, version={}", es.getOrderId(), version);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void purge(Object aggregateId) {
        try {
            elasticsearchClient.delete(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .id(aggregateId.toString()));
        } catch (ResponseException ignored) {
            // 文档可能不存在，清理时忽略删除异常。
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * 读取订单在 ES 中的文档 _version 作为读模型副本版本 V'。
     * 文档不存在（副本缺失但 ES 可达）时返回 0，使对账判为 STALE 并由 rebuild 自动回填；
     * 文档真实存在时返回其 _version。ES 不可达时由底层 IOException 经 @SneakyThrows 抛出。
     *
     * @param aggregateId 订单聚合标识
     * @return ES 文档版本；副本缺失时返回 0
     */
    @Override
    @SneakyThrows
    public long readVersion(Long aggregateId) {
        co.elastic.clients.elasticsearch.core.GetResponse<Map> response =
                elasticsearchClient.get(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                        .id(aggregateId.toString()), Map.class);
        if (!response.found()) {
            return 0L;
        }
        return Optional.ofNullable(response.version()).orElse(0L);
    }

    /**
     * 从写模型当前快照重建本副本：load 聚合后经 {@link #sync} 物化。
     *
     * @param aggregateId 订单聚合标识
     */
    @Override
    public void rebuild(Long aggregateId) {
        Order order = orderRepository.findById(aggregateId);
        if (order == null) {
            return;
        }
        sync(order);
    }

    @Override
    public OrderEsProjection getById(Object id) {
        return ProjectionExceptions.retrieve(() -> doGetById(id.toString()), "getById");
    }

    @Override
    public List<OrderEsProjection> getByIds(List<Object> ids) {
        return ProjectionExceptions.retrieve(() -> doGetByIds(ids), "getByIds");
    }

    @Override
    public List<OrderEsProjection> search(OrderOneQuery criteria) {
        return ProjectionExceptions.retrieve(() -> {
            if (criteria instanceof OrderOneQuery.LatestByCustomer c) {
                return searchLatestByCustomer(c);
            }
            return List.<OrderEsProjection>of();
        }, "search");
    }

    @Override
    public List<OrderEsProjection> search(OrderListQuery criteria) {
        return ProjectionExceptions.retrieve(() -> {
            if (criteria instanceof OrderListQuery.TopByAmount c) {
                return searchTopByAmount(c);
            }
            if (criteria instanceof OrderListQuery.TopRecent c) {
                return searchTopRecent(c);
            }
            return List.<OrderEsProjection>of();
        }, "search");
    }

    @Override
    public PageResult<OrderEsProjection> searchPage(OrderPageQuery criteria, PageRequest pageRequest) {
        return ProjectionExceptions.retrieve(() -> doSearchPage(criteria, pageRequest), "searchPage");
    }

    @Override
    public ScrollResult<OrderEsProjection> searchScroll(
            OrderPageQuery criteria, ScrollPosition cursor, int pageSize) {
        return ProjectionExceptions.retrieve(() -> doSearchScroll(criteria, cursor, pageSize), "searchScroll");
    }

    @SneakyThrows
    private OrderEsProjection doGetById(String id) {
        return elasticsearchClient.get(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .id(id), OrderEsProjection.class).source();
    }

    @SneakyThrows
    private List<OrderEsProjection> doGetByIds(List<Object> ids) {
        List<String> docIds = ids.stream()
                .map(Object::toString)
                .toList();
        IdsQuery idsQuery = IdsQuery.of(q -> q.values(docIds));
        Query query = Query.of(q -> q.ids(idsQuery));
        return elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .size(docIds.size()), OrderEsProjection.class).hits().hits().stream()
                .map(Hit::source)
                .toList();
    }

    @SneakyThrows
    private List<OrderEsProjection> searchLatestByCustomer(OrderOneQuery.LatestByCustomer condition) {
        TermQuery term = TermQuery.of(t -> t.field("customer.customerId").value(condition.customerId()));
        Query query = Query.of(q -> q.term(term));
        return elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .sort(sort -> sort.field(f -> f.field("createdAt").order(SortOrder.Desc)))
                .size(1), OrderEsProjection.class).hits().hits().stream()
                .map(Hit::source)
                .toList();
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
                t.field("customer.customerId").value(customerId)))));
        must.add(Query.of(q -> q.term(TermQuery.of(t ->
                t.field("status").value(status)))));
        BoolQuery bool = BoolQuery.of(b -> b.must(must));
        return Query.of(q -> q.bool(bool));
    }

    @SneakyThrows
    private PageResult<OrderEsProjection> doSearchPage(OrderPageQuery condition, PageRequest pageRequest) {
        Query query = buildConditionQuery(condition);
        SearchResponse<OrderEsProjection> response = elasticsearchClient.search(req -> req
                .index(OrderEsTargets.ORDER_INDEX_NAME)
                .query(query)
                .sort(defaultSort())
                .from(pageRequest.offset())
                .size(pageRequest.pageSize())
                .trackTotalHits(t -> t.enabled(true)), OrderEsProjection.class);
        List<OrderEsProjection> data = response.hits().hits().stream()
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
    private ScrollResult<OrderEsProjection> doSearchScroll(
            OrderPageQuery condition, ScrollPosition cursor, int pageSize) {
        Query query = buildConditionQuery(condition);
        SearchResponse<OrderEsProjection> response = elasticsearchClient.search(req -> {
            var b = req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .query(query)
                    .sort(defaultSort())
                    .size(pageSize);
            if (!cursor.isInitial()) {
                b.searchAfter(cursor.cursor());
            }
            return b;
        }, OrderEsProjection.class);
        List<Hit<OrderEsProjection>> hits = response.hits().hits();
        List<OrderEsProjection> data = hits.stream()
                .map(Hit::source)
                .toList();
        String nextCursor = hits.isEmpty() ? null : hits.get(hits.size() - 1).id();
        return ScrollResult.of(data, nextCursor);
    }

    private Query buildConditionQuery(OrderPageQuery condition) {
        if (condition instanceof OrderPageQuery.ByConditions c) {
            return OrderEsConditionFactory.build(c);
        }
        return Query.of(q -> q.bool(BoolQuery.of(b -> b)));
    }

    private List<SortOptions> defaultSort() {
        return List.of(
                SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))),
                SortOptions.of(s -> s.field(f -> f.field("orderId").order(SortOrder.Desc))));
    }
}
