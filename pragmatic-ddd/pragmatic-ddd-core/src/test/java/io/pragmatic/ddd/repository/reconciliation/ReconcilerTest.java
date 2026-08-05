package io.pragmatic.ddd.repository.reconciliation;

import io.pragmatic.ddd.repository.reconciliation.fixture.StubAggregate;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubRepository;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubResolver;
import io.pragmatic.ddd.repository.reconciliation.fixture.StubResynchronizer;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcilerTest {

    private static final ReconciliationTarget TARGET =
            new ReconciliationTarget(StubAggregate.class, "es:stub");

    @Test
    void reconcile_detectsStale_whenReadBehindWrite() {
        StubRepository repo = new StubRepository(Map.of(1L, new StubAggregate(5)));
        StubResolver resolver = new StubResolver(TARGET, 3);
        Reconciliation r = Reconciler.reconcile(resolver, repo, 1L);
        assertThat(r.isStale()).isTrue();
    }

    @Test
    void reconcile_detectsConsistent_whenReadGteWrite() {
        StubRepository repo = new StubRepository(Map.of(1L, new StubAggregate(5)));
        StubResolver resolver = new StubResolver(TARGET, 5);
        Reconciliation r = Reconciler.reconcile(resolver, repo, 1L);
        assertThat(r.isConsistent()).isTrue();
    }

    @Test
    void reconcileAndResync_stale_triggersResync() {
        StubRepository repo = new StubRepository(Map.of(1L, new StubAggregate(5)));
        StubResolver resolver = new StubResolver(TARGET, 3);
        StubResynchronizer resyncer = new StubResynchronizer(TARGET);
        Reconciliation r = Reconciler.reconcileAndResync(resolver, resyncer, repo, 1L);
        assertThat(r.isStale()).isTrue();
        assertThat(resyncer.lastResyncedId.get()).isEqualTo(1L);
        assertThat(resyncer.lastPurgedId.get()).isNull();
    }

    @Test
    void reconcileAndResync_orphan_triggersPurge() {
        // 写模型无此聚合：currentVersion 返回 -1（findById 返回 null）
        StubRepository repo = new StubRepository(Map.of());
        StubResolver resolver = new StubResolver(TARGET, 3);
        StubResynchronizer resyncer = new StubResynchronizer(TARGET);
        Reconciliation r = Reconciler.reconcileAndResync(resolver, resyncer, repo, 1L);
        assertThat(r.isOrphan()).isTrue();
        assertThat(resyncer.lastPurgedId.get()).isEqualTo(1L);
        assertThat(resyncer.lastResyncedId.get()).isNull();
    }

    @Test
    void reconcileAndResync_consistent_noResyncOrPurge() {
        StubRepository repo = new StubRepository(Map.of(1L, new StubAggregate(5)));
        StubResolver resolver = new StubResolver(TARGET, 5);
        StubResynchronizer resyncer = new StubResynchronizer(TARGET);
        Reconciler.reconcileAndResync(resolver, resyncer, repo, 1L);
        assertThat(resyncer.lastResyncedId.get()).isNull();
        assertThat(resyncer.lastPurgedId.get()).isNull();
    }
}
