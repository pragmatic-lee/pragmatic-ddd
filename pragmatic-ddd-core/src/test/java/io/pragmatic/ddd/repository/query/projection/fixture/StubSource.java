package io.pragmatic.ddd.repository.query.projection.fixture;

import io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjector;
import io.pragmatic.ddd.repository.query.projection.IReducer;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;

import java.util.List;

/**
 * 内存源适配器测试桩：模拟一份物理副本，可构造注入裁剪器。
 * 仅用于测试，materialize / purge / readVersion / rebuild 为空实现或返回 0。
 *
 * @author wizard-lee
 */
public final class StubSource extends AbstractProjectionSource<StubAggregate, Long, StubProjection> {

    public StubSource(ProjectionSource source) {
        this(source, new StubProjector(), List.of());
    }

    public StubSource(
            ProjectionSource source,
            IAggregateProjector<StubAggregate, StubProjection> projector,
            List<IReducer<StubProjection, ?>> reducers) {
        super(source, StubAggregate.class, StubProjection.class, projector, reducers);
    }

    @Override
    public void materialize(IAggregateProjection projection, long version) {
    }

    @Override
    public void purge(Object aggregateId) {
    }

    @Override
    public long readVersion(Long aggregateId) {
        return 0L;
    }

    @Override
    public void rebuild(Long aggregateId) {
    }
}
