package io.pragmatic.ddd.repository.query;

/**
 * 聚合级查询便捷组合接口（6 类查询能力全量组合）。
 *
 * <p>泛型参数：
 * <ul>
 *   <li>{@code ID}          — 聚合 ID 类型（queryById / queryByIds）</li>
 *   <li>{@code PROJECTION}  — 投影类型，通常传入 sealed 基类（全部方法）</li>
 *   <li>{@code ONE_QUERY}   — queryOne 的条件（通常为 sealed interface）</li>
 *   <li>{@code LIST_QUERY}  — queryList 的条件（通常为 sealed interface）</li>
 *   <li>{@code PAGE_QUERY}  — queryPage / queryScroll 共享的条件（字段通常全 Optional）</li>
 * </ul>
 *
 * <p>若所有查询共用同一条件类型，可将后三个泛型传入同一类型（如 {@code Q, Q, Q}）。
 * 若需更多独立条件类型（极少见），可不继承本接口，直接按需组合 ISP trait。</p>
 *
 * @param <ID>          聚合 ID 类型
 * @param <PROJECTION>  投影类型
 * @param <ONE_QUERY>   queryOne 条件类型
 * @param <LIST_QUERY>  queryList 条件类型
 * @param <PAGE_QUERY>  queryPage / queryScroll 共享条件类型
 *
 * @author wizard-lee
 */
public interface IAggregateQuery<ID, PROJECTION, ONE_QUERY, LIST_QUERY, PAGE_QUERY>
        extends IQueryById<ID, PROJECTION>,
                IQueryByIds<ID, PROJECTION>,
                IQueryOne<PROJECTION, ONE_QUERY>,
                IQueryList<PROJECTION, LIST_QUERY>,
                IQueryPage<PROJECTION, PAGE_QUERY>,
                IQueryScroll<PROJECTION, PAGE_QUERY> {
}
