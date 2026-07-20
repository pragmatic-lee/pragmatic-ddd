package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.BaseDomainEvent;

/**
 * @author lixiaojing
 * @date 2021/3/18 3:02 下午
 */
public class ShareDomainEvent extends BaseDomainEvent {

    public ShareDomainEvent(String id, String name) {
        super(id);
        this.name = name;
    }

    protected ShareDomainEvent() {
    }

    public String name;
}
