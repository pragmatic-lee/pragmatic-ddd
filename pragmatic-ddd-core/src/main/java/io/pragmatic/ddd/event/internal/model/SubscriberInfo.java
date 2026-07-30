package io.pragmatic.ddd.event.internal.model;

import io.pragmatic.ddd.event.spi.IExecuteCondition;
import io.pragmatic.ddd.event.spi.ISubscriber;

/**
 * 事件订阅对象，承载订阅者、别名、执行条件与投递策略。
 *
 * @author wizard-lee
 */
public record SubscriberInfo(ISubscriber subscriber, String alias, IExecuteCondition condition,
                             DeliveryPolicy deliveryPolicy) {

    /**
     * 返回订阅者。
     */
    @Override
    public ISubscriber subscriber() {
        return subscriber;
    }

    /**
     * 返回别名。
     */
    @Override
    public String alias() {
        return alias;
    }

    /**
     * 返回执行条件。
     */
    @Override
    public IExecuteCondition condition() {
        return condition;
    }

    /**
     * 返回投递策略。
     */
    @Override
    public DeliveryPolicy deliveryPolicy() {
        return deliveryPolicy;
    }

    /**
     * 是否延时投递。
     */
    public boolean isDelayed() {
        return deliveryPolicy == DeliveryPolicy.DELAYED;
    }
}
