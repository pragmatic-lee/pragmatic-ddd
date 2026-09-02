/**
 * 聚合级查询（Q 侧）门面：查询契约与三跳查询编排。
 *
 * <p>包含：</p>
 * <ul>
 *   <li>6 个 ISP trait —— {@code IQueryById} / {@code IQueryByIds} / {@code IQueryOne} /
 *       {@code IQueryList} / {@code IQueryPage} / {@code IQueryScroll}，可按需独立组合</li>
 *   <li>便捷组合接口 —— {@link IAggregateQuery}（6 类查询能力全量组合，追加源寻址）与
 *       {@link IProjectionSourceQuery}（绑定源的查询视图）</li>
 *   <li>查询编排基类 —— {@link AbstractProjectionQuery}（解析源 → 检索 → 裁剪 三跳链路）</li>
 * </ul>
 *
 * <p>子包按职责与受众分层：</p>
 * <ul>
 *   <li>{@code query.criteria} —— 条件族契约（业务建模者实现）</li>
 *   <li>{@code query.paging} —— 分页 / 滚动值对象</li>
 *   <li>{@code query.projection} —— 投影模型、检索器 / 裁剪器 SPI 与源登记中心（存储集成者实现）</li>
 *   <li>{@code query.exception} —— 读侧投影检索域异常体系</li>
 * </ul>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query;
