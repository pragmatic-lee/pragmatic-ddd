package io.pragmatic.ddd.scenario.subscriber.handler;

import io.pragmatic.ddd.scenario.event.PersonInitEvent;
import io.pragmatic.ddd.scenario.event.PersonUpdateEvent;
import io.pragmatic.ddd.scenario.event.PersonUpdateStatusEvent;

public interface IScoreHandler {

    void eventHandler(PersonInitEvent event);
    void eventHandler(PersonUpdateEvent event);
    void eventHandler(PersonUpdateStatusEvent event);
}
