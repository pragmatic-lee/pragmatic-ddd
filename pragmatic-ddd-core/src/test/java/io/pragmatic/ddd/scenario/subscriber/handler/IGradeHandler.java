package io.pragmatic.ddd.scenario.subscriber.handler;

import io.pragmatic.ddd.scenario.event.PersonInitEvent;
import io.pragmatic.ddd.scenario.event.PersonUpdateEvent;

public interface IGradeHandler {

    void eventHandler(PersonInitEvent event);
    void eventHandler(PersonUpdateEvent event);
}
