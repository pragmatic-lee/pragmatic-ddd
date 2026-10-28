package io.pragmatic.ddd.event;

/**
 * 异构数据同步事件标记。
 * 表示该事件仅用于把聚合自身状态同步到异构副本（ES 索引 / Redis 键空间 / 宽表等），
 * 不承载跨限界上下文的集成语义；实现类只携带聚合标识，不携带业务快照。
 *
 * @author wizard-lee
 */
public interface IDataSyncEvent extends IDomainEvent {

    /**
     * 同步动作：副本应被写入还是被清除。
     *
     * @return 同步动作
     */
    DataSyncAction getDataSyncAction();
}
