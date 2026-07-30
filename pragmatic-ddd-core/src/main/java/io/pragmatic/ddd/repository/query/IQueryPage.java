package io.pragmatic.ddd.repository.query;

/**
 * 分页查询（按需过滤，条件对象字段通常全 Optional）。
 *
 * @param <PROJECTION>     投影类型
 * @param <QUERY_CRITERIA> 查询条件类型
 *
 * @author wizard-lee
 */
public interface IQueryPage<PROJECTION, QUERY_CRITERIA> {

    /**
     * @return 分页结果，包含当页数据与总记录数。
     */
    PageResult<PROJECTION> queryPage(QUERY_CRITERIA query, PageRequest pageRequest);
}
