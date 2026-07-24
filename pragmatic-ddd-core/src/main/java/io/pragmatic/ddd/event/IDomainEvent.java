package io.pragmatic.ddd.event;

import java.time.Instant;

/**
 * 领域事件契约。
 *
 * <p>每个领域事件代表一个已经发生的、不可改变的领域事实。
 * 实现类应保持不可变（Immutable），所有字段通过构造函数注入。</p>
 *
 * <p><b>元数据字段语义：</b></p>
 * <ul>
 *   <li>{@link #getEventId()}        — 全局唯一标识，用于幂等去重</li>
 *   <li>{@link #getEntityId()}        — 实体身份标识，关联原始实体对象</li>
 *   <li>{@link #getOccurredOn()}      — 事件发生时间</li>
 *   <li>{@link #getOperationCode()}   — 触发该事件的实体 Operation 编码</li>
 *   <li>{@link #getVersion()}         — 聚合根版本号，由 EntityBase 自动设定</li>
 *   <li>{@link #getAggregateId()}     — 关联的聚合根标识（默认返回 entityId）</li>
 * </ul>
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
     * 由 {@code EntityBase.publishEvent()} 自动设置。
     */
    String getOperationCode();

    /**
     * 发布时刻的聚合根版本号。
     * 由 {@code EntityBase.publishEvent()} 设置为 {@code EntityBase.getNewVersion()} 的值。
     */
    long getVersion();

    /** 关联的聚合根标识，默认返回 {@code getEntityId()} */
    default String getAggregateId() {
        return getEntityId();
    }
}
