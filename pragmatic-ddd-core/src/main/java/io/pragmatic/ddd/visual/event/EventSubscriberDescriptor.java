package io.pragmatic.ddd.visual.event;

/**
 * 事件订阅者描述符 —— 承载订阅者键、描述及其依赖的订阅者键。
 *
 * @author wizard-lee
 */
public class EventSubscriberDescriptor {
    private final String subscriberKey;
    private final String subscriberDescription;
    private final String dependsOnSubscriber;

    /** 构造事件订阅者描述符。 */
    public EventSubscriberDescriptor(String subscriberKey,
                                     String subscriberDescription,
                                     String dependsOnSubscriber) {
        this.subscriberKey = subscriberKey;
        this.subscriberDescription = subscriberDescription;
        this.dependsOnSubscriber = dependsOnSubscriber;
    }

    /** 返回订阅者键。 */
    public String getSubscriberKey() {
        return subscriberKey;
    }

    /** 返回订阅者描述。 */
    public String getSubscriberDescription() {
        return subscriberDescription;
    }

    /** 返回所依赖的订阅者键。 */
    public String getDependsOnSubscriber() {
        return dependsOnSubscriber;
    }
}
