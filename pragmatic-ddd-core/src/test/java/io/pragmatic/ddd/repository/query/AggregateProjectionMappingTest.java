package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.repository.query.fixture.StubProjector;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聚合投影映射落地测试：覆盖 ProjectorRegistry 以「源」为中心的登记/解析与
 * AggregateProjectorSupport 门面编排（sync / purge）。
 * @author wizard-lee
 */
class AggregateProjectionMappingTest {

    /** 投影实现：仅为测试载体。 */
    static class SampleProjection implements IAggregateProjection {
    }

    /** 内存 projector：记录被调用与产出。 */
    static class SampleProjector extends AbstractAggregateProjector<SampleAggregate, SampleProjection> {
        final AtomicReference<SampleAggregate> lastInput = new AtomicReference<>();
        final boolean returnNull;

        SampleProjector(boolean returnNull) {
            super(SampleProjection.class);
            this.returnNull = returnNull;
        }

        @Override
        public SampleProjection project(SampleAggregate aggregateRoot) {
            lastInput.set(aggregateRoot);
            return returnNull ? null : new SampleProjection();
        }
    }

    /**
     * 内存源适配器：模拟一份物理副本（如 ES 一个索引），记录 materialize / purge 调用与入参。
     * 源 id 与写侧 ReconciliationTarget.storeId 同名，由基类构造器派生 target。
     */
    static class SampleSource extends AbstractProjectionSource<SampleAggregate, SampleProjection> {
        static final ProjectionSource ES_SOURCE = ProjectionSource.of("es:orders");
        static final ProjectionSource REDIS_SOURCE = ProjectionSource.of("redis:orders");

        final AtomicReference<Long> materializedVersion = new AtomicReference<>();
        final AtomicReference<Object> purgedId = new AtomicReference<>();
        final AtomicInteger materializeCount = new AtomicInteger();
        final AtomicInteger purgeCount = new AtomicInteger();

        SampleSource(ProjectionSource source) {
            super(source, SampleAggregate.class, SampleProjection.class,
                    new SampleProjector(false), null);
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

        try {
            registry.register(new SampleSource(SampleSource.ES_SOURCE));
            org.junit.jupiter.api.Assertions.fail("应抛 ProjectionSourceConflictException");
        } catch (ProjectionSourceConflictException e) {
            assertThat(e.getMessage()).contains("es:orders");
        }
    }

    @Test
    void support_sync_projectsAndMaterializes_withVersion() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleSource es = new SampleSource(SampleSource.ES_SOURCE);
        registry.register(es);

        SampleAggregate aggregate = new SampleAggregate();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);

        support.sync(aggregate, SampleSource.ES_SOURCE);

        assertThat(es.materializeCount.get()).isEqualTo(1);
        assertThat(es.materializedVersion.get()).isEqualTo(aggregate.getOldVersion());
    }

    @Test
    void support_sync_missingSource_skipsSilently() {
        ProjectorRegistry registry = new ProjectorRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);

        // 未登记任何源：sync 静默跳过，不抛异常
        support.sync(new SampleAggregate(), SampleSource.ES_SOURCE);

        // 通过 target 桥接：storeId 无对应源同样跳过
        support.sync(new SampleAggregate(),
                new ReconciliationTarget(SampleAggregate.class, "es:orders"));
    }

    @Test
    void support_sync_nullProjection_skipsMaterialize() {
        ProjectorRegistry registry = new ProjectorRegistry();
        NullProjectorSource source = new NullProjectorSource(SampleSource.ES_SOURCE);
        registry.register(source);

        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        support.sync(new SampleAggregate(), SampleSource.ES_SOURCE);

        assertThat(source.materializeCount.get()).isZero();
    }

    @Test
    void support_purge_invokesSource() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleSource es = new SampleSource(SampleSource.ES_SOURCE);
        registry.register(es);

        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        support.purge(SampleSource.ES_SOURCE, 42L);

        assertThat(es.purgeCount.get()).isEqualTo(1);
        assertThat(es.purgedId.get()).isEqualTo(42L);
    }

    @Test
    void support_purge_missingSource_skipsSilently() {
        ProjectorRegistry registry = new ProjectorRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        support.purge(SampleSource.ES_SOURCE, 42L);
        // 无源：应静默跳过（不抛异常）
    }

    /** 返回 null 投影的源，专门覆盖 null 投影分支。 */
    static class NullProjectorSource extends AbstractProjectionSource<SampleAggregate, SampleProjection> {
        final AtomicInteger materializeCount = new AtomicInteger();

        NullProjectorSource(ProjectionSource source) {
            super(source, SampleAggregate.class, SampleProjection.class,
                    new StubProjector<>(SampleProjection.class), null);
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
            materializeCount.incrementAndGet();
        }

        @Override
        public void purge(Object aggregateId) {
        }
    }
}
