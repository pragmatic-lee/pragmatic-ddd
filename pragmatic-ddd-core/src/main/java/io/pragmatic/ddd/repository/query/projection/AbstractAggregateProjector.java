package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.base.AggregateRoot;

/**
 * 聚合投影器抽象基类：预置 projectionType()，调用方只需实现 project。
 * 框架不提供任何默认映射逻辑（方案 C），字段取值/裁剪/组合由子类完成。
 *
 * @param <T> 聚合根类型
 * @param <P> 投影类型
 * @author wizard-lee
 */
public abstract class AbstractAggregateProjector<T extends AggregateRoot<?>, P extends IAggregateProjection>
        implements IAggregateProjector<T, P> {

    private final Class<P> projectionType;

    protected AbstractAggregateProjector(Class<P> projectionType) {
        this.projectionType = projectionType;
    }

    @Override
    public final Class<P> projectionType() {
        return projectionType;
    }

    /** 子类实现：聚合 → 投影 的具体映射（纯手写，无反射）。 */
    @Override
    public abstract P project(T aggregateRoot);
}
