package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IQueryScrollTest {

    private record Criteria(String key) {}

    private static final class InMemoryQuery implements IQueryScroll<String, Criteria> {
        @Override
        public ScrollResult<String> queryScroll(Criteria query, ScrollPosition cursor, int pageSize) {
            if (cursor.isInitial()) {
                return ScrollResult.of(List.of("a", "b"), "next");
            }
            return ScrollResult.of(List.of(), null);
        }
    }

    @Test
    void queryScroll_initial_returnsDataAndNextCursor() {
        IQueryScroll<String, Criteria> query = new InMemoryQuery();
        ScrollResult<String> result = query.queryScroll(new Criteria("k"), ScrollPosition.initial(), 10);
        assertThat(result.data()).containsExactly("a", "b");
        assertThat(result.nextCursor()).isEqualTo("next");
    }

    @Test
    void queryScroll_lastPage_nextCursorIsNull() {
        IQueryScroll<String, Criteria> query = new InMemoryQuery();
        ScrollResult<String> result = query.queryScroll(new Criteria("k"), ScrollPosition.of("next"), 10);
        assertThat(result.data()).isEmpty();
        assertThat(result.nextCursor()).isNull();
    }
}
