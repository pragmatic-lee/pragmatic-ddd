package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.criteria.OneQueryCriteria;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;

/**
 * 按指定条件查询一个投影（精确规约，条件对象字段通常全必填）。
 *
 * @param <P> 投影类型，必须是 {@link IAggregateProjection} 的子类型
 * @param <C> 查询条件类型，必须是 {@link OneQueryCriteria} 的子类型
 *
 * @author wizard-lee
 */
public interface IQueryOne<P extends IAggregateProjection, C extends OneQueryCriteria> {

    /**
     * @param projectionType 目标投影类型，决定返回的投影具体子类型
     * @param <X>            实际返回的投影子类型，必须是 {@code P} 的子类型
     * @return 命中则返回投影，未命中返回 null；若匹配到多条，由实现层定义行为（取首条或抛异常）。
     */
    <X extends P> X queryOne(C query, Class<X> projectionType);
}
