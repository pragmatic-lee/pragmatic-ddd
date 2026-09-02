/**
 * 分页 / 滚动值对象：读侧两种取数形状的数据载体，不承载查询语义。
 *
 * <ul>
 *   <li>分页 —— {@link PageRequest}（页码 / 页大小请求）与 {@link PageResult}（数据 + 总数 + 原请求）</li>
 *   <li>滚动 —— {@link ScrollPosition}（游标）与 {@link ScrollResult}（数据 + 下一游标）</li>
 * </ul>
 *
 * <p>由查询门面（{@code IQueryPage} / {@code IQueryScroll}）与分页检索器 SPI
 * （{@code IProjectionPagedSearcher}）共同消费。</p>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query.paging;
