package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 IQueryPage 分页查询并返回分页元数据的契约。
 *
 * @author wizard-lee
 */
class IQueryPageTest {

    private record Criteria(String key) {}

    private static final class InMemoryQuery implements IQueryPage<String, Criteria> {
        @Override
        public PageResult<String> queryPage(Criteria query, PageRequest pageRequest) {
            return PageResult.of(List.of("a"), 1L, pageRequest);
        }
    }

    @Test
    void queryPage_returnsResultWithMeta() {
        IQueryPage<String, Criteria> query = new InMemoryQuery();
        PageRequest request = PageRequest.of(1, 10);
        PageResult<String> result = query.queryPage(new Criteria("k"), request);
        assertThat(result.data()).containsExactly("a");
        assertThat(result.totalCount()).isEqualTo(1L);
        assertThat(result.request()).isSameAs(request);
    }
}
