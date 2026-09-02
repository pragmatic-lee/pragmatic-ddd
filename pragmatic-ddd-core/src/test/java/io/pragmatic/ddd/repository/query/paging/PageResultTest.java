package io.pragmatic.ddd.repository.query.paging;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 PageResult 分页结果的数据与元信息承载。
 *
 * @author wizard-lee
 */
class PageResultTest {

    @Test
    void of_holdsDataAndMeta() {
        PageRequest request = PageRequest.of(1, 10);
        PageResult<String> result = PageResult.of(List.of("a", "b"), 2L, request);
        assertThat(result.data()).containsExactly("a", "b");
        assertThat(result.totalCount()).isEqualTo(2L);
        assertThat(result.request()).isSameAs(request);
    }

    @Test
    void data_isDefensiveCopy_immutable() {
        List<String> source = new java.util.ArrayList<>(List.of("a"));
        PageResult<String> result = PageResult.of(source, 1L, PageRequest.of(1, 10));
        source.add("b");
        assertThat(result.data()).containsExactly("a");
        assertThatThrownBy(() -> result.data().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
