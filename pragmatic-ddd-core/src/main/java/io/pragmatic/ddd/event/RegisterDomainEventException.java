package io.pragmatic.ddd.event;


/**
 * 事件注册失败异常。
 *
 * @author wizard-lee
 */
public class RegisterDomainEventException extends EventException {

    public RegisterDomainEventException(String message) {
        super(message);
    }

    public RegisterDomainEventException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
