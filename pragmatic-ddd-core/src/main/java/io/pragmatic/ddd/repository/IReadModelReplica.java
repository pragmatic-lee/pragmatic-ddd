package io.pragmatic.ddd.repository;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 读模型副本的自我维护契约：一份物理副本（ES 一个索引 / Redis 一个键空间）自行声明
 * 身份（聚合类型 + 副本标识）、读取副本版本、从写模型重建自身、清理残留条目。
 *
 * <p>「源即副本」：读侧源（{@code AbstractProjectionSource}）实现本契约后，
 * 其写（materialize / purge）、读（查询族）与对账（版本读取 / 重建）收敛于同一对象，
 * 不再需要独立的目标标识与补同步适配器。</p>
 *
 * <p>不变式：实现本契约的每个副本都必须可对账——副本版本读取是必然能力，不存在「可选对账」的副本。</p>
 *
 * @param <ID> 聚合标识类型
 * @author wizard-lee
 */
public interface IReadModelReplica<ID> {

    /** 本副本所属的聚合类型（副本身份的聚合维度）。 */
    Class<? extends AggregateRoot<ID>> aggregateType();

    /** 副本标识（如 es:orders / redis:orders），在本聚合内全局唯一，是副本寻址的第二维度。 */
    String replicaId();

    /** 副本在本聚合下的寻址键，用于对账注册表入账与查取。 */
    default ReplicaKey key() {
        return new ReplicaKey(aggregateType(), replicaId());
    }

    /**
     * 读取副本中聚合 id 对应的已物化版本 V'。
     * 缺省值语义由实现按本副本物理存储选择：{@code 0} 表示副本缺失、需重建（对账判 STALE），
     * {@code -1} 表示副本未追踪（对账判 UNTRACKED，不触发重建）。
     *
     * @param aggregateId 聚合标识
     * @return 副本已物化版本
     */
    long readVersion(ID aggregateId);

    /** 以聚合 id 为粒度，从写模型当前快照重建本副本。 */
    void rebuild(ID aggregateId);

    /** 写模型已无此聚合时，删除本副本中的残留条目。 */
    void purgeOrphan(ID aggregateId);
}
