package io.pragmatic.ddd.scenario.domain.person.event;

import io.pragmatic.ddd.event.BaseDomainEvent;
import lombok.Getter;

/**
 * 人员领域事件抽象基类，承载聚合标识 personId。
 *
 * @author wizard-lee
 */
@Getter
public abstract class BasePersonEvent extends BaseDomainEvent {

    protected final Long personId;

    protected BasePersonEvent(Long personId) {
        super(String.valueOf(personId));
        this.personId = personId;
    }

    protected BasePersonEvent() {
        super();
        this.personId = null;
    }
}
