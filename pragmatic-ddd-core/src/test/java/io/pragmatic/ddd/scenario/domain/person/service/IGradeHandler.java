package io.pragmatic.ddd.scenario.domain.person.service;

import io.pragmatic.ddd.scenario.domain.person.event.PersonInitEvent;
import io.pragmatic.ddd.scenario.domain.person.event.PersonUpdateEvent;

public interface IGradeHandler {

    void eventHandler(PersonInitEvent event);
    void eventHandler(PersonUpdateEvent event);
}
