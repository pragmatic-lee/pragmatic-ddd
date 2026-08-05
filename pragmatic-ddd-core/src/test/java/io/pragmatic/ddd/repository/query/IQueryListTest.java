package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IQueryListTest {

    private record Criteria(String key) {}

    private static final class InMemoryQuery implements IQueryList<String, Criteria> {
        @Override
        public List<String> queryList(Criteria query) {
            return "k".equals(query.key()) ? List.of("a", "b") : List.of();
        }
    }

    @Test
    void queryList_hit_returnsList() {
        IQueryList<String, Criteria> query = new InMemoryQuery();
        assertThat(query.queryList(new Criteria("k"))).containsExactly("a", "b");
    }

    @Test
    void queryList_miss_returnsEmptyList() {
        IQueryList<String, Criteria> query = new InMemoryQuery();
        assertThat(query.queryList(new Criteria("x"))).isEmpty();
    }
}
