package io.pragmatic.ddd.scenario.domain.person.service;

import io.pragmatic.ddd.scenario.domain.person.event.PersonInitEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonUpdateEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonUpdateStatusEvent;

public interface IScoreHandler {

    void eventHandler(PersonInitEvent event);
    void eventHandler(PersonUpdateEvent event);
    void eventHandler(PersonUpdateStatusEvent event);
}
