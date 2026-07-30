package io.pragmatic.ddd.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RulePosition} 枚举冒烟测试。
 *
 * @author wizard-lee
 */
class RulePositionTest {

    @Test
    void values_containsLastBeforeAfter() {
        assertThat(RulePosition.values())
                .containsExactly(RulePosition.LAST, RulePosition.BEFORE, RulePosition.AFTER);
    }
}
