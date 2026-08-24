package io.pragmatic.ddd.repository;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 聚合仓储抽象基类：在 insert / update / remove 落库前统一触发聚合根的数据同步钩子，
 * 自动收集聚合自身异构事件。真实持久化动作由子类在 doInsert / doUpdate / doRemove 实现。
 *
 * @param <ID> 标识类型
 * @param <T>  聚合根类型
 * @author wizard-lee
 */
public abstract class AbstractRepository<ID, T extends AggregateRoot<ID>>
        implements IRepository<ID, T> {

    /**
     * 插入：先触发聚合根数据同步钩子，再真实落库。
     */
    @Override
    public void insert(T aggregateRoot) {
        aggregateRoot.triggerDataSyncHook();
        this.doInsert(aggregateRoot);
    }

    /**
     * 更新：先触发聚合根数据同步钩子，再真实落库。
     */
    @Override
    public void update(T aggregateRoot) {
        aggregateRoot.triggerDataSyncHook();
        this.doUpdate(aggregateRoot);
    }

    /**
     * 删除：先触发聚合根数据同步钩子，再真实落库。
     */
    @Override
    public void remove(T aggregateRoot) {
        aggregateRoot.triggerDataSyncHook();
        this.doRemove(aggregateRoot);
    }

    /**
     * 子类实现：真实插入逻辑。
     */
    protected abstract void doInsert(T aggregateRoot);

    /**
     * 子类实现：真实更新逻辑。
     */
    protected abstract void doUpdate(T aggregateRoot);

    /**
     * 子类实现：真实删除逻辑。
     */
    protected abstract void doRemove(T aggregateRoot);
}
