/**
 * 读模型投影域：投影模型、查询族 / 裁剪 SPI 与源适配器（存储集成者实现侧）。
 *
 * <p>「源」指读侧一份可查的数据来源：<b>异构副本</b>（ES 一个索引 / Redis 一个键空间）与
 * <b>同源直读</b>（数据库读源）都是源。</p>
 *
 * <p>源中心模型：副本源的寻址第一维是 {@link ProjectionSource}（一份物理副本，如 ES 一个索引 /
 * Redis 一个键空间），源确定后其索引级全量投影类型唯一确定；直读源不需要 {@code ProjectionSource}，
 * 也不实现 {@code IReadModelReplica}，只 implements 查询族端口并以行投影作为读源形状。</p>
 *
 * <ul>
 *   <li>投影模型 —— {@link IAggregateProjection} 读模型标记接口（业务侧以 sealed 体系落地）</li>
 *   <li>投影器 —— {@link IAggregateProjector} / {@link AbstractAggregateProjector}：聚合根 → 全量投影</li>
 *   <li>查询族 SPI —— {@link IProjectionByIdSearcher}（按主键直取）、
 *       {@link IOneQuerySearcher} / {@link IListQuerySearcher}（按条件）、
 *       {@link IPagedQuerySearcher}（分页 / 滚动）；由 Source 按需 implements 领域接口复用</li>
 *   <li>裁剪器 SPI —— {@link IReducer}：索引级全量投影 → 业务子投影，随 Source 走</li>
 *   <li>源适配器 —— {@link AbstractProjectionSource}：一份副本的写（materialize / purge）
 *       与读（子类 implements 查询族）、裁剪（构造注入 reducer）收敛于同一处</li>
 *   <li>直读读源（业务侧落地）—— 不继承 {@link AbstractProjectionSource}：无物化 / 版本 / 对账，
 *       仅 implements 查询族端口，行投影由 resultMap 产出</li>
 * </ul>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query.projection;
