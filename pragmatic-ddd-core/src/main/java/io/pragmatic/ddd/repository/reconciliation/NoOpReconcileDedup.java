package io.pragmatic.ddd.repository.reconciliation;

/** 不去重的默认实现（每次都应处理），供 ReconciliationManager 默认装配。 */
public final class NoOpReconcileDedup implements IReconcileDedup {
    public static final NoOpReconcileDedup INSTANCE = new NoOpReconcileDedup();

    private NoOpReconcileDedup() {}

    @Override public boolean shouldSkip(ReconciliationTarget target, Object aggregateId) { return false; }
    @Override public void mark(ReconciliationTarget target, Object aggregateId) { /* no-op */ }
}
