package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.reconciliation.ReconciliationTarget;

/**
 * 投影物化器：将框架中立的投影对象转换为某异构存储的结构并写入。
 * 由各集成模块（ES / Redis / 读表连接器）实现，core 只定义接口。
 * 同一投影 P 可对应多个 materializer（不同存储目标），统一由
 * ProjectorRegistry 按 (投影类型, target) 登记与寻址。
 *
 * @param <P> 投影类型
 * @author wizard-lee
 */
public interface IProjectionMaterializer<P extends IAggregateProjection> {

    /** 本物化器服务的投影类型，供 ProjectorRegistry 按型登记。 */
    Class<P> projectionType();

    /** 投影对应的对账目标（聚合类型 + 存储 ID），供 reconcile 寻址与 registry 区分多副本。 */
    ReconciliationTarget target();

    /** 写入/更新该异构存储中的副本，持久化 version 字段（来自写模型快照版本）。 */
    void materialize(P projection, long version);

    /** ORPHAN 时清理副本中残留条目。 */
    void purge(Object aggregateId);
}
