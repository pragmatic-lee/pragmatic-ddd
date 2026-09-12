package io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.replica;

import com.alibaba.fastjson2.JSON;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import io.pragmatic.ddd.example.order.domain.order.model.Order;
import io.pragmatic.ddd.example.order.domain.order.projection.IOrderRedisSource;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheProjection;
import io.pragmatic.ddd.example.order.domain.order.projection.OrderCacheTargets;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.projector.OrderCacheProjector;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.projection.reducer.OrderCacheSummaryReducer;
import io.pragmatic.ddd.example.order.infrastructure.persistent.order.repository.OrderRepository;
import io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 订单 Redis 投影源：以「源」为中心聚合写（project → 缓存）、读（按主键）与对账（版本读取 / 自我重建）。
 * 实现领域层端口 {@link IOrderRedisSource}（仅 ById 族）。
 *
 * <p>写读对账一体——{@code materialize} 写缓存键、{@code getById} 读同一批键。
 * 原 OrderRedisByIdSearcher 的检索逻辑集中于此；子投影由构造注入的
 * {@link OrderCacheSummaryReducer} 裁剪，经基类 {@code getReducer} 取用。
 * 对账能力（原 OrderRedisVersionResolver / OrderRedisResynchronizer）亦收敛于本类：源自身即副本。</p>
 *
 * @author wizard-lee
 */
@Component
public class OrderRedisSource extends AbstractProjectionSource<Order, Long, OrderCacheProjection>
        implements IOrderRedisSource {

    private final RedisCommands<String, String> redis;
    private final long ttlSeconds;
    private final OrderRepository orderRepository;

    public OrderRedisSource(
            OrderCacheProjector projector,
            OrderCacheSummaryReducer summaryReducer,
            RedisCommands<String, String> redis,
            OrderRepository orderRepository,
            @Value("${order.cache.redis.ttl:0}") long ttlSeconds) {
        super(ProjectionSource.of(OrderCacheTargets.REPLICA_ID),
                Order.class, OrderCacheProjection.class, projector, List.of(summaryReducer));
        this.redis = redis;
        this.orderRepository = orderRepository;
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

    /**
     * 读取副本版本 V'：版本内嵌于投影 JSON，读取后解析其 version 字段。
     * key 缺失或 JSON 损坏（副本缺失/损坏但 Redis 可达）时返回 0，使对账判为 STALE 并由 rebuild 自动重建。
     *
     * @param aggregateId 订单聚合标识
     * @return 副本内嵌版本；副本缺失或损坏时返回 0
     */
    @Override
    public long readVersion(Long aggregateId) {
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
