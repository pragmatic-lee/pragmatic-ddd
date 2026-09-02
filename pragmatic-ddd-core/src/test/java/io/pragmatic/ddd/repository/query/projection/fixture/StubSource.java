package io.pragmatic.ddd.repository.query.projection.fixture;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.query.projection.AbstractProjectionSource;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjector;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionReducer;
import io.pragmatic.ddd.repository.query.projection.IProjectionSearcher;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;

/**
 * 内存源适配器测试桩：模拟一份物理副本，可链式挂载检索器 / 裁剪器，便于按「源」为中心的 API 测试。
 * 仅用于测试，materialize / purge 为空实现。
 */
public final class StubSource extends AbstractProjectionSource<StubAggregate, StubProjection> {

    public StubSource(ProjectionSource source) {
        this(source, new StubProjector(), null);
    }

    public StubSource(
            ProjectionSource source,
            IAggregateProjector<StubAggregate, StubProjection> projector,
            IProjectionByIdSearcher<StubProjection> idSearcher) {
        super(source, StubAggregate.class, StubProjection.class, projector, idSearcher);
    }

    /** 链式挂载按条件检索器。 */
    public StubSource with(IProjectionSearcher<?, StubProjection> searcher) {
        bind(searcher);
        return this;
    }

    /** 链式挂载分页检索器。 */
    public StubSource with(IProjectionPagedSearcher<?, StubProjection> searcher) {
        bind(searcher);
        return this;
    }

    /** 链式挂载裁剪器。 */
    public <S extends IAggregateProjection> StubSource with(IProjectionReducer<S, StubProjection> reducer) {
        bind(reducer);
        return this;
    }

    @Override
    public void materialize(IAggregateProjection projection, long version) {
    }

    @Override
    public void purge(Object aggregateId) {
    }
}
