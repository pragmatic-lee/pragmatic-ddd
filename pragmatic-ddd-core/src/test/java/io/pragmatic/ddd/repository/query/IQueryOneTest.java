package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.fixture.StubProjection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryOne 按条件查询单个投影（命中/未命中）的契约。
 *
 * @author wizard-lee
 */
class IQueryOneTest {

    private record Criteria(String key) implements OneQueryCriteria {}

    private static final class InMemoryQuery implements IQueryOne<StubProjection, Criteria> {
        @Override
        public <X extends StubProjection> X queryOne(Criteria query, Class<X> projectionType) {
            return "k".equals(query.key()) ? (X) new StubProjection(1L, "hit") : null;
        }
    }

    @Test
    void queryOne_hit_returnsProjection() {
        IQueryOne<StubProjection, Criteria> query = new InMemoryQuery();
        assertThat(query.queryOne(new Criteria("k"), StubProjection.class).name()).isEqualTo("hit");
    }

    @Test
    void queryOne_miss_returnsNull() {
        IQueryOne<StubProjection, Criteria> query = new InMemoryQuery();
        assertThat(query.queryOne(new Criteria("x"), StubProjection.class)).isNull();
    }
}
