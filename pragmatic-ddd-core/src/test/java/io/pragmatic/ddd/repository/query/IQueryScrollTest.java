package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;

import io.pragmatic.ddd.repository.query.projection.fixture.StubProjection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryScroll 游标滚动查询并返回下一游标的契约。
 *
 * @author wizard-lee
 */
class IQueryScrollTest {

    private record Criteria(String key) implements PageQueryCriteria {}

    private static final class InMemoryQuery implements IQueryScroll<StubProjection, Criteria> {
        @Override
        public <X extends StubProjection> ScrollResult<X> queryScroll(
                Criteria query, ScrollPosition cursor, int pageSize, Class<X> projectionType) {
            if (cursor.isInitial()) {
                return ScrollResult.of(
                        List.of((X) new StubProjection(1L, "a"), (X) new StubProjection(2L, "b")), "next");
            }
            return ScrollResult.of(List.of(), null);
        }
    }

    @Test
    void queryScroll_initial_returnsDataAndNextCursor() {
        IQueryScroll<StubProjection, Criteria> query = new InMemoryQuery();
        ScrollResult<StubProjection> result =
                query.queryScroll(new Criteria("k"), ScrollPosition.initial(), 10, StubProjection.class);
        assertThat(result.data().stream().map(StubProjection::name)).containsExactly("a", "b");
        assertThat(result.nextCursor()).isEqualTo("next");
    }

    @Test
    void queryScroll_lastPage_nextCursorIsNull() {
        IQueryScroll<StubProjection, Criteria> query = new InMemoryQuery();
        ScrollResult<StubProjection> result =
                query.queryScroll(new Criteria("k"), ScrollPosition.of("next"), 10, StubProjection.class);
        assertThat(result.data()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }
}
