package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.fixture.SampleAggregate;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 聚合投影映射落地测试：覆盖 ProjectorRegistry 登记/解析与
 * AggregateProjectorSupport 门面编排（sync / purge）。
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

    /** 内存 materializer：记录 materialize / purge 调用与入参。 */
    static class SampleMaterializer implements IProjectionMaterializer<SampleProjection> {
        static final ReconciliationTarget ES_TARGET =
                new ReconciliationTarget(SampleAggregate.class, "es:orders");

        final AtomicReference<Long> materializedVersion = new AtomicReference<>();
        final AtomicReference<Object> purgedId = new AtomicReference<>();
        final AtomicInteger materializeCount = new AtomicInteger();
        final AtomicInteger purgeCount = new AtomicInteger();

        @Override
        public Class<SampleProjection> projectionType() {
            return SampleProjection.class;
        }

        @Override
        public ReconciliationTarget target() {
            return ES_TARGET;
        }

        @Override
        public void materialize(SampleProjection projection, long version) {
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
    void registry_resolveProjector_registersAndFinds() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleProjector projector = new SampleProjector(false);
        registry.register(SampleAggregate.class, projector);

        IAggregateProjector<SampleAggregate, SampleProjection> resolved =
                registry.resolveProjector(SampleAggregate.class, SampleProjection.class);
        assertThat(resolved).isSameAs(projector);
    }

    @Test
    void registry_resolveMaterializer_distinguishesByTarget() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleMaterializer es = new SampleMaterializer();
        SampleMaterializer redis = new SampleMaterializer();
        ReconciliationTarget redisTarget = new ReconciliationTarget(SampleAggregate.class, "redis:orders");
        // 用匿名子类覆写 target 以模拟第二个存储目标
        IProjectionMaterializer<SampleProjection> redisMaterializer = new SampleMaterializer() {
            @Override
            public ReconciliationTarget target() {
                return redisTarget;
            }
        };
        registry.register(es);
        registry.register(redisMaterializer);

        assertThat(registry.resolveMaterializer(SampleProjection.class, SampleMaterializer.ES_TARGET))
                .isSameAs(es);
        assertThat(registry.resolveMaterializer(SampleProjection.class, redisTarget))
                .isSameAs(redisMaterializer);
    }

    @Test
    void support_sync_projectsAndMaterializes_withVersion() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleProjector projector = new SampleProjector(false);
        SampleMaterializer materializer = new SampleMaterializer();
        registry.register(SampleAggregate.class, projector);
        registry.register(materializer);

        SampleAggregate aggregate = new SampleAggregate();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);

        support.sync(aggregate, SampleProjection.class, SampleMaterializer.ES_TARGET);

        assertThat(projector.lastInput.get()).isEqualTo(aggregate);
        assertThat(materializer.materializeCount.get()).isEqualTo(1);
        assertThat(materializer.materializedVersion.get()).isEqualTo(aggregate.getOldVersion());
    }

    @Test
    void support_sync_missingProjector_skipsSilently() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleMaterializer materializer = new SampleMaterializer();
        registry.register(materializer);

        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        support.sync(new SampleAggregate(), SampleProjection.class, SampleMaterializer.ES_TARGET);

        assertThat(materializer.materializeCount.get()).isZero();
    }

    @Test
    void support_sync_missingMaterializer_skipsSilently() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleProjector projector = new SampleProjector(false);
        registry.register(SampleAggregate.class, projector);

        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        support.sync(new SampleAggregate(), SampleProjection.class, SampleMaterializer.ES_TARGET);

        assertThat(projector.lastInput.get()).isNull();
    }

    @Test
    void support_sync_nullProjection_skipsMaterialize() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleProjector projector = new SampleProjector(true);
        SampleMaterializer materializer = new SampleMaterializer();
        registry.register(SampleAggregate.class, projector);
        registry.register(materializer);

        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        support.sync(new SampleAggregate(), SampleProjection.class, SampleMaterializer.ES_TARGET);

        assertThat(projector.lastInput.get()).isNotNull();
        assertThat(materializer.materializeCount.get()).isZero();
    }

    @Test
    void support_purge_invokesMaterializer() {
        ProjectorRegistry registry = new ProjectorRegistry();
        SampleMaterializer materializer = new SampleMaterializer();
        registry.register(materializer);

        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        support.purge(SampleProjection.class, 42L, SampleMaterializer.ES_TARGET);

        assertThat(materializer.purgeCount.get()).isEqualTo(1);
        assertThat(materializer.purgedId.get()).isEqualTo(42L);
    }

    @Test
    void support_purge_missingMaterializer_skipsSilently() {
        ProjectorRegistry registry = new ProjectorRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        support.purge(SampleProjection.class, 42L, SampleMaterializer.ES_TARGET);
        // 无 materializer：应静默跳过（不抛异常）
    }
}
