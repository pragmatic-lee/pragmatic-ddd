package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 按指定条件查询多个投影（精确规约，条件对象字段通常全必填）。
 *
 * @param <PROJECTION>     投影类型
 * @param <QUERY_CRITERIA> 查询条件类型
 */
public interface IQueryList<PROJECTION, QUERY_CRITERIA> {

    /**
     * @return 命中的投影列表；未命中返回空列表而非 null。
     */
    List<PROJECTION> queryList(QUERY_CRITERIA query);
}
