package io.pragmatic.ddd.visual.event;

import io.pragmatic.ddd.base.AbstractSubscriberKey;
import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.visual.MockDomainEventManager;
import io.pragmatic.ddd.visual.MockEntity;
import io.pragmatic.ddd.visual.TestEvent;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EventParserTest {

    @Test
    public void eventParserTest() {
        EventParser eventParser = new EventParser(MockDomainEventManager.mockIDomainEventManager());
        eventParser.registerDomainEvent(MockEntity.class, mockEventFinder());
        List<EventDescriptor> parse = eventParser.parse(MockEntity.class);

        String s = JSON.toJSONString(parse);
        System.out.println(s);
    }

    private IEventFinder mockEventFinder() {
        return new IEventFinder() {
            @Override
            public <T extends AbstractEntity<?>> List<Class<?>> findersList(Class<T> cls) {
                return Stream.of(TestEvent.class).collect(Collectors.toList());
            }

            @Override
            public AbstractSubscriberKey eventSubscribeKey() {
                return new AbstractSubscriberKey() {
                    @Override
                    protected void populateKeys() {
                    }
                };
            }
        };
    }
}
