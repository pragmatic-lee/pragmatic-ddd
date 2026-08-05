package io.pragmatic.ddd.repository.query.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.query.AbstractAggregateProjector;

/**
 * 把 StubAggregate 投影为 StubProjection 的测试投影器。
 */
public final class StubProjector extends AbstractAggregateProjector<StubAggregate, StubProjection> {

    public StubProjector() {
        super(StubProjection.class);
    }

    @Override
    public StubProjection project(StubAggregate aggregateRoot) {
        if (aggregateRoot == null) {
            return null;
        }
        return new StubProjection(aggregateRoot.getEntityId(), aggregateRoot.name());
    }
}
