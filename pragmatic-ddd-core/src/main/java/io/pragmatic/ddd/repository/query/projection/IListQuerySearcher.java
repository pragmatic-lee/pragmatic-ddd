package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.criteria.ListQueryCriteria;

import java.util.List;

/**
 * 列表条件查询族：从某异构存储按业务条件取回投影列表。
 *
 * <p>条件泛型 {@code C} 的上界为框架抽象 {@link ListQueryCriteria}，实现方在
 * {@code implements} 时钉死为业务查询类型（如 {@code OrderListQuery}）。</p>
 *
 * <p>由各集成模块实现，core 只定义中立接口。服务的是索引级全量投影类型，
 * 子投影由 {@link IReducer} 在内存中二次裁剪。</p>
 *
 * @param <P> 投影类型
 * @param <C> 业务条件类型
 * @author wizard-lee
 */
public interface IListQuerySearcher<P extends IAggregateProjection, C extends ListQueryCriteria> {

    /**
     * 按业务条件取回投影列表（无结果返回空列表，不返回 null）。
     *
     * @param criteria 业务条件
     * @return 投影列表
     */
    List<P> search(C criteria);
}
