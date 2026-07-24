package io.pragmatic.ddd.event;

import lombok.Getter;

/**
 * 领域事件对象
 * @author lixiaojing
 * @date 2021/2/2 11:27 上午
 */
@Getter
public class TestDomainEvent extends BaseDomainEvent {
    private String name;


    public TestDomainEvent(String name) {
        super(name);
        this.name = name;
    }
    protected TestDomainEvent(){

    }
}
