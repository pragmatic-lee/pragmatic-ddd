package io.pragmatic.ddd.repository.query;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
