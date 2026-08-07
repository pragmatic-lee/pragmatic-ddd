package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 ScrollResult 滚动结果的数据与下一游标承载。
 *
 * @author wizard-lee
 */
class ScrollResultTest {

    @Test
    void of_holdsDataAndNextCursor() {
        ScrollResult<String> result = ScrollResult.of(List.of("a"), "next");
        assertThat(result.data()).containsExactly("a");
        assertThat(result.nextCursor()).isEqualTo("next");
    }

    @Test
    void nextCursorNull_meansLastPage() {
        ScrollResult<String> result = ScrollResult.of(List.of("a"), null);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void data_isDefensiveCopy_immutable() {
        List<String> source = new java.util.ArrayList<>(List.of("a"));
        ScrollResult<String> result = ScrollResult.of(source, null);
        source.add("b");
        assertThat(result.data()).containsExactly("a");
        assertThatThrownBy(() -> result.data().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
