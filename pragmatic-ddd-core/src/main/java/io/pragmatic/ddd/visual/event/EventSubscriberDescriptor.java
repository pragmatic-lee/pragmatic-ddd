package io.pragmatic.ddd.visual.event;

public class EventSubscriberDescriptor {
    private final String subscriberKey;
    private final String subscriberDescription;
    private final String dependsOnSubscriber;

    public EventSubscriberDescriptor(String subscriberKey,
                                     String subscriberDescription,
                                     String dependsOnSubscriber) {
        this.subscriberKey = subscriberKey;
        this.subscriberDescription = subscriberDescription;
        this.dependsOnSubscriber = dependsOnSubscriber;
    }

    public String getSubscriberKey() {
        return subscriberKey;
    }

    public String getSubscriberDescription() {
        return subscriberDescription;
    }

    public String getDependsOnSubscriber() {
        return dependsOnSubscriber;
    }
}
