package io.pragmatic.ddd.event;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TriggeredEvents 单元测试（自 base/test1 迁入，迁 JUnit5 + AssertJ）。
 */
class TriggeredEventsTest {

    @Test
    void eventCollectorTest() {
        TriggeredEvents triggeredEvents = new TriggeredEvents();

        triggeredEvents.collect(new Demo1Event());
        triggeredEvents.collect(new Demo2Event());

        final long id = System.currentTimeMillis();

        triggeredEvents.collectDelayed(() -> {
            Demo3Event demo3Event = new Demo3Event();
            demo3Event.id = id;
            return demo3Event;
        });

        assertThat(triggeredEvents.getEvents()).hasSize(3);

        triggeredEvents.removeEvent(Demo1Event.class);

        assertThat(triggeredEvents.getEvents()).hasSize(2);
    }

    /**
     * 幂等：连续两次 getEvents() 返回的延迟事件应为同一对象实例（materializeDeferred 仅求值一次）。
     */
    @Test
    void deferredEventIsIdempotent() {
        TriggeredEvents triggeredEvents = new TriggeredEvents();
        triggeredEvents.collect(new Demo1Event());
        triggeredEvents.collectDelayed(() -> new Demo3Event());

        List<IDomainEvent> first = triggeredEvents.getEvents();
        List<IDomainEvent> second = triggeredEvents.getEvents();

        IDomainEvent firstDeferred = first.stream()
                .filter(e -> e instanceof Demo3Event)
                .findFirst()
                .orElseThrow();
        IDomainEvent secondDeferred = second.stream()
                .filter(e -> e instanceof Demo3Event)
                .findFirst()
                .orElseThrow();

        assertThat(firstDeferred).isSameAs(secondDeferred);
    }

    /**
     * drain()：返回全部事件且之后 getEvents() 为空。
     */
    @Test
    void drainReturnsAllAndClears() {
        TriggeredEvents triggeredEvents = new TriggeredEvents();
        triggeredEvents.collect(new Demo1Event());
        triggeredEvents.collect(new Demo2Event());
        triggeredEvents.collectDelayed(() -> new Demo3Event());

        List<IDomainEvent> drained = triggeredEvents.drain();
        assertThat(drained).hasSize(3);
        assertThat(triggeredEvents.getEvents()).isEmpty();
    }

    /**
     * removeEvent 按基类：登记 Demo1Event 的子类实例后，removeEvent(Demo1Event.class) 应移除子类实例。
     */
    @Test
    void removeEventByBaseTypeRemovesSubclass() {
        TriggeredEvents triggeredEvents = new TriggeredEvents();
        triggeredEvents.collect(new Demo1Event());
        triggeredEvents.collect(new Demo1EventSub());

        triggeredEvents.removeEvent(Demo1Event.class);

        assertThat(triggeredEvents.getEvents()).isEmpty();
    }

    /**
     * getEvents() 返回不可变快照：对结果执行 add(...) 应抛 UnsupportedOperationException。
     */
    @Test
    void getEventsReturnsImmutableSnapshot() {
        TriggeredEvents triggeredEvents = new TriggeredEvents();
        triggeredEvents.collect(new Demo1Event());

        assertThatThrownBy(() -> triggeredEvents.getEvents().add(new Demo2Event()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

}

class Demo1Event extends BaseDomainEvent {
    public int id;

    public Demo1Event() {
        super("demo1");
    }

    protected Demo1Event(String bizId) {
        super(bizId);
    }
}

/** Demo1Event 的子类，用于验证 removeEvent 按基类移除。 */
class Demo1EventSub extends Demo1Event {
    public Demo1EventSub() {
        super("demo1sub");
    }
}

class Demo2Event extends BaseDomainEvent {
    public int id;

    public Demo2Event() {
        super("demo2");
    }

    protected Demo2Event(String bizId) {
        super(bizId);
    }
}

class Demo3Event extends BaseDomainEvent {
    public long id;

    public Demo3Event() {
        super("demo3");
    }

    protected Demo3Event(String bizId) {
        super(bizId);
    }
}
