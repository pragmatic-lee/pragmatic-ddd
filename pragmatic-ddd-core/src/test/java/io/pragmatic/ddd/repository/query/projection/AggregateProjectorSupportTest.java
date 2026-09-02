package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.projection.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjection;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjector;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 AggregateProjectorSupport 编排 projector 注册、同步与物化（以「源」为中心）。
 *
 * @author wizard-lee
 */
class AggregateProjectorSupportTest {

    private static final ProjectionSource STUB_SOURCE = ProjectionSource.of("es:stub");

    /** 记录 materialize / purge 入参的内存源，覆盖门面编排断言。 */
    private static final class RecordingSource extends AbstractProjectionSource<StubAggregate, StubProjection> {

        final AtomicReference<StubProjection> lastProjection = new AtomicReference<>();
        final AtomicReference<Object> lastPurgedId = new AtomicReference<>();

        RecordingSource() {
            super(STUB_SOURCE, StubAggregate.class, StubProjection.class, new ReturningProjector(), null);
        }

        RecordingSource(IAggregateProjector<StubAggregate, StubProjection> projector) {
            super(STUB_SOURCE, StubAggregate.class, StubProjection.class, projector, null);
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
            lastProjection.set((StubProjection) projection);
        }

        @Override
        public void purge(Object aggregateId) {
            lastPurgedId.set(aggregateId);
        }
    }

    /** 返回真实投影（聚合 name 透传）的投影器，供默认 RecordingSource 验证 materialize 链路。 */
    private static final class ReturningProjector extends AbstractAggregateProjector<StubAggregate, StubProjection> {

        private ReturningProjector() {
            super(StubProjection.class);
        }

        @Override
        public StubProjection project(StubAggregate aggregateRoot) {
            return new StubProjection(1L, aggregateRoot.name());
        }
    }

    private ProjectorRegistry newRegisteredRegistry() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new RecordingSource());
        return registry;
    }

    @Test
    void sync_projectsAndMaterializes() {
        ProjectorRegistry registry = newRegisteredRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        StubAggregate aggregate = new StubAggregate();
        aggregate.setName("v");

        support.sync(aggregate, STUB_SOURCE);

        RecordingSource source = (RecordingSource) registry.getSource(STUB_SOURCE);
        assertThat(source.lastProjection.get()).isNotNull();
        assertThat(source.lastProjection.get().name()).isEqualTo("v");
    }

    @Test
    void sync_missingProjector_skipsSilently() {
        ProjectorRegistry registry = new ProjectorRegistry();
        // projector 返回 null：sync 不应抛异常，且 materialize 未被调用
        registry.register(new RecordingSource(new StubProjector<>(StubProjection.class)));
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        StubAggregate aggregate = new StubAggregate();

        support.sync(aggregate, STUB_SOURCE);

        RecordingSource source = (RecordingSource) registry.getSource(STUB_SOURCE);
        assertThat(source.lastProjection.get()).isNull();
    }

    @Test
    void sync_byTarget_bridgesToSource() {
        ProjectorRegistry registry = newRegisteredRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        StubAggregate aggregate = new StubAggregate();
        aggregate.setName("t");
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:stub");

        support.sync(aggregate, target);

        RecordingSource source = (RecordingSource) registry.getSource(STUB_SOURCE);
        assertThat(source.lastProjection.get()).isNotNull();
    }

    @Test
    void purge_delegatesToSource() {
        ProjectorRegistry registry = newRegisteredRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);

        support.purge(STUB_SOURCE, 99L);

        RecordingSource source = (RecordingSource) registry.getSource(STUB_SOURCE);
        assertThat(source.lastPurgedId.get()).isEqualTo(99L);
    }

    @Test
    void purge_byTarget_bridgesToSource() {
        ProjectorRegistry registry = newRegisteredRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:stub");

        support.purge(target, 99L);

        RecordingSource source = (RecordingSource) registry.getSource(STUB_SOURCE);
        assertThat(source.lastPurgedId.get()).isEqualTo(99L);
    }
}
