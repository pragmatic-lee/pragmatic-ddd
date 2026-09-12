/**
 * 读侧查询（Q 侧）契约：条件族、查询族 SPI、分页值对象与异常体系。
 *
 * <p>查询能力不再由框架基类编排，而是由各 Source 实现查询族接口（
 * {@code IProjectionByIdSearcher} / {@code IOneQuerySearcher} / {@code IListQuerySearcher} /
 * {@code IPagedQuerySearcher}），应用层通过领域层源接口注入调用。</p>
 *
 * <p>子包按职责与受众分层：</p>
 * <ul>
 *   <li>{@code query.criteria} —— 条件族契约（业务建模者实现）</li>
 *   <li>{@code query.paging} —— 分页 / 滚动值对象</li>
 *   <li>{@code query.projection} —— 投影模型、查询族 / 裁剪 SPI 与源登记中心（存储集成者实现）</li>
 *   <li>{@code query.exception} —— 读侧投影检索域异常体系</li>
 * </ul>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query;
