package io.pragmatic.ddd.event;

import java.time.Instant;

/**
 * 异构数据同步事件基类：固定「聚合标识 + 同步动作」契约，不携带业务快照。
 * 与 BaseDomainEvent 一致保持不可变语义，动作字段仅供 Fastjson2 反序列化回填。
 *
 * @author wizard-lee
 */
public abstract class BaseDataSyncEvent extends BaseDomainEvent implements IDataSyncEvent {

    /** 由 Fastjson2 Feature.FieldBased 反序列化回填；字段名为序列化契约，不得改名 */
    public DataSyncAction dataSyncAction = DataSyncAction.UPSERT;

    /** 常规构造：自动生成 eventId + 记录当前时间 */
    protected BaseDataSyncEvent(String entityId, DataSyncAction dataSyncAction) {
        super(entityId);
        this.dataSyncAction = dataSyncAction;
    }

    /** 事件重放构造：指定 entityId + eventId + 时间 */
    protected BaseDataSyncEvent(String entityId, String eventId, Instant occurredOn, DataSyncAction dataSyncAction) {
        super(entityId, eventId, occurredOn);
        this.dataSyncAction = dataSyncAction;
    }

    /** Fastjson2 Feature.FieldBased 反序列化入口 */
    protected BaseDataSyncEvent() {
        super();
    }

    @Override
    public DataSyncAction getDataSyncAction() {
        return this.dataSyncAction;
    }
}
