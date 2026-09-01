package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import com.alibaba.fastjson2.JSON;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.SetArgs;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.repository.query.IProjectionMaterializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis 缓存副本物化器：将缓存投影序列化为 JSON 写入 Redis。
 * 定位为「事件实时物化、必然存在」的读副本，默认永久留存（不依赖 TTL 驱逐）；
 * 仅当 {@code order.cache.redis.ttl > 0} 时启用长 TTL 兜底。版本内嵌于投影 JSON，
 * 不另设并列 key；并发消费下以版本跳过保护避免旧事件覆盖新缓存。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisMaterializer implements IProjectionMaterializer<OrderCacheProjection> {

    private final RedisCommands<String, String> redis;

    private final long ttlSeconds;

    public OrderRedisMaterializer(
            RedisCommands<String, String> redis,
            @Value("${order.cache.redis.ttl:0}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public Class<OrderCacheProjection> projectionType() {
        return OrderCacheProjection.class;
    }

    @Override
    public io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget target() {
        return OrderCacheTargets.TARGET_REDIS_ORDERS;
    }

    @Override
    public void materialize(OrderCacheProjection projection, long version) {
        String key = OrderCacheTargets.ORDER_CACHE_KEY_PREFIX + projection.getOrderId();
        String existing = redis.get(key);
        if (existing != null) {
            Long current = JSON.parseObject(existing, OrderCacheProjection.class).getVersion();
            if (current != null && current >= version) {
                return;
            }
        }
        String json = JSON.toJSONString(projection);
        if (ttlSeconds > 0) {
            redis.set(key, json, SetArgs.Builder.ex(ttlSeconds));
        } else {
            redis.set(key, json);
        }
    }

    @Override
    public void purge(Object aggregateId) {
        redis.del(OrderCacheTargets.ORDER_CACHE_KEY_PREFIX + aggregateId);
    }
}
