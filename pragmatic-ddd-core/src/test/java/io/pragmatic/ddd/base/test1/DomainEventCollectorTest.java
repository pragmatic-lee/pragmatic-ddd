package io.pragmatic.ddd.base.test1;

import io.pragmatic.ddd.event.DomainEventCollector;
import io.pragmatic.ddd.event.BaseDomainEvent;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author lixiaojing10
 * @date 2022/3/12 4:29 下午
 */
public class DomainEventCollectorTest {

    @Test
    public void eventCollectorTest() {

        DomainEventCollector domainEventCollector = new DomainEventCollector();

        domainEventCollector.pushEvent(new Demo1Event());
        domainEventCollector.pushEvent(new Demo2Event());

        final long id = System.currentTimeMillis();

        domainEventCollector.pushDelayGenerateEvent(() -> {

            Demo3Event demo3Event = new Demo3Event();
            demo3Event.id = id;

            return demo3Event;
        });

        Assert.assertEquals(3, domainEventCollector.getEventList().size());

        domainEventCollector.removeEvent(Demo1Event.class);

        Assert.assertEquals(2, domainEventCollector.getEventList().size());

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