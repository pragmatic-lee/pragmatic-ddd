package io.pragmatic.ddd.scenario.subscriber;

import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.scenario.event.PersonInitEvent;
import io.pragmatic.ddd.scenario.subscriber.handler.IGradeHandler;
import io.pragmatic.ddd.scenario.subscriber.handler.IScoreHandler;
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
