package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 按指定条件查询多个投影（精确规约，条件对象字段通常全必填）。
 *
 * @param <P> 投影类型，必须是 {@link IAggregateProjection} 的子类型
 * @param <C> 查询条件类型，必须是 {@link ListQueryCriteria} 的子类型
 *
 * @author wizard-lee
 */
public interface IQueryList<P extends IAggregateProjection, C extends ListQueryCriteria> {

    /**
     * @param projectionType 目标投影类型，决定返回的投影具体子类型
     * @param <X>            实际返回的投影子类型，必须是 {@code P} 的子类型
     * @return 命中的投影列表；未命中返回空列表而非 null。
     */
    <X extends P> List<X> queryList(C query, Class<X> projectionType);
}
