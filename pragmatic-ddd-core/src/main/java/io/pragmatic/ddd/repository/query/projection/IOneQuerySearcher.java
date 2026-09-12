package io.pragmatic.ddd.repository.query.projection;

import io.pragmatic.ddd.repository.query.criteria.OneQueryCriteria;

import java.util.List;

/**
 * 单条条件查询族：从某异构存储按业务条件取回投影列表（由调用方取首条）。
 *
 * <p>条件泛型 {@code C} 的上界为框架抽象 {@link OneQueryCriteria}，实现方在
 * {@code implements} 时钉死为业务查询类型（如 {@code OrderOneQuery}），故调用方
 * 传入具体业务条件子类时编译期即校验。</p>
 *
 * <p>由各集成模块（ES / Redis / 读表连接器）实现，core 只定义中立接口。
 * 服务的是索引级全量投影类型，子投影由 {@link IReducer} 在内存中二次裁剪。</p>
 *
 * @param <P> 投影类型
 * @param <C> 业务条件类型
 * @author wizard-lee
 */
public interface IOneQuerySearcher<P extends IAggregateProjection, C extends OneQueryCriteria> {

    /**
     * 按业务条件取回投影列表（无结果返回空列表，不返回 null）。
     *
     * @param criteria 业务条件
     * @return 投影列表
     */
    List<P> search(C criteria);
}
