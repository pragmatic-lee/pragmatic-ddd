package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.fixture.StubProjection;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryById 按主键查询投影的契约。
 *
 * @author wizard-lee
 */
class IQueryByIdTest {

    private static final class InMemoryQuery implements IQueryById<Long, StubProjection> {
        private final Map<Long, StubProjection> store = Map.of(
                1L, new StubProjection(1L, "a"),
                2L, new StubProjection(2L, "b"));

        @Override
        public <X extends StubProjection> X queryById(Long id, Class<X> projectionType) {
            return (X) store.get(id);
        }
    }

    @Test
    void queryById_hit_returnsProjection() {
        IQueryById<Long, StubProjection> query = new InMemoryQuery();
        assertThat(query.queryById(1L, StubProjection.class).name()).isEqualTo("a");
    }

    @Test
    void queryById_miss_returnsNull() {
        IQueryById<Long, StubProjection> query = new InMemoryQuery();
        assertThat(query.queryById(99L, StubProjection.class)).isNull();
    }
}
