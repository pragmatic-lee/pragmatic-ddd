package io.pragmatic.ddd.rules;

/**
 * 校验项级契约 —— 对模型执行单条不变量的校验。
 *
 * <p>校验接收「新模型」与「旧模型」两个入参，使规则成为无状态纯函数：
 * 需要新旧对比的规则通过 {@code oldModel} 获取修改前快照，
 * 不需要的规则忽略第二参数即可。</p>
 *
 * <p>返回 {@link RuleCheckResult} 以携带动态消息参数与自动格式化控制。</p>
 *
 * @param <T> 被校验的模型类型
 * @author wizard-lee
 */
@FunctionalInterface
public interface ICheckRule<T> {

    /**
     * 对模型执行单条不变量校验。
     *
     * @param newModel 当前被校验的模型
     * @param oldModel 修改前的模型快照，不存在（如创建操作）或规则不需要时为 null
     * @return 带参数的校验结果
     */
    RuleCheckResult check(T newModel, T oldModel);

    /** 仅关心当前模型时的便捷入口（旧模型传 null）。 */
    default RuleCheckResult check(T newModel) {
        return check(newModel, null);
    }
}
