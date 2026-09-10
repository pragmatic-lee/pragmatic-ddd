package io.pragmatic.ddd.example.order.infrastructure.config.reconciliation;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import io.pragmatic.ddd.repository.reconciliation.IReconcileDedup;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;

/**
 * 基于 Redis 的时间窗去重：与具体聚合无关，多实例部署安全。以 SET key value NX EX 记录
 * 最近处理时间，窗口内重复对账直接跳过。key 格式 reconcile:dedup:{storeId}:{aggregateId}。
 *
 * @author wizard-lee
 */
public class RedisTimeWindowReconcileDedup implements IReconcileDedup {

    private static final String KEY_PREFIX = "reconcile:dedup:";

    private final RedisCommands<String, String> redis;

    private final long windowSeconds;

    public RedisTimeWindowReconcileDedup(RedisCommands<String, String> redis, long windowSeconds) {
        this.redis = redis;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public boolean shouldSkip(ReconciliationTarget target, Object aggregateId) {
        return redis.get(keyOf(target, aggregateId)) != null;
    }

    @Override
    public void mark(ReconciliationTarget target, Object aggregateId) {
        redis.set(keyOf(target, aggregateId), "1", SetArgs.Builder.nx().ex(windowSeconds));
    }

    private String keyOf(ReconciliationTarget target, Object aggregateId) {
        return KEY_PREFIX + target.storeId() + ":" + aggregateId;
    }
}
