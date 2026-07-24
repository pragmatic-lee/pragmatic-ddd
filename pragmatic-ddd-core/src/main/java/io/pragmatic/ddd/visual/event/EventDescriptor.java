package io.pragmatic.ddd.visual.event;

import java.util.List;

public class EventDescriptor {

    private final String eventName;
    private final String eventDescription;
    private final List<EventSubscriberDescriptor> subscriberDescriptorList;

    public EventDescriptor(String eventName, String eventDescription,
                           List<EventSubscriberDescriptor> subscriberDescriptorList) {
        this.eventName = eventName;
        this.eventDescription = eventDescription;
        this.subscriberDescriptorList = subscriberDescriptorList;
    }


    public String getEventName() {
        return eventName;
    }

    public String getEventDescription() {
        return eventDescription;
    }

    public List<EventSubscriberDescriptor> getSubscriberDescriptorList() {
        return subscriberDescriptorList;
    }
}
