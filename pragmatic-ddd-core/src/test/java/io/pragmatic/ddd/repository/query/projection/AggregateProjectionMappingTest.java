package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceConflictException;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjector;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 聚合投影映射落地测试：覆盖 ProjectorRegistry 以「源」为中心的登记/解析，
 * 以及 AbstractProjectionSource 以「源」为中心的同步（sync）/清除（purge）编排。
 * @author wizard-lee
 */
class AggregateProjectionMappingTest {

    /** 投影实现：仅为测试载体。 */
    static class SampleProjection implements IAggregateProjection {
    }

    /** 内存 projector：记录被调用与产出。 */
    static class SampleProjector extends AbstractAggregateProjector<SampleAggregate, SampleProjection> {
        final boolean returnNull;

        SampleProjector(boolean returnNull) {
            super(SampleProjection.class);
            this.returnNull = returnNull;
        }

        @Override
        public SampleProjection project(SampleAggregate aggregateRoot) {
            return returnNull ? null : new SampleProjection();
        }
    }

    /**
     * 内存源适配器：模拟一份物理副本（如 ES 一个索引），记录 materialize / purge 调用与入参。
     * 源自身即副本，副本标识即源 id。
     */
    static class SampleSource extends AbstractProjectionSource<SampleAggregate, Long, SampleProjection> {
        static final ProjectionSource ES_SOURCE = ProjectionSource.of("es:orders");

        final AtomicReference<Long> materializedVersion = new AtomicReference<>();
        final AtomicReference<Object> purgedId = new AtomicReference<>();
        final AtomicInteger materializeCount = new AtomicInteger();
        final AtomicInteger purgeCount = new AtomicInteger();

        SampleSource(ProjectionSource source) {
            super(source, SampleAggregate.class, SampleProjection.class,
                    new SampleProjector(false), List.of());
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
            materializedVersion.set(version);
            materializeCount.incrementAndGet();
        }

        @Override
        public void purge(Object aggregateId) {
            purgedId.set(aggregateId);
            purgeCount.incrementAndGet();
        }

        @Override
        public long readVersion(Long aggregateId) {
            return 0L;
        }

        @Override
        public void rebuild(Long aggregateId) {
        }
    }

    @Test
    void registry_register_source_resolvesById() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleSource es = new SampleSource(SampleSource.ES_SOURCE);
        registry.register(es);

        assertThat(registry.getSource(SampleSource.ES_SOURCE)).isSameAs(es);
    }

    @Test
    void registry_register_duplicateSourceId_conflicts() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new SampleSource(SampleSource.ES_SOURCE));

        assertThrows(ProjectionSourceConflictException.class,
                () -> registry.register(new SampleSource(SampleSource.ES_SOURCE)));
    }

    @Test
    void source_sync_projectsAndMaterializes_withVersion() {
        SampleSource es = new SampleSource(SampleSource.ES_SOURCE);
        SampleAggregate aggregate = new SampleAggregate();

        es.sync(aggregate);

        assertThat(es.materializeCount.get()).isEqualTo(1);
        assertThat(es.materializedVersion.get()).isEqualTo(aggregate.getOldVersion());
    }

    @Test
    void source_sync_nullProjection_skipsMaterialize() {
        NullProjectorSource source = new NullProjectorSource(SampleSource.ES_SOURCE);
        source.sync(new SampleAggregate());

        assertThat(source.materializeCount.get()).isZero();
    }

    @Test
    void source_purge_invokesSource() {
        SampleSource es = new SampleSource(SampleSource.ES_SOURCE);
        es.purge(42L);

        assertThat(es.purgeCount.get()).isEqualTo(1);
        assertThat(es.purgedId.get()).isEqualTo(42L);
    }

    /** 返回 null 投影的源，专门覆盖 null 投影分支。 */
    static class NullProjectorSource extends AbstractProjectionSource<SampleAggregate, Long, SampleProjection> {
        final AtomicInteger materializeCount = new AtomicInteger();

        NullProjectorSource(ProjectionSource source) {
            super(source, SampleAggregate.class, SampleProjection.class,
                    new StubProjector<>(SampleProjection.class), List.of());
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
            materializeCount.incrementAndGet();
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
}
