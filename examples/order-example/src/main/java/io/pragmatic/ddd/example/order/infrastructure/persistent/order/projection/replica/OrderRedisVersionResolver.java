package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica;

import com.alibaba.fastjson2.JSON;
import io.lettuce.core.api.sync.RedisCommands;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.repository.reconciliation.IReadModelVersionResolver;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存副本版本解析器：版本内嵌于投影 JSON，读取后解析其 version 字段。
 * key 缺失或 JSON 损坏（副本缺失/损坏但 Redis 可达）时返回 0，使对账判为 STALE 并由
 * resync 自动重建；Redis 不可达时由 Lettuce 抛出连接异常，交由对账调用方按条目捕获处理。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisVersionResolver implements IReadModelVersionResolver<Long> {

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
            return 0L;
        }
        try {
            Long version = JSON.parseObject(json, OrderCacheProjection.class).getVersion();
            return version == null ? 0L : version;
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }
}
