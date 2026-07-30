package io.pragmatic.ddd.rules;

/**
 * 始终激活的规则条件 —— 无条件参与校验。
 *
 * <p>对应重构计划 3.5 节：去除 BrokenRuleObject 上界。</p>
 *
 * @author wizard-lee
 */
public class AlwaysActiveRuleCondition<T> implements IActiveRuleCondition<T> {
    @Override
    public ActiveStatus status(T model) {
        return ActiveStatus.ACTIVE;
    }
}
