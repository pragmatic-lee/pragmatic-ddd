package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.fixture.StubProjection;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryByIds 按主键集合批量查询并保序的契约。
 *
 * @author wizard-lee
 */
class IQueryByIdsTest {

    private static final class InMemoryQuery implements IQueryByIds<Long, StubProjection> {
        private final Map<Long, StubProjection> store = Map.of(
                1L, new StubProjection(1L, "a"),
                2L, new StubProjection(2L, "b"));

        @Override
        public <X extends StubProjection> List<X> queryByIds(List<Long> ids, Class<X> projectionType) {
            return ids.stream().map(store::get).filter(java.util.Objects::nonNull).map(p -> (X) p).toList();
        }
    }

    @Test
    void queryByIds_returnsMatchedInOrder() {
        IQueryByIds<Long, StubProjection> query = new InMemoryQuery();
        assertThat(query.queryByIds(List.of(2L, 1L), StubProjection.class).stream().map(StubProjection::name))
                .containsExactly("b", "a");
    }

    @Test
    void queryByIds_noMatch_returnsEmptyList() {
        IQueryByIds<Long, StubProjection> query = new InMemoryQuery();
        assertThat(query.queryByIds(List.of(99L), StubProjection.class)).isEmpty();
    }
}
