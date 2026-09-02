package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;

import io.pragmatic.ddd.repository.query.projection.fixture.StubProjection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryPage 分页查询并返回分页元数据的契约。
 *
 * @author wizard-lee
 */
class IQueryPageTest {

    private record Criteria(String key) implements PageQueryCriteria {}

    private static final class InMemoryQuery implements IQueryPage<StubProjection, Criteria> {
        @Override
        public <X extends StubProjection> PageResult<X> queryPage(
                Criteria query, PageRequest pageRequest, Class<X> projectionType) {
            return PageResult.of(List.of((X) new StubProjection(1L, "a")), 1L, pageRequest);
        }
    }

    @Test
    void queryPage_returnsResultWithMeta() {
        IQueryPage<StubProjection, Criteria> query = new InMemoryQuery();
        PageRequest request = PageRequest.of(1, 10);
        PageResult<StubProjection> result = query.queryPage(new Criteria("k"), request, StubProjection.class);
        assertThat(result.data().stream().map(StubProjection::name)).containsExactly("a");
        assertThat(result.totalCount()).isEqualTo(1L);
        assertThat(result.request()).isSameAs(request);
    }
}
