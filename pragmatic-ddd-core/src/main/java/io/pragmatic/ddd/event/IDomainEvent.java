package io.pragmatic.ddd.event;

import java.time.Instant;

/**
 * 领域事件契约。
 * 每个事件代表一个已发生、不可改变的领域事实，实现类应保持不可变。
 *
 * @author wizard-lee
 */
public interface IDomainEvent {

    /** 事件全局唯一标识 */
    String getEventId();

    /** 实体身份标识 */
    String getEntityId();

    /** 事件发生时间 */
    Instant getOccurredOn();

    /**
     * 触发该事件的实体 Operation 编码。
     * 由 {@code AggregateRoot.collectEvent()} 自动设置。
     */
    String getOperationCode();

    /**
     * 发布时刻的聚合根版本号。
     * 由 {@code AggregateRoot.collectEvent()} 设置为 {@code AggregateRoot.getNewVersion()} 的值。
     */
    long getVersion();

    /** 关联的聚合根标识，默认返回 {@code getEntityId()} */
    default String getAggregateId() {
        return getEntityId();
    }
}
