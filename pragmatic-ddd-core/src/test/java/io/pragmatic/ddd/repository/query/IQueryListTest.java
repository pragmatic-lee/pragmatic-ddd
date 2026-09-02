package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.criteria.ListQueryCriteria;

import io.pragmatic.ddd.repository.query.projection.fixture.StubProjection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryList 按条件查询投影列表的契约。
 *
 * @author wizard-lee
 */
class IQueryListTest {

    private record Criteria(String key) implements ListQueryCriteria {}

    private static final class InMemoryQuery implements IQueryList<StubProjection, Criteria> {
        @Override
        public <X extends StubProjection> List<X> queryList(Criteria query, Class<X> projectionType) {
            return "k".equals(query.key())
                    ? List.of((X) new StubProjection(1L, "a"), (X) new StubProjection(2L, "b"))
                    : List.of();
        }
    }

    @Test
    void queryList_hit_returnsList() {
        IQueryList<StubProjection, Criteria> query = new InMemoryQuery();
        assertThat(query.queryList(new Criteria("k"), StubProjection.class).stream().map(StubProjection::name))
                .containsExactly("a", "b");
    }

    @Test
    void queryList_miss_returnsEmptyList() {
        IQueryList<StubProjection, Criteria> query = new InMemoryQuery();
        assertThat(query.queryList(new Criteria("x"), StubProjection.class)).isEmpty();
    }
}
