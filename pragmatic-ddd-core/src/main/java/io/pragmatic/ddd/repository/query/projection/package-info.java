/**
 * 读模型投影域：投影模型、检索 / 裁剪 SPI 与源登记中心（存储集成者实现侧）。
 *
 * <p>源中心模型：寻址第一维是 {@link ProjectionSource}（一份物理副本，如 ES 一个索引 /
 * Redis 一个键空间），源确定后其索引级全量投影类型唯一确定。</p>
 *
 * <ul>
 *   <li>投影模型 —— {@link IAggregateProjection} 读模型标记接口（业务侧以 sealed 体系落地）</li>
 *   <li>投影器 —— {@link IAggregateProjector} / {@link AbstractAggregateProjector}：聚合根 → 全量投影</li>
 *   <li>检索器 SPI —— {@link IProjectionByIdSearcher}（按主键直取）、
 *       {@link IProjectionSearcher}（按条件，源 × 条件族 定位）、
 *       {@link IProjectionPagedSearcher}（分页 / 滚动）</li>
 *   <li>裁剪器 SPI —— {@link IProjectionReducer}：索引级全量投影 → 业务子投影（两跳取数的第二跳）</li>
 *   <li>源适配器 —— {@link AbstractProjectionSource}：一份副本的写（materialize / purge）
 *       与读（bind 检索器 / 裁剪器）收敛于同一处</li>
 *   <li>登记中心 —— {@link ProjectorRegistry}：源 / 构件注册与定位、源解析（默认源 / 歧义检测）</li>
 *   <li>写侧同步门面 —— {@link AggregateProjectorSupport}：sync / purge，桥接对账目标</li>
 * </ul>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query.projection;
