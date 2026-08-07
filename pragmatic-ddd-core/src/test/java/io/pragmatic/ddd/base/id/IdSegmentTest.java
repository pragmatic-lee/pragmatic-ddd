package io.pragmatic.ddd.base.id;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IdSegment 号段值对象测试：验证区间 [current, max] 的分配语义、不可变性与耗尽异常。
  * @author wizard-lee
 */
class IdSegmentTest {

    @Test
    void record_accessors_exposeCurrentMaxAndStep() {
        IdSegment segment = new IdSegment(1, 5, 5);
        assertThat(segment.current()).isEqualTo(1);
        assertThat(segment.max()).isEqualTo(5);
        assertThat(segment.step()).isEqualTo(5);
    }

    @Test
    void hasNext_trueWhenCurrentWithinMax() {
        assertThat(new IdSegment(1, 5, 5).hasNext()).isTrue();
        assertThat(new IdSegment(5, 5, 5).hasNext()).isTrue();
    }

    @Test
    void hasNext_falseWhenCurrentExceedsMax() {
        assertThat(new IdSegment(6, 5, 5).hasNext()).isFalse();
    }

    @Test
    void take_advancesCurrentAndKeepsSegmentImmutable() {
        IdSegment segment = new IdSegment(1, 5, 5);

        IdSegment next = segment.take();

        assertThat(next.current()).isEqualTo(2);
        assertThat(next.max()).isEqualTo(5);
        // 原号段未被修改（不可变）
        assertThat(segment.current()).isEqualTo(1);
    }

    @Test
    void take_whenExhausted_throwsIllegalState() {
        IdSegment exhausted = new IdSegment(6, 5, 5);

        assertThatThrownBy(exhausted::take)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("号段已耗尽");
    }

    @Test
    void remaining_countsAvailableIds() {
        assertThat(new IdSegment(1, 5, 5).remaining()).isEqualTo(5);
        assertThat(new IdSegment(5, 5, 5).remaining()).isEqualTo(1);
    }

    @Test
    void remaining_whenExhausted_returnsZero() {
        assertThat(new IdSegment(6, 5, 5).remaining()).isZero();
    }
}
