package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderEsTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.materializer.IOrderReadModelVersionResolver;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 读取 ES 文档 _version 元数据作为订单读模型副本版本 V'。
 *
 * @author wizard-lee
 */
@Component
public class OrderEsVersionResolver implements IOrderReadModelVersionResolver {
    private final ElasticsearchClient elasticsearchClient;
    public OrderEsVersionResolver(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 返回本解析器支持的订单 ES 对账目标。
     *
     * @return 订单 ES 读模型对账目标
     */
    @Override
    public ReconciliationTarget supportedTarget() {
        return OrderEsTargets.TARGET_ES_ORDERS;
    }

    /**
     * 读取订单在 ES 中的文档 _version 作为读模型副本版本 V'。
     * 文档不存在或发生异常时返回 -1，表示副本缺失或不可达。
     *
     * @param aggregateId 订单聚合标识
     * @return ES 文档版本；缺失或异常时返回 -1
     */
    @Override
    @SneakyThrows
    public long resolve(Long aggregateId) {

        co.elastic.clients.elasticsearch.core.GetResponse<Map> response =
                elasticsearchClient.get(req -> req.index(OrderEsTargets.ORDER_INDEX_NAME).id(aggregateId.toString()),
                        java.util.Map.class);
        if (!response.found()) {
            return -1L;
        }
        return Optional.ofNullable(response.version()).orElse(-1L);

    }
}
