package io.pragmatic.ddd.repository.reconciliation;

/**
 * 异构读存储的补同步器：当对账发现不一致时，把副本补到最新或清理残留。
 * 关键：resync 必须"从写模型当前快照重建"（通过 IRepository.findById），
 * 而不是"重放那一条被漏消费的事件"——因为丢失的事件已不在事件流里。
 *
 * @author wizard-lee
 */
public interface IReadModelResynchronizer<ID> {
    /** STALE 时：以 aggregateId 为粒度，从写模型重建该副本。 */
    void resync(ID aggregateId);

    /** ORPHAN 时：写模型已无此聚合，删除副本中残留条目。 */
    void purge(ID aggregateId);

    /** 本补同步器服务的对账目标（聚合类型 + 存储 ID），供 Registry 登记与寻址。 */
    ReconciliationTarget supportedTarget();
}
