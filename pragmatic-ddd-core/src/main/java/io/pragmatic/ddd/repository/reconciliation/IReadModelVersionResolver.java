package io.pragmatic.ddd.repository.reconciliation;

/**
 * 异构读存储（ES / Redis / 独立读表）当前版本的获取器，各连接器实现。
 */
public interface IReadModelVersionResolver<ID> {
    /** 该异构存储中聚合 id 对应的已物化版本 V'；不存在/未追踪返回 -1。 */
    long resolve(ID aggregateId);

    /** 本解析器服务的对账目标（聚合类型 + 存储 ID），供 Registry 登记与寻址。 */
    ReconciliationTarget supportedTarget();
}
