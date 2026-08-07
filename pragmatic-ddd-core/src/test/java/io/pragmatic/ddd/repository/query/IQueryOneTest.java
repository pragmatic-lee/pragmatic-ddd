package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryOne 按条件查询单个投影（命中/未命中）的契约。
 *
 * @author wizard-lee
 */
class IQueryOneTest {

    private record Criteria(String key) {}

    private static final class InMemoryQuery implements IQueryOne<String, Criteria> {
        @Override
        public String queryOne(Criteria query) {
            return "k".equals(query.key()) ? "hit" : null;
        }
    }

    @Test
    void queryOne_hit_returnsProjection() {
        IQueryOne<String, Criteria> query = new InMemoryQuery();
        assertThat(query.queryOne(new Criteria("k"))).isEqualTo("hit");
    }

    @Test
    void queryOne_miss_returnsNull() {
        IQueryOne<String, Criteria> query = new InMemoryQuery();
        assertThat(query.queryOne(new Criteria("x"))).isNull();
    }
}
