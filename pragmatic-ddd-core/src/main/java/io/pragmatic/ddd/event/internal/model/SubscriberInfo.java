package io.pragmatic.ddd.event.internal.model;

import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.ISubscriber;

/**
 * 事件订阅对象
 *
 * @author lixiaojing
 */
public class SubscriberInfo {

    private final ISubscriber subscriber;
    private final String alias;
    private final IExecuteCondition condition;
    private final DeliveryPolicy deliveryPolicy;

    public SubscriberInfo(ISubscriber subscriber, String alias,
                          IExecuteCondition condition, DeliveryPolicy deliveryPolicy) {
        this.subscriber = subscriber;
        this.alias = alias;
        this.condition = condition;
        this.deliveryPolicy = deliveryPolicy;
    }

    public ISubscriber getSubscriber() {
        return subscriber;
    }

    public String getAlias() {
        return alias;
    }

    public IExecuteCondition getCondition() {
        return condition;
    }

    public DeliveryPolicy getDeliveryPolicy() {
        return deliveryPolicy;
    }

    public boolean isDelayed() {
        return deliveryPolicy == DeliveryPolicy.DELAYED;
    }
}
