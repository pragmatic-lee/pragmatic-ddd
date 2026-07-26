package io.pragmatic.ddd.visual.rule;

import io.pragmatic.ddd.base.AbstractEntity;
import io.pragmatic.ddd.rules.EntityRule;
import io.pragmatic.ddd.visual.MockEntity;
import io.pragmatic.ddd.visual.MockEntityBrokenRuleRegistry;
import io.pragmatic.ddd.visual.MockEntityRule;
import com.alibaba.fastjson2.JSON;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class RuleTest {

    @Test
    public void parse() {


        RuleParser ruleParser = new RuleParser();

        ruleParser.registerDomainRule(MockEntity.class, new IRuleFinder() {
            @Override
            public <T extends AbstractEntity<?>> RuleFinderObject findEntityRuleList(Class<T> cls) {

                ArrayList<EntityRule<?>> classes = new ArrayList<>();
                classes.add(new MockEntityRule());
                return new RuleFinderObject(classes, MockEntityBrokenRuleRegistry.INSTANCE);
            }
        });


        List<RuleDescriptorGroup> parse = ruleParser.parse(MockEntity.class);

        System.out.println(JSON.toJSONString(parse));


    }
}




