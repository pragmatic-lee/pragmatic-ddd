package io.pragmatic.ddd.broadcast;

/**
 * 对外广播发送失败异常，具有"可重试"语义（对应 AclCommunicationException）。
 * 上层可据此类型决策重试、降级或熔断。
 *
 * @author wizard-lee
 */
public class BroadcastSendException extends BroadcastException {

    public BroadcastSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
