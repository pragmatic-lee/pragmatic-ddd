package io.pragmatic.ddd.repository.query;

import io.pragmatic.ddd.repository.query.criteria.PageQueryCriteria;
import io.pragmatic.ddd.repository.query.paging.ScrollPosition;
import io.pragmatic.ddd.repository.query.paging.ScrollResult;
import io.pragmatic.ddd.repository.query.projection.IAggregateProjection;

import java.util.List;

/**
 * 滚动（游标）查询（按需过滤，条件对象字段通常全 Optional）。
 *
 * @param <P> 投影类型，必须是 {@link IAggregateProjection} 的子类型
 * @param <C> 查询条件类型，必须是 {@link PageQueryCriteria} 的子类型
 *
 * @author wizard-lee
 */
public interface IQueryScroll<P extends IAggregateProjection, C extends PageQueryCriteria> {

    /**
     * @param cursor          游标位置；首次查询传 {@link ScrollPosition#initial()}。
     * @param pageSize        每批大小。
     * @param projectionType  目标投影类型，决定返回的投影具体子类型
     * @param <X>             实际返回的投影子类型，必须是 {@code P} 的子类型
     * @return 滚动结果，{@code nextCursor == null} 表示无更多数据。
     */
    <X extends P> ScrollResult<X> queryScroll(C query, ScrollPosition cursor, int pageSize, Class<X> projectionType);
}
