package io.pragmatic.ddd.base.test2.subscriber.handler;

import io.pragmatic.ddd.base.test2.event.PersonInitEvent;
import io.pragmatic.ddd.base.test2.event.PersonUpdateEvent;

public interface IGradeHandler {

    void eventHandler(PersonInitEvent event);
    void eventHandler(PersonUpdateEvent event);
}
