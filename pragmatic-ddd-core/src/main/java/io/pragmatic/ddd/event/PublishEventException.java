package io.pragmatic.ddd.event;


public class PublishEventException extends EventException {

    public PublishEventException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
