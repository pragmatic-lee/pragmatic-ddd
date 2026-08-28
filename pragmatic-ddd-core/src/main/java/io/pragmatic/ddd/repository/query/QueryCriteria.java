package io.pragmatic.ddd.repository.query;

/**
 * 查询条件的共同根类型，是 One / List / Page / Scroll 各分族父类的公共上界。
 *
 * <p>各分族父类（{@link OneQueryCriteria} / {@link ListQueryCriteria} / {@link PageQueryCriteria}）
 * 均继承本接口，使 {@link IProjectionSearcher} 能以统一的 {@code C extends QueryCriteria} 表达任意族的检索条件，
 * 同时仍保留分族父类对跨族传参的编译期隔离能力。</p>
 *
 * @author wizard-lee
 */
public interface QueryCriteria {
}
