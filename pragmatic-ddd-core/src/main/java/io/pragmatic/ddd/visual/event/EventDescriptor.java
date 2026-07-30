package io.pragmatic.ddd.visual.event;

import java.util.List;

/**
 * 领域事件描述符 —— 承载事件名称、描述及其订阅者描述符列表。
 *
 * @author wizard-lee
 */
public class EventDescriptor {

    private final String eventName;
    private final String eventDescription;
    private final List<EventSubscriberDescriptor> subscriberDescriptorList;

    /** 构造领域事件描述符。 */
    public EventDescriptor(String eventName, String eventDescription,
                           List<EventSubscriberDescriptor> subscriberDescriptorList) {
        this.eventName = eventName;
        this.eventDescription = eventDescription;
        this.subscriberDescriptorList = subscriberDescriptorList;
    }


    /** 返回事件名称。 */
    public String getEventName() {
        return eventName;
    }

    /** 返回事件描述。 */
    public String getEventDescription() {
        return eventDescription;
    }

    /** 返回订阅者描述符列表。 */
    public List<EventSubscriberDescriptor> getSubscriberDescriptorList() {
        return subscriberDescriptorList;
    }
}
