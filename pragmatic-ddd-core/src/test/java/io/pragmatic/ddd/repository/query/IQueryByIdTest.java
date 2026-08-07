package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryById 按主键查询投影的契约。
 *
 * @author wizard-lee
 */
class IQueryByIdTest {

    private static final class InMemoryQuery implements IQueryById<Long, String> {
        private final Map<Long, String> store = Map.of(1L, "a", 2L, "b");

        @Override
        public String queryById(Long id) {
            return store.get(id);
        }
    }

    @Test
    void queryById_hit_returnsProjection() {
        IQueryById<Long, String> query = new InMemoryQuery();
        assertThat(query.queryById(1L)).isEqualTo("a");
    }

    @Test
    void queryById_miss_returnsNull() {
        IQueryById<Long, String> query = new InMemoryQuery();
        assertThat(query.queryById(99L)).isNull();
    }
}
