package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.criteria.ListQueryCriteria;
import io.pragmatic.ddd.repository.query.criteria.OneQueryCriteria;
import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.exception.ProjectionSourceNotFoundException;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.IProjectionByIdSearcher;
import io.pragmatic.ddd.repository.query.projection.IProjectionPagedSearcher;
import io.pragmatic.ddd.repository.query.projection.ProjectionSource;
import io.pragmatic.ddd.repository.query.projection.ProjectorRegistry;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjection;
import io.pragmatic.ddd.repository.query.projection.fixture.StubProjector;
import io.pragmatic.ddd.repository.query.projection.fixture.StubSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 AbstractProjectionQuery 的候选源解析：内置回源链的能力过滤、逐源回源与分页不回源。
 *
 * <p>以「源是否具备本次查询所需检索器」作为可被过滤的能力维度，用记录型检索器捕获调用次数与来源，
 * 不依赖任何具体存储实现。</p>
 *
 * @author wizard-lee
 */
class AbstractProjectionQueryTest {

    private static final ProjectionSource REDIS = ProjectionSource.of("redis:stub");
    private static final ProjectionSource ES = ProjectionSource.of("es:stub");

    private record OneCriteria(String key) implements OneQueryCriteria {}

    private record ListCriteria(String key) implements ListQueryCriteria {}

    private record PageCriteria(String key) implements PageQueryCriteria {}

    /** 测试用查询门面：回源链由构造参数注入，模拟子类覆写 fallbackChain()。 */
    private static final class TestQuery
            extends AbstractProjectionQuery<Long, StubProjection, OneCriteria, ListCriteria, PageCriteria> {

        private final List<ProjectionSource> chain;

        private TestQuery(ProjectorRegistry registry, List<ProjectionSource> chain) {
            super(registry, OneCriteria.class, ListCriteria.class, PageCriteria.class);
            this.chain = chain;
        }

        @Override
        protected List<ProjectionSource> fallbackChain() {
            return chain;
        }
    }

    /** 按主键检索器桩：记录调用次数，可注入 null 模拟未命中。 */
    private static final class RecordingByIdSearcher implements IProjectionByIdSearcher<StubProjection> {

        private final String name;
        private int calls;
        private StubProjection nextResult;

        private RecordingByIdSearcher(String name) {
            this.name = name;
            this.nextResult = new StubProjection(1L, name);
        }

        @Override
        public StubProjection getById(Object id) {
            this.calls++;
            return nextResult;
        }

        @Override
        public List<StubProjection> getByIds(List<Object> ids) {
            this.calls++;
            return nextResult == null ? List.of() : List.of(nextResult);
        }
    }

    /** 分页检索器桩：记录调用次数。 */
    private static final class RecordingPagedSearcher implements IProjectionPagedSearcher<PageCriteria, StubProjection> {

        private final String name;
        private int calls;

        private RecordingPagedSearcher(String name) {
            this.name = name;
        }

        @Override
        public Class<PageCriteria> criteriaType() {
            return PageCriteria.class;
        }

        @Override
        public PageResult<StubProjection> searchPage(PageCriteria condition, PageRequest pageRequest) {
            this.calls++;
            return PageResult.of(List.of(new StubProjection(1L, name)), 1L, pageRequest);
        }

        @Override
        public ScrollResult<StubProjection> searchScroll(
                PageCriteria condition, ScrollPosition cursor, int pageSize) {
            this.calls++;
            return ScrollResult.of(List.of(new StubProjection(1L, name)), "next");
        }
    }

    /** 登记一个源；byIdSearcher 为 null 表示该源不支持按主键查询。 */
    private static StubSource source(ProjectionSource source, IProjectionByIdSearcher<StubProjection> byIdSearcher) {
        return new StubSource(source, new StubProjector<>(StubProjection.class), byIdSearcher);
    }

    @Test
    void queryById_skipsSourceWithoutByIdSearcher() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(source(REDIS, null));
        RecordingByIdSearcher es = new RecordingByIdSearcher("es");
        registry.register(source(ES, es));
        TestQuery query = new TestQuery(registry, List.of(REDIS, ES));

