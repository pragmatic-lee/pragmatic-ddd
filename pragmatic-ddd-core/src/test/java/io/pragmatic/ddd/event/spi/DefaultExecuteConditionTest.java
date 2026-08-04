package io.pragmatic.ddd.event.spi;

import io.pragmatic.ddd.event.internal.defaults.DefaultExecuteCondition;
import io.pragmatic.ddd.event.support.TestDomainEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证默认执行条件恒返回 EXECUTE 的行为。
 *
 * @author wizard-lee
 */
class DefaultExecuteConditionTest {

    @Test
    void status_alwaysReturnsExecute() {
        DefaultExecuteCondition<TestDomainEvent> condition = new DefaultExecuteCondition<>();
        assertThat(condition.status(new TestDomainEvent())).isEqualTo(ExecuteStatus.EXECUTE);
    }

    @Test
    void status_withNullEvent_stillReturnsExecute() {
        DefaultExecuteCondition<TestDomainEvent> condition = new DefaultExecuteCondition<>();
        assertThat(condition.status(null)).isEqualTo(ExecuteStatus.EXECUTE);
    }
}
