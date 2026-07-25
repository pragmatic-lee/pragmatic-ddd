package io.pragmatic.ddd.visual.rule;

import io.pragmatic.ddd.base.BrokenRuleMessage;
import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.rules.AlwaysActiveRuleCondition;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.rules.RuleItem;
import io.pragmatic.ddd.visual.VisualException;

import java.util.*;
import java.util.stream.Collectors;

public class RuleParser {


    private final Map<Class<?>, IRuleFinder> ruleFinderMap = new HashMap<>();

    public <T extends AbstractEntity<?>> void registerDomainRule(Class<T> entityClass, IRuleFinder finder) {
        this.ruleFinderMap.put(entityClass, finder);
    }


    public <T extends AbstractEntity<?>> List<RuleDescriptorGroup> parse(Class<T> cls) {

        IRuleFinder.RuleFinderObject entityRuleInfo = Optional.ofNullable(this.ruleFinderMap.get(cls))
                .map(f -> f.findEntityRuleList(cls)).orElse(null);


        return Optional.ofNullable(entityRuleInfo).map(IRuleFinder.RuleFinderObject::getEntityRuleCls)
                .orElse(Collections.emptyList()).stream().map(s ->
                        buildRuleDescriptor(entityRuleInfo.getBrokenRuleMessage(),s)
                )
                .collect(Collectors.toList());
    }

    private RuleDescriptorGroup buildRuleDescriptor(BrokenRuleMessage brokenRuleMessage,
                                                    EntityRule<?> entityRule) {

        try {

            EntityRuleVisual annotation = entityRule.getClass().getAnnotation(EntityRuleVisual.class);

            String entityRuleDescription = "";
            if (annotation != null) {
                entityRuleDescription = annotation.description();
            }

            List<? extends RuleItem<?>> ruleItems = entityRule.allRuleItems();

            List<RuleDescriptor> collect = ruleItems.stream().map(r -> {
                String ruleDescription = brokenRuleMessage.getRuleDescription(r.getMessageCode().code());
                return new RuleDescriptor(r.getMessageCode().code(),
                        ruleDescription,
                        !(r.getCondition() instanceof AlwaysActiveRuleCondition));

            }).collect(Collectors.toList());

            return new RuleDescriptorGroup(entityRuleDescription, collect);


        } catch (Exception e) {
            throw new VisualException(e);
        }
    }

}
