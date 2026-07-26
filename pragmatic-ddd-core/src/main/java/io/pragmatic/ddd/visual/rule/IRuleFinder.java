package io.pragmatic.ddd.visual.rule;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.rules.EntityRule;

import java.util.List;

public interface IRuleFinder {

    <T extends AbstractEntity<?>> RuleFinderObject findEntityRuleList(Class<T> cls);


    class RuleFinderObject{
        private final List<EntityRule<?>> entityRuleCls;
        private final BrokenRuleRegistry brokenRuleRegistry;

        public List<EntityRule<?>> getEntityRuleCls() {

            return entityRuleCls;
        }

        public BrokenRuleRegistry getBrokenRuleRegistry() {
            return brokenRuleRegistry;
        }



        public RuleFinderObject(List<EntityRule<?>> entityRuleCls, BrokenRuleRegistry brokenRuleRegistry) {
            this.entityRuleCls = entityRuleCls;
            this.brokenRuleRegistry = brokenRuleRegistry;
        }
    }
}
