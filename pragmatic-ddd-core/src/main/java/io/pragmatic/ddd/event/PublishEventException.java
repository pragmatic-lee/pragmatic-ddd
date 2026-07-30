package io.pragmatic.ddd.event;


/**
 * 事件发布失败异常。
 *
 * @author wizard-lee
 */
public class PublishEventException extends EventException {

    public PublishEventException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
