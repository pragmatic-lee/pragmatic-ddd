package io.pragmatic.ddd.scenario.application.person.subscriber;

import io.pragmatic.ddd.scenario.domain.person.event.PersonInitEvent;
import io.pragmatic.ddd.scenario.domain.person.service.IGradeHandler;
import io.pragmatic.ddd.scenario.domain.person.service.IScoreHandler;
import io.pragmatic.ddd.event.spi.IEventRegistry;

public class PersonInitEventSubscriber {
    protected PersonInitEventSubscriber(IEventRegistry evtManager,
                                        IScoreHandler iScoreHandler,
                                        IGradeHandler gradeHandler) {

        evtManager.registerSubscriber(PersonSubscriberRegistry.UPDATE_SCORE,
                PersonInitEvent.class, iScoreHandler::eventHandler);
        evtManager.registerSubscriber(PersonSubscriberRegistry.UPDATE_GRADE,
                PersonInitEvent.class, gradeHandler::eventHandler);
    }
}
