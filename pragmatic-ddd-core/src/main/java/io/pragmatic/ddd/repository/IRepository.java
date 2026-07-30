package io.pragmatic.ddd.repository;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 聚合仓储契约（写模型持久化）。
 *
 * @author wizard-lee
 */
public interface IRepository<ID, T extends AggregateRoot<ID>> {

    /** 插入聚合根。 */
    void insert(T aggregateRoot);

    /** 更新聚合根。 */
    void update(T aggregateRoot);

    default void save(T aggregateRoot) {
        if (aggregateRoot.isNew()) {
            insert(aggregateRoot);
        } else {
            update(aggregateRoot);
        }
    }

    /** 按主键查询聚合根；未命中返回 null。 */
    T findById(ID id);

    /**
     * 按主键删除聚合。实现方必须提供真实删除逻辑（本方法不再有默认空实现）。
     */
    void removeById(ID id);

    /**
     * 判断主键是否存在。默认实现基于 {@link #findById(Object)} 是否为 null。
     */
    default boolean existsById(ID id) {
        return findById(id) != null;
    }

    /**
     * 写模型当前版本；聚合不存在返回 -1（供 ORPHAN 判定）。
     * 默认基于 findById 后取 oldVersion（AggregateRoot.getOldVersion()），
     * 高频对账可实现为只查版本列 / 读 outbox 最大版本。
     */
    default long currentVersion(ID id) {
        T agg = findById(id);
        return agg != null ? agg.getOldVersion() : -1L;
    }
}
