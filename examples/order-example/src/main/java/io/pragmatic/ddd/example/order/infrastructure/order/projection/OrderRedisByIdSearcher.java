package io.pragmatic.ddd.example.order.infrastructure.order.projection;

import com.alibaba.fastjson2.JSON;
import io.lettuce.core.api.sync.RedisCommands;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Redis 缓存副本按 ID 检索实现：基于 Lettuce 同步命令读取缓存投影。
 * 不回源——命中返回，未命中返回 null，不查 ES / DB / 仓储；一致性由对账补偿自愈。
 *
 * @author wizard-lee
 */
public class OrderRedisByIdSearcher implements IProjectionByIdSearcher<OrderCacheProjection> {

    private final RedisCommands<String, String> redis;

    public OrderRedisByIdSearcher(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public OrderCacheProjection getById(Object id) {
        String json = redis.get(OrderCacheTargets.ORDER_CACHE_KEY_PREFIX + id);
        if (json == null) {
            return null;
        }
        return JSON.parseObject(json, OrderCacheProjection.class);
    }

    @Override
    public List<OrderCacheProjection> getByIds(List<Object> ids) {
        return ids.stream()
                .map(this::getById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