        StubProjection result = query.queryById(1L, StubProjection.class);

        assertThat(result.name()).isEqualTo("es");
        assertThat(es.calls).isEqualTo(1);
    }

    @Test
    void queryById_fallsBackToNextSourceWhenFirstMisses() {
        ProjectorRegistry registry = new ProjectorRegistry();
        RecordingByIdSearcher redis = new RecordingByIdSearcher("redis");
        RecordingByIdSearcher es = new RecordingByIdSearcher("es");
        redis.nextResult = null;
        registry.register(source(REDIS, redis));
        registry.register(source(ES, es));
        TestQuery query = new TestQuery(registry, List.of(REDIS, ES));

        StubProjection result = query.queryById(1L, StubProjection.class);

        assertThat(result.name()).isEqualTo("es");
        assertThat(redis.calls).isEqualTo(1);
        assertThat(es.calls).isEqualTo(1);
    }

    @Test
    void queryById_returnsNullWhenWholeChainMisses() {
        ProjectorRegistry registry = new ProjectorRegistry();
        RecordingByIdSearcher redis = new RecordingByIdSearcher("redis");
        RecordingByIdSearcher es = new RecordingByIdSearcher("es");
        redis.nextResult = null;
        es.nextResult = null;
        registry.register(source(REDIS, redis));
        registry.register(source(ES, es));
        TestQuery query = new TestQuery(registry, List.of(REDIS, ES));

        assertThat(query.queryById(1L, StubProjection.class)).isNull();
        assertThat(redis.calls).isEqualTo(1);
        assertThat(es.calls).isEqualTo(1);
    }

    @Test
    void queryById_throwsWhenNoSourceSupports() {
        ProjectorRegistry registry = new ProjectorRegistry();
        registry.register(source(REDIS, null));
        registry.register(source(ES, null));
        TestQuery query = new TestQuery(registry, List.of(REDIS, ES));

        assertThatThrownBy(() -> query.queryById(1L, StubProjection.class))
                .isInstanceOf(ProjectionSourceNotFoundException.class)
                .hasMessageContaining(StubProjection.class.getSimpleName());
    }

    @Test
    void queryPage_usesFirstSourceWithPagedSearcher_withoutFallback() {
        ProjectorRegistry registry = new ProjectorRegistry();
        RecordingPagedSearcher redis = new RecordingPagedSearcher("redis");
        RecordingPagedSearcher es = new RecordingPagedSearcher("es");
        registry.register(source(REDIS, null).with(redis));
        registry.register(source(ES, null).with(es));
        TestQuery query = new TestQuery(registry, List.of(REDIS, ES));

        PageResult<StubProjection> result =
                query.queryPage(new PageCriteria("k"), PageRequest.of(1, 10), StubProjection.class);

        assertThat(result.data().get(0).name()).isEqualTo("redis");
        assertThat(redis.calls).isEqualTo(1);
        assertThat(es.calls).isZero();
    }

    @Test
    void queryScroll_skipsSourceWithoutPagedSearcher() {
        ProjectorRegistry registry = new ProjectorRegistry();
        RecordingPagedSearcher es = new RecordingPagedSearcher("es");
        registry.register(source(REDIS, null));
        registry.register(source(ES, null).with(es));
        TestQuery query = new TestQuery(registry, List.of(REDIS, ES));

        ScrollResult<StubProjection> result =
                query.queryScroll(new PageCriteria("k"), ScrollPosition.initial(), 10, StubProjection.class);

        assertThat(result.data().get(0).name()).isEqualTo("es");
        assertThat(es.calls).isEqualTo(1);
    }

    @Test
    void queryById_withoutChain_usesResolvedDefaultSource() {
        ProjectorRegistry registry = new ProjectorRegistry();
        RecordingByIdSearcher es = new RecordingByIdSearcher("es");
        registry.register(source(ES, es));
        TestQuery query = new TestQuery(registry, List.of());

        StubProjection result = query.queryById(1L, StubProjection.class);

        assertThat(result.name()).isEqualTo("es");
        assertThat(es.calls).isEqualTo(1);
    }
}
