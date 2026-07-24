package io.pragmatic.ddd.event;


public class RegisterDomainEventException extends EventException {

    public RegisterDomainEventException(String message) {
        super(message);
    }

    public RegisterDomainEventException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
