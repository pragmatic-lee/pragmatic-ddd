package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.paging.PageRequest;
import io.pragmatic.ddd.repository.query.paging.PageResult;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;

/**
 * 分页查询（按需过滤，条件对象字段通常全 Optional）。
 *
 * @param <P> 投影类型，必须是 {@link IAggregateProjection} 的子类型
 * @param <C> 查询条件类型，必须是 {@link PageQueryCriteria} 的子类型
 *
 * @author wizard-lee
 */
public interface IQueryPage<P extends IAggregateProjection, C extends PageQueryCriteria> {

    /**
     * @param projectionType 目标投影类型，决定返回的投影具体子类型
     * @param <X>            实际返回的投影子类型，必须是 {@code P} 的子类型
     * @return 分页结果，包含当页数据与总记录数。
     */
    <X extends P> PageResult<X> queryPage(C query, PageRequest pageRequest, Class<X> projectionType);
}
