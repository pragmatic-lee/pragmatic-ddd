package io.pragmatic.ddd.rules;

import io.pragmatic.ddd.base.BrokenRuleObject;

/**
 * 始终激活的规则条件 —— 无条件参与校验。
 */
public class AlwaysActiveRuleCondition<T extends BrokenRuleObject> implements IActiveRuleCondition<T> {
    @Override
    public ActiveStatus status(T model) {
        return ActiveStatus.ACTIVE;
    }
}
