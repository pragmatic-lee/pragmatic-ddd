package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import com.alibaba.fastjson2.JSON;
import io.lettuce.core.api.sync.RedisCommands;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.domain.order.projection.materializer.IOrderReadModelVersionResolver;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存副本版本解析器：版本内嵌于投影 JSON，读取后解析其 version 字段。
 * 缺失或异常返回 -1，交由 ReconciliationRegistry 判定为需 resync。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisVersionResolver implements IOrderReadModelVersionResolver {

    private final RedisCommands<String, String> redis;

    public OrderRedisVersionResolver(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget supportedTarget() {
        return OrderCacheTargets.TARGET_REDIS_ORDERS;
    }

    @Override
    public long resolve(Long aggregateId) {
        String json = redis.get(OrderCacheTargets.ORDER_CACHE_KEY_PREFIX + aggregateId);
        if (json == null) {
            return -1L;
        }
        try {
            Long version = JSON.parseObject(json, OrderCacheProjection.class).getVersion();
            return version == null ? -1L : version;
        } catch (RuntimeException ignored) {
            return -1L;
        }
    }
}
