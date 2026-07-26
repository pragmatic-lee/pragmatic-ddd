package io.pragmatic.ddd.base.test1;

import io.pragmatic.ddd.event.BaseDomainEvent;
import io.pragmatic.ddd.event.IDomainEvent;
import io.pragmatic.ddd.event.TriggeredEvents;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author lixiaojing10
 * @date 2022/3/12 4:29 下午
 */
public class TriggeredEventsTest {

    @Test
    public void eventCollectorTest() {

        TriggeredEvents triggeredEvents = new TriggeredEvents();

        triggeredEvents.collect(new Demo1Event());
        triggeredEvents.collect(new Demo2Event());

        final long id = System.currentTimeMillis();

        triggeredEvents.collectDelayed(() -> {

            Demo3Event demo3Event = new Demo3Event();
            demo3Event.id = id;

            return demo3Event;
        });

        Assert.assertEquals(3, triggeredEvents.getEvents().size());

        triggeredEvents.removeEvent(Demo1Event.class);

        Assert.assertEquals(2, triggeredEvents.getEvents().size());
    }

    /**
     * 幂等：连续两次 getEvents() 返回的延迟事件应为同一对象实例（materializeDeferred 仅求值一次）。
     */
    @Test
    public void deferredEventIsIdempotent() {
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

        Assert.assertSame("延迟事件应只被物化一次，两次读取返回同一实例", firstDeferred, secondDeferred);
    }

    /**
     * drain()：返回全部事件且之后 getEvents() 为空。
     */
    @Test
    public void drainReturnsAllAndClears() {
        TriggeredEvents triggeredEvents = new TriggeredEvents();
        triggeredEvents.collect(new Demo1Event());
        triggeredEvents.collect(new Demo2Event());
        triggeredEvents.collectDelayed(() -> new Demo3Event());

        List<IDomainEvent> drained = triggeredEvents.drain();
        Assert.assertEquals(3, drained.size());
        Assert.assertTrue("drain() 后内部应已清空", triggeredEvents.getEvents().isEmpty());
    }

    /**
     * removeEvent 按基类：登记 Demo1Event 的子类实例后，removeEvent(Demo1Event.class) 应移除子类实例。
     */
    @Test
    public void removeEventByBaseTypeRemovesSubclass() {
        TriggeredEvents triggeredEvents = new TriggeredEvents();
        triggeredEvents.collect(new Demo1Event());
        triggeredEvents.collect(new Demo1EventSub());

        triggeredEvents.removeEvent(Demo1Event.class);

        Assert.assertEquals(0, triggeredEvents.getEvents().size());
    }

    /**
     * getEvents() 返回不可变快照：对结果执行 add(...) 应抛 UnsupportedOperationException。
     */
    @Test
    public void getEventsReturnsImmutableSnapshot() {
        TriggeredEvents triggeredEvents = new TriggeredEvents();
        triggeredEvents.collect(new Demo1Event());

        boolean threw = false;
        try {
            triggeredEvents.getEvents().add(new Demo2Event());
        } catch (UnsupportedOperationException ignored) {
            threw = true;
        }
        Assert.assertTrue("getEvents() 应返回不可变列表", threw);
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
