package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.VersionType;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.materializer.IOrderProjectionMaterializer;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 将订单 ES 投影物化（upsert）到 Elasticsearch，并以 _version 元数据承载副本版本。
 *
 * @author wizard-lee
 */
@Component
public class OrderEsMaterializer implements IOrderProjectionMaterializer {

    private static final Logger log = LoggerFactory.getLogger(OrderEsMaterializer.class);

    private final ElasticsearchClient elasticsearchClient;

    public OrderEsMaterializer(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 返回本物化器处理的投影类型。
     *
     * @return 订单 ES 投影类型
     */
    @Override
    public Class<OrderEsProjection> projectionType() {
        return OrderEsProjection.class;
    }

    /**
     * 返回本物化器归属的订单 ES 对账目标。
     *
     * @return 订单 ES 读模型对账目标
     */
    @Override
    public io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget target() {
        return OrderEsTargets.TARGET_ES_ORDERS;
    }

    /**
     * 将订单投影 upsert 到 ES，并以传入副本版本写入 _version 元数据。
     *
     * @param projection 订单 ES 投影
     * @param version    写模型的副本版本，用于 ES 乐观并发控制
     */
    @Override
    public void materialize(OrderEsProjection projection, long version) {
        try {
            elasticsearchClient.index(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                    .id(projection.getOrderId().toString())
                    .versionType(VersionType.External)
                    .version(version)
                    .document(projection));
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("订单 ES 物化版本冲突或写入失败 orderId={} version={}",
                    projection.getOrderId(), version, ex);
        }
    }

    /**
     * 删除订单在 ES 中的投影文档，文档不存在时静默忽略。
     *
     * @param aggregateId 订单聚合标识
     */
    @Override
    @SneakyThrows
    public void purge(Object aggregateId) {
        elasticsearchClient.delete(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME)
                .id(aggregateId.toString()));
    }
}
