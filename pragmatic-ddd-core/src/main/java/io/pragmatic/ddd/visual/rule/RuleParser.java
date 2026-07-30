package io.pragmatic.ddd.visual.rule;

import io.pragmatic.ddd.base.BrokenRuleRegistry;
import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.rules.AlwaysActiveRuleCondition;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.rules.RuleItem;
import io.pragmatic.ddd.visual.VisualException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 领域规则解析器 —— 借助查找器扫描实体类，提取规则并构建分组描述符。
 *
 * @author wizard-lee
 */
public class RuleParser {


    private final Map<Class<?>, IRuleFinder> ruleFinderMap = new HashMap<>();

    /** 注册某实体类的领域规则查找器。 */
    public <T extends AbstractEntity<?>> void registerDomainRule(Class<T> entityClass, IRuleFinder finder) {
        this.ruleFinderMap.put(entityClass, finder);
    }


    /** 解析实体类的全部领域规则，返回分组描述符列表。 */
    public <T extends AbstractEntity<?>> List<RuleDescriptorGroup> parse(Class<T> cls) {

        IRuleFinder.RuleFinderObject entityRuleInfo = Optional.ofNullable(this.ruleFinderMap.get(cls))
                .map(f -> f.findEntityRuleList(cls)).orElse(null);


        return Optional.ofNullable(entityRuleInfo).map(IRuleFinder.RuleFinderObject::getEntityRuleCls)
                .orElse(Collections.emptyList()).stream().map(s ->
                        buildRuleDescriptor(entityRuleInfo.getBrokenRuleRegistry(),s)
                )
                .collect(Collectors.toList());
    }

    private RuleDescriptorGroup buildRuleDescriptor(BrokenRuleRegistry brokenRuleRegistry,
                                                    EntityRule<?> entityRule) {

        try {

            EntityRuleVisual annotation = entityRule.getClass().getAnnotation(EntityRuleVisual.class);

            String entityRuleDescription = "";
            if (annotation != null) {
                entityRuleDescription = annotation.description();
            }

            List<? extends RuleItem<?>> ruleItems = entityRule.allRuleItems();

            List<RuleDescriptor> collect = ruleItems.stream().map(r -> {
                String ruleDescription = brokenRuleRegistry.getRuleDescription(r.getMessageCode().code());
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
