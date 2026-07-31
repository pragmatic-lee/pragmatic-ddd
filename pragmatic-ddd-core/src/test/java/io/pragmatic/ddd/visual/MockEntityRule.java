package io.pragmatic.ddd.visual;

import io.pragmatic.ddd.base.RuleCheckResult;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.visual.rule.EntityRuleVisual;

@EntityRuleVisual(description = "场景1")
public class MockEntityRule extends EntityRule<MockEntity> {
    public MockEntityRule() {
        super();
        this.init();
    }

    @Override
    public void init() {

        this.addRule((t, old) -> {

            return RuleCheckResult.of(t.showName() != null);

        }, MockEntityBrokenRuleRegistry.Name_Error);
    }
}
