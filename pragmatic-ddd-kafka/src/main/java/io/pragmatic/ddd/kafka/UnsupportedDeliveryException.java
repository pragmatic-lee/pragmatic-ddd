package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.EventException;

/**
 * Kafka 实现不支持当前投递策略时抛出（如 delayed-policy=reject 拒绝 DELAYED 事件）。
 *
 * @author wizard-lee
 */
public class UnsupportedDeliveryException extends EventException {

    /**
     * 构建不支持投递策略异常。
     *
     * @param message 异常描述
     */
    public UnsupportedDeliveryException(String message) {
        super(message);
    }
}
