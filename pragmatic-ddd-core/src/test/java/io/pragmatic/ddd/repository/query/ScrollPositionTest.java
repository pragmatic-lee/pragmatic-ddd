package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ScrollPosition 游标初始态与构造语义。
 *
 * @author wizard-lee
 */
class ScrollPositionTest {

    @Test
    void initial_isInitialAndCursorNull() {
        ScrollPosition position = ScrollPosition.initial();
        assertThat(position.isInitial()).isTrue();
        assertThat(position.cursor()).isNull();
    }

    @Test
    void of_withCursor_isNotInitial() {
        ScrollPosition position = ScrollPosition.of("cur-1");
        assertThat(position.isInitial()).isFalse();
        assertThat(position.cursor()).isEqualTo("cur-1");
    }
}
