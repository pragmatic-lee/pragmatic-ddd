package io.pragmatic.ddd.event;

import io.pragmatic.ddd.event.support.TestDomainEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证已触发领域事件收集器的收集、惰性求值、取空与移除行为。
 *
 * @author wizard-lee
 */
class TriggeredEventsTest {

    @Test
    void collect_thenGetEvents_containsEvent() {
        TriggeredEvents triggered = new TriggeredEvents();
        IDomainEvent event = new TestDomainEvent();
        triggered.collect(event);
        List<IDomainEvent> events = triggered.getEvents();
        assertThat(events).containsExactly(event);
    }

    @Test
    void collectDelayed_materializedOnceOnGetEvents() {
        TriggeredEvents triggered = new TriggeredEvents();
        int[] counter = {0};
        triggered.collectDelayed(() -> {
            counter[0]++;
            return new TestDomainEvent();
        });
        triggered.getEvents();
        triggered.getEvents();
        assertThat(counter[0]).isEqualTo(1);
    }

    @Test
    void getEvents_returnsImmutableSnapshot() {
        TriggeredEvents triggered = new TriggeredEvents();
        triggered.collect(new TestDomainEvent());
        List<IDomainEvent> snapshot = triggered.getEvents();
        assertThat(snapshot).isNotEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshot.add(new TestDomainEvent()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void drain_returnsAllAndClears() {
        TriggeredEvents triggered = new TriggeredEvents();
        IDomainEvent e1 = new TestDomainEvent();
        IDomainEvent e2 = new TestDomainEvent();
        triggered.collect(e1);
        triggered.collectDelayed(() -> e2);
        List<IDomainEvent> drained = triggered.drain();
        assertThat(drained).containsExactly(e1, e2);
        assertThat(triggered.getEvents()).isEmpty();
    }

    @Test
    void removeEvent_byType_removesMatching() {
        TriggeredEvents triggered = new TriggeredEvents();
        IDomainEvent target = new TestDomainEvent();
        triggered.collect(target);
        triggered.collect(new OtherEvent());
        triggered.removeEvent(TestDomainEvent.class);
        assertThat(triggered.getEvents()).hasSize(1);
        assertThat(triggered.getEvents().get(0)).isInstanceOf(OtherEvent.class);
    }

    @Test
    void clear_emptiesBothImmediateAndDeferred() {
        TriggeredEvents triggered = new TriggeredEvents();
        triggered.collect(new TestDomainEvent());
        triggered.collectDelayed(TestDomainEvent::new);
        triggered.clear();
        assertThat(triggered.getEvents()).isEmpty();
    }

    static class OtherEvent extends BaseDomainEvent {

        OtherEvent() {
            super("other-agg");
        }
    }
}
