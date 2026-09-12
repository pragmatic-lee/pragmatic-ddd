/**
 * 读模型投影域：投影模型、查询族 / 裁剪 SPI 与源登记中心（存储集成者实现侧）。
 *
 * <p>源中心模型：寻址第一维是 {@link ProjectionSource}（一份物理副本，如 ES 一个索引 /
 * Redis 一个键空间），源确定后其索引级全量投影类型唯一确定。</p>
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
 *   <li>登记中心 —— {@link ProjectorRegistry}：仅 {@code sourceId → Source} 登记与按 id 取源</li>
 * </ul>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query.projection;
