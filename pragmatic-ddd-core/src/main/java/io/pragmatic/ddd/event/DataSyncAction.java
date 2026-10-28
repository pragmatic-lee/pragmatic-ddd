package io.pragmatic.ddd.event;

/**
 * 异构数据同步动作，描述副本应如何被改变。
 *
 * @author wizard-lee
 */
public enum DataSyncAction {

    /** 以聚合当前状态覆盖副本（新增 / 变更）。 */
    UPSERT,

    /** 清除副本（聚合被删除，或不再满足投影条件）。 */
    PURGE
}
