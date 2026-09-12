package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.exception.ProjectionSourceConflictException;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceNotFoundException;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjection;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjector;
import io.pragmatic.ddd.repository.query.projection.fixture.StubSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证极薄化后的源登记中心：登记、按 id 取源、重复冲突与投影器取用。
 *
 * @author wizard-lee
 */
class ProjectorRegistryTest {

    private static final ProjectionSource ES = ProjectionSource.of("es:stub");

    private static StubSource source(ProjectionSource source) {
        return new StubSource(source, new StubProjector<>(StubProjection.class), List.of());
    }

    @Test
    void registerAndGetSource_byId() {
        ProjectorRegistry registry = new ProjectorRegistry();
        StubSource src = source(ES);
        registry.register(src);

        assertThat(registry.getSource(ES)).isSameAs(src);
        assertThat(registry.findSource(ES)).contains(src);
    }

    @Test
    void getSource_unregistered_throwsNotFound() {
        ProjectorRegistry registry = new ProjectorRegistry();

        assertThatThrownBy(() -> registry.getSource(ES))
                .isInstanceOf(ProjectionSourceNotFoundException.class)
                .hasMessageContaining(ES.id());
    }

    @Test
    void findSource_unregistered_returnsEmpty() {
        ProjectorRegistry registry = new ProjectorRegistry();

        assertThat(registry.findSource(ES)).isEmpty();
    }

    @Test
    void register_sameIdDifferentInstance_throwsConflict() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(source(ES));

        assertThatThrownBy(() -> registry.register(source(ES)))
                .isInstanceOf(ProjectionSourceConflictException.class);
    }

    @Test
    void register_sameInstanceTwice_isIdempotent() {
        ProjectorRegistry registry = new ProjectorRegistry();
        StubSource src = source(ES);
        registry.register(src);
        registry.register(src);

        assertThat(registry.getSource(ES)).isSameAs(src);
    }

    @Test
    void getProjector_bySource() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(source(ES));

        assertThat(registry.getProjector(ES)).isNotNull();
    }

    @Test
    void replicaIdentity_bridgesToSource() {
        StubSource src = source(ES);
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(src);

        assertThat(src.replicaId()).isEqualTo(ES.id());
        assertThat(src.key().replicaId()).isEqualTo(ES.id());
    }
}
