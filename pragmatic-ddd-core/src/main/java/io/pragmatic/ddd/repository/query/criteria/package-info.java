/**
 * 查询条件族契约：读侧查询条件的分族标记体系。
 *
 * <p>{@link QueryCriteria} 为共同根类型，
 * 三族分族父类各自独立、互不继承：</p>
 * <ul>
 *   <li>{@link OneQueryCriteria} —— 单条查询条件族</li>
 *   <li>{@link ListQueryCriteria} —— 列表查询条件族</li>
 *   <li>{@link PageQueryCriteria} —— 分页 / 滚动共享条件族</li>
 * </ul>
 *
 * <p>业务侧以 sealed interface 继承分族父类落地具体条件，跨族传参在编译期报错。
 * 检索器 SPI（{@code IProjectionSearcher} 等）以 {@code C extends QueryCriteria}
 * 统一表达任意族的检索条件。</p>
 *
 * @author wizard-lee
 */
package io.pragmatic.ddd.repository.query.criteria;
