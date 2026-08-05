package io.pragmatic.ddd.repository.reconciliation.fixture;

import io.pragmatic.ddd.repository.reconciliation.IReadModelResynchronizer;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 补同步器桩：记录最近一次 resync / purge 的聚合 id，供断言验证。
 */
public final class StubResynchronizer implements IReadModelResynchronizer<Long> {

    private final ReconciliationTarget target;

    public final AtomicReference<Long> lastResyncedId = new AtomicReference<>();

    public final AtomicReference<Long> lastPurgedId = new AtomicReference<>();

    public StubResynchronizer(ReconciliationTarget target) {
        this.target = target;
    }

    @Override
    public void resync(Long aggregateId) {
        lastResyncedId.set(aggregateId);
    }

    @Override
    public void purge(Long aggregateId) {
        lastPurgedId.set(aggregateId);
    }

    @Override
    public ReconciliationTarget supportedTarget() {
        return target;
    }
}
