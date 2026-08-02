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
public class ShareDomainEvent extends BaseDomainEvent {

    private String name;

    protected ShareDomainEvent(String entityId, String name) {
        super(entityId);
        this.name = name;
    }

    public static ShareDomainEvent buildEvent(String entityId, String name) {
        return new ShareDomainEvent(entityId, name);
    }
}
