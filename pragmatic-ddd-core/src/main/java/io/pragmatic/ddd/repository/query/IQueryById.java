package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;

/**
 * 按聚合 ID 查询一个投影。
 *
 * @param <ID> 聚合 ID 类型
 * @param <P>  投影类型，必须是 {@link IAggregateProjection} 的子类型
 *
 * @author wizard-lee
 */
public interface IQueryById<ID, P extends IAggregateProjection> {

    /**
     * @param projectionType 目标投影类型，决定返回的投影具体子类型
     * @param <X>            实际返回的投影子类型，必须是 {@code P} 的子类型
     * @return 命中则返回投影，未命中返回 null（与 {@code IRepository.findById} 行为一致）。
     */
    <X extends P> X queryById(ID id, Class<X> projectionType);
}
