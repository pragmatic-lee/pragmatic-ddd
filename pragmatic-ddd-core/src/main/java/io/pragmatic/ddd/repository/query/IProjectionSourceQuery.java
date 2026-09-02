package io.pragmatic.ddd.repository.query;

import java.util.List;

/**
 * 绑定了「源」的投影查询视图：先定源、再裁剪。
 * 调用方拿到视图后，6 个查询 trait 的方法调用形状完全一致；与默认视图的唯一差异是寻址时所用的源。
 *
 * <p>由 {@link IAggregateQuery#source(ProjectionSource)} 或 {@link IAggregateQuery#fallbackChain(List)} 产生，
 * 不引入新查询语义，仅固定寻址维度。</p>
 *
 * @param <ID> 聚合 ID 类型
 * @param <P> 投影体系基类型
 * @param <ONE> 单条查询条件族
 * @param <LIST> 列表查询条件族
 * @param <PAGE> 分页查询条件族
 * @author wizard-lee
 */
public interface IProjectionSourceQuery<ID, P extends IAggregateProjection,
        ONE extends OneQueryCriteria,
        LIST extends ListQueryCriteria,
        PAGE extends PageQueryCriteria>
        extends IQueryById<ID, P>,
        IQueryByIds<ID, P>,
        IQueryOne<P, ONE>,
        IQueryList<P, LIST>,
        IQueryPage<P, PAGE>,
        IQueryScroll<P, PAGE> {

    /** 当前视图绑定的源（默认视图返回 null，表示未指定）。 */
    ProjectionSource source();
}
