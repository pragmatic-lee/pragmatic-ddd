package io.pragmatic.ddd.example.order.infrastructure.config.reconciliation;

import io.pragmatic.ddd.repository.ReplicaKey;
import io.pragmatic.ddd.repository.reconciliation.IReconcileDedup;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内时间窗去重：在窗口时间内对同一 (replicaId, aggregateId) 仅处理一次。
 * 与具体聚合无关，作为对账默认去重实现；多实例部署请改用 Redis 时间窗去重实现。
 *
 * @author wizard-lee
 */
public class InMemoryTimeWindowReconcileDedup implements IReconcileDedup {

    private final long windowMillis;

    private final ConcurrentHashMap<String, Long> marks = new ConcurrentHashMap<>();

    public InMemoryTimeWindowReconcileDedup(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    @Override
    public boolean shouldSkip(ReplicaKey key, Object aggregateId) {
        String markKey = keyOf(key, aggregateId);
        Long last = marks.get(markKey);
        if (last == null) {
            return false;
        }
        if (System.currentTimeMillis() - last > windowMillis) {
            marks.remove(markKey);
            return false;
        }
        return true;
    }

    @Override
    public void mark(ReplicaKey key, Object aggregateId) {
        marks.put(keyOf(key, aggregateId), System.currentTimeMillis());
    }

    private String keyOf(ReplicaKey key, Object aggregateId) {
        return key.replicaId() + ":" + aggregateId;
    }
}
