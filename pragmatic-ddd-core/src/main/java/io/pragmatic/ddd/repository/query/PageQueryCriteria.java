package io.pragmatic.ddd.repository.query;

/**
 * 分页（queryPage）与滚动（queryScroll）查询共用的条件族父类。
 *
 * <p>作为 {@link IQueryPage} 与 {@link IQueryScroll} 条件泛型的编译期上界，使 Page / Scroll 一族
 * 的条件与 One / List 各族在类型上相互隔离——跨族传参在编译期报错。本类为 marker 父类，
 * 不含任何行为，仅承载"族"的隔离语义；族内具体场景由各聚合以 sealed interface + record permits 扩展。</p>
 *
 * @author wizard-lee
 */
public interface PageQueryCriteria extends QueryCriteria {
}
