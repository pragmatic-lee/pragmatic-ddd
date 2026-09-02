package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.exception.ProjectionSourceConflictException;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceNotFoundException;

import io.pragmatic.ddd.repository.query.projection.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjection;
import io.pragmatic.ddd.repository.query.projection.fixture.StubSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 ProjectorRegistry 以「源」为中心的登记与解析。
 *
 * @author wizard-lee
 */
class ProjectorRegistryTest {

    @Test
    void registerAndResolve_sourceById() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:stub");
        StubSource stub = new StubSource(source);
        registry.register(stub);

        assertThat(registry.getSource(source)).isSameAs(stub);
        assertThat(registry.getProjector(source)).isNotNull();
    }

    @Test
    void resolveProjector_unregisteredSource_throws() {
        ProjectorRegistry registry = new ProjectorRegistry();
        assertThatThrownBy(() -> registry.getProjector(ProjectionSource.of("missing")))
                .isInstanceOf(ProjectionSourceNotFoundException.class);
    }

    @Test
    void register_duplicateSourceId_conflicts() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:stub");
        registry.register(new StubSource(source));

        assertThatThrownBy(() -> registry.register(new StubSource(source)))
                .isInstanceOf(ProjectionSourceConflictException.class);
    }

    @Test
    void register_projectionBelongsToSingleSource() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new StubSource(ProjectionSource.of("es:stub")));
        registry.register(new StubSource(ProjectionSource.of("redis:stub")));

        // 两个不同源 id 各用同一投影类，不应冲突（源 id 唯一即可）
        assertThat(registry.sourcesOf(StubProjection.class)).isEmpty();
        assertThat(registry.fullProjectionOf(StubProjection.class)).isPresent();
    }
}
