package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IQueryByIdsTest {

    private static final class InMemoryQuery implements IQueryByIds<Long, String> {
        private final Map<Long, String> store = Map.of(1L, "a", 2L, "b");

        @Override
        public List<String> queryByIds(List<Long> ids) {
            return ids.stream().map(store::get).filter(java.util.Objects::nonNull).toList();
        }
    }

    @Test
    void queryByIds_returnsMatchedInOrder() {
        IQueryByIds<Long, String> query = new InMemoryQuery();
        assertThat(query.queryByIds(List.of(2L, 1L))).containsExactly("b", "a");
    }

    @Test
    void queryByIds_noMatch_returnsEmptyList() {
        IQueryByIds<Long, String> query = new InMemoryQuery();
        assertThat(query.queryByIds(List.of(99L))).isEmpty();
    }
}
