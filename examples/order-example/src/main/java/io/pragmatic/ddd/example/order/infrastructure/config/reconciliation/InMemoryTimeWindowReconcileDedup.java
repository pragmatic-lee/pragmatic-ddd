package io.pragmatic.ddd.example.order.infrastructure.config.reconciliation;

import io.pragmatic.ddd.repository.reconciliation.IReconcileDedup;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内时间窗去重：在窗口时间内对同一 (storeId, aggregateId) 仅处理一次。
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
    public boolean shouldSkip(ReconciliationTarget target, Object aggregateId) {
        String key = keyOf(target, aggregateId);
        Long last = marks.get(key);
        if (last == null) {
            return false;
        }
        if (System.currentTimeMillis() - last > windowMillis) {
            marks.remove(key);
            return false;
        }
        return true;
    }

    @Override
    public void mark(ReconciliationTarget target, Object aggregateId) {
        marks.put(keyOf(target, aggregateId), System.currentTimeMillis());
    }

    private String keyOf(ReconciliationTarget target, Object aggregateId) {
        return target.storeId() + ":" + aggregateId;
    }
}
