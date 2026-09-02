package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.base.AggregateRoot;
import io.pragmatic.ddd.repository.query.fixture.StubAggregate;
import io.pragmatic.ddd.repository.query.fixture.StubProjector;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证以「源」为中心后，检索器登记键（源 + 条件族）必须精确相等。
 *
 * <p>Registry 以 (源, 条件族 Class) 精确键匹配定位检索器，不做 isAssignableFrom 向上查找。
 * 「按投影接口登记、按具体子类型查询」无法命中，且 get*Searcher 未命中抛
 * {@link ProjectionSearcherNotFoundException}（与 source 未登记抛 ProjectionSourceNotFoundException 不同）。</p>
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

    /** 用于承载检索器的内存源：源投影固定为 DetailProjection。 */
    private static final class DetailSource extends AbstractProjectionSource<StubAggregate, DetailProjection> {

        private DetailSource(ProjectionSource source) {
            super(source, StubAggregate.class, DetailProjection.class,
                    new StubProjector<>(DetailProjection.class), null);
        }

        private DetailSource(ProjectionSource source, IProjectionByIdSearcher<DetailProjection> idSearcher) {
            super(source, StubAggregate.class, DetailProjection.class,
                    new StubProjector<>(DetailProjection.class), idSearcher);
        }

        private static DetailSource of(ProjectionSource source, IProjectionSearcher<?, DetailProjection> searcher) {
            DetailSource s = new DetailSource(source);
            s.bind(searcher);
            return s;
        }

        private static DetailSource of(ProjectionSource source, IProjectionPagedSearcher<?, DetailProjection> searcher) {
            DetailSource s = new DetailSource(source);
            s.bind(searcher);
            return s;
        }

        private static DetailSource of(ProjectionSource source, IProjectionByIdSearcher<DetailProjection> searcher) {
            DetailSource s = new DetailSource(source, searcher);
            return s;
        }

        @Override
        public void materialize(IAggregateProjection projection, long version) {
        }

        @Override
        public void purge(Object aggregateId) {
        }
    }

    /** 按主键检索器：projectionType 可在构造期指定，用于复现登记/查询键不一致。 */
    private static final class StubByIdSearcher implements IProjectionByIdSearcher<DetailProjection> {

        private final Class<?> registeredProjectionType;

        private StubByIdSearcher(Class<?> registeredProjectionType) {
            this.registeredProjectionType = registeredProjectionType;
        }

        @Override
        public DetailProjection getById(Object id) {
            return new DetailProjection();
        }

        @Override
        public List<DetailProjection> getByIds(List<Object> ids) {
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

        @Override
        public List<DetailProjection> search(Criteria condition) {
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

        @Override
        public PageResult<DetailProjection> searchPage(PagedCriteria condition, PageRequest pageRequest) {
            return PageResult.of(List.of(new DetailProjection()), 1L, pageRequest);
        }

        @Override
        public ScrollResult<DetailProjection> searchScroll(PagedCriteria condition, ScrollPosition cursor, int pageSize) {
            return ScrollResult.of(List.of(new DetailProjection()), null);
        }
    }

    @Test
    void getByIdSearcher_registeredByConcreteType_isResolvedBySameType() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:detail");
        registry.register(DetailSource.of(source, new StubByIdSearcher(DetailProjection.class)));

        assertThat(registry.getByIdSearcher(source)).isNotNull();
    }

    @Test
    void getByIdSearcher_unregisteredSource_throws() {
        ProjectorRegistry registry = new ProjectorRegistry();

        assertThatThrownBy(() -> registry.getByIdSearcher(ProjectionSource.of("missing")))
                .isInstanceOf(ProjectionSourceNotFoundException.class);
    }

    @Test
    void getSearcher_registeredByConcreteType_isResolvedBySameType() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:detail");
        registry.register(DetailSource.of(source, new StubSearcher(DetailProjection.class)));

        assertThat(registry.getSearcher(source, Criteria.class)).isNotNull();
    }

    @Test
    void getSearcher_unregisteredCriteriaType_throwsNotFound() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:detail");
        registry.register(DetailSource.of(source, new StubSearcher(DetailProjection.class)));

        assertThatThrownBy(() -> registry.getSearcher(source, PagedCriteria.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class);
    }

    @Test
    void getPagedSearcher_registeredByConcreteType_isResolvedBySameType() {
        ProjectorRegistry registry = new ProjectorRegistry();
        ProjectionSource source = ProjectionSource.of("es:detail");
        registry.register(DetailSource.of(source, new StubPagedSearcher(DetailProjection.class)));

        assertThat(registry.getPagedSearcher(source, PagedCriteria.class)).isNotNull();
    }

    @Test
    void searcherKeyMismatch_isDifferentFromSourceResolution() {
        ProjectorRegistry registry = new ProjectorRegistry();

        // 对照：source 未登记抛 ProjectionSourceNotFoundException
        assertThatThrownBy(() -> registry.getSource(ProjectionSource.of("missing")))
                .isInstanceOf(ProjectionSourceNotFoundException.class);

        // 而 searcher 未命中抛 ProjectionSearcherNotFoundException（源存在但条件族缺失）
        ProjectionSource source = ProjectionSource.of("es:detail");
        registry.register(DetailSource.of(source, new StubSearcher(DetailProjection.class)));
        assertThatThrownBy(() -> registry.getSearcher(source, PagedCriteria.class))
                .isInstanceOf(ProjectionSearcherNotFoundException.class);
    }
}
