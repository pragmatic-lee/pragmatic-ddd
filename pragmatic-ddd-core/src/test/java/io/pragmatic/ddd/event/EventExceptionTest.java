package io.pragmatic.ddd.event;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 event 根包下两个具体事件异常类的构造行为。
 *
 * @author wizard-lee
 */
class EventExceptionTest {

    @Test
    void publishEventException_withMessageAndCause_holdsBoth() {
        Throwable cause = new RuntimeException("root");
        PublishEventException exception = new PublishEventException("boom", cause);
        assertThat(exception.getMessage()).isEqualTo("boom");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void registerDomainEventException_withMessage_holdsMessage() {
        RegisterDomainEventException exception = new RegisterDomainEventException("dup");
        assertThat(exception.getMessage()).isEqualTo("dup");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void registerDomainEventException_withMessageAndCause_holdsBoth() {
        Throwable cause = new IllegalStateException("root");
        RegisterDomainEventException exception = new RegisterDomainEventException("dup", cause);
        assertThat(exception.getMessage()).isEqualTo("dup");
        assertThat(exception.getCause()).isSameAs(cause);
    }

    @Test
    void publishEventException_isThrowableAndCatchable() {
        assertThatThrownBy(() -> {
            throw new PublishEventException("boom", new RuntimeException());
        }).isInstanceOf(PublishEventException.class)
                .hasMessage("boom");
    }

    @Test
    void registerDomainEventException_isThrowableAndCatchable() {
        assertThatThrownBy(() -> {
            throw new RegisterDomainEventException("dup");
        }).isInstanceOf(RegisterDomainEventException.class)
                .hasMessage("dup");
    }
}
