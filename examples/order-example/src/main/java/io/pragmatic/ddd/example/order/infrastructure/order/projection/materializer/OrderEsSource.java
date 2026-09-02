package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import java.io.IOException;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderByIdSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderListSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderOneSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderPageSearcher;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderEsProjector;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.reducer.OrderSummaryReducer;
import io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import org.elasticsearch.client.ResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单 ES 投影源：以「源」为中心聚合写（project → index）与读（检索器 + 概要裁剪器）。
 * 寻址串 es:orders 由源标识承载，写读共享同一份 ES 索引副本地址。
 *
 * @author wizard-lee
 */
@Component
public class OrderEsSource extends AbstractProjectionSource<Order, OrderEsProjection> {

    private static final Logger log = LoggerFactory.getLogger(OrderEsSource.class);

    private final ElasticsearchClient elasticsearchClient;

    public OrderEsSource(
            OrderEsProjector projector,
            OrderByIdSearcher byIdSearcher,
            OrderOneSearcher oneSearcher,
            OrderListSearcher listSearcher,
            OrderPageSearcher pageSearcher,
            OrderSummaryReducer summaryReducer,
            ElasticsearchClient elasticsearchClient) {
        super(ProjectionSource.of(OrderEsTargets.TARGET_ES_ORDERS.storeId()),
                Order.class, OrderEsProjection.class, projector, byIdSearcher);
        bind(oneSearcher);
        bind(listSearcher);
        bind(pageSearcher);
        bind(summaryReducer);
        this.elasticsearchClient = elasticsearchClient;
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
}
