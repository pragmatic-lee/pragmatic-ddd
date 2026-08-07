package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.reconciliation.fixture.StubAggregate;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubRepository;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubResolver;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubResynchronizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ReconciliationRegistry 核对目标与重新同步器的登记解析。
 *
 * @author wizard-lee
 */
class ReconciliationRegistryTest {

    private static final ReconciliationTarget TARGET =
            new ReconciliationTarget(StubAggregate.class, "es:stub");

    @Test
    void registerAndResolve_resolverAndResyncerAndRepository() {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        registry.registerResolver(TARGET, new StubResolver(TARGET, 1L));
        registry.registerResynchronizer(TARGET, new StubResynchronizer(TARGET));
        registry.registerRepository(StubAggregate.class, new StubRepository(java.util.Map.of()));

        assertThat(registry.resolverFor(TARGET)).isInstanceOf(StubResolver.class);
        assertThat(registry.resyncerFor(TARGET)).isInstanceOf(StubResynchronizer.class);
        assertThat(registry.repositoryFor(StubAggregate.class)).isInstanceOf(StubRepository.class);
    }

    @Test
    void targetsOf_returnsTargetsForAggregateType() {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        ReconciliationTarget other = new ReconciliationTarget(StubAggregate.class, "redis:stub");
        registry.registerResolver(TARGET, new StubResolver(TARGET, 1L));
        registry.registerResolver(other, new StubResolver(other, 1L));
        assertThat(registry.targetsOf(StubAggregate.class))
                .containsExactlyInAnyOrder(TARGET, other);
    }

    @Test
    void targetsOf_unknownType_returnsEmpty() {
        ReconciliationRegistry registry = new ReconciliationRegistry();
        assertThat(registry.targetsOf(StubAggregate.class)).isEmpty();
    }
}
