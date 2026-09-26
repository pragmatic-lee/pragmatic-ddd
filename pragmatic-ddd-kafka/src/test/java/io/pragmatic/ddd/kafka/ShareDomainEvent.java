package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.BaseDomainEvent;

/**
 * Kafka 集成测试用领域事件示例，结构与 RocketMQ 的 {@code ShareDomainEvent} 对齐。
 * 为避免给 Kafka 模块额外引入 Lombok 依赖，此处使用原生 getter/setter 写法。
 *
 * @author wizard-lee
 */
public class ShareDomainEvent extends BaseDomainEvent {

    private String orderId;

    public ShareDomainEvent() {
        super();
    }

    protected ShareDomainEvent(String entityId, String orderId) {
        super(entityId);
        this.orderId = orderId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public static ShareDomainEvent buildEvent(String entityId, String orderId) {
        return new ShareDomainEvent(entityId, orderId);
    }
}
