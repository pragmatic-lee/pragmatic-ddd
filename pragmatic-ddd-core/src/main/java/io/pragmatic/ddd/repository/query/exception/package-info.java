/**
 * 读侧投影检索域异常体系：全部继承 {@link ProjectionException}（上接 {@code PragmaticException}），
 * 可通过 {@code catch (PragmaticException)} 统一兜底。
 *
 * <p>按发生阶段分类：</p>
 * <ul>
 *   <li>注册期 —— {@link ProjectionSourceConflictException}（源 id / 构件绑定冲突）</li>
 *   <li>查询期（源与构件定位）—— {@link ProjectionSourceNotFoundException}、
 *       {@link ProjectionSourceAmbiguousException}（多源无默认）、
 *       {@link ProjectionSearcherNotFoundException}、{@link ProjectionReducerNotFoundException}</li>
 *   <li>执行期 —— {@link ProjectionConditionException}（条件翻译失败，不可重试）、
 *       {@link ProjectionRetrieveException}（通信 / 反序列化失败，可重试）</li>
 * </ul>
 *
 * <p>{@link ProjectionExceptions} 提供 retrieve / translate 两阶段包装辅助，
 * 收敛样板处理并避免异常重复嵌套。</p>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query.exception;
