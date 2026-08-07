package io.pragmatic.ddd.application.outbox;

import io.pragmatic.ddd.base.fixture.SampleEvent;
import io.pragmatic.ddd.event.IDomainEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OutboxEntry 配对结构测试：验证原始事件与 outbox 行一一对应、字段透传。
  * @author wizard-lee
 */
class OutboxEntryTest {

    @Test
    void entry_holdsEventAndMessagePair() {
        IDomainEvent event = new SampleEvent("e-1");
        OutboxMessage message = new OutboxMessage();
        message.setId("m-1");

        OutboxEntry entry = new OutboxEntry(event, message);

        assertThat(entry.event()).isSameAs(event);
        assertThat(entry.message()).isSameAs(message);
    }

    @Test
    void record_equalsAndHashCode_areValueBased() {
        IDomainEvent event = new SampleEvent("e-1");
        OutboxMessage message = new OutboxMessage();
        message.setId("m-1");

        OutboxEntry a = new OutboxEntry(event, message);
        OutboxEntry b = new OutboxEntry(event, message);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
