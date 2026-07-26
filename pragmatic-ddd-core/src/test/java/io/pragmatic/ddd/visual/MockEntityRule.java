package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.visual.rule.EntityRuleVisual;

@EntityRuleVisual(description = "场景1")
public class MockEntityRule extends EntityRule<MockEntity> {
    public MockEntityRule() {
        super();
        this.init();
    }

    @Override
    protected MockEntity supplyOldEntity() {
        return null;
    }

    @Override
    public void init() {

        this.addRule(t -> {

            return t.showName() != null;

        }, MockEntityBrokenRuleRegistry.Name_Error);
    }
}
