package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 滚动（游标）查询（按需过滤，条件对象字段通常全 Optional）。
 *
 * @param <PROJECTION>     投影类型
 * @param <QUERY_CRITERIA> 查询条件类型
 */
public interface IQueryScroll<PROJECTION, QUERY_CRITERIA> {

    /**
     * @param cursor   游标位置；首次查询传 {@link ScrollPosition#initial()}。
     * @param pageSize 每批大小。
     * @return 滚动结果，{@code nextCursor == null} 表示无更多数据。
     */
    ScrollResult<PROJECTION> queryScroll(QUERY_CRITERIA query, ScrollPosition cursor, int pageSize);
}
