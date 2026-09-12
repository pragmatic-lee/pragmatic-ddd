package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.ReplicaKey;

/**
 * 不去重的默认实现（每次都应处理），供 ReconciliationManager 默认装配。
 *
 * @author wizard-lee
 */
public final class NoOpReconcileDedup implements IReconcileDedup {
    public static final NoOpReconcileDedup INSTANCE = new NoOpReconcileDedup();

    private NoOpReconcileDedup() {}

    @Override public boolean shouldSkip(ReplicaKey key, Object aggregateId) { return false; }
    @Override public void mark(ReplicaKey key, Object aggregateId) { /* no-op */ }
}
