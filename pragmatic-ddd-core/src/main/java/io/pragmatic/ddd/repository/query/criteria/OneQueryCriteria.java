package io.pragmatic.ddd.repository.query.criteria;

/**
 * 单投影查询（queryOne）的条件族父类。
 *
 * <p>作为 {@code IQueryOne} 条件泛型的编译期上界，使 One 一族的条件与 List / Page / Scroll
 * 各族在类型上相互隔离——跨族传参在编译期报错。本类为 marker 父类，不含任何行为，
 * 仅承载"族"的隔离语义；族内具体场景由各聚合以 sealed interface + record permits 扩展。</p>
 *
 * @author wizard-lee
 */
public interface OneQueryCriteria extends QueryCriteria {
}
