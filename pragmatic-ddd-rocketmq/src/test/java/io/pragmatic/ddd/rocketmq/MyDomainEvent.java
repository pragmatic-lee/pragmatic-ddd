package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.BaseDomainEvent;

/**
 * @author lixiaojing
 * @date 2021/3/17 5:24 下午
 */
public class MyDomainEvent extends BaseDomainEvent {

    public MyDomainEvent(String name) {
        super(name);
        this.name = name;
    }

    protected MyDomainEvent() {
    }

    public String name;
}
