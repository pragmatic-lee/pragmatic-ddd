package io.pragmatic.ddd.repository.query;

/**
 * 聚合级查询便捷组合接口（6 类查询能力全量组合）。
 *
 * <p>泛型参数：
 * <ul>
 *   <li>{@code ID}         — 聚合 ID 类型（queryById / queryByIds）</li>
 *   <li>{@code P}          — 投影类型，必须是 {@link IAggregateProjection} 的子类型，
 *                            通常传入 sealed 基类（全部方法）</li>
 *   <li>{@code ONE_QUERY}  — queryOne 的条件，必须是 {@link OneQueryCriteria} 的子类型（通常为 sealed interface）</li>
 *   <li>{@code LIST_QUERY} — queryList 的条件，必须是 {@link ListQueryCriteria} 的子类型（通常为 sealed interface）</li>
 *   <li>{@code PAGE_QUERY} — queryPage / queryScroll 共享的条件，必须是 {@link PageQueryCriteria} 的子类型（字段通常全 Optional）</li>
 * </ul>
 *
 * <p>四族条件各自独立、互不继承：One / List / Page(Scroll) 分别承载于不同分族父类，
 * 跨族传参在编译期报错。所有查询方法额外接收 {@code Class<P> projectionType} 入参，
 * 由调用方显式指定返回的投影具体子类型（如概要投影或详情投影）。</p>
 *
 * <p>若需更多独立条件类型（极少见），可不继承本接口，直接按需组合 ISP trait。</p>
 *
 * @param <ID>         聚合 ID 类型
 * @param <P>          投影类型，必须是 {@link IAggregateProjection} 的子类型
 * @param <ONE_QUERY>  queryOne 条件类型，必须是 {@link OneQueryCriteria} 的子类型
 * @param <LIST_QUERY> queryList 条件类型，必须是 {@link ListQueryCriteria} 的子类型
 * @param <PAGE_QUERY> queryPage / queryScroll 共享条件类型，必须是 {@link PageQueryCriteria} 的子类型
 *
 * @author wizard-lee
 */
public interface IAggregateQuery<ID, P extends IAggregateProjection,
        ONE_QUERY extends OneQueryCriteria,
        LIST_QUERY extends ListQueryCriteria,
        PAGE_QUERY extends PageQueryCriteria>
        extends IQueryById<ID, P>,
                IQueryByIds<ID, P>,
                IQueryOne<P, ONE_QUERY>,
                IQueryList<P, LIST_QUERY>,
                IQueryPage<P, PAGE_QUERY>,
                IQueryScroll<P, PAGE_QUERY> {
}
