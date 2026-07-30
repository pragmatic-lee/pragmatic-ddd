package io.pragmatic.ddd.rules;

/**
 * 规则激活条件 —— 决定一条规则在特定模型上下文中是否参与校验。
 *
 * @author wizard-lee
 */
public interface IActiveRuleCondition<T> {
    /** 返回规则在给定模型下的激活状态。 */
    ActiveStatus status(T model);
}
