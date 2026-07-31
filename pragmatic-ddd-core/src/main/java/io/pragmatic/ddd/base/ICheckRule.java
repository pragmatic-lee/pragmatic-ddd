package io.pragmatic.ddd.base;

/**
 * 校验项级契约 —— 对模型执行单条不变量的校验。
 * 返回 {@link RuleCheckResult} 以携带动态消息参数与自动格式化控制。
 *
 * @param <T> 被校验的模型类型
 * @author wizard-lee
 */
public interface ICheckRule<T> {

    /** 对模型执行单条不变量校验，返回带参数的结果。 */
    RuleCheckResult check(T model);

    /** 兼容实体级 boolean 语义的默认方法。 */
    default boolean satisfiesRule(T model) {
        return check(model).isSatisfy();
    }
}
