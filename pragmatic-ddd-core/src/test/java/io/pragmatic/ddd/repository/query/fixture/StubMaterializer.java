package io.pragmatic.ddd.repository.query.fixture;

import io.pragmatic.ddd.repository.query.IProjectionMaterializer;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 投影物化器测试桩：记录最近一次 materialize / purge 调用，供断言验证。
 */
public final class StubMaterializer implements IProjectionMaterializer<StubProjection> {

    private final ReconciliationTarget target;

    public final AtomicReference<StubProjection> lastProjection = new AtomicReference<>();

    public final AtomicReference<Long> lastVersion = new AtomicReference<>();

    public final AtomicReference<Object> lastPurgedId = new AtomicReference<>();

    public StubMaterializer(ReconciliationTarget target) {
        this.target = target;
    }

    @Override
    public Class<StubProjection> projectionType() {
        return StubProjection.class;
    }

    @Override
    public ReconciliationTarget target() {
        return target;
    }

    @Override
    public void materialize(StubProjection projection, long version) {
        lastProjection.set(projection);
        lastVersion.set(version);
    }

    @Override
    public void purge(Object aggregateId) {
        lastPurgedId.set(aggregateId);
    }
}
