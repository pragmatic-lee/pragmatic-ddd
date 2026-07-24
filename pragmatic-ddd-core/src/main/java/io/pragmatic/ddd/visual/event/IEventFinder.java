package io.pragmatic.ddd.visual.event;

import io.pragmatic.ddd.base.AbstractSubscriberKey;
import io.pragmatic.ddd.base.AbstractEntity;
import java.util.List;

public interface IEventFinder {

    <T extends AbstractEntity<?>> List<Class<?>> findersList(Class<T> cls);

    AbstractSubscriberKey eventSubscribeKey();
}
