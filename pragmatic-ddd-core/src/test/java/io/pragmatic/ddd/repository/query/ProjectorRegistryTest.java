package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.fixture.StubMaterializer;
import io.pragmatic.ddd.repository.query.fixture.StubProjection;
import io.pragmatic.ddd.repository.query.fixture.StubProjector;
import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ProjectorRegistry 投影器与物化器的登记与解析。
 *
 * @author wizard-lee
 */
class ProjectorRegistryTest {

    @Test
    void registerAndResolve_projectorByType() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(StubAggregate.class, new StubProjector());
        IAggregateProjector<StubAggregate, StubProjection> projector =
                registry.resolveProjector(StubAggregate.class, StubProjection.class);
        assertThat(projector).isNotNull();
        assertThat(projector.projectionType()).isEqualTo(StubProjection.class);
    }

    @Test
    void resolveProjector_unregisteredType_returnsNull() {
        ProjectorRegistry registry = new ProjectorRegistry();
        assertThat(registry.resolveProjector(StubAggregate.class, StubProjection.class)).isNull();
    }

    @Test
    void registerAndResolve_materializerByProjectionAndTarget() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:stub");
        StubMaterializer materializer = new StubMaterializer(target);
        registry.register(materializer);
        assertThat(registry.resolveMaterializer(StubProjection.class, target)).isSameAs(materializer);
    }

    @Test
    void resolveMaterializer_unregistered_returnsNull() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ReconciliationTarget target = new ReconciliationTarget(StubAggregate.class, "es:stub");
        assertThat(registry.resolveMaterializer(StubProjection.class, target)).isNull();
    }
}
