package io.pragmatic.ddd.kafka;

import io.pragmatic.ddd.event.BaseDomainEvent;

/**
 * Kafka 集成测试用领域事件示例，结构与 RocketMQ 的 {@code MyDomainEvent} 对齐。
 * 为避免给 Kafka 模块额外引入 Lombok 依赖，此处使用原生 getter/setter 写法。
 *
 * @author wizard-lee
 */
public class MyDomainEvent extends BaseDomainEvent {

    private String name;

    public MyDomainEvent() {
        super();
    }

    protected MyDomainEvent(String entityId, String name) {
        super(entityId);
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static MyDomainEvent buildEvent(String entityId, String name) {
        return new MyDomainEvent(entityId, name);
    }
}
