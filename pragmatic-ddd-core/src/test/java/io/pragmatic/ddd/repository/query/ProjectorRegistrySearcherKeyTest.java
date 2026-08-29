package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.fixture.StubProjection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 ProjectorRegistry 三类检索器的登记键与查询键必须精确相等。
 *
 * <p>Registry 以 Map 精确键匹配（Class 相等）定位检索器，不做 isAssignableFrom 向上查找。
 * 因此「按投影接口类型登记、按具体子类型查询」无法命中，且 get*Searcher 未命中抛
 * {@link ProjectionSearcherNotFoundException}（与 resolveProjector / resolveMaterializer 返回 null 不同）。</p>
 *
 * @author wizard-lee
 */
class ProjectorRegistrySearcherKeyTest {

    /** 投影体系基类（sealed 体系的根）。 */
    private interface IProjection extends IAggregateProjection {
    }

    /** 投影体系的具体子类型。 */
    private static final class DetailProjection implements IProjection {
    }

    private record Criteria(String key) implements OneQueryCriteria {
    }

    private record PagedCriteria(String key) implements PageQueryCriteria {
    }

    /** 按主键检索器：projectionType 可在构造期指定，用于复现登记/查询键不一致。 */
    private static final class StubByIdSearcher implements IProjectionByIdSearcher<DetailProjection> {

        private final Class<?> registeredProjectionType;

        private StubByIdSearcher(Class<?> registeredProjectionType) {
            this.registeredProjectionType = registeredProjectionType;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<DetailProjection> projectionType() {
            return (Class<DetailProjection>) registeredProjectionType;
        }

        @Override
        public DetailProjection getById(Object id, Class<DetailProjection> projectionType) {
            return new DetailProjection();
        }

        @Override
        public List<DetailProjection> getByIds(List<Object> ids, Class<DetailProjection> projectionType) {
            return List.of(new DetailProjection());
        }
    }

    /** 按条件检索器：projectionType 可在构造期指定。 */
    private static final class StubSearcher implements IProjectionSearcher<Criteria, DetailProjection> {

        private final Class<?> registeredProjectionType;

        private StubSearcher(Class<?> registeredProjectionType) {
            this.registeredProjectionType = registeredProjectionType;
        }

        @Override
        public Class<Criteria> criteriaType() {
            return Criteria.class;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<DetailProjection> projectionType() {
            return (Class<DetailProjection>) registeredProjectionType;
        }

        @Override
        public List<DetailProjection> search(Criteria condition, Class<DetailProjection> projectionType) {
            return List.of(new DetailProjection());
        }
    }

    /** 分页 / 滚动检索器：projectionType 可在构造期指定。 */
    private static final class StubPagedSearcher
            implements IProjectionPagedSearcher<PagedCriteria, DetailProjection> {

        private final Class<?> registeredProjectionType;

        private StubPagedSearcher(Class<?> registeredProjectionType) {
            this.registeredProjectionType = registeredProjectionType;
        }

        @Override
        public Class<PagedCriteria> criteriaType() {
            return PagedCriteria.class;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Class<DetailProjection> projectionType() {
            return (Class<DetailProjection>) registeredProjectionType;
        }

        @Override
        public PageResult<DetailProjection> searchPage(
                PagedCriteria condition, PageRequest pageRequest, Class<DetailProjection> projectionType) {
            return PageResult.of(List.of(new DetailProjection()), 1L, pageRequest);
        }

        @Override
        public ScrollResult<DetailProjection> searchScroll(
                PagedCriteria condition, ScrollPosition cursor, int pageSize, Class<DetailProjection> projectionType) {
            return ScrollResult.of(List.of(new DetailProjection()), null);
        }
    }

    @Test
    void getByIdSearcher_registeredByConcreteType_isResolvedBySameType() {
        ProjectorRegistry registry = new ProjectorRegistry();
        StubByIdSearcher searcher = new StubByIdSearcher(DetailProjection.class);
        registry.register(searcher);

        assertThat(registry.getByIdSearcher(DetailProjection.class)).isSameAs(searcher);
    }

    @Test
    void getByIdSearcher_registeredByInterfaceType_isNotFoundBySubtype() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new StubByIdSearcher(IProjection.class));

        assertThatThrownBy(() -> registry.getByIdSearcher(DetailProjection.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class)
                .hasMessageContaining(DetailProjection.class.getName());
    }

    @Test
    void getSearcher_registeredByConcreteType_isResolvedBySameType() {
        ProjectorRegistry registry = new ProjectorRegistry();
        StubSearcher searcher = new StubSearcher(DetailProjection.class);
        registry.register(searcher);

        assertThat(registry.getSearcher(Criteria.class, DetailProjection.class)).isSameAs(searcher);
    }

    @Test
    void getSearcher_registeredByInterfaceType_isNotFoundBySubtype() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new StubSearcher(IProjection.class));

        assertThatThrownBy(() -> registry.getSearcher(Criteria.class, DetailProjection.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class)
                .hasMessageContaining(DetailProjection.class.getName());
    }

    @Test
    void getPagedSearcher_registeredByConcreteType_isResolvedBySameType() {
        ProjectorRegistry registry = new ProjectorRegistry();
        StubPagedSearcher searcher = new StubPagedSearcher(DetailProjection.class);
        registry.register(searcher);

        assertThat(registry.getPagedSearcher(PagedCriteria.class, DetailProjection.class)).isSameAs(searcher);
    }

    @Test
    void getPagedSearcher_registeredByInterfaceType_isNotFoundBySubtype() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new StubPagedSearcher(IProjection.class));

        assertThatThrownBy(() -> registry.getPagedSearcher(PagedCriteria.class, DetailProjection.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class)
                .hasMessageContaining(DetailProjection.class.getName());
    }

    @Test
    void getSearcher_unregisteredCriteriaType_throwsNotFound() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(new StubSearcher(DetailProjection.class));

        assertThatThrownBy(() -> registry.getSearcher(PagedCriteria.class, DetailProjection.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class);
    }

    @Test
    void searcherKeyMismatch_isDifferentFromProjectorResolution() {
        ProjectorRegistry registry = new ProjectorRegistry();

        // 对照：projector / materializer 未登记返回 null，不抛异常
        assertThat(registry.resolveProjector(
                io.pragmatic.ddd.repository.query.fixture.StubAggregate.class, StubProjection.class)).isNull();

        // 而 searcher 未命中抛异常
        assertThatThrownBy(() -> registry.getByIdSearcher(DetailProjection.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class);
    }
}
