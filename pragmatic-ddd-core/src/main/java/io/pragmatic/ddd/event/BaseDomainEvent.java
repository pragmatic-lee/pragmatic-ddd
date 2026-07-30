package io.pragmatic.ddd.event;

import java.time.Instant;
import java.util.UUID;

import lombok.Getter;

/**
 * 领域事件不可变基类。
 * 所有字段通过构造函数注入，不提供 setter，由 Lombok 的 @Getter 生成全部 getter。
 *
 * @author wizard-lee
 */
@Getter
public abstract class BaseDomainEvent implements IDomainEvent {

    private final String eventId;
    private final String entityId;
    private final Instant occurredOn;

    /** 由 AggregateRoot.collectEvent() / Fastjson2 反序列化设置，子类不应主动赋值 */
    public String operationCode;
    public long version;

    /** 常规构造：自动生成 eventId + 记录当前时间 */
    protected BaseDomainEvent(String entityId) {
        this(entityId, UUID.randomUUID().toString(), Instant.now());
    }

    /** 事件重放构造：指定 entityId + eventId + 时间 */
    protected BaseDomainEvent(String entityId, String eventId, Instant occurredOn) {
        this.eventId = eventId;
        this.entityId = entityId;
        this.occurredOn = occurredOn;
    }

    /** Fastjson2 Feature.FieldBased 反序列化入口 */
    protected BaseDomainEvent() {
        this.eventId = null;
        this.entityId = null;
        this.occurredOn = null;
    }
}
