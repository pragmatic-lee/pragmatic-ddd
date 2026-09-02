package io.pragmatic.ddd.example.order.infrastructure.order.projection.materializer;

import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.SetArgs;
import com.alibaba.fastjson2.JSON;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.OrderCacheProjector;
import io.pragmatic.ddd.example.order.infrastructure.order.projection.reducer.OrderCacheSummaryReducer;
import io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 订单 Redis 投影源：以「源」为中心聚合写（project → 缓存）与读（概要裁剪器）。
 * 仅持有概要裁剪器，按 id 直取由 {@code materialize} 写入的缓存键承载。
 * 寻址串 redis:orders 由源标识承载，写读共享同一份缓存地址。
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisSource extends AbstractProjectionSource<Order, OrderCacheProjection> {

    private final RedisCommands<String, String> redis;
    private final long ttlSeconds;

    public OrderRedisSource(
            OrderCacheProjector projector,
            OrderCacheSummaryReducer summaryReducer,
            RedisCommands<String, String> redis,
            @Value("${order.cache.redis.ttl:0}") long ttlSeconds) {
        super(ProjectionSource.of(OrderCacheTargets.TARGET_REDIS_ORDERS.storeId()),
                Order.class, OrderCacheProjection.class, projector, null);
        bind(summaryReducer);
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public void materialize(IAggregateProjection projection, long version) {
        OrderCacheProjection cache = (OrderCacheProjection) projection;
        String key = OrderCacheTargets.ORDER_CACHE_KEY_PREFIX + cache.getOrderId();
        String existing = redis.get(key);
        if (existing != null) {
            Long current = JSON.parseObject(existing, OrderCacheProjection.class).getVersion();
            if (current != null && current >= version) {
                return;
            }
        }
        String json = JSON.toJSONString(cache);
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
