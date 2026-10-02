package io.pragmatic.ddd.application.compensation;

import io.pragmatic.ddd.application.compensation.spi.ICompensationLog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 补偿编排器工厂测试：durable 与日志装配的一致性守卫。
 *
 * @author wizard-lee
 */
class LocalCompensationManagerTest {

    @Test
    void begin_default_isInMemory() {
        assertThat(new LocalCompensationManager().begin()).isNotNull();
    }

    @Test
    void begin_durable_withoutLog_throwsIllegalState() {
        ICompensationManager manager = new LocalCompensationManager();

        assertThatThrownBy(() -> manager.begin(CompensationOptions.durableMode()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ICompensationLog");
    }

    @Test
    void begin_durable_withLog_succeeds() {
        ICompensationLog log = new InMemoryCompensationLog();
        ICompensationManager manager = new LocalCompensationManager(log);

        assertThat(manager.begin(CompensationOptions.durableMode())).isNotNull();
    }
}
