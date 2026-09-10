package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.criteria.OneQueryCriteria;
import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceConflictException;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceNotFoundException;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;

import io.pragmatic.ddd.repository.query.projection.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjection;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjector;
import io.pragmatic.ddd.repository.query.projection.fixture.StubSource;
import org.junit.jupiter.api.Test;

import java.util.List;

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

    @Test
    void supportsProjection_fullProjectionAndUnrelated() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:stub");
        registry.register(new StubSource(source));

        assertThat(registry.supportsProjection(source, StubProjection.class)).isTrue();
        assertThat(registry.supportsProjection(source, StubAggregate.class)).isFalse();
        assertThat(registry.supportsProjection(ProjectionSource.of("missing"), StubProjection.class)).isFalse();
    }

    @Test
    void hasSearcher_capabilitiesFollowBindings() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:stub");
        StubSource stub = new StubSource(source, new StubProjector<>(StubProjection.class), new ByIdSearcher())
                .with(new Searcher())
                .with(new PagedSearcher());
        registry.register(stub);

        assertThat(registry.hasByIdSearcher(source)).isTrue();
        assertThat(registry.hasSearcher(source, Criteria.class)).isTrue();
        assertThat(registry.hasPagedSearcher(source, PageCriteria.class)).isTrue();
        assertThat(registry.hasSearcher(source, PageCriteria.class)).isFalse();
    }

    @Test
    void hasSearcher_unboundOrUnregistered_returnsFalse() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:stub");
        registry.register(new StubSource(source));

        assertThat(registry.hasByIdSearcher(source)).isFalse();
        assertThat(registry.hasSearcher(source, Criteria.class)).isFalse();
        assertThat(registry.hasPagedSearcher(source, PageCriteria.class)).isFalse();
        assertThat(registry.hasByIdSearcher(ProjectionSource.of("missing"))).isFalse();
    }

    private record Criteria(String key) implements OneQueryCriteria {}

    private record PageCriteria(String key) implements PageQueryCriteria {}

    /** 按主键检索器桩：仅用于满足能力探测。 */
    private static final class ByIdSearcher implements IProjectionByIdSearcher<StubProjection> {

        @Override
        public StubProjection getById(Object id) {
            return null;
        }

        @Override
        public List<StubProjection> getByIds(List<Object> ids) {
            return List.of();
        }
    }

    /** 按条件检索器桩：仅用于满足能力探测。 */
    private static final class Searcher implements IProjectionSearcher<Criteria, StubProjection> {

        @Override
        public Class<Criteria> criteriaType() {
            return Criteria.class;
        }

        @Override
        public List<StubProjection> search(Criteria condition) {
            return List.of();
        }
    }

    /** 分页检索器桩：仅用于满足能力探测。 */
    private static final class PagedSearcher implements IProjectionPagedSearcher<PageCriteria, StubProjection> {

        @Override
        public Class<PageCriteria> criteriaType() {
            return PageCriteria.class;
        }

        @Override
        public PageResult<StubProjection> searchPage(PageCriteria condition, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0L, pageRequest);
        }

        @Override
        public ScrollResult<StubProjection> searchScroll(
                PageCriteria condition, ScrollPosition cursor, int pageSize) {
            return ScrollResult.of(List.of(), "next");
        }
    }
}
