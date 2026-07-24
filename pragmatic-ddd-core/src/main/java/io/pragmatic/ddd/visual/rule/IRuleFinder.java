package io.pragmatic.ddd.visual.rule;

import io.pragmatic.ddd.base.BrokenRuleMessage;
import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.rules.EntityRule;

import java.util.List;

public interface IRuleFinder {

    <T extends AbstractEntity<?>> RuleFinderObject findEntityRuleList(Class<T> cls);


    class RuleFinderObject{
        private final List<EntityRule<?>> entityRuleCls;
        private final BrokenRuleMessage brokenRuleMessage;

        public List<EntityRule<?>> getEntityRuleCls() {

            return entityRuleCls;
        }

        public BrokenRuleMessage getBrokenRuleMessage() {
            return brokenRuleMessage;
        }



        public RuleFinderObject(List<EntityRule<?>> entityRuleCls, BrokenRuleMessage brokenRuleMessage) {
            this.entityRuleCls = entityRuleCls;
            this.brokenRuleMessage = brokenRuleMessage;
        }
    }
}
