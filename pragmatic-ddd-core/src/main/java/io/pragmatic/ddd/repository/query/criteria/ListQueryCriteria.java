package io.pragmatic.ddd.repository.query.criteria;

/**
 * 列表查询（queryList）的条件族父类。
 *
 * <p>作为 {@code IQueryList} 条件泛型的编译期上界，使 List 一族的条件与 One / Page / Scroll
 * 各族在类型上相互隔离——跨族传参在编译期报错。本类为 marker 父类，不含任何行为，
 * 仅承载"族"的隔离语义；族内具体场景由各聚合以 sealed interface + record permits 扩展。</p>
 *
 * @author wizard-lee
 */
public interface ListQueryCriteria extends QueryCriteria {
}
