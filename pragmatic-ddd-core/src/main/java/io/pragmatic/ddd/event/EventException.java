package io.pragmatic.ddd.event;

import io.pragmatic.ddd.base.PragmaticException;

/**
 * 领域事件类异常的抽象基类。
 *
 * <p>覆盖事件注册失败和事件发布失败两种场景。</p>
 *
 * @see PublishEventException
 * @see RegisterDomainEventException
 * @author lixiaojing10
 */
public abstract class EventException extends PragmaticException {

    protected EventException() {
        super();
    }

    protected EventException(String message) {
        super(message);
    }

    protected EventException(String message, Throwable cause) {
        super(message, cause);
    }
}
