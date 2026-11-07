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

    /**
     * 按聚合声明的持久化意图路由：已删除走 remove，新建走 insert，其余走 update。
     * 删除意图优先级最高：新建后立即删除的聚合也走 remove。
     * 删除事实由实体既有标记 entityDelete 声明，实现方不得覆写本方法判断删除。
     */
    default void save(T aggregateRoot) {
        if (aggregateRoot.isEntityDelete()) {
            remove(aggregateRoot);
            return;
        }
        if (aggregateRoot.isNew()) {
            insert(aggregateRoot);
            return;
        }
        update(aggregateRoot);
    }

    /** 按主键查询聚合根；未命中返回 null。 */
    T findById(ID id);

    /**
     * 按主键删除聚合。实现方必须提供真实删除逻辑（本方法不再有默认空实现）。
     */
    void remove(T aggregateRoot);

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
