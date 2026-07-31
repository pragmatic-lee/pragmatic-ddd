package io.pragmatic.ddd.visual.entity;

import io.pragmatic.ddd.base.RuleCheckResult;
import io.pragmatic.ddd.rules.EntityRule;

public class EntityTest2EntityRule extends EntityRule<EntityTest2> {

    public EntityTest2EntityRule() {
        this.init();
    }

    @Override
    protected EntityTest2 supplyOldEntity() {
        return null;
    }

    @Override
    public void init() {
        this.addRule(s -> {

            return RuleCheckResult.of(true);

        }, EntityTest2BrokenRuleRegistry.testError);

    }
}
