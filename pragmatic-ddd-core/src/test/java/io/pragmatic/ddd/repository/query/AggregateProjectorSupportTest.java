package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.fixture.StubMaterializer;
import io.pragmatic.ddd.repository.query.fixture.StubProjection;
import io.pragmatic.ddd.repository.query.fixture.StubProjector;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AggregateProjectorSupportTest {

    private ProjectorRegistry newRegisteredRegistry() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(StubAggregate.class, new StubProjector());
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:stub");
        registry.register(new StubMaterializer(target));
        return registry;
    }

    @Test
    void sync_projectsAndMaterializes() {
        ProjectorRegistry registry = newRegisteredRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:stub");
        StubAggregate aggregate = new StubAggregate();
        aggregate.setName("v");
        support.sync(aggregate, StubProjection.class, target);
        // 通过 materializer 桩记录验证 project→materialize 链路
        StubMaterializer materializer = (StubMaterializer) registry.resolveMaterializer(StubProjection.class, target);
        assertThat(materializer.lastProjection.get()).isNotNull();
        assertThat(materializer.lastProjection.get().name()).isEqualTo("v");
    }

    @Test
    void sync_missingProjector_skipsSilently() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:stub");
        registry.register(new StubMaterializer(target));
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        StubAggregate aggregate = new StubAggregate();
        // 未登记 projector：sync 不应抛异常，且 materializer 未被调用
        support.sync(aggregate, StubProjection.class, target);
        StubMaterializer materializer = (StubMaterializer) registry.resolveMaterializer(StubProjection.class, target);
        assertThat(materializer.lastProjection.get()).isNull();
    }

    @Test
    void purge_delegatesToMaterializer() {
        ProjectorRegistry registry = newRegisteredRegistry();
        AggregateProjectorSupport support = new AggregateProjectorSupport(registry);
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:stub");
        support.purge(StubProjection.class, 99L, target);
        StubMaterializer materializer = (StubMaterializer) registry.resolveMaterializer(StubProjection.class, target);
        assertThat(materializer.lastPurgedId.get()).isEqualTo(99L);
    }
}
