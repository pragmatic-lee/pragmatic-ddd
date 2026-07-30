package io.pragmatic.ddd.visual.rule;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.rules.EntityRule;

import java.util.List;

/**
 * 领域规则查找器 —— 按实体类定位其规则列表与损坏规则注册表。
 *
 * @author wizard-lee
 */
public interface IRuleFinder {

    /** 返回实体类对应的规则查找结果（规则列表与注册表）。 */
    <T extends AbstractEntity<?>> RuleFinderObject findEntityRuleList(Class<T> cls);


    /**
     * 规则查找结果 —— 承载规则类列表与损坏规则注册表。
     */
    class RuleFinderObject{
        private final List<EntityRule<?>> entityRuleCls;
        private final BrokenRuleRegistry brokenRuleRegistry;

        /** 返回规则类列表。 */
        public List<EntityRule<?>> getEntityRuleCls() {

            return entityRuleCls;
        }

        /** 返回损坏规则注册表。 */
        public BrokenRuleRegistry getBrokenRuleRegistry() {
            return brokenRuleRegistry;
        }



        /** 构造规则查找结果。 */
        public RuleFinderObject(List<EntityRule<?>> entityRuleCls, BrokenRuleRegistry brokenRuleRegistry) {
            this.entityRuleCls = entityRuleCls;
            this.brokenRuleRegistry = brokenRuleRegistry;
        }
    }
}
