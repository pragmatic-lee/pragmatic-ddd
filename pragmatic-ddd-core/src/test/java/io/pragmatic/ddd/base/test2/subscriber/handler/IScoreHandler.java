package io.pragmatic.ddd.base.test2.subscriber.handler;

import io.pragmatic.ddd.base.test2.event.PersonInitEvent;
import io.pragmatic.ddd.base.test2.event.PersonUpdateEvent;
import io.pragmatic.ddd.base.test2.event.PersonUpdateStatusEvent;

public interface IScoreHandler {

    void eventHandler(PersonInitEvent event);
    void eventHandler(PersonUpdateEvent event);
    void eventHandler(PersonUpdateStatusEvent event);
}
