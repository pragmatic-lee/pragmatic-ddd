package io.pragmatic.ddd.base.test2.subscriber;

import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.base.test2.event.PersonInitEvent;
import io.pragmatic.ddd.base.test2.subscriber.handler.IGradeHandler;
import io.pragmatic.ddd.base.test2.subscriber.handler.IScoreHandler;
import io.pragmatic.ddd.event.spi.IEventRegistry;

public class PersonInitEventSubscriber {
    protected PersonInitEventSubscriber(IEventRegistry evtManager,
                                        IScoreHandler iScoreHandler,
                                        IGradeHandler gradeHandler) {

        evtManager.registerSubscriber(PersonSubscriberKey.updateScore,
                PersonInitEvent.class, iScoreHandler::eventHandler);
        evtManager.registerSubscriber(PersonSubscriberKey.updateGrade,
                PersonInitEvent.class, gradeHandler::eventHandler);
    }
}
