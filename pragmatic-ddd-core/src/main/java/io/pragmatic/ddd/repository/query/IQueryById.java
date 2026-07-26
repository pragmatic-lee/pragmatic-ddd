package io.pragmatic.ddd.repository.query;

/**
 * 按聚合 ID 查询一个投影。
 *
 * @param <ID>         聚合 ID 类型
 * @param <PROJECTION> 投影类型
 */
public interface IQueryById<ID, PROJECTION> {

    /**
     * @return 命中则返回投影，未命中返回 null（与 {@code IRepository.findById} 行为一致）。
     */
    PROJECTION queryById(ID id);
}
