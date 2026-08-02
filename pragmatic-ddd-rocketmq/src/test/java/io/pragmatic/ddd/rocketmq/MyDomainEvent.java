package io.pragmatic.ddd.rocketmq;

import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * RocketMQ 测试用领域事件示例。
 *
 * @author wizard-lee
 */
@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyDomainEvent extends BaseDomainEvent {

    private String name;

    protected MyDomainEvent(String entityId, String name) {
        super(entityId);
        this.name = name;
    }

    public static MyDomainEvent buildEvent(String entityId, String name) {
        return new MyDomainEvent(entityId, name);
    }
}
