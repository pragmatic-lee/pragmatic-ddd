package io.pragmatic.ddd.broadcast;

import io.pragmatic.ddd.event.IDomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * 聚合对外广播消息的统一信封数据结构。
 * <p>
 * 信封由固定的元数据（框架填充）与自由的消息体（payload，由引用方定义）两部分组成。
 * 元数据均取自触发广播的领域事件，无需聚合根额外参与回填；消息体承载对接方约定的业务字段。
 *
 * @param <P> 消息体类型
 * @author wizard-lee
 */
@Getter
public abstract class AggregateMessageEnvelope<P> {

    /** 全局唯一消息标识，作为对外幂等去重主键。 */
    private final String messageId;

    /** 聚合根类型（聚合根简单类名），由调用方在构造时传入。 */
    private final String aggregateType;

    /** 聚合实体标识，作为顺序消费分区键与反查主键。 */
    private final String aggregateId;

    /** 发布时刻的聚合版本号，供对接方丢弃乱序旧版本。 */
    private final long version;

    /** 消息成因操作编码。 */
    private final String causeOperation;

    /** 事件发生时间，供对接方对账与时效判断。 */
    private final Instant occurredOn;

    /** 信封协议版本，用于后续演进兼容。 */
    private final int schemaVersion;

    /** 触发此消息的领域事件标识，用于溯源。 */
    private final String sourceEventId;

    /** 消息体，承载对接方约定的业务字段。 */
    private final P payload;

    protected AggregateMessageEnvelope(String aggregateType, IDomainEvent source, P payload) {
        this.messageId = UUID.randomUUID().toString();
        this.aggregateType = aggregateType;
        this.aggregateId = source.getAggregateId();
        this.version = source.getVersion();
        this.causeOperation = source.getOperationCode();
        this.occurredOn = source.getOccurredOn();
        this.schemaVersion = 1;
        this.sourceEventId = source.getEventId();
        this.payload = payload;
    }
}
