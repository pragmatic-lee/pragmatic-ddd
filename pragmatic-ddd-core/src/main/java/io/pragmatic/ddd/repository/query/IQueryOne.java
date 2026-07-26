package io.pragmatic.ddd.repository.query;

/**
 * 按指定条件查询一个投影（精确规约，条件对象字段通常全必填）。
 *
 * @param <PROJECTION>     投影类型
 * @param <QUERY_CRITERIA> 查询条件类型
 */
public interface IQueryOne<PROJECTION, QUERY_CRITERIA> {

    /**
     * @return 命中则返回投影，未命中返回 null；若匹配到多条，由实现层定义行为（取首条或抛异常）。
     */
    PROJECTION queryOne(QUERY_CRITERIA query);
}
