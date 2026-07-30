package io.pragmatic.ddd.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ActiveStatus} 枚举冒烟测试。
 *
 * @author wizard-lee
 */
class ActiveStatusTest {

    @Test
    void values_containsActiveAndInactive() {
        assertThat(ActiveStatus.values())
                .containsExactly(ActiveStatus.ACTIVE, ActiveStatus.INACTIVE);
    }

    @Test
    void valueOf_resolvesConstants() {
        assertThat(ActiveStatus.valueOf("ACTIVE")).isEqualTo(ActiveStatus.ACTIVE);
        assertThat(ActiveStatus.valueOf("INACTIVE")).isEqualTo(ActiveStatus.INACTIVE);
    }
}
