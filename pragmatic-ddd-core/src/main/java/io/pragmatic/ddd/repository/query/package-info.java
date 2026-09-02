/**
 * 聚合级查询（Q 侧）契约。
 *
 * <p>包含：</p>
 * <ul>
 *   <li>6 个 ISP trait —— {@code IQueryById} / {@code IQueryByIds} / {@code IQueryOne} /
 *       {@code IQueryList} / {@code IQueryPage} / {@code IQueryScroll}，可按需独立组合</li>
 *   <li>便捷组合接口 —— {@link IAggregateQuery}（6 类查询能力全量组合）</li>
 *   <li>读模型投影 —— 投影标记 {@link IAggregateProjection}、投影器 {@link IAggregateProjector} /
 *       {@code AbstractAggregateProjector} / {@code AggregateProjectorSupport}、源适配器
 *       {@link AbstractProjectionSource} 与构件登记中心 {@code ProjectorRegistry}</li>
 *   <li>分页 / 滚动值对象 —— {@code PageRequest} / {@code PageResult} / {@code ScrollPosition} / {@code ScrollResult}</li>
 * </ul>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query;
